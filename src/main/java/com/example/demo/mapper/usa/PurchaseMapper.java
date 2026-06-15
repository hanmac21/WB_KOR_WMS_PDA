package com.example.demo.mapper.usa;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

import com.example.demo.vo.BarcodeVO;
import com.example.demo.vo.InventoryVO;
import com.example.demo.vo.ItemLocationVO;
import com.example.demo.vo.PalletDetailVO;
import com.example.demo.vo.StockHistoryVO;
import com.example.demo.vo.UnpackVO;
import com.example.demo.vo.WorkmoveVO;

@Mapper
public interface PurchaseMapper {

	int insValidation(Map<String, Object> map);

	List<String> barcodeYN(List<String> barcodes);

	List<String> existInbound(List<String> barcodes);
	
	List<String> palletExistIn(List<String> barcodes);

	List<String> existScm(List<String> barcodes);

	int saveBarcode(Map<String, Object> map);

	List<String> selectpbBarcode(String barcode);

	int selectpbBarcode(Map<String, Object> map);

	int insRealStock(Map<String, Object> map);

	int insStock(Map<String, Object> map);
	
	int insInbound(Map<String, Object> map);

	List<String> mexScmExist(List<String> barcodes);

	List<String> palletExist(List<String> barcodes);

	String selectPalletSeq(String date);

	int makePalletBarcode(Map<String, Object> map);
	
	Map<String, Object> palletManagementSearch(String barcode);

	List<ItemLocationVO> selItemLocation(String itemcode);

	int existLocation(Map<String, Object> map);
	
	//
	int saveLocation(Map<String, Object> map);
	
	// worklocation에 기본값 insert
	int insWorkLocationBasic(Map<String, Object> map);
	
	// worklocation에 피딩값 insert
	int insWorkLocationFeeding(Map<String, Object> map);

	List<String> selFactory();

	List<String> selStorage();
	
	List<String> selRack();

	List<String> selModule();

	List<String> selLevelCode();

	List<String> selPosition();

	List<String> selCar();

	int existItem(Map<String, Object> map);

	List<String> existWorkmove(Map<String, Object> map);

	// 살티오 공정불출
	int saveWorkmove(Map<String, Object> map);
	// 푸에블라 공정불출
	int saveWorkmovePuebla(Map<String, Object> map);

	String searchRoomcode(String barcode);

	int locationIssue(Map<String, Object> map);

	String selectQty(String barcode);

	PalletDetailVO palletInfo(String barcode);

	//int pur_location_checkInbound(String barcode);

	int pur_location_insert_inbound(Map<String, Object> param);

	//int pur_location_insert_realStock(Map<String, Object> param);

	/**
	 * RACK 목록 조회
	 */
	List<Map<String, Object>> selectRackList(Map<String, Object> params);
	List<Map<String, Object>> selectWorkRackList(Map<String, Object> params);

	/**
	 * RACK 기본 정보 조회
	 */
	Map<String, Object> selectRackInfo(Map<String, Object> params);
	Map<String, Object> selectWorkRackInfo(Map<String, Object> params);

	/**
	 * RACK 상세 포지션 정보 조회
	 */
	List<Map<String, Object>> selectRackDetail(Map<String, Object> params);
	List<Map<String, Object>> selectWorkRackDetail(Map<String, Object> params);

	/**
	 * 전체 창고 통계 조회
	 */
	Map<String, Object> selectWarehouseStatistics();
	
	int checkLocationRow(Map<String, String> param);
	int checkWorkLocationRow(Map<String, String> param);

	int removeLocation(Map<String, Object> map);
	
	// location에 barcode를 조건으로 N 업데이트
	int removeBarcode(Map<String, Object> map);
	
	// worklocation에 barcode를 조건으로 N 업데이트
	int removeWorkLocationBarcode(Map<String, Object> map);
	
	List<Map<String, Object>> getItemInfo(String itemcode);
	
	List<Map<String, String>> getItemInfo_barcode(String barcode);

	List<Map<String, Object>> getPalletInfo(String barcode);
	
	List<Map<String, Object>> getPartInfo(String barcode);
	
	List<InventoryVO> searchInventoryDetail(Map<String, Object> map);
	
	List<InventoryVO> searchInventorySummary(Map<String, Object> map);

	int unpack(Map<String, Object> map);

	List<UnpackVO> searchUnpack(Map<String, Object> map);
	
	// 공정불출 미도착 내역
	List<WorkmoveVO> searchWIPInputNotArrived(Map<String, Object> map);

	// 공정불출 완료 내역
	List<WorkmoveVO> searchWIPInputCompleted(Map<String, Object> map);
	
	List<WorkmoveVO> searchWIPInputDetail(Map<String, Object> map);
	
	List<WorkmoveVO> searchWIPInputSummary(Map<String, Object> map);

	List<Map<String, Object>> searchLocation(String barcode);

	List<Map<String, Object>> searchPartLabel(Map<String, Object> map);

	List<Map<String, Object>> searchPalletLabel(String barcode);

	List<Map<String, Object>> searchBoxBarcode(String barcode);

	List<String> existWipReturn(Map<String, Object> map);

	String searchWccode(String barcode);

	int wipReturn(Map<String, Object> map);

	String searchPallet(String barcode);
	
	// 입고 인보이스 리스트 조회
	List<Map<String, Object>> searchIncomingInvoiceList(Map<String, Object> map);
	
	// 인보이스 조회
	List<Map<String, Object>> searchInvoice(Map<String, Object> map);
	
	List<Map<String, Object>> searchIncomingDetail(Map<String, Object> map);

	List<Map<String, Object>> searchIncomingSummary(Map<String, Object> map);
	
	int palletN(Map<String, Object> map);

	int barcodeN(Map<String, Object> map);

	List<String> existUnpack(Map<String, Object> map);

	List<String> unpackList(Map<String, Object> map);

	List<Map<String, Object>> unpackCompleteDetail(String barcode);

	List<Map<String, Object>> unpackBarcodeList(String barcode);

	int updateUnpackStatus(Map<String, Object> map);

	int updateUnpackBarcodeYN(Map<String, Object> map);

	List<Map<String, Object>> selectLocationDetail(String location);
	List<Map<String, Object>> selectWorkLocationDetail(String location);

	List<Map<String, Object>> searchWIPReturnDetail(Map<String, Object> map);

	List<Map<String, Object>> searchWIPReturnSummary(Map<String, Object> map);

	// Stock 테이블에 입고 내역 존재하는지 검사
	List<String> findExistingInStock(List<String> barcodes);
	
	// 인바운드 테이블에 존재하는지 검사
	List<String> findInbound(List<String> barcodes);

	// 인바운드 테이블에 예외 입고 등록
	int insInput(Map<String, Object> m);

	// 입고반품 - useyn 업데이트
	void incomingReturn_updateYn(Map<String,Object> param);
	
	// 입고반품 - 입고 여부체크
	//int incomingReturn_inboundCheck(Map<String,Object> param);

	// 입고반품 - inbound insert
	void incomingReturn_insertInboundTable(Map<String, Object> map);
	
	// 입고반품 - stock insert
	void incomingReturn_insertStockTable(Map<String,Object> param);
	
	// 입고 반품 - detail
	List<Map<String, Object>> searchIncomingReturnDetail(Map<String, Object> map);
	
	// 재고 테이블에 입고 등록
	int insStockInbound(Map<String, Object> m);
	
	// 예외 입고 내역 - detail
	List<Map<String, Object>> searchexceptionInputDetail(Map<String, Object> map);
	
	// Stock 테이블에 출고 내역 존재하는지 검사
	List<String> findExistingOutStock(List<String> barcodes);
	
	// 아웃바운드 테이블에 존재하는지 검사
	List<String> findOutbound(List<String> barcodes);

	// 아웃바운드 테이블에 출고 등록
	int insOutput(Map<String, Object> m);

	// 재고 테이블에 예외 출고 등록
	int insertStockOutput(Map<String, Object> m);
	
	// 예외 출고 내역 - detail
	List<Map<String, Object>> searchexceptionOutputDetail(Map<String, Object> map);

	// 재고 조회 Detail
	List<InventoryVO> searchStockDetail(Map<String, Object> map);
	
	StockHistoryVO stockHistoryMain(String barcode);

	List<StockHistoryVO> stockHistoryList(String barcode);

	Map<String,Object> show_stockHistory_sangho(String custCode);
	
	// 창고 이동 조회
	List<BarcodeVO> searchWarehouse(List<String> barcodes);
	
	// 창고 이동 처리 전에 같은 창고인지 체크
	List<String> sameWarehouseCheck(Map<String, Object> map);

	// 창고 이동 선입선출 체크
	List<Map<String, Object>> checkFifo(Map<String, Object> map);

	// 창고 이동 처리 insert - stockmove
	void transferWarehouseStockMove(Map<String, Object> map);

	// 창고 이동 처리 insert - stock처리
	int transferWarehouseStock(Map<String, Object> map);
	
	// 창고 이동 처리 - detail
	List<Map<String, Object>> searchWarehouseDetail(Map<String, Object> map);

	// 창고 이동 처리 - summary
	List<Map<String, Object>> searchWarehouseSummary(Map<String, Object> map);
	
	// 창고 이동 처리 insert - factorymove
	void transferFactoryFactoryMove(Map<String, Object> map);

	// 창고 이동 처리 insert - stock
	void transferFactoryStock(Map<String, Object> map);
	
	// 공장 이동 처리 - detail
	List<Map<String, Object>> searchFactoryDetail(Map<String, Object> map);
	
	// 공장 이동 처리 - summary
	List<Map<String, Object>> searchFactorySummary(Map<String, Object> map);

	int stockInsertUnload(Map<String, Object> map);

	// 출고 - detail
	List<Map<String, Object>> searchLoadDetail(Map<String, Object> map);
	
	// 출고 반품 - detail
	List<Map<String, Object>> searchLoadReturnDetail(Map<String, Object> map);
	
	// 이전 위치 조회
	String getPreviousLocation(String barcode);
	// 이전 창고 조회
	String getPreviousStorage(String barcode);

	// 상호명 리스트 불러오기
	List<String> incomingSanghoLocal();
	List<String> incomingSanghoCkd();
	List<String> incomingSanghoException();
	
	int updateExceptionCheckStatus(String barcode);
	int palletExceptionCheckStatus(String barcode);
	int partExceptionCheckStatus(String barcode);
	
	int updateLaststatusPallet(Map<String, Object> map);
	int updateLaststatusPart(Map<String, Object> map);
	List<String> loadSangho();

	List<String> sameFactoryCheck(Map<String, Object> map2);

	List<String> palletBarcodeCheck(List<String> partList);

	int palletUseynN(List<String> barcodes);

	int selectLocationSave(Map<String, Object> m);

	int insStockMinus(Map<String, Object> map);

	int insWorkStockPlus(Map<String, Object> map);
	// 팔레트 조회
	List<String> findChildBarcodesForPallets(List<String> pbarcodes);

	List<String> forIncoming(List<String> list);

	int wipReturnWorkstock(Map<String, Object> map);
	int wipReturnWorkmove(Map<String, Object> map);

	List<String> forIncomingReturn(List<String> barcodes);

	List<String> IncomingOk(List<String> barcodes);

	List<String> wipOk(List<String> barcodes);

	List<String> outputOk(List<String> barcodes);

	int insSelectLocation(Map<String, Object> map);

	// 미적재 리스트 조회
	List<Map<String, Object>> unloadedList(Map<String, String> param);

	// 테이블에 존재하는지 확인
	int factoryMoveOk(List<String> barcodes);

	// 테이블 업데이트
	int completeFactoryMove(Map<String, Object> map);

	int getBarcodeStatus(List<String> barcodes);

	List<String> canMakePallet(List<String> barcodes);

	int basicLocation(Map<String, Object> palletLocationMap);

	int insStockUnpack(Map<String, Object> map);

	int insStockUnpackplus(Map<String, Object> map);

	List<String> receivingOk(List<String> barcodes);

	List<Map<String, Object>> getItemInfo2(Map<String, Object> param);

	List<String> palletInboundCheck(Map<String, Object> map);

	List<String> itemcodeList(List<String> barcodes);

	List<ItemLocationVO> wipFifo(Map<String, Object> map);

	List<Map<String, Object>> getStatus(Map<String, Object> param);

	int insOutputReturn(Map<String, Object> m);

	int basicSaveLocation(Map<String, Object> m);

	String selectStorage(String barcode);

	void insertStockOutputReturn(Map<String, Object> m);

	String searchRoomcodeyn(String barcode);

	List<String> selectPallet(List<String> barcodes3);

	PalletDetailVO palletInfoUnload(String barcode);

	void factoryReceivingStock(Map<String, Object> rc);

	String searchRoomcodeY(String pallet);

	int insInboundException(Map<String, Object> m);

	int insertExceptionReceiving(Map<String, Object> map);

	int insInboundExceptionReceiving(Map<String, Object> map);

	PalletDetailVO locationInfo(String pbarcode);
	
	// 팔레트라벨에 파트라벨 추가
	int insPalletBarcode(Map<String, Object> map);
	// 파트정보 조회
	PalletDetailVO partInfo(String barcode);
	// 팔레트라벨에 lastqty값 업데이트
	int updatePalletQtyInfo(Map<String, Object> map);
	// location 테이블에 qty값 업데이트
	int updatePalletQtyLocation(Map<String, Object> map);

	List<WorkmoveVO> searchWIPFeedingDetail(Map<String, Object> param);

	List<WorkmoveVO> searchWIPFeedingSummary(Map<String, Object> param);
	
	// 팔레트 해체 파트라벨 search
	List<Map<String, Object>> palletUnboundSearch(String barcode);

	// 예외출고
	int insOutboundException(Map<String, Object> m);
	
	// 예외입고 쿼리 source2, invoiceno 둘다 insert
	int insInboundException2(Map<String, Object> m);

	// 예외출고 invoice, source2 insert
	int insOutputException2(Map<String, Object> m);
	
	// 말일 재고실사하면 출고테이블에 N으로 insert
	int insOutboundExceptionN(Map<String, Object> m);
	
	// 말일 재고실사하면 입고테이블에 N으로 insert
	int insInboundExceptionN(Map<String, Object> m);

	// 파트라벨 품번 조정에서 품명 가져오기
	List<Map<String, Object>> partItemnameSearch(String itemcode);

	// 파트라벨 수량 조정에서 파트정보 가져오기
	Map<String, Object> partInfoSearch(String barcode);

	// 파트라벨 수량 조정 업데이트
	int partAdjustmentUpdate(Map<String, Object> param);

	// 파트라벨 수량 조정 예외출고
	int insOutputExceptionDiffQty(Map<String, Object> map);

	// 파트라벨 수량 조정 예외입고
	int insInboundExceptionDiffQty(Map<String, Object> map);

	// 파트라벨 품번 조정 업데이트
	int partAdjustmentItemcodeUpdate(Map<String, Object> param);

	// 파트라벨 품번 조정 예외출고
	void insOutputExceptionNowItemcode(Map<String, Object> map);

	// 파트라벨 품번 조정 예외입고
	void insInboundExceptionNewItemcode(Map<String, Object> map);

	// 기존 파트라벨의 정보를 가져옴
	Map<String,Object> getPartInfoForAdjust(String barcode);

	// 파트라벨의 최종수량 가져오기
	String getPartQty(String barcode);

	// 파트라벨 수량 조정 후 파트라벨 정보 업데이트
	int partAdjustmentUpdatePart(Map<String, Object> param);

	// 파트라벨 품번 조정 후 파트라벨 정보 업데이트
	int partAdjustmentItemcodeUpdatePart(Map<String, Object> param);

	// 작업장 위치 가져오기
	String getWorkshop(String barcode);

	// 창고 <-> 작업장 이동처리
	int insWorkmove(Map<String, Object> row);

	String barcodeLabelType(String barcode);

	// 창고에 저장 위치 저장 OKYN 추가
	int saveLocationOKYN(Map<String, Object> m);

	// 작업장 N업데이트
	int worklocationN(Map<String, Object> map);

	// 수량조정 로그 남기기
	int insAdjustLog(Map<String, Object> map);

	// 품번조정 로그 남기기
	int insAdjustItemcodeLog(Map<String, Object> map);


	String getItemcode(String oitemcode);

	// 고객사품번으로 아이템정보 가져오기
	Map<String, Object> getItemInfoSpec(String oitemcode);

	// erp품번으로 아이템정보 가져오기
	Map<String, Object> getItemInfoSItemcode(String part);

	// 검사 insert
	int insInspection(Map<String, Object> map);

	// 검사 목록 조회
	List<Map<String, Object>> searchInspectionList(Map<String, Object> map);

	// 양불전환 isnert
    int conditionChange(Map<String, Object> map);

	// 불량라벨로 업데이트
	int updateLabelType(String barcode);
}