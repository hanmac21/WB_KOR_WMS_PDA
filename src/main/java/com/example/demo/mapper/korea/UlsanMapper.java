package com.example.demo.mapper.korea;

import com.example.demo.vo.InventoryVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface UlsanMapper {

    Map<String, Object> getItemInfo(String barcode);

    String getPartQty(String barcode);





    void removeBarcode(Map<String, Object> m);

    int insInbound(Map<String, Object> m);

    int saveLocation(Map<String, Object> m);

    int insStockInbound(Map<String, Object> m);

    void updateLaststatusPart(Map<String, Object> m);

    void insRealStock(Map<String, Object> map);




    List<String> incomingSanghoException();

    List<Map<String, Object>> searchIncomingDetail(Map<String, Object> map);

    List<Map<String, Object>> searchIncomingSummary(Map<String, Object> map);

    List<InventoryVO> searchInventoryDetail(Map<String, Object> map);

    List<InventoryVO> searchInventorySummary(Map<String, Object> map);
}
