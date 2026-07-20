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
            // 예: "10.00" 또는 "10.10" 또는 "10.1"
            qtyStr = parts[3];
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
        String date = vo.getDate();
        String main = vo.getMain();
        String source = vo.getSource();
        String kind = vo.getKind();
        List<String> barcodes = vo.getBarcode();
        List<String> qtys = vo.getQtys();
        String factory = vo.getFactory();
        String loginId = vo.getLoginid();
        String userName = "username";
        String storage = vo.getStorage();
//        String memo = vo.getMemo();

        for (int i = 0; i < barcodes.size(); i++) {
            String barcode = barcodes.get(i);
            String qty = (qtys != null && i < qtys.size()) ? qtys.get(i) : null;

            Map<String,Object> m = new HashMap<>();
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
            m.put("memo", "");
            m.put("okyn", "Y");
            m.put("tradebarcode", "");

            // 바코드 파싱 (인라인)
            if (barcode.startsWith("M")) {		                // SCM 바코드
                Map<String, Object> item = ulsanMapper.getItemInfo(barcode);
                m.put("itemcode", item.get("PNO"));
                m.put("qty", resolveBarcodeQty(barcode));
                m.put("type", "scm");
            } else if (barcode.length() == 11) {		            // 거래명세서
                List<Map<String,Object>> lists = ulsanMapper.getTradebarcode(barcode);
                for (Map<String, Object> row : lists) {
                    m.put("barcode", row.get("BARCODE"));
                    m.put("tradebarcode", barcode);
                    m.put("qty", row.get("QTY"));
                    m.put("itemcode", row.get("PNO"));
                    m.put("type", "trade");

                    int insInbound = ulsanMapper.insInbound(m);
                    int inslocation = ulsanMapper.saveLocation(m);
                }
                continue;   // 이 바코드 처리 완료, 다음 바코드로
            } else if (barcode.split(",").length == 6) {		 // 대차 라벨
                String[] parts = barcode.split(",");
                m.put("itemcode", parts[2]);
                m.put("type", "CART");

                // 화면에서 보낸 수량이 있으면
                if (qty != null && !qty.isEmpty()) {
                    m.put("qty", qty);
                } else {
                    m.put("qty", resolveBarcodeQty(barcode));
                }
            } else {
                result.put("response", "fail4");
                result.put("message", "지원되지 않는 바코드 형식: " + barcode);
                System.out.println("INVALID_BARCODE_FORMAT: " + barcode);
                throw new RuntimeException("INVALID_BARCODE_FORMAT");
            }

            // INSERT: 입고
            int insInbound = ulsanMapper.insInbound(m);
            int inslocation = ulsanMapper.saveLocation(m);

            if (insInbound==0) {
                result.put("response", "fail5");
                System.out.println("insInbound:"+insInbound);
                result.put("message", "재고 반영 실패(동시성). 다시 시도: " + barcode);
                throw new RuntimeException("STOCK_TXN_FAILED");
            }
        }

        result.put("response", "success");
        return result;
    }

    // 수기 입력 입고 insert
    @Transactional(transactionManager = "koreaTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> insInboundManual(BarcodeVO vo) {
        Map<String, Object> result = new HashMap<>();
        final String date = vo.getDate();
        final String main = vo.getMain();
        final String source = vo.getSource();
        final String kind = vo.getKind();
        final String factory = vo.getFactory();
        final String loginId = vo.getLoginid();
        final String userName = "username";
        final String storage = vo.getStorage();

        final List<String> itemcodes = vo.getItemcodes();
        final List<String> qtys = vo.getQtys();

        for (int i = 0; i < itemcodes.size(); i++) {
            String itemcode = itemcodes.get(i);
            String qty = qtys.get(i);

            Map<String, Object> m = new HashMap<>();
            m.put("date", date);
            m.put("barcode", "");
            m.put("loginid", loginId);
            m.put("username", userName);
            m.put("source", source);
            m.put("main", main);
            m.put("kind", kind);
            m.put("storage", storage);
            m.put("factory", factory);
            m.put("location", factory + "-" + storage);
            m.put("rack", "");
            m.put("module", "");
            m.put("levelcode", "");
            m.put("position", "");
            m.put("memo", "");
            m.put("okyn", "Y");
            m.put("tradebarcode", "");

            m.put("itemcode", itemcode);
            m.put("qty", qty);
            m.put("type", "manual");

            int insInbound = ulsanMapper.insInbound(m);
            int inslocation = ulsanMapper.saveLocation(m);

            if (insInbound == 0) {
                result.put("response", "fail5");
                result.put("message", "재고 반영 실패(동시성). 다시 시도: " + itemcode);
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
        List<String> qtys =  request.getQtys();
        String loginid = request.getLoginid();

        for (int i = 0; i < barcodes.size(); i++) {
            String barcode = barcodes.get(i);
            String qty = qtys.get(i);

            Map<String, Object> map = new HashMap<>();
            map.put("date", date);
            map.put("barcode", barcode);
            map.put("loginid", loginid);
            map.put("storage", storage);
            map.put("location", factory+"-"+storage);
            map.put("factory", factory);
            map.put("source", "BARCODECOUNT");
            map.put("kind", "COUNT");

            if (barcode.startsWith("M")) {                               // scm 라벨
                Map<String, Object> item = ulsanMapper.getItemInfo(barcode);
                map.put("itemcode", item.get("PNO"));
                map.put("qty", item.get("QTY"));
                map.put("type", "scm");
            } else if (barcode.split(",",-1).length == 6){                  // 대차
                String[] parts = barcode.split(",", -1);
                map.put("itemcode", parts[2]);
                map.put("type", "cart");

                // 화면에서 보낸 수량이 있으면
                if (qty != null && !qty.isEmpty()) {
                    map.put("qty", qty);
                } else {
                    map.put("qty", resolveBarcodeQty(barcode));
                }
            } else if (barcode.startsWith("[)>")){                  // 부품
                String[] parts = barcode.split("\\|");
                String spec = parts[3].substring(1);
                String item = ulsanMapper.getHeadrestInfo(spec);
                map.put("itemcode", item);
                map.put("qty", "1");
                map.put("type", "headrest");
            } else {
                result.put("response", "fail4");
                result.put("message", "지원되지 않는 바코드 형식: " + barcode);
                System.out.println("INVALID_BARCODE_FORMAT: " + barcode);
                throw new RuntimeException("INVALID_BARCODE_FORMAT");
            }

            ulsanMapper.insRealStock(map);
            System.out.println("저장할 바코드: " + barcode);
        }
        result.put("response", "success");
		return result;
    }

    // 재고실사 - 수기
    @Transactional(transactionManager = "koreaTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> insertRealStockManual(BarcodeVO request) {
        Map<String, Object> result = new HashMap<>();
        String date = request.getDate();
        String factory = request.getFactory();
        String storage = request.getStorage();
        String loginid = request.getLoginid();

        List<String> itemcodes = request.getItemcodes();
        List<String> qtys = request.getQtys();

        for (int i = 0; i < itemcodes.size(); i++) {
            String itemcode = itemcodes.get(i);
            String qty = qtys.get(i);

            Map<String, Object> map = new HashMap<>();
            map.put("date", date);
            map.put("barcode", "");
            map.put("loginid", loginid);
            map.put("storage", storage);
            map.put("location", factory + "-" + storage);
            map.put("factory", factory);
            map.put("source", "ITEMCODECOUNT");
            map.put("kind", "COUNT");

            map.put("itemcode", itemcode);
            map.put("qty", qty);
            map.put("type", "manual");

            ulsanMapper.insRealStock(map);
            System.out.println("저장할 품번: " + itemcode + " / 수량: " + qty);
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

    public List<String> getStorage(String type) {
        return ulsanMapper.getStorage(type);
    }

    public List<Map<String, Object>> getItemList() {
        return ulsanMapper.getItemList();
    }

    public Map<String, Object> searchSequenceList(Map<String, Object> param) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> list = ulsanMapper.searchSequenceList(param);
            result.put("list", list);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
