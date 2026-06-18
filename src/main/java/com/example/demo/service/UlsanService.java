package com.example.demo.service;

import com.example.demo.mapper.korea.UlsanMapper;
import com.example.demo.validator.BarcodeValidator;
import com.example.demo.vo.BarcodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UlsanService {

    private final BarcodeValidator barcodeValidator;

    private final UlsanMapper ulsanMapper;

    // ====== Utils ======
    // 바코드에 있는 수량 정수 또느 실수로 바꾸는 메소드
    public static String formatQty(String qty) {
        if (qty == null || qty.isEmpty()) {
            return qty;
        }
        try {
            // 소수점까지 포함해서 읽고
            BigDecimal bd = new BigDecimal(qty);
            // 불필요한 0 제거
            bd = bd.stripTrailingZeros();
            // 문자열로 반환
            return bd.toPlainString();
        } catch (NumberFormatException e) {
            return qty; // 숫자가 아니면 그대로 반환
        }
    }

    // 파트라벨 수량 가져오기
    private String resolveBarcodeQty(String barcode) {
        // 1) DB에 저장된 수량 (있으면 우선 사용)
        String lastqty = ulsanMapper.getPartQty(barcode);

        String qtyStr;
        if (lastqty == null || lastqty.isEmpty() || "0".equals(lastqty)) {
            // DB에 없으면 바코드에서 4번째 필드 사용
            String[] parts = barcode.split(",");
            qtyStr = parts[3];   // 예: "10.00" 또는 "10.10" 또는 "10.1"
        } else {
            qtyStr = lastqty;
        }

        // 2) BigDecimal로 정확하게 파싱
        java.math.BigDecimal bd = new java.math.BigDecimal(qtyStr);

        // 3) 끝의 0 정리 (10.00 -> 10, 10.10 -> 10.1)
        bd = bd.stripTrailingZeros();

        // 4) BigDecimal을 일반적인 숫자 형태의 문자열로 변환
        return bd.toPlainString();
    }
    // ===================

    // 입고 insert
    @Transactional(transactionManager = "koreaTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> insInbound(BarcodeVO vo) {
        Map<String, Object> result = new HashMap<>();
        final String date = vo.getDate();
        final String main = vo.getMain();
        final String source = vo.getSource();
        final String kind = vo.getKind();
        final List<String> barcodes = vo.getBarcode();
        String factory = vo.getFactory();
        final String loginId = vo.getLoginid();
        final String userName = "username";
        final String storage = vo.getStorage();
        String memo = vo.getMemo();

//        Map<String, Object> magamMap = new HashMap<String, Object>();
//        magamMap.put("date", date);
//        magamMap.put("loginid", loginId);
//        // 마감 확인
//        Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
//        if (!checkClosedMonth.isEmpty()) {
//            return checkClosedMonth; // 실패 시 리턴
//        }

        // 등록이 안되어 있으면 T_SCM_BARCODE 테이블에 바코드 생성
        Map<String, Object> lotCheckResult = barcodeValidator.lotCheck(vo.getBarcode());
        if (!lotCheckResult.isEmpty()) {
            return lotCheckResult;
        }

        // 등록된 바코드인지 체크
        Map<String, Object> notRegistered = barcodeValidator.notRegistered(vo.getBarcode());
        if (!notRegistered.isEmpty()) {
            return notRegistered; // 실패 시 리턴
        }

        // 이미 해당 창고에 있는지 체크
        Map <String, Object> checkMap = new HashMap<String, Object>();
        checkMap.put("list", vo.getBarcode());
        checkMap.put("factory", factory);
        checkMap.put("storage", storage);
        Map<String, Object> storageCheckIn = barcodeValidator.storageCheckIn(checkMap);
        if (!storageCheckIn.isEmpty()) {
            return storageCheckIn; // 실패 시 리턴
        }

        // 입고시 다른 창고나 다른공장에 있으면 창고이동 하라고 메시지
        Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
        if (!inputStorageCheck.isEmpty()) {
            return inputStorageCheck; // 실패 시 리턴
        }

        // 2. 10이상인거 가져옴
        Map<String, Object> alreadyIncoming = barcodeValidator.alreadyIncoming(vo.getBarcode());
        if (!alreadyIncoming.isEmpty()) {
            return alreadyIncoming; // 실패 시 리턴
        }

        // 출고된 바코드인지 체크
        Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(vo.getBarcode());
        if (!alreadyLoad.isEmpty()) {
            return alreadyLoad; // 실패 시 리턴
        }

        List<String> list = ulsanMapper.forIncoming(barcodes);	// laststatus 1~9사이
        List<String> missingBarcodes = new ArrayList<>(barcodes);
        missingBarcodes.removeAll(list);

        if(!missingBarcodes.isEmpty()) {
            result.put("barcode",missingBarcodes);
            result.put("response","warning.barcode.cannotIncoming");
            return result;
        }

        for (String barcode : barcodes) {
            Map<String,Object> m = new HashMap<String, Object>();
            m.put("date", date);
            m.put("barcode", barcode);
            m.put("loginid", loginId);
            m.put("username", userName);
            m.put("source", source);
            m.put("main", main);
            m.put("kind", kind);
            m.put("storage", storage);
            m.put("factory", factory);
            m.put("location", factory+"-"+storage);
            m.put("rack", "");
            m.put("module", "");
            m.put("levelcode", "");
            m.put("position", "");
            m.put("memo", memo);
            m.put("okyn", "Y");

            // 바코드 파싱 (인라인)
            if (barcode.split(",").length == 4) {		            // SCM라벨바코드
                String[] parts = barcode.split(",");
                m.put("itemcode", parts[0]);
                m.put("bdate", "");
                m.put("seq", "");
                m.put("qty", resolveBarcodeQty(barcode));
                m.put("type", "box");
            } else if (barcode.startsWith("M")) {		                // SCM라벨바코드
                Map<String, Object> item = ulsanMapper.getItemInfo(barcode);
                m.put("itemcode", item.get("PNO"));
                m.put("bdate", "");
                m.put("seq", "");
                m.put("qty", resolveBarcodeQty(barcode));
                m.put("type", "box");
            } else {
                result.put("response", "fail4");
                result.put("message", "지원되지 않는 바코드 형식: " + barcode);
                System.out.println("INVALID_BARCODE_FORMAT: " + barcode);
                throw new RuntimeException("INVALID_BARCODE_FORMAT");
            }

            int insInbound = 0;
            // INSERT: 입고
            insInbound = ulsanMapper.insInbound(m);

            m.put("laststatus", 10);
            m.put("dmemo", "INCOMING");
            ulsanMapper.removeBarcode(m);
            int inslocation = ulsanMapper.saveLocation(m);
            int affected = 0;
            affected = ulsanMapper.insStockInbound(m);
            ulsanMapper.updateLaststatusPart(m);

            if (insInbound==0 || affected == 0) {
                result.put("response", "fail5");
                System.out.println("insInbound:"+insInbound+" affected:"+affected);
                result.put("message", "재고 반영 실패(동시성). 다시 시도: " + barcode);
                throw new RuntimeException("STOCK_TXN_FAILED");
            }
        }

        result.put("response", "success");
        return result;
    }

    public List<String> incomingSanghoException() {
        return ulsanMapper.incomingSanghoException();
    }

    public Map<String, Object> searchIncomingDetail(Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> list = ulsanMapper.searchIncomingDetail(map);
            result.put("list", list);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> searchIncomingSummary(Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> list = ulsanMapper.searchIncomingSummary(map);
            result.put("list", list);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
