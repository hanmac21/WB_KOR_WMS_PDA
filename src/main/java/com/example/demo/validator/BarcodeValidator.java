package com.example.demo.validator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.demo.mapper.usa.BarcodeCheckMapper;
import com.example.demo.mapper.usa.PurchaseMapper;

@Component
public class BarcodeValidator {

    private final PurchaseMapper purchaseMapper;

    private final BarcodeCheckMapper barcodeMapper;

    public BarcodeValidator(PurchaseMapper purchaseMapper, BarcodeCheckMapper barcodeMapper) {
        this.purchaseMapper = purchaseMapper;
		this.barcodeMapper = barcodeMapper;
    }

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
 	
 	// 팔레트가 해체된 경우, 모든 메뉴에서 사용 251107 일부 해체된 팔레트 라벨 사용불가 완전 해체된건 notRegistered 여기서 걸러짐
 	public Map<String, Object> palletNCheck(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.palletNCheck(barcodes);		// 251031팔레트 추가삭제하면서 N 조건을 Y로 바꾸고 removeAll로 변경
        //List<String> missing = new ArrayList<>(barcodes);
        //missing.removeAll(existBarcode);
        if (!existBarcode.isEmpty()) { 
            result.put("barcode", existBarcode);
            result.put("response", "warning.pallet.unbound");
        }
        return result; 
    }
 	
 	//001(제품), 002(상품)이면 불출제한
 	public Map<String, Object> goodsCodeCheck(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.goodsCodeCheck(barcodes);
        if (!existBarcode.isEmpty()) { 
            result.put("barcode", existBarcode);
            result.put("response", "warning.product.cannot");
        }
        return result; 
    }
 	
 	// 반제품인데 무상사급 (006 and freeofcharge =1) or 002(상품), 004(원재료), 005(부자재) 면 입고가능 입고에서만 사용, 예외입고 사용 X
 	public Map<String, Object> incomingCodeCheck(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.incomingCodeCheck(barcodes);
        List<String> missing = new ArrayList<>(barcodes);
        missing.removeAll(existBarcode);
        if (!missing.isEmpty()) { 
            result.put("barcode", missing);
            result.put("response", "warning.barcode.semi");
        }
        return result; 
    }
 	
 	// 제품001,002, 006 이면 반환해서 제거  제품 생산실적에서 사용 , 영업입고에도 있지만 영업입고메뉴사용안함
  	public Map<String, Object> productionCheck(List<String> barcodes) {
  		Map<String, Object> result = new HashMap<>();
  		List<String> existBarcode = barcodeMapper.productionCheck(barcodes);
  		List<String> missing = new ArrayList<>(barcodes);
  		missing.removeAll(existBarcode);
  		if (!missing.isEmpty()) { 
  			result.put("barcode", missing);
  			result.put("response", "warning.barcode.product.not");
  		}
  		return result; 
  	}
  	
  	// c001은 반제품이지만 살티오에서 생산안함
  	public Map<String, Object> c001Check(List<String> barcodes) {
  		Map<String, Object> result = new HashMap<>();
  		List<String> existBarcode = barcodeMapper.c001Check(barcodes);
  		if (!existBarcode.isEmpty()) { 
  			result.put("barcode", existBarcode);
  			result.put("response", "warning.barcode.production.cannot");
  		}
  		return result; 
  	}

 	// 반제품006 이면 반환해서 제거  반제품 생산실적에서 사용
 	public Map<String, Object> semiProductionCheck(List<String> barcodes) {
 		Map<String, Object> result = new HashMap<>();
 		List<String> existBarcode = barcodeMapper.semiProductionCheck(barcodes);
 		List<String> missing = new ArrayList<>(barcodes);
 		missing.removeAll(existBarcode);
 		if (!missing.isEmpty()) { 
 			result.put("barcode", missing);
 			result.put("response", "warning.barcode.semi.not");
 		}
 		return result; 
 	}
 	
 	//004(원재료), 005(부자재), 006(반제품 )이면 출고제한, 출고반품 제한  
 	public Map<String, Object> materialCodeCheck(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.materialCodeCheck(barcodes);
        if (!existBarcode.isEmpty()) { 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.material.cannot.use");
        }
        return result; 
    }
 	
 	// 0. 언팩하고 등록안된바코드 useyn ='N'
    public Map<String, Object> unpackCheck(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.unpackCheck(barcodes);
        if (!existBarcode.isEmpty()) { 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.unpack.notcomplete");
        }
        return result; 
    }
    
 	// 1. 등록안된바코드 useyn ='Y' 찾아서 remove
    public Map<String, Object> notRegistered(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.notRegistered(barcodes);
        List<String> missing = new ArrayList<>(barcodes);
        missing.removeAll(existBarcode);
        if (!missing.isEmpty()) { 
            result.put("barcode", missing);
            result.put("response", "warning.barcode.notregistered");
        }
        return result; 
    }
    
    // 2입고 가능한 상태인지 확인	// 입고에서만 사용안해도될듯
    public Map<String, Object> forIncoming(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.forIncoming(barcodes);
        if (!existBarcode.isEmpty()) { 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.duplicate");
        }
        return result; 
    }
    // 2.5 이미 입고된 바코드	// 입고에서만 사용
    public Map<String, Object> alreadyIncoming(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.alreadyIncoming(barcodes);
        if (!existBarcode.isEmpty()) { 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.duplicate");
        }
        return result; 
    }
    
    // 공장이송중인 바코드인지 확인 상태값 5인지 확인/ 입고에서 사용
    public Map<String, Object> factoryMoving(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.factoryMoving(barcodes);
        if (!existBarcode.isEmpty()) { 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.factorymoving");
        }
        return result; 
    }
    
    // 이미 wip readye된 바코드 // 
    public Map<String, Object> alreadyWIPReady(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> existBarcode = barcodeMapper.alreadyWIPReady(barcodes);
    	if (!existBarcode.isEmpty()) { 
    		result.put("barcode", existBarcode);
    		result.put("response", "warning.barcode.already.wip");
    	}
    	return result; 
    }

    // 이미 불출된 바코드 // 
    public Map<String, Object> alreadyWIP(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.alreadyWIP(barcodes);
        if (!existBarcode.isEmpty()) { 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.already.wip");
        }
        return result; 
    }
    
    // 이미 불출된 바코드 불출반납사용하라고 메시지만 변경 입고에서 사용 // 
    public Map<String, Object> alreadyWIPMessage(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.alreadyWIP(barcodes);
        if (!existBarcode.isEmpty()) { 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.already.wip.messaage");
        }
        return result; 
    }
    
    // 이미 출고된 바코드
    public Map<String, Object> alreadyLoad(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.alreadyLoad(barcodes);
        if (!existBarcode.isEmpty()) { 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.already.load");
        }
        return result; 
    }

    // 이미 출고된 바코드 outbound에서 조회
    public Map<String, Object> alreadyOutbound(List<String> barcodes) {
        Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.alreadyOutbound(barcodes);
        if (!existBarcode.isEmpty()) {
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.already.load");
        }
        return result;
    }

    // 이미 출고된 바코드 outbound에서 조회
    public Map<String, Object> alreadyLoadReturn(List<String> barcodes) {
        Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.alreadyLoadReturn(barcodes);
        if (!existBarcode.isEmpty()) {
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.already.loadreturn");
        }
        return result;
    }

    // 이미 샌딩된 바코드
    public Map<String, Object> alreadyStockmoveSending(List<String> barcodes) {
        Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.alreadyStockmoveSending(barcodes);
        if (!existBarcode.isEmpty()) {
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.already.sending");
        }
        return result;
    }
    
    // laststatus 10~29사이인 바코드
    public Map<String, Object> factoryCheck(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> existBarcode = barcodeMapper.factoryCheck(map);
    	List<String> missing = new ArrayList<>((List<String>)map.get("list"));
        missing.removeAll(existBarcode);
        System.out.println("미싱바코드 : "+missing);
    	if (!missing.isEmpty()) { 
    		result.put("barcode", missing);
    		result.put("response", "warning.barcode.storagecheck");
    	}
    	return result; 
    }
    
    // 3.5. 같은창고내 있는지 확인
    public Map<String, Object> storageCheck(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> existBarcode = barcodeMapper.storageCheck(map);
    	List<String> missing = new ArrayList<>((List<String>)map.get("list"));
        missing.removeAll(existBarcode);
        System.out.println("같은 창고 미싱바코드 : "+missing);
    	if (!missing.isEmpty()) { 
    		result.put("barcode", missing);
    		result.put("response", "warning.barcode.storagecheck");
    	}
    	return result; 
    }
    
    // 파트바코드가 해당공장에 있는지 location에 있는지 확인 팔레트생성에서 사용 location테이블에 없는건 걸러내지 못함
    public Map<String, Object> partLocationFactoryCheck(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> partLocationFactoryCheck = barcodeMapper.partLocationFactoryCheck(map);
    	if (!partLocationFactoryCheck.isEmpty()) {
    		result.put("barcode", partLocationFactoryCheck);
    		result.put("response", "warning.barcode.diff.factory");
    	}
    	return result; 
    }
    
    // 파트바코드가 해당 창고에 있는지 location에 있는지 확인 팔레트생성에서 사용 location테이블에 없는건 걸러내지 못함
    public Map<String, Object> partLocationStorageCheck(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> partLocationStorageCheck = barcodeMapper.partLocationStorageCheck(map);
    	if (!partLocationStorageCheck.isEmpty()) {
    		result.put("barcode", partLocationStorageCheck);
    		result.put("response", "warning.barcode.diff.storage");
    	}
    	return result; 
    }
    
    // 파트바코드가 location에 있는지 확인
    public Map<String, Object> partLocationCheck(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> partLocationCheck = barcodeMapper.partLocationCheck(map);
    	List<String> missing = new ArrayList<>((List<String>)map.get("list"));
        missing.removeAll(partLocationCheck);
    	if (!missing.isEmpty()) {
    		result.put("barcode", missing);
    		result.put("response", "warning.barcode.storagecheck");
    	}
    	return result; 
    }
    
    // 입고시 다른공장에 있는거 return, 불출반납, 출고반납 적용
    public Map<String, Object> inputFactoryCheck(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> inputFactoryCheck = barcodeMapper.inputFactoryCheck(map);
    	if (!inputFactoryCheck.isEmpty()) {
    		result.put("barcode", inputFactoryCheck);
    		result.put("response", "warning.barcode.diff.factory");
    	}
    	return result; 
    }
    
    // 입고시 다른 창고에 있는거 return 불출반납, 출고반납 적용
    public Map<String, Object> inputStorageCheck(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> inputStorageCheck = barcodeMapper.inputStorageCheck(map);
    	if (!inputStorageCheck.isEmpty()) {
    		result.put("barcode", inputStorageCheck);
    		result.put("response", "warning.barcode.diff.storage");
    	}
    	return result; 
    }
    
    // 바코드가 location table에 있는지 확인, 팔레트에 속한것도 걸러낼수 있음
    public Map<String, Object> barcodeLocation(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> barcodeLocation = barcodeMapper.barcodeLocation(map);
    	List<String> missing = new ArrayList<>((List<String>)map.get("list"));
        missing.removeAll(barcodeLocation);
    	if (!missing.isEmpty()) {
    		result.put("barcode", missing);
    		result.put("response", "warning.barcode.storagecheck");
    	}
    	return result; 
    }
    
    // 바코드가 stockinfo에 있는지 확인 없는걸 반환 불출,
    public Map<String, Object> barcodeStockInfo(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> barcodeStockInfo = barcodeMapper.barcodeStockInfo(map);
    	List<String> missing = new ArrayList<>((List<String>)map.get("list"));
        missing.removeAll(barcodeStockInfo);
    	if (!missing.isEmpty()) {
    		result.put("barcode", missing);
    		result.put("response", "warning.barcode.storagecheck");
    	}
    	return result; 
    }
    
    // 재고에 존재하는걸 반환 반품검사에서 사용, 이미 재고가 존재한다고 메시지
    public Map<String, Object> barcodeStockInfoForReturn(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> barcodeStockInfo = barcodeMapper.barcodeStockInfo(map);
    	if (!barcodeStockInfo.isEmpty()) {
    		result.put("barcode", barcodeStockInfo);
    		result.put("response", "warning.barcode.storagehere");
    	}
    	return result; 
    }
    
    
    // 작업장에 있는지 확인 작업장에 있는걸 반환, 작업장 예외입고에서 사용
    public Map<String, Object> existWorkLocation(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> existWorkLocation = barcodeMapper.existWorkLocation(map);
    	if (!existWorkLocation.isEmpty()) {
    		result.put("barcode", existWorkLocation);
    		result.put("response", "warning.barcode.worklocation.y");
    	}
    	return result; 
    }
    // 작업장에 있는지 확인 작업장에 없는걸 반화 작업장 예외출고에서 사용
    public Map<String, Object> noWorkLocation(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> existWorkLocation = barcodeMapper.existWorkLocation(map);
    	List<String> missing = new ArrayList<>((List<String>)map.get("list"));
        missing.removeAll(existWorkLocation);
    	if (!missing.isEmpty()) {
    		result.put("barcode", missing);
    		result.put("response", "warning.barcode.worklocation.n");
    	}
    	return result; 
    }
    
    
    // 창고에 없는걸 확인 10~29사이에 있는것들을 가져옴. 출고반품, 불출반납, 입고
    public Map<String, Object> notStorageCheck(List<String> list) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> notStorageCheck = barcodeMapper.notStorageCheck(list);
    	if (!notStorageCheck.isEmpty()) {
    		result.put("barcode", notStorageCheck);
    		result.put("response", "warning.barcode.storagehere");
    	}
    	return result; 
    }
    
    // 해당창고에 이미 있는지 확인  입고 적용 출고반품 불출반납, 영업이송, 창고이송 적용
    public Map<String, Object> storageCheckIn(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> storageCheckIn = barcodeMapper.storageCheckIn(map);
    	if (!storageCheckIn.isEmpty()) {
    		result.put("barcode", storageCheckIn);
    		result.put("response", "warning.barcode.storagehere");
    	}
    	return result; 
    }
    
    // 해당창고에 없는걸 return 출고에서 사용 , 재고실사, 팔레트 추가 사용
    public Map<String, Object> storageCheckOut(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> storageCheckOut = barcodeMapper.storageCheckIn(map);
    	List<String> missing = new ArrayList<>((List<String>)map.get("list"));
        missing.removeAll(storageCheckOut);
    	if (!missing.isEmpty()) {
    		result.put("barcode", missing);
    		result.put("response", "warning.barcode.storagecheck");
    	}
    	return result; 
    }
    
    // 해당공장에 이미 있는지 확인  입고 적용 출고반품 불출반납 적용
    public Map<String, Object> factoryCheckIn(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> factoryCheckIn = barcodeMapper.factoryCheckIn(map);
    	if (!factoryCheckIn.isEmpty()) {
    		result.put("barcode", factoryCheckIn);
    		result.put("response", "warning.barcode.storagehere");
    	}
    	return result; 
    }
    
    // 재고실사에서 창고에 없는지 확인, return된 바코드들은 예외입고처리
    public Map<String, Object> forStockCount(List<String> list) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> forStockCount = barcodeMapper.forStockCount(list);
    	List<String> missing = new ArrayList<>(list);
        missing.removeAll(forStockCount);
    	if (!missing.isEmpty()) {
    		result.put("barcode", missing);
    	}
    	return result; 
    }
 	
    // 4. 
    public Map<String, Object> palletExist(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.palletExist(barcodes);
        if (!existBarcode.isEmpty()) { 
            result.put("barcode", existBarcode);
            result.put("response", "warning.pallet.part.cannnot");
        }
        return result; 
    }
    
    // 불출반납 - 바코드의 가장 최종값이 반납인지 체크
    public Map<String, Object> wipreturnCheck(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.wipreturnCheck(barcodes);
        if (!existBarcode.isEmpty()) { 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.duplicate");
        }
        return result; 
    }
    
    // 반제품인지 (입고,출고에서 사용)
    public Map<String, Object> semiProduction(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> existBarcode = barcodeMapper.semiProduction(barcodes);
    	if (!existBarcode.isEmpty()) { 
    		result.put("barcode", existBarcode);
    		result.put("response", "warning.barcode.semi");
    	}
    	return result; 
    }
    
    // 공장이송 - 보낸내역 체크
    public Map<String, Object> sendingCheck(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> existBarcode = barcodeMapper.sendingCheck(barcodes);
    	if (!existBarcode.isEmpty()) { 
    		result.put("barcode", existBarcode);
    		result.put("response", "warning.barcode.already.movement");
    	}
    	return result; 
    }
    
    // 공장이송 - 받는내역 체크
    public Map<String, Object> receivingCheck(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> existBarcode = barcodeMapper.receivingCheck(barcodes);
    	if (!existBarcode.isEmpty()) { 
    		result.put("barcode", existBarcode);
    		result.put("response", "warning.barcode.duplicate");
    	}
    	return result; 
    }
    
    // 반제품으로 실적잡혔는지 확인
    public Map<String, Object> semiProductionBarcodeProductionCheck(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.semiProductionBarcodeProductionCheck(barcodes);
        if (!existBarcode.isEmpty()) { // 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.semi.registered");
        }

        return result; 
    }

    // 제품으로 실적잡혔는지 확인
    public Map<String, Object> productionBarcodeProductionCheck(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.productionBarcodeProductionCheck(barcodes);
        if (!existBarcode.isEmpty()) { // 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.semi.registered");
        }

        return result; 
    }
    
    // 불량바코드인지 체크(상태값 90미만인 바코드 return)
    public Map<String, Object> errorBarcodeCheck(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.errorBarcodeCheck(barcodes);
        if (!existBarcode.isEmpty()) { // 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.error.cannot");
        }

        return result; 
    }
    
    // 이미 검수된 불량라벨 체크(상태값 91)
    public Map<String, Object> alreadyError(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.alreadyError(barcodes);
        if (!existBarcode.isEmpty()) { // 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.duplicate");
        }

        return result; 
    }
    
    // 불출반납할때 사용자가 선택한 값이 맞는지 확인
    public Map<String, Object> wccodeCheck(Map<String,Object> map) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.wccodeCheck(map);
        if (!existBarcode.isEmpty()) { // 
            result.put("barcode", existBarcode);
            result.put("response", "warning.barcode.wccodecheck");
        }

        return result; 
    }
    
    // 평택 출고 테이블에 존재해야함
	public Map<String, Object> ptCheck(Map<String,Object> map) {
		Map<String, Object> result = new HashMap<>();
    	List<String> existBarcode = barcodeMapper.ptCheck(map); // 
    	if (existBarcode.isEmpty()) { // 비어있다면 입고 불가 
    		result.put("barcode", existBarcode);
    		result.put("response",  "warning.barcode.ptcheck");
    	}
    	return result; // 입고 불가 바코드 리턴
	}
    
	// 피딩에서 사용 해당작업장에 있는 것만 사용가능 
	public Map<String, Object> workLocationCheck(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
    	List<String> existBarcode = barcodeMapper.workLocationCheck(map); 
    	 List<String> missing = new ArrayList<>((List<String>)map.get("list"));		// 해당 작업장에 존재하는 바코드 제거
         missing.removeAll(existBarcode);
    	if (!missing.isEmpty()) {
    		result.put("barcode", missing);
    		result.put("response",  "warning.barcode.diff.workLocation");
    	}
    	return result; 
	}
    
	
	// work 테이블에 있는지 확인
	public Map<String, Object> workCheck(Map<String,Object> map) {
		Map<String, Object> result = new HashMap<>();
    	List<String> existBarcode = barcodeMapper.workCheck(map); 
    	 List<String> missing = new ArrayList<>((List<String>)map.get("list"));		// 해당 작업장에 존재하는 바코드 제거
         missing.removeAll(existBarcode);
    	if (!missing.isEmpty()) {
    		result.put("barcode", missing);
    		result.put("response",  "warning.barcode.work.not");
    	}
    	return result;
	}

	// work 테이블에 없는지 확인
	public Map<String, Object> alreadyWork(Map<String,Object> map) {
		Map<String, Object> result = new HashMap<>();
		List<String> existBarcode = barcodeMapper.workCheck(map); 
		if (!existBarcode.isEmpty()) {
			result.put("barcode", existBarcode);
			result.put("response",  "warning.barcode.already.work");
		}
		return result;
	}
	
	// 영업 검증 조건
	// worklocation에 존재하는지 확인 useyn = 'Y' source = 'PROCUREMENT'
	public Map<String, Object> workLocationY(Map<String,Object> map) {
		Map<String, Object> result = new HashMap<>();
    	List<String> existBarcode = barcodeMapper.workLocationY(map); 
    	 List<String> missing = new ArrayList<>((List<String>)map.get("list"));		// 해당 작업장에 존재하는 바코드 제거
         missing.removeAll(existBarcode);
    	if (!missing.isEmpty()) {
    		result.put("barcode", missing);
    		result.put("response",  "warning.barcode.worklocation.n");
    	}
    	return result; 
	}
	
	// worklocation에 있는지 체크 품질검사에서 사용
	public Map<String, Object> workStockInfoCheck(Map<String,Object> map) {
		Map<String, Object> result = new HashMap<>();
    	 List<String> missing = new ArrayList<>((List<String>)map.get("list"));		// 사용자가 스캔한 바코드
         // worklocation에 있는 바코드
         List<String> workExistBarcode = barcodeMapper.barcodeWorkStockInfo(map); 
         missing.removeAll(workExistBarcode);
    	if (!missing.isEmpty()) {
    		result.put("barcode", missing);
    		result.put("response",  "warning.barcode.worklocation.n");
    	}
    	return result; 
	}

	// location에 있는지 체크 품질검사에서 사용
	public Map<String, Object> stockInfoRedcageCheck(Map<String,Object> map) {
		Map<String, Object> result = new HashMap<>();
		List<String> missing = new ArrayList<>((List<String>)map.get("list"));		// 사용자가 스캔한 바코드
		//stockinfo에 있는 바코드
		List<String> existBarcode = barcodeMapper.barcodeStockInfo(map); 
		// location redcage에 있는 바코드
		missing.removeAll(existBarcode);
		if (!missing.isEmpty()) {
			result.put("barcode", missing);
			result.put("response",  "warning.barcode.location.n");
		}
		return result;
	}
	// worklocation 또는 redcage에 있는지 체크 품질검사에서 사용
	public Map<String, Object> workStockInfoRedcageCheck(Map<String,Object> map) {
		Map<String, Object> result = new HashMap<>();
		List<String> missing = new ArrayList<>((List<String>)map.get("list"));		// 사용자가 스캔한 바코드
		// worklocation에 있는 바코드
		List<String> workExistBarcode = barcodeMapper.barcodeWorkStockInfo(map); 
		// location redcage에 있는 바코드
		List<String> redcageExistCheck = barcodeMapper.redcageExistCheck(map); 
		missing.removeAll(workExistBarcode);
		missing.removeAll(redcageExistCheck);
		if (!missing.isEmpty()) {
			result.put("barcode", missing);
			result.put("response",  "warning.barcode.worklocation.n");
		}
		return result; 
	}
	
	// location, worklocation에 있는지 체크 품질 검사에서 사용
	public Map<String, Object> allLocationCheck(Map<String,Object> map) {
		Map<String, Object> result = new HashMap<>();
		// location에 잇는 바코드
    	List<String> locationExistBarcode = barcodeMapper.barcodeStockInfo(map); 
    	 List<String> missing = new ArrayList<>((List<String>)map.get("list"));		// 사용자가 스캔한 바코드
    	 // location에 존재하는 barcode 제거
         missing.removeAll(locationExistBarcode);
         
         // worklocation에 있는 바코드
         List<String> workExistBarcode = barcodeMapper.barcodeWorkStockInfo(map); 
         missing.removeAll(workExistBarcode);
    	if (!missing.isEmpty()) {
    		result.put("barcode", missing);
    		result.put("response",  "warning.barcode.alllocation.n");
    	}
    	return result;
	}
	
	// redcage가 아닌걸 반환 창고에 없는것도 반환됨
	public Map<String, Object> redcageCheck(Map<String,Object> map) {
		Map<String, Object> result = new HashMap<>();
    	List<String> existBarcode = barcodeMapper.redcageCheck(map); 
    	if (!existBarcode.isEmpty()) {
    		result.put("barcode", existBarcode);
    		result.put("response",  "warning.barcode.work.not");
    	}
    	return result;
	}

	// 불량라벨을 반환
	public Map<String, Object> defectiveCheck(Map<String,Object> map) {
		Map<String, Object> result = new HashMap<>();
		List<String> existBarcode = barcodeMapper.defectiveCheck(map); 
		if (!existBarcode.isEmpty()) {
			result.put("barcode", existBarcode);
			result.put("response",  "warning.barcode.defective");
		}
		return result;
	}

    // 불량라벨이 아닌걸 반환 입고반품사용
    public Map<String, Object> defectiveNReturn(Map<String,Object> map) {
        Map<String, Object> result = new HashMap<>();
        List<String> missing = new ArrayList<>((List<String>) map.get("list"));	// 사용자가 스캔한 바코드
        List<String> existBarcode = barcodeMapper.defectiveCheck(map);			// 불량인 바코드
        missing.removeAll(existBarcode);											// 남은건 비불량
        if (!missing.isEmpty()) {
            result.put("barcode", missing);
            result.put("response",  "warning.barcode.not.defective");
        }
        return result;
    }

	// 이미 판정된라벨인지 체크
	public Map<String, Object> inspectionCheck(Map<String,Object> map) {
		Map<String, Object> result = new HashMap<>();
		List<String> existBarcode = barcodeMapper.inspectionCheck(map); 
		if (!existBarcode.isEmpty()) {
			result.put("barcode", existBarcode);
			result.put("response",  "warning.barcode.already.inspection");
		}
		return result;
	}

    // 이미 판정된라벨인지 체크
    public Map<String, Object> check91(Map<String,Object> map) {
        Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = barcodeMapper.check91(map);
        if (!existBarcode.isEmpty()) {
            result.put("barcode", existBarcode);
            result.put("response",  "warning.barcode.quality.inspection");
        }
        return result;
    }

	// 등록이 안되어 있으면서 박스 바코드 체크
	public Map<String, Object> lotCheck(List<String> barcode) {
		Map<String, Object> result = new HashMap<>();
		barcode = barcode.stream()
			    .filter(b -> b.split("_",-1).length == 6)
			    .collect(Collectors.toList());
		if (barcode.isEmpty()) {
		    return result;
		}
		List<String> existBarcode = barcodeMapper.lotCheck(barcode);		// 등록이 안되어 있는 바코드
		List<String> missing = new ArrayList<>(barcode);
		missing.removeAll(existBarcode);                                    // 등록 된 바코드 제외 => 등록 안된 바코드만 남음
        for (String barcode1 : missing) {
            Map<String, Object> map = new HashMap<>();
            String[] arr = barcode1.split("_");
            map.put("barcode", barcode1);
            map.put("laststatus", 1);
            BigDecimal qty = new BigDecimal(arr[4]); // 4번째 = 수량
            map.put("qty", qty);
            map.put("oitemcode",arr[3]);
            Map<String, Object> item = purchaseMapper.getItemInfoSpec(arr[3]);
            if (item == null || item.isEmpty()) {
                result.put("response", "warning.item.notFound");
                result.put("barcode", barcode1);
                return result;
            }
            map.put("itemname", item.get("ITEMNAME"));
            map.put("itemcode", item.get("ITEMCODE"));
            String date = arr[2] + "-" + arr[1] + "-" + arr[0];
            map.put("date", date);

            int makeBarcode = barcodeMapper.makeBarcode(map);        // 바코드 등록
        }
        return result;
	}

    // 등록이 안되어 있으면서 파트바코드 체크
    public void lotPartBarcodeCheck(List<String> barcode) {
        barcode = barcode.stream()
                .filter(b -> b.split(",").length == 5)
                .collect(Collectors.toList());
        if (barcode.isEmpty()) {
            return; // 처리할 바코드 없음 → 쿼리 안 탐
        }
        List<String> existBarcode = barcodeMapper.lotCheck(barcode);		// 등록이 안되어 있는 바코드
        List<String> missing = new ArrayList<>(barcode);
        missing.removeAll(existBarcode);                                    // 등록 된 바코드 제외 => 등록 안된 바코드만 남음
        for (String barcode1 : missing) {
            Map<String, Object> map = new HashMap<>();
            String[] arr = barcode1.split(",");
            map.put("barcode", barcode1);
            map.put("laststatus", 1);
            BigDecimal qty = new BigDecimal(arr[3]); // 4번째 = 수량
            map.put("qty", qty);
            map.put("itemcode", arr[0]);
            List<Map<String, Object>> item = purchaseMapper.getItemInfo(arr[0]);
            map.put("itemname", item.get(0).get("ITEMNAME"));
            String date = "20" + arr[1].substring(0, 2) + "-" + arr[1].substring(2, 4) + "-" + arr[1].substring(4, 6);
            map.put("date", date);

            int makeBarcode = barcodeMapper.makeBarcode(map);        // 바코드 등록
        }
    }
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // 1. 생성된 바코드인지 확인
    public Map<String, Object> validateExist(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> existBarcode = purchaseMapper.barcodeYN(barcodes);
        List<String> missing = new ArrayList<>(barcodes);
        missing.removeAll(existBarcode);
        if (!missing.isEmpty()) { // ❗ 에러가 있을 때만 fail1 세팅
            result.put("barcode", missing);
            result.put("response", "fail1");
        }
        return result; 
    }
    
    
    // 1. 반제품으로 생성된 바코드인지 확인 laststatus = 2
    

    // 2. 입고 가능한 상태인지 확인
    public Map<String,Object> validateIncoming(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> valid = purchaseMapper.forIncoming(barcodes); // laststatus 1~9 사이
        List<String> invalid = new ArrayList<>(barcodes);
        invalid.removeAll(valid);
        if (!invalid.isEmpty()) { // ❗ 에러가 있을 때만 fail1 세팅
            result.put("barcode", invalid);
            result.put("response", "fail2");
        }
        return result; // 입고 불가 바코드 리턴
    }

    // 3. 입고가 안된 바코드 확인
    public Map<String,Object> IncomingOk(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
        List<String> valid = purchaseMapper.forIncoming(barcodes); // laststatus 1~9 사이
        if (!valid.isEmpty()) { // 
            result.put("barcode", valid);
            result.put("response", "fail4");
        }
        return result; // 입고 불가 바코드 리턴
    }
    
//    // 4. 적재된 바코드 확인
//    public Map<String,Object> locationOk(List<String> barcodes) {
//    	Map<String, Object> result = new HashMap<>();
//    	List<String> valid = purchaseMapper.forIncoming(barcodes); // laststatus 1~9 사이
//    	if (!valid.isEmpty()) { // 
//    		result.put("barcode", valid);
//    		result.put("response", "fail4");
//    	}
//    	return result; // 입고 불가 바코드 리턴
//    }
//    
//    // 5. 언팩
//    public Map<String,Object> unpackOk(List<String> barcodes) { 언팩 10
//    	Map<String, Object> result = new HashMap<>();
//    	List<String> valid = purchaseMapper.forIncoming(barcodes); // laststatus 1~9 사이
//    	if (!valid.isEmpty()) { // 
//    		result.put("barcode", valid);
//    		result.put("response", "fail10");
//    	}
//    	return result; // 입고 불가 바코드 리턴
//    }
    
    // 5. 이미 불출된 바코드인지 확인
    public Map<String,Object> wipOk(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> valid = purchaseMapper.wipOk(barcodes); // 
    	if (!valid.isEmpty()) { // 
    		result.put("barcode", valid);
    		result.put("response", "fail5");
    	}
    	return result; // 입고 불가 바코드 리턴
    }
    
    // 6. 출고된 바코드인지 확인
    public Map<String,Object> outputOk(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> valid = purchaseMapper.outputOk(barcodes); // 
    	if (!valid.isEmpty()) { // 
    		result.put("barcode", valid);
    		result.put("response", "fail6");
    	}
    	return result; // 입고 불가 바코드 리턴
    }
    
    // 공장간 이송 receiving 가능한지 확인
    public Map<String,Object> receivingOk(List<String> barcodes) {
    	Map<String, Object> result = new HashMap<>();
    	List<String> valid = purchaseMapper.receivingOk(barcodes); // 
    	if (!valid.isEmpty()) { // 
    		result.put("barcode", valid);
    		result.put("response", "fail7");
    	}
    	return result; //
    }

    // 같은 품번인지 확인 (품번 전환으로 다를 수 있음)
    public Map<String, Object> itemcodeCheck(Map<String,Object> map) {
        Map<String, Object> result = new HashMap<>();
        List<String> itemcodeCheck = barcodeMapper.itemcodeCheck(map);
        if (itemcodeCheck != null && itemcodeCheck.size() > 1) {
            result.put("barcode", map.get("list"));
            result.put("response", "warning.barcode.itemcode.diff");
        }
        return result;
    }

    // 입고 반품된 바코드인지 체크
    public Map<String, Object> alreadyIncomingReturn(Map<String, Object> map){
        Map<String, Object> result = new HashMap<>();
        List<String> check = barcodeMapper.alreadyIncomingReturn(map);
        if (!check.isEmpty()) {
            result.put("barcode", map.get("list"));
            result.put("response", "warning.barcode.duplicate");
        }
        return result;
    }
}