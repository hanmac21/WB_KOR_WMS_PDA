package com.example.demo.mapper.mexico;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

import com.example.demo.vo.ItemLocationVO;

@Mapper
public interface SalesMapper {

	List<String> existOutbound(List<String> barcodes);

	int insOutbound(Map<String, Object> map);

	List<String> selectpbBarcode(String barcode);

	List<String> selCust();
	
	// 파트너아이템코드 가져오기
	String getPItemcode(String itemcode);
	
	// 영업이송
	int insSalesTransfer(Map<String, Object> map);

	// 영업이송 내역
	List<Map<String, Object>> searchSalesTransferDetail(Map<String, Object> map);
	
	// 생산품이동 내역 삭제
	int searchTransferDetailDel(Map<String, Object> m);

	// 작업장 위치 N업데이트
	int updateWorkLocationN(Map<String, Object> param);

	// work_stock에서 수량 마이너스
	int insWorkStockMinus(Map<String, Object> param);

	// 로케이션에 기본적재
	int insBasicLocation(Map<String, Object> param);

	// worklocation에서 N값을 Y로 업데이트
	int updateWorkLocationY(Map<String, Object> m);

	// 영업 출고
	int insSalesOutput(Map<String, Object> m);

	// 생산품 이송 삭제
	int deleteEntersub(Map<String, String> paramMap);
	

	
	
}