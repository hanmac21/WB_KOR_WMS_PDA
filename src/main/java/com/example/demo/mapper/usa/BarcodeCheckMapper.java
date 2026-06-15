package com.example.demo.mapper.usa;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BarcodeCheckMapper {
	// 등록안된 바코드
	List<String> notRegistered (List<String> barcodes);
	
	//이미 입고된 바코드
	
	
	//입고되지 않은 바코드 1~9
	List<String> forIncoming (List<String> barcodes);
	
	// 이미 등록됐는지 확인
	List<String> alreadyIncoming(List<String> barcodes);

	// 공장이송중인 바코드인지 확인 상태값 5인지 확인/ 입고에서 사용
	List<String> factoryMoving(List<String> barcodes);

	// 출고 불출 가능한지 10~29
	List<String> storageCheck (Map<String, Object> map);
	
	//언팩해야하는 바코드
	List<String> unpackCheck (List<String> barcodes);
	
	// 팔레트에 속한 파트 바코드 확인
	List<String> palletExist (List<String> barcodes);

	List<String> factoryCheck(Map<String, Object> map);
	
	// 불출반납한 내역있는지 확인
	List<String> wipreturnCheck(List<String> barcodes);
	
	// 공장이송 - 보낸내역있는지 체크
	List<String> sendingCheck(List<String> barcodes);

	// 공장이송 - 받은내역있는지 체크
	List<String> receivingCheck(List<String> barcodes);
	
	// 반제품인지 (입고,출고에서 사용)
	List<String> semiProduction(List<String> barcodes);

	List<String> semiProductionBarcodeProductionCheck(List<String> barcodes);

	// 상태가 90보다 작은지체크(에러바코드값이 90)
	List<String> errorBarcodeCheck(List<String> barcodes);
	
	// 상태값이 91인 바코드
	List<String> alreadyError(List<String> barcodes);
	
	
	// 상태가 10~29인바코드 출고반품용
	List<String> notStorageCheck(List<String> list);

	// 반제품인데 무상사급 (006 and freeofcharge =1) or 002(상품), 004(원재료), 005(부자재) 면 입고가능
	List<String> incomingCodeCheck(List<String> barcodes);

	//004(원재료), 005(부자재)이면 출고제한
	List<String> materialCodeCheck(List<String> barcodes);

	List<String> goodsCodeCheck(List<String> barcodes);
	
	// 불출반납할때 사용자가 선택한 값이 맞는지 확인
	List<String> wccodeCheck(Map<String, Object> map);
	
	// 이미 불출 ready된 바코드
	List<String> alreadyWIPReady(List<String> barcodes);

	// 이미 불출된 바코드
	List<String> alreadyWIP(List<String> barcodes);
	
	// 이미 출고된 바코드
	List<String> alreadyLoad(List<String> barcodes);

	//실사시 이미 재고로 잡힌건지 확인, 바코드실사, 로케이션 실사
	List<String> forStockCount(List<String> list);

	// 팔레트가 해체된 경우 모든 메뉴에서 사용
	List<String> palletNCheck(List<String> barcodes);
	
	// 해당창고에 없는지 확인 출고반품, 불출반납, 입고
	List<String> storageCheckIn(Map<String,Object> map);
	
	// 입고시 같은 공장에 잇는지 확인
	List<String> inputFactoryCheck(Map<String, Object> map);
	
	// 입고시 같은 창고에 잇는지 확인
	List<String> inputStorageCheck(Map<String, Object> map);

	// 바코드가 location table에 있는지 확인
	List<String> barcodeLocation(Map<String, Object> map);
	
	// 바코드가 v_stockinfo에 있는지 확인
	List<String> barcodeStockInfo(Map<String, Object> map);

	List<String> factoryCheckIn(Map<String, Object> map);

	// 평택 출고 테이블에 있는지 확인
	List<String> ptCheck(Map<String, Object> map);

	// 피딩에서 사용 해당작업장에 있는 것만 사용가능
	List<String> workLocationCheck(Map<String, Object> map);
	
	// 제품으로 실적잡혔는지 확인
	List<String> productionBarcodeProductionCheck(List<String> barcodes);
	
	// 반제품006 이면 반환
	List<String> semiProductionCheck(List<String> barcodes);

	// 작업장에 있는지 확인 작업장에 있는걸 반환, 작업장 예외입고에서 사용
	List<String> existWorkLocation(Map<String, Object> map);

	 // 생산품이동에서 사용 작업장내에 Y, PRODUCTION
	List<String> workLocationY(Map<String, Object> map);

	// 제품001,002, 006 이면 반환해서 제거  제품 생산실적에서 사용 , 영업입고에도 있지만 영업입고메뉴사용안함
	List<String> productionCheck(List<String> barcodes);

	// 파트바코드 location테이블에 존재하는지 확인
	List<String> partLocationCheck(Map<String, Object> map);

	// 파트바코드 storage동일한지 location테이블로 확인
	List<String> partLocationStorageCheck(Map<String, Object> map);

	// 파트바코드 공장 동일한지 location테이블로 학인
	List<String> partLocationFactoryCheck(Map<String, Object> map);

	// worklocation에 존재하는 바코드
	List<String> barcodeWorkStockInfo(Map<String, Object> map);

	// work테이블에 있는지 확인
	List<String> workCheck(Map<String, Object> map);

	// 레드케이즈 창고가 아닌 location에 있는지 확인
	List<String> redcageCheck(Map<String, Object> map);

	// 레드케이지에 있는 바코드를 가져옴
	List<String> redcageExistCheck(Map<String, Object> map);

	// 불량바코드인지 확인
	List<String> defectiveCheck(Map<String, Object> map);

	// 이미 판정된라벨인지 체크
	List<String> inspectionCheck(Map<String, Object> map);
	
	// 등록된 바코드 목록 가져오기
	List<String> lotCheck(List<String> barcode);

	// 상태값 91일 이상 가져옴
	List<String> check91(Map<String, Object> map);

	// 바코드 생성
	int makeBarcode(Map<String, Object> map);

	List<String> c001Check(List<String> barcodes);

	// 같은 품번인지 확인 (품번 전환으로 다를 수 있음)
	List<String> itemcodeCheck(Map<String, Object> map);

	// 이미 출고된 바코드 outbound에서 조회
    List<String> alreadyOutbound(List<String> barcodes);

	// 이미 창고 샌딩된 바코드
    List<String> alreadyStockmoveSending(List<String> barcodes);

	// 이미 출고반납된 바코드
	List<String> alreadyLoadReturn(List<String> barcodes);

	// 이미 입고반품된 바코드
	List<String> alreadyIncomingReturn(Map<String, Object> map);
}