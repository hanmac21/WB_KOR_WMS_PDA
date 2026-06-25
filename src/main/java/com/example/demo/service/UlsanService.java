package com.example.demo.service;

import com.example.demo.mapper.korea.UlsanMapper;
import com.example.demo.utils.FilterResult;
import com.example.demo.validator.BarcodeValidator;
import com.example.demo.vo.BarcodeVO;
import com.example.demo.vo.InventoryVO;
import com.example.demo.vo.PalletDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UlsanService {
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
            if (parts.length == 6) {
                qtyStr = parts[3];
            } else {
                qtyStr = parts[3];   // 예: "10.00" 또는 "10.10" 또는 "10.1"
            }
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

    //재고실사 - 바코드
    @Transactional(transactionManager = "koreaTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> insRealStock(BarcodeVO request) {
        String date = request.getDate();

        Map<String, Object> result = new HashMap<>();
        String factory = request.getFactory();
        String storage = request.getStorage();
        List<String> barcodes = request.getBarcode();
        String loginid = request.getLoginid();

        for (String barcode : barcodes) {		// 바코드 실사
            Map<String, Object> map = new HashMap<>();
            map.put("date", date);
            map.put("barcode", barcode);
            map.put("loginid", loginid);
            map.put("storage", storage);
            map.put("location", factory+"-"+storage);
            map.put("factory", factory);
            map.put("source", "BARCODECOUNT");
            map.put("kind", "COUNT");

            if (barcode.split(",").length == 5) {                               // 통합 라벨
                map.put("itemcode", barcode.split(",")[0]);
                map.put("qty", resolveBarcodeQty(barcode));
                map.put("type", "box");
            } else if (barcode.split(",",-1).length == 6){                  // 대차
                String[] parts = barcode.split(",", -1);
                map.put("itemcode", parts[2]);
                map.put("qty", resolveBarcodeQty(barcode));
                map.put("type", "trolley");
            }  else {
                result.put("response", "fail4");
                result.put("message", "지원되지 않는 바코드 형식: " + barcode);
                System.out.println("INVALID_BARCODE_FORMAT: " + barcode);
                throw new RuntimeException("INVALID_BARCODE_FORMAT");
            }

            ulsanMapper.insRealStock(map);
            System.out.println("저장할 바코드: " + barcode);
            map.put("laststatus",20);
            ulsanMapper.updateLaststatusPart(map);
        }
        result.put("response", "success");
		return result;
    }

    // 재고실사현황 - Detail
    public Map<String, Object> searchInventoryDetail(Map<String, Object> map) {
        System.out.println("itemcode: " + map.get("itemcode"));
        Map<String, Object> result = new HashMap<>();
        try {
            List<InventoryVO> list = ulsanMapper.searchInventoryDetail(map);
            result.put("list", list);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    // 재고실사현황 - summary
    public Map<String, Object> searchInventorySummary(Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<InventoryVO> list = ulsanMapper.searchInventorySummary(map);
            result.put("list", list);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    // 출고 insert
    @Transactional(transactionManager = "koreaTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> insOutput(BarcodeVO vo) {
        Map<String, Object> result = new HashMap<>();
        final String date = vo.getDate();
        final String main = vo.getMain();
        final String source = vo.getSource();
        final String kind = vo.getKind();
        final List<String> bc = vo.getBarcode();
        String factory = vo.getFactory();
        String memo = vo.getMemo();
        System.out.println("memo @@@@ :"+memo);
        final String loginId = vo.getLoginid();
        final String userName = "username";

        String shipTo = vo.getShipTo();		// 출고처
        List<String> barcodes = vo.getBarcode();

        for (String barcode : barcodes) {
            String custcode = "";
            if("LEAR".equalsIgnoreCase(shipTo)){
                custcode = "1981";
            }else if("TRANSYS_".equalsIgnoreCase(shipTo)){
                custcode = "1380";
            }

            Map<String, Object> m = new HashMap<>();
            m.put("factory", factory);
            m.put("date", date);
            m.put("barcode", barcode);
            m.put("loginid", loginId);
            m.put("username", userName);
            m.put("source", source);
            m.put("source2", "PURCHASE");
            m.put("custcode", custcode);
            m.put("custname", shipTo);
            m.put("main", main);
            m.put("kind", kind);
            m.put("memo", memo);

            // 바코드 파싱 (인라인)
            if (barcode.split(",").length == 6 && barcode.endsWith("WBT")) {
                String[] parts = barcode.split(",", -1);
                m.put("oitemcode", parts[1]);
                m.put("itemcode", parts[2]);
                m.put("seq", parts[4]);
                m.put("qty", resolveBarcodeQty(barcode));
            } else {
                result.put("response", "fail4");
                result.put("message", "지원되지 않는 바코드 형식: " + barcode);
                throw new RuntimeException("INVALID_BARCODE_FORMAT");
            }

            m.put("dmemo","OUTPUT");

            int insOutbound = ulsanMapper.insOutput(m);
            int affected = ulsanMapper.insertStockOutput(m);
            ulsanMapper.removeBarcode(m);

            if (insOutbound == 0 || affected == 0) {
                result.put("response", "fail5");
                result.put("message", "재고 반영 실패(동시성). 다시 시도: " + barcode);
                throw new RuntimeException("STOCK_TXN_FAILED");
            }
        }


        result.put("response", "success");
        return result;
    }

    public Map<String, Object> searchLoadDetail(Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> list = ulsanMapper.searchLoadDetail(map);
            result.put("list", list);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    // 트랜시스 검증 저장
    @Transactional(transactionManager = "koreaTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> saveValidation(Map<String, Object> param) {
        Map<String, Object> result = new HashMap<>();
        try {
            String cartBarcode = (String) param.get("cartBarcode");
            String cartQty     = String.valueOf(param.get("cartQty"));
            String assyQty     = String.valueOf(param.get("assyQty"));
            String itemcode    = (String) param.get("oitemcode");
            String source      = (String) param.get("source");
            String factory      = (String) param.get("factory");

            @SuppressWarnings("unchecked")
            List<String> assyBarcodes = (List<String>) param.get("assyBarcodes");

            for (String assyBarcode : assyBarcodes) {
                Map<String, Object> m = new HashMap<>();
                m.put("cartBarcode", cartBarcode);
                m.put("assyBarcode", assyBarcode);
                m.put("cartQty",     cartQty);
                m.put("assyQty",     1);
                m.put("oitemcode",    itemcode);
                m.put("source",      source);
                m.put("factory",      factory);
                ulsanMapper.insValidation(m);
            }

            result.put("response", "success");
        } catch (Exception e) {
            result.put("response", "fail");
            result.put("message", e.getMessage());
            throw e;
        }
        return result;
    }
}
