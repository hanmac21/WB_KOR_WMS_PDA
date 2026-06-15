package com.example.demo.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import com.example.demo.mapper.usa.PalletMapper;
import com.example.demo.mapper.usa.PurchaseMapper;
import com.example.demo.utils.FilterResult;
import com.example.demo.utils.PalletFilter;
import com.example.demo.validator.BarcodeValidator;
import com.example.demo.vo.BarcodeVO;
import com.example.demo.vo.InventoryVO;
import com.example.demo.vo.ItemLocationVO;
import com.example.demo.vo.PalletDetailVO;
import com.example.demo.vo.StockHistoryVO;
import com.example.demo.vo.UnpackVO;
import com.example.demo.vo.WorkmoveVO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PurchaseService {

    private final DataSource usaDataSource;

    @Autowired
    private CloseService closeService;
	@Autowired
	private PurchaseMapper purchaseMapper;

	@Autowired
	private PalletMapper palletMapper;
	
	@Autowired
	PalletFilter palletFilter;

	private final BarcodeValidator barcodeValidator;
	public PurchaseService(BarcodeValidator barcodeValidator, DataSource usaDataSource) {
        this.barcodeValidator = barcodeValidator; 
        this.usaDataSource = usaDataSource;
    }
	
	// 파트라벨 수량 가져오기
	private String resolveBarcodeQty(String barcode) {
	    // 1) DB에 저장된 수량 (있으면 우선 사용)
	    String lastqty = purchaseMapper.getPartQty(barcode);

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
	
	// 팔레트 생성
	public  Map<String, Object> makePalletBarcode(BarcodeVO request) {
		String date = request.getDate();
		int laststatus = 0;
		Map<String, Object> result = new HashMap<>();
		
		List<String> barcodes = request.getBarcode();
		String factory = request.getFactory();
		String loginid = request.getLoginid();// (String) session.getAttribute("cu_sano");
		String storage = request.getStorage();
		
		Map<String, Object> checkMap = new HashMap<>();
		checkMap.put("list", request.getBarcode());
		checkMap.put("factory", request.getFactory());
		checkMap.put("storage", request.getStorage());
		checkMap.put("date", date);
		checkMap.put("loginid", loginid);
		
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(checkMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}

		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(request.getBarcode());
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }

		// 같은 품번인지 확인 (품번 전환으로 다를 수 있음)
		Map<String, Object> itemcodeCheck = barcodeValidator.itemcodeCheck(checkMap);
		if (!itemcodeCheck.isEmpty()){
			return itemcodeCheck;
		}


	    // 같은 창고인지 확인
	    Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
	    if (!inputStorageCheck.isEmpty()) {
	    	return inputStorageCheck; // 실패 시 리턴
	    }

	    // location에 있는지 확인 stockinfo에서 조회
	    Map<String, Object> barcodeStockInfo = barcodeValidator.barcodeStockInfo(checkMap);
	    if (!barcodeStockInfo.isEmpty()) {
	    	return barcodeStockInfo; // 실패 시 리턴
	    }
	    
	    // 불량라벨을 반환
	    Map<String, Object> defectiveCheck = barcodeValidator.defectiveCheck(checkMap);
	    if (!defectiveCheck.isEmpty()) {
	    	return defectiveCheck; // 실패 시 리턴
	    }
	    
	    // laststatus 10~29사이인것
	    Map<String, Object> factoryCheck = barcodeValidator.factoryCheck(checkMap);
	    if (!factoryCheck.isEmpty()) {
	    	return factoryCheck; // 실패 시 리턴
	    }
 	    
 	    // 출고된 바코드인지 체크
  		Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(request.getBarcode());
  	    if (!alreadyLoad.isEmpty()) {
  	        return alreadyLoad; // 실패 시 리턴
  	    }
		
		laststatus = 10;
		
		String pbarcode = purchaseMapper.selectPalletSeq(date);
		String cucode = "master";// (String) session.getAttribute("cu_code");
		String cname = "WOOBOTECH";// (String) session.getAttribute("cu_sangho");
		

		int seq = 0;
		if (pbarcode != null && !pbarcode.isEmpty() && pbarcode.length() > 6) {
			String firstPart = pbarcode.split(",")[0];
			seq = Integer.parseInt(firstPart.substring(firstPart.length() - 5));
		}
		seq++;
		double qty = 0;
		String itemcode = "";
		for (String barcode : barcodes) {
			qty += Double.parseDouble(resolveBarcodeQty(barcode));
			itemcode = barcode.split(",")[0];
		}
		qty = Math.round(qty * 100.0) / 100.0;
		String pbar = "P" + date.replace("-", "").substring(2) + String.format("%05d", seq) + "," + itemcode
				+ "," + String.format("%08.2f", qty) + ",WMSUSA"; // 팔레트 바코드 양식변경 250813 구성 : P날짜순번,품번,수량,고정텍스트
																	// 스캔시 파레트라벨 인식조건 : P로 시작, 마지막글자 3자리가 USA인것.
		for (String barcode : barcodes) {
			
			//바코드가 팔레트에 속해 있는지 확인
			String ppbarcode = palletMapper.searchPallet(barcode);
			if (ppbarcode != null && !ppbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",ppbarcode);
				palletMap.put("memo","CREATE PALLET");
				// 팔레트테이블에서 해당 바코드 N처리
				int barcodeN = palletMapper.bbarcodeN(palletMap);
				// 수량 가져오기
				Double doublePQty = palletMapper.palletQty(ppbarcode);
				palletMap.put("pqty",doublePQty);
				
				//바코드 수량 가져오기
				double doubleqty = palletMapper.partQty(barcode);
				palletMap.put("date",date);
				palletMap.put("qty",doubleqty);
				palletMap.put("loginid",loginid);
				// 팔레트 위치로 적재 insert
				int partLocation = palletMapper.partLocation(palletMap);
				
				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateLQtyN(palletMap);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateLQty(palletMap);
				}
			}

			Map<String, Object> map = new HashMap<>();
			map.put("date", date);
			map.put("pbarcode", pbar);
			map.put("barcode", barcode);
			map.put("dmemo", "New Pallet");
			map.put("cucode", cucode);
			map.put("loginid", loginid);
			map.put("itemcode", barcode.split(",")[0]);
			map.put("cname", cname);
			map.put("laststatus", laststatus);
			map.put("factory", factory);
			map.put("storage", storage);
			map.put("lastqty", qty);
			purchaseMapper.removeBarcode(map);// 
			if (barcode.split(",").length == 5) {
				map.put("itemcode", barcode.split(",")[0]);
				map.put("bdate", barcode.split(",")[1]);
				map.put("seq", barcode.split(",")[2]);
				map.put("qty", resolveBarcodeQty(barcode));
				map.put("scmmex", barcode.split(",")[4]);
			} else {
				map.put("itemcode", "");
				map.put("bdate", "");
				map.put("seq", "");
				map.put("qty", "");
				map.put("scmmex", "");
			}
			purchaseMapper.updateLaststatusPart(map);
			purchaseMapper.makePalletBarcode(map);
		}
		//팔레트 라벨 기본창고에 적재
		Map<String, Object> palletLocationMap = new HashMap<>();
		palletLocationMap.put("barcode", pbar);

		palletLocationMap.put("factory", factory);
		palletLocationMap.put("storage", storage);
		palletLocationMap.put("itemcode",itemcode);
		palletLocationMap.put("qty",qty);
		palletLocationMap.put("source","CREATE PALLET");
		palletLocationMap.put("loginid", loginid);
		palletLocationMap.put("date", date);
		Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(itemcode);
		palletLocationMap.put("rack", item.get("CAR"));
		palletLocationMap.put("location", factory+"-"+storage+"-"+item.get("CAR"));
		purchaseMapper.basicLocation(palletLocationMap);
		
		result.put("response", "success");
		return result;
	}
	
	// 팔레트 관리 search
	public Map<String,Object> palletManagementSearch(String barcode) {
		Map<String, Object> result = new HashMap<String, Object>();
		Map<String, Object> map =  purchaseMapper.palletManagementSearch(barcode);
		result.put("list", map);
		return result;
	}
	
	// 팔레트 라벨에 파트라벨 추가
	public Map<String, Object> insPalletBarcode(BarcodeVO request) {
		Map<String,Object> result = new HashMap<String, Object>();
		String pbarcode = request.getBarcodeone();
		List<String> barcodes = request.getBarcode();
		String loginid = request.getLoginid();
		String factory = request.getFactory();
		PalletDetailVO pvo = purchaseMapper.locationInfo(pbarcode);
		// 로케이션이 없으면 return 창고에 있는 팔레트만 작업 가능
		if (pvo == null || pvo.getStorage() == null) {
			result.put("barcode", Arrays.asList(pbarcode));
			result.put("response", "warning.pallet.location.cannot");
			return result;
		}
		Map<String, Object> checkMap = new HashMap<>();
		checkMap.put("list", request.getBarcode());
		checkMap.put("factory", pvo.getFactory());
		checkMap.put("storage", pvo.getStorage());
		// 언팩바코드 체크
		Map<String, Object> unpackCheck = barcodeValidator.unpackCheck(request.getBarcode());
		if (!unpackCheck.isEmpty()) {
			return unpackCheck; // 실패 시 리턴
		}
		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(request.getBarcode());
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }
	    
	    // 바코드가 location table에 있는지 확인, 팔레트에 속한것도 걸러낼수 있음
	    Map<String, Object> barcodeLocation = barcodeValidator.barcodeLocation(checkMap);
	    if (!barcodeLocation.isEmpty()) {
	    	return barcodeLocation; // 실패 시 리턴
	    }
	    
	    // 불출된 바코드인지 체크
  		Map<String, Object> alreadyWIP = barcodeValidator.alreadyWIP(request.getBarcode());
  	    if (!alreadyWIP.isEmpty()) {
  	        return alreadyWIP; // 실패 시 리턴
  	    }
 	    
 	    // 출고된 바코드인지 체크
  		Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(request.getBarcode());
  	    if (!alreadyLoad.isEmpty()) {
  	        return alreadyLoad; // 실패 시 리턴
  	    }
	    
	    // 같은 공장인지 확인
	    Map<String, Object> inputFactoryCheck = barcodeValidator.inputFactoryCheck(checkMap);
	    if (!inputFactoryCheck.isEmpty()) {
	    	return inputFactoryCheck; // 실패 시 리턴
	    }
	    // 같은 창고인지 확인
	    Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
	    if (!inputStorageCheck.isEmpty()) {
	    	return inputStorageCheck; // 실패 시 리턴
	    }
	    
	    // 해당공장, 창고에 있는지 체크
	    Map<String, Object> storageCheckOut = barcodeValidator.storageCheckOut(checkMap);
	    if (!storageCheckOut.isEmpty()) {
	    	return storageCheckOut; // 실패 시 리턴
	    }
	    
		
		for(String barcode : barcodes) {
			PalletDetailVO palletInfo = purchaseMapper.palletInfo(pbarcode);
			PalletDetailVO partInfo = purchaseMapper.partInfo(barcode);
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("pbarcode",pbarcode);
			map.put("barcode",barcode);
			map.put("loginid",loginid);
			map.put("qty",partInfo.getQty());
			BigDecimal qty = new BigDecimal(partInfo.getQty());
			BigDecimal pqty = new BigDecimal(palletInfo.getQty());
			map.put("lastqty",qty.add(pqty));
			// 팔레트라벨에 파트라벨 추가
			purchaseMapper.insPalletBarcode(map);
			// 팔레트라벨에 lastqty값 업데이트
			purchaseMapper.updatePalletQtyInfo(map);
			// 기존 파트라벨 적재위치 N
			map.put("dmemo","INS PALLET");
			purchaseMapper.removeBarcode(map);
			// location 테이블에 qty값 업데이트
			purchaseMapper.updatePalletQtyLocation(map);
		}
		result.put("response", "success");
		return result;
	}
	
	// 팔레트 해체 파트라벨 search
	public Map<String,Object> palletUnboundSearch(String barcode) {
		Map<String, Object> result = new HashMap<String, Object>();
		List<Map<String, Object>> map =  purchaseMapper.palletUnboundSearch(barcode);
		result.put("list", map);
		return result;
	}
	
	// 팔레트 해체
	public Map<String,Object> palletUnbound(Map<String, Object> param) {
		String loginid = (String) param.get("loginid");
		String barcode = (String) param.get("barcode");
		Map<String, Object> result = new HashMap<String, Object>();
		// 언팩바코드 체크
		Map<String, Object> unpackCheck = barcodeValidator.unpackCheck(Arrays.asList(barcode));
		if (!unpackCheck.isEmpty()) {
			return unpackCheck; // 실패 시 리턴
		}
		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(Arrays.asList(barcode));
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }
	    
	    // 불출된 바코드인지 체크
  		Map<String, Object> alreadyWIP = barcodeValidator.alreadyWIP(Arrays.asList(barcode));
  	    if (!alreadyWIP.isEmpty()) {
  	        return alreadyWIP; // 실패 시 리턴
  	    }
 	    
 	    // 출고된 바코드인지 체크
  		Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(Arrays.asList(barcode));
  	    if (!alreadyLoad.isEmpty()) {
  	        return alreadyLoad; // 실패 시 리턴
  	    }
  	    
  	    Map<String, Object> checkMap = new HashMap<String, Object>();
  	    checkMap.put("list",Arrays.asList(barcode));
  	    // 일단 로케이션에 있는것만 팔레트 해체 가능하도록 개발
  	    // 로케이션에 해당 바코드가 있는지 확인 
  	    Map<String, Object> barcodeLocation = barcodeValidator.barcodeLocation(checkMap);
  	    if (!barcodeLocation.isEmpty()) {
  	    	return barcodeLocation; // 실패 시 리턴
  	    }
  	    
	  	LocalDate today = LocalDate.now();
	    String date = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
  	    
  	    // 팔레트 해체 작업
		Map<String, Object> m = new HashMap<String, Object>();
		m.put("dmemo", "PALLET UNBOUND");
		String location = purchaseMapper.searchRoomcodeY(barcode);		// 팔레트 위치 가져옴
		m.put("barcode", barcode);
		m.put("location", location);
		String[] parts = location.split("-");
		m.put("factory", parts.length > 0 ? parts[0] : "");
		m.put("storage", parts.length > 1 ? parts[1] : "");
		m.put("rack", parts.length > 2 ? parts[2] : "");
		m.put("module", parts.length > 3 ? parts[3] : "");
		m.put("levelcode", parts.length > 4 ? parts[4] : "");
		m.put("position", parts.length > 5 ? parts[5] : "");
		m.put("date", date);
		m.put("loginid",loginid);
		m.put("source", "PALLET UNBOUND");
		// 기존 팔레트라벨 적재위치 정보로 파트라벨 적재
		purchaseMapper.selectLocationSave(m);
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("barcode", barcode);
		map.put("dmemo", "PALLET UNBOUND");
		purchaseMapper.removeBarcode(map);		// 적재된 팔레트바코드 제거
		// 팔레트 바코드 useyn = n
		purchaseMapper.palletN(map);				// 팔레트라벨 사용 N
		result.put("response", "success");
		return result;
	}

	// 파트라벨 품번 조정에서 품명 가져오기
	public List<Map<String, Object>> partItemnameSearch(String itemcode) {
		return purchaseMapper.partItemnameSearch(itemcode);
	}
	
	// 파트라벨 수량 조정에서 파트정보 가져오기
	public Map<String,Object> partInfoSearch(String barcode) {
		Map<String, Object> result = new HashMap<String, Object>();

		//바코드가 팔레트에 속해 있는지 확인
		String pbarcode = palletMapper.searchPallet(barcode);
		if (pbarcode != null) {
			result.put("return", pbarcode);
			return result;
		}

		Map<String, Object> map =  purchaseMapper.partInfoSearch(barcode);
		result.put("result", map);
		return result;
	}
	
	// 파트라벨 수량 조정 업데이트
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String,Object> partAdjustmentUpdate(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<String, Object>();
		
		BigDecimal nowqty = new BigDecimal(param.get("nowqty").toString());
		BigDecimal newqty = new BigDecimal(param.get("qty").toString());
		String barcode = (String) param.get("barcode");
		String loginid = (String) param.get("loginid");
		String memo = (String) param.get("memo");
		
		// nowqty - newqty
		BigDecimal diff = nowqty.subtract(newqty).abs();
		
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("diffqty", diff);
		map.put("barcode", barcode);
		map.put("loginid", loginid);
		
		// 기존 파트라벨의 정보를 가져옴
		Map<String, Object> partInfo = purchaseMapper.getPartInfoForAdjust(barcode);
		
		String factory = (String)partInfo.get("FACTORY");
		String storage = (String)partInfo.get("STORAGE");
		String itemcode = (String)partInfo.get("ITEMCODE");
		map.put("factory", factory);
		map.put("storage", storage);
		map.put("itemcode", itemcode);
		map.put("invoiceno", memo);
		map.put("source2", "PARTADJUSTMENT");
		if(nowqty.compareTo(newqty) > 0) {		// nowqty가 더 큼
			map.put("source","LOADEXCEPTION");
			// 기존 qty가 더 크므로 차이분만큰 예외출고 작업
			purchaseMapper.insOutputExceptionDiffQty(map);
		}else {									// newqty가 더 큼
			map.put("source","INCOMINGEXCEPTION");
			// 새로운 qty가 더 크므르 차이분만큼 예외입고 작업
			purchaseMapper.insInboundExceptionDiffQty(map);
		}
		// 조정 로그 남기기
		map.put("oqty", nowqty);
		map.put("qty", newqty);
		int insAdjustLog = purchaseMapper.insAdjustLog(map);
		
		// location테이블에 조정 수량 업데이트
		int update = purchaseMapper.partAdjustmentUpdate(param);
		
		// 파트라벨 테이블에 조정 수량 업데이트
		int updatePart = purchaseMapper.partAdjustmentUpdatePart(param);
		
		if(update>0) {
			result.put("response", "success");
		}else {
			result.put("response", "fail");
		}
		
		return result;
	}

	// 파트라벨 품번 조정 업데이트
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String,Object> partAdjustmentItemcodeUpdate(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<String, Object>();

		String barcode = (String) param.get("barcode");
		String nowItemcode = (String) param.get("nowItemcode");
		String nowItemname = (String) param.get("nowItemname");
		String nowqty = (String) param.get("nowqty");
		String adjustItemcode = (String) param.get("adjustItemcode");
		String adjustItemname = (String) param.get("adjustItemname");
		String loginid = (String) param.get("loginid");
		String memo = (String) param.get("memo");

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("barcode", barcode);
		map.put("loginid", loginid);

		// 기존 파트라벨의 정보를 가져옴
		Map<String, Object> partInfo = purchaseMapper.getPartInfoForAdjust(barcode);

		String factory = (String)partInfo.get("FACTORY");
		String storage = (String)partInfo.get("STORAGE");
		map.put("factory", factory);
		map.put("storage", storage);
		map.put("itemcode", nowItemcode);
		map.put("itemname", nowItemname);
		map.put("qty", nowqty);
		map.put("adjustItemcode", adjustItemcode);
		map.put("adjustItemname", adjustItemname);
		map.put("invoiceno", memo);
		map.put("source2", "ITEMADJUSTMENT");

		// 팔레트에 속해있는 파트 바코드의 품번을 전환하는 경우
		//바코드가 팔레트에 속해 있는지 확인
		String pbarcode = palletMapper.searchPallet(barcode);
		if (pbarcode != null && !pbarcode.isEmpty()) {
			Map<String, Object> palletMap = new HashMap<String, Object>();
			palletMap.put("barcode",barcode);
			palletMap.put("pbarcode",pbarcode);
			palletMap.put("memo", "CHANGE ITEMCODE");
			// 팔레트테이블에서 해당 바코드 N처리
			int barcodeN = palletMapper.bbarcodeN(palletMap);
			// 수량 가져오기
			Double doublePQty = palletMapper.palletQty(pbarcode);
			palletMap.put("pqty",doublePQty);

			//바코드 수량 가져오기
			double doubleqty = palletMapper.partQty(barcode);
			// 오늘 날짜 가져오기
			String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
			palletMap.put("date",date);
			palletMap.put("qty",doubleqty);
			palletMap.put("loginid", loginid);
			// 팔레트 위치로 적재 insert  하단에 위치 삭제하는 코드가 있어서 살려둠
			int partLocation = palletMapper.partLocation(palletMap);

			// 입고는 거의 작동안하겠지만 혹시 몰라서 남겨둠
			// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
			if(doublePQty == 0) {
				palletMap.put("dmemo","PALLET 0");
				// 로케이션에 위치한 팔레트 수량이 0이면 폐기
				int pqty0 = palletMapper.updateLQtyN(palletMap);
				System.out.println("incoming update pqty0 :"+pqty0);
			}else {
				//location 수량 업데이트
				int updateLQty = palletMapper.updateLQty(palletMap);
				System.out.println("incoming update updateLQty :"+updateLQty);
			}
		}

		// 기존 품번으로 예외 출고
		map.put("source","LOADEXCEPTION");
		purchaseMapper.insOutputExceptionNowItemcode(map);

		// 새 품번으로 예외 입고
		map.put("source","INCOMINGEXCEPTION");
		purchaseMapper.insInboundExceptionNewItemcode(map);

		// 조정 로그 남기기
		int insAdjustLog = purchaseMapper.insAdjustItemcodeLog(map);

		// location테이블에 조정 수량 업데이트
		int update = purchaseMapper.partAdjustmentItemcodeUpdate(param);

		// 파트라벨 테이블에 조정 수량 업데이트
		int updatePart = purchaseMapper.partAdjustmentItemcodeUpdatePart(param);

		if(update>0) {
			result.put("response", "success");
		}else {
			result.put("response", "fail");
		}

		return result;
	}
	
	// 말일인지 체크하는 로직
	public boolean isLastDayOfMonth(String dateStr) {
	    LocalDate date = LocalDate.parse(dateStr); // yyyy-MM-dd 형식 그대로 파싱
	    int lastDay = date.lengthOfMonth();        // 해당 월의 말일
	    return date.getDayOfMonth() == lastDay;
	}
	
	//재고실사 - 바코드
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> insRealStock(BarcodeVO request) {
		String date = request.getDate();

		Map<String, Object> result = new HashMap<>();
		String factory = request.getFactory();
		String storage = request.getStorage();
		List<String> list = request.getBarcode();
		String loginid = request.getLoginid();
		String status = request.getStatus1();
		List<String> barcodes2 = request.getBarcode2();
		
		String labelType = "";
		
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}

		// barcode 테이블에 존재 하지 않는 박스 바코드 insert
		Map<String, Object> lotCheckResult1 = barcodeValidator.lotCheck(request.getBarcode());
		if (!lotCheckResult1.isEmpty()) {
			return lotCheckResult1;
		}

		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(request.getBarcode());
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }

	    log.info("status : "+status);

		// ✅ 팔레트-파트 전처리
		FilterResult fr = palletFilter.removeChildPartsIfParentPresent(list);
		List<String> barcodes = fr.getFiltered();
		
		// 해당 창고에 없는지 체크
	    Map <String, Object> checkMap = new HashMap<String, Object>();
	    checkMap.put("list", request.getBarcode());
	    checkMap.put("factory", factory);
	    checkMap.put("storage", storage);
	    Map<String, Object> storageCheckOut = barcodeValidator.storageCheckOut(checkMap);		// 현재 재고에 없는 바코드
	    List<String> missingBarcodes = (List<String>) storageCheckOut.getOrDefault("barcode", new ArrayList<>());
	    
	    // 다른창고에 있는 바코드
	    Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
	    List<String> storageBarcodes = (List<String>) inputStorageCheck.getOrDefault("barcode", new ArrayList<>());

	    // 4. Set으로 겹치는 바코드만 추출
	    Set<String> otherBarcodesSet = new HashSet<>();
	    otherBarcodesSet.addAll(storageBarcodes);

	    // 현재 창고에 없는 바코드 중 다른 공장/창고에 있는 바코드 예외출고, location N 진행  ====> 예외출고 대신 창고이동으로 처리
	    List<String> exceptionOutBarcodes = missingBarcodes.stream()
	        .filter(otherBarcodesSet::contains)
	        .collect(Collectors.toList());
	    System.out.println("창고이동바코드: " + exceptionOutBarcodes);
	    
	    System.out.println("예외입고 바코드: " + missingBarcodes);
	    
	    // 예외출고작업
//	    if(!exceptionOutBarcodes.isEmpty()) {
//	    	for(String barcode: exceptionOutBarcodes) {
//	    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
//	    		String okyn = "Y";
//	    		if ("Defective".equals(labelType)) {
//	    			okyn = "N";
//	    		}
//	    		String preLocation = purchaseMapper.getPreviousLocation(barcode);
//	    		Map<String,Object> m = new HashMap<String, Object>();
//	    		m.put("date", date);
//	    		m.put("loginid", loginid);
//	    		m.put("source", "LOADEXCEPTION");
//	    		m.put("main", "OUT");
//	    		m.put("kind", "LOADEXCEPTION");
//	    		m.put("storage", preLocation.split("-")[1]);
//	    		m.put("factory", preLocation.split("-")[0]);
//	    		m.put("location", preLocation);
//	    		m.put("memo", "BARCODECOUNT");
//	    		m.put("barcode", barcode);
//	    		m.put("source2", "STOCKCOUNT");
//	    		m.put("okyn",okyn );
//	    		// 바코드 파싱 (인라인)
//		        if (barcode.split(",").length ==5) {
//		            String[] parts = barcode.split(",", -1);
//					Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(parts[0]);
//					m.put("oitemcode", item.get("OITEMCODE"));
//		            m.put("itemcode", parts[0]);
//		            m.put("bdate", parts[1]);
//		            m.put("seq", parts[2]);
//		            m.put("qty", resolveBarcodeQty(barcode));
//		            m.put("scmmex", parts[4]);
//		            m.put("type", "box");
//
//		        } else if (barcode.startsWith("P") && (barcode.endsWith("MEX") || barcode.endsWith("USA"))) {
//					PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
//					if (pal == null) {
//						result.put("response", "warning.pallet.infoNotFound");
//		                result.put("barcode", Arrays.asList(barcode));
//		                return result;
//						//throw new RuntimeException("NO_PALLET_INFO");
//					}
//					String[] parts = barcode.split(",", -1);
//					String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
//					String seq = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7) : "";
//					String scmmex = (parts.length >= 4) ? parts[3] : "";
//					Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(parts[0]);
//					m.put("oitemcode", item.get("OITEMCODE"));
//		            m.put("itemcode", pal.getItemcode());
//		            m.put("qty", formatQty(pal.getQty()));
//		            m.put("bdate", bdate);
//		            m.put("seq", seq);
//		            m.put("scmmex", scmmex);
//		            m.put("type", "pallet");
//
//
//				} else if (barcode.split("_", -1).length == 6){
//					String[] parts = barcode.split("_", -1);
//					Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
//					m.put("oitemcode", item.get("OITEMCODE"));
//					m.put("itemcode", item.get("ITEMCODE"));
//					m.put("bdate", parts[2].substring(2) + parts[1] + parts[0]);
//					m.put("seq", (parts != null && parts.length > 5) ? parts[5] : "");
//					m.put("qty", resolveBarcodeQty(barcode));
//				}else {
//					result.put("response", "fail4");
//					result.put("message", "지원되지 않는 바코드 형식: " + barcode);
//					throw new RuntimeException("INVALID_BARCODE_FORMAT");
//				}
//		        m.put("dmemo","OUTPUT");
//		        // INSERT: 예외출고
//		        int insOutbound = 0;
//
//	            System.out.println("not month enddate!");
//	            insOutbound = purchaseMapper.insOutboundException(m);
//
//		        int affected = 0;
//		        purchaseMapper.removeBarcode(m);
//		        m.put("laststatus", 50);
//		        if(barcode.split(",").length ==5) {
//		        	affected = purchaseMapper.insertStockOutput(m);
//		        	purchaseMapper.updateLaststatusPart(m);
//		        }else {
//		        	purchaseMapper.updateLaststatusPallet(m);
//		        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
//		        	for(int i = 0; i<bbarcode.size(); i++) {
//		        		m.put("barcode", bbarcode.get(i));
//		        		m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
//		        		affected = purchaseMapper.insertStockOutput(m);
//		        		purchaseMapper.updateLaststatusPart(m);
//		        	}
//		        }
//
//	    	}
//	    }

		// 창고이동작업
		if(!exceptionOutBarcodes.isEmpty()) {
			for(String barcode: exceptionOutBarcodes) {
	    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
	    		String okyn = "Y";
	    		if ("Defective".equals(labelType)) {
	    			okyn = "N";
	    		}

				String preStorage = purchaseMapper.getPreviousStorage(barcode);
	    		Map<String,Object> m = new HashMap<String, Object>();
				m.put("mainkind", "MOVE");
				m.put("barcode", barcode);
				m.put("kind", "STORAGEMOVE");
				m.put("source", "STORAGEMOVE");
				m.put("storage", storage);
				m.put("storage1", preStorage);				// 이전 창고
				m.put("storage2", storage);					// 현재 창고
				m.put("factory", factory);
				m.put("factory2", factory);
				m.put("date", date);
				m.put("loginid", loginid);
				m.put("memo", "-");
				m.put("rack", "-");
				m.put("module", "-");
				m.put("levelcode", "-");
				m.put("position", "-");
				m.put("okyn", okyn );
				m.put("movesource", "RECEIVING");
				m.put("dmemo", "MOVE WAREHOUSE");
				m.put("laststatus", 10);

	    		// 바코드 파싱 (인라인)
		        if (barcode.split("_", -1).length == 6){						// 박스 바코드만 가능
					String[] parts = barcode.split("_", -1);
					Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
					m.put("oitemcode", item.get("OITEMCODE"));
					m.put("itemcode", item.get("ITEMCODE"));
					m.put("qty", resolveBarcodeQty(barcode));
					m.put("location", factory+"-"+storage+"-"+item.get("CAR"));
					m.put("rack", item.get("CAR"));
				}else {
					result.put("response", "fail4");
					result.put("message", "지원되지 않는 바코드 형식: " + barcode);
					throw new RuntimeException("INVALID_BARCODE_FORMAT");
				}
				
				// 창고 이동 처리
				purchaseMapper.transferWarehouseStockMove(m);

				// 이동한 창고로 location 저장
				// 단품 OUT (from)
				m.put("outqty", resolveBarcodeQty(barcode));
				m.put("inqty", 0);
				purchaseMapper.transferWarehouseStock(m);

				// 단품 IN (to)
				m.put("outqty", 0);
				m.put("inqty", resolveBarcodeQty(barcode));
				m.put("storage1", storage);
				m.put("storage2", "");
				purchaseMapper.transferWarehouseStock(m);

				purchaseMapper.removeBarcode(m);
				purchaseMapper.saveLocation(m);
				purchaseMapper.updateLaststatusPart(m);
	    	}
	    }
	    
	    // 예외입고작업
	    if (!missingBarcodes.isEmpty()) { 
	    	for(String barcode:missingBarcodes) {
	    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
	    		String okyn = "Y";
	    		if ("Defective".equals(labelType)) {
	    			okyn = "N";
	    		}
	    		Map<String,Object> m = new HashMap<String, Object>();
		    	m.put("date", date);
		    	m.put("barcode", barcode);
		    	m.put("loginid", loginid);
		    	m.put("source", "INCOMINGEXCEPTION");
		    	m.put("main", "IN");
		    	m.put("kind", "INCOMINGEXCEPTION");
		    	m.put("storage", storage);
		    	m.put("factory", factory);
		    	//m.put("custname", custname);
		    	m.put("location", factory+"-"+storage);
		    	m.put("memo", "");
		    	m.put("rack", "");
		    	m.put("module", "");
		    	m.put("levelcode", "");
		    	m.put("position", "");
		    	m.put("memo", "BARCODECOUNT");
		    	m.put("okyn", okyn);
		    	
		    	//m.put("custcode", cucode);
		        // 바코드 파싱 (인라인)
		        if (barcode.split(",").length == 5) {		// 파트라벨바코드
		            String[] parts = barcode.split(",");
					Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(parts[0]);
					m.put("oitemcode", item.get("OITEMCODE"));
		            m.put("itemcode", parts[0]);
		            m.put("bdate", parts[1]);
		            m.put("seq", parts[2]);
		            m.put("qty", resolveBarcodeQty(barcode));
		            m.put("scmmex", parts[4]);
		            m.put("type", "box");
					m.put("rack", item.get("CAR"));
					m.put("location", factory+"-"+storage+"-"+item.get("CAR"));

		        } else if (barcode.startsWith("P") && (barcode.endsWith("MEX") || barcode.endsWith("USA"))) {                // 새로운 팔레트 라벨 바코드
					PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
					if (pal == null) {
						result.put("response", "warning.pallet.infoNotFound");
						result.put("barcode", Arrays.asList(barcode));
						return result;
						//throw new RuntimeException("NO_PALLET_INFO");
					} else {
						String[] parts = barcode.split(",", -1);
						String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
						String seq = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7) : "";
						String scmmex = (parts.length >= 4) ? parts[3] : "";
						Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(pal.getItemcode());
						m.put("oitemcode", item.get("OITEMCODE"));
						m.put("itemcode", pal.getItemcode());
						m.put("qty", formatQty(pal.getQty()));
						m.put("bdate", bdate);
						m.put("seq", seq);
						m.put("scmmex", scmmex);
						m.put("type", "pallet");
						m.put("rack", item.get("CAR"));
						m.put("location", factory+"-"+storage+"-"+item.get("CAR"));
					}
				} else if (barcode.split("_",-1).length == 6){
					String[] parts = barcode.split("_", -1);
					Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
					m.put("oitemcode", item.get("OITEMCODE"));
					m.put("itemcode", item.get("ITEMCODE"));
					m.put("bdate", parts[2].substring(2) + parts[1] + parts[0]);
					m.put("seq", (parts != null && parts.length > 5) ? parts[5] : "");
					m.put("qty", resolveBarcodeQty(barcode));
					m.put("rack", item.get("CAR"));
					m.put("location", factory+"-"+storage+"-"+item.get("CAR"));
				} else {
		            result.put("response", "fail4");
		            result.put("message", "지원되지 않는 바코드 형식: " + barcode);
		            throw new RuntimeException("INVALID_BARCODE_FORMAT");
		        }

		        // INSERT: 재고실사 예외입고 
		        m.put("source2", "STOCKCOUNT");
		        int insInbound = 0;

	            System.out.println("not month enddate!");
	            // 말일이 아니면 기존으로 예외 insert
	            insInbound = purchaseMapper.insInboundException(m);
		        
		        
		        m.put("laststatus", 10);
		        m.put("dmemo", "BARCODECOUNT");
		        purchaseMapper.removeBarcode(m);
		        int inslocation = purchaseMapper.saveLocationOKYN(m);
		        int affected = 0;
		        if(barcode.split(",").length ==5) {
		        	affected = purchaseMapper.insStockInbound(m);
		        	purchaseMapper.updateLaststatusPart(m);
		        }else {
		        	purchaseMapper.updateLaststatusPallet(m);
		        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
		        	for(int i = 0; i<bbarcode.size(); i++) {
		        		m.put("barcode", bbarcode.get(i));
		        		m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
		        		affected = purchaseMapper.insStockInbound(m);
		        		purchaseMapper.updateLaststatusPart(m);
		        	}
		        }
	    	}
	    }
	    
	    
		
		for (String barcode : barcodes) {		// 바코드 실사
			labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
    		String okyn = "Y";
    		if("Defective".equals(labelType)) {
    			okyn = "N";
    		}
			Map<String, Object> map = new HashMap<>();
			map.put("date", date);
			map.put("barcode", barcode);
			map.put("loginid", loginid);
			map.put("storage", storage);
			map.put("scantype", "BARCODE");
			map.put("location", factory+"-"+storage);
			map.put("factory", factory);
			map.put("memo", " ");
			map.put("rack", " ");
			map.put("module", " ");
			map.put("levelcode", " ");
			map.put("position", " ");
			map.put("source", "BARCODECOUNT");
			map.put("kind", "COUNT");
			map.put("previousLocation", " ");
			map.put("stocksource", "ALWAYS");
			map.put("okyn", okyn);
			String location = purchaseMapper.searchRoomcodeyn(barcode);
			
			if (barcode.split(",").length == 5) {
				map.put("itemcode", barcode.split(",")[0]);
				map.put("lotdate", "20" + barcode.split(",")[1]);
				map.put("seq", barcode.split(",")[2]);
				map.put("qty", resolveBarcodeQty(barcode));
				map.put("scmmex", barcode.split(",")[4]);
				map.put("type", "box");
				map.put("pbarcode", "");
				purchaseMapper.insRealStock(map);
				// stockInService.intfInsert1(map);
				// stockInService.intfInsert2(map);
			} else if (barcode.startsWith("P") && (barcode.endsWith("MEX") || barcode.endsWith("USA"))) {
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				if (palletInfo != null) {			// 팔레트값이 없을때 fail2
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("qty", palletInfo.getQty());
				    map.put("lotdate", "20" + barcode.split(",")[0].substring(1, 7));
				    map.put("seq", barcode.split(",")[0].substring(7));
				    map.put("qty",formatQty( palletInfo.getQty()));
				    map.put("scmmex", barcode.split(",")[3]);
				} else {
					result.put("response", "warning.pallet.infoNotFound ");
					result.put("barcode", Arrays.asList(barcode));
					return result;
				}
				map.put("type", "pallet");
				map.put("pbarcode", "");
				purchaseMapper.insRealStock(map);
			}else if (barcode.split("_",-1).length == 6){
				String[] parts = barcode.split("_", -1);
				Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
				map.put("itemcode", item.get("ITEMCODE"));
				map.put("bdate", parts[2].substring(2) + parts[1] + parts[0]);
				map.put("seq", (parts != null && parts.length > 5) ? parts[5] : "");
				map.put("qty", resolveBarcodeQty(barcode));
				map.put("scmmex", "");
				map.put("lotdate",  parts[2] + parts[1] + parts[0]);
				map.put("type", "etc");
				map.put("pbarcode", "");
				purchaseMapper.insRealStock(map);
			}  else {// 기타 바코드
				map.put("itemcode", "");
				map.put("lotdate", "");
				map.put("seq", "");
				map.put("qty", "");
				map.put("scmmex", "");
				map.put("type", "etc");
				map.put("pbarcode", "");
				purchaseMapper.insRealStock(map);
			}
			if (location == null || location.isEmpty()) {
				if(barcode.split(",").length ==5) {		// 파트라벨
					purchaseMapper.insStock(map);
					Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(barcode.split(",")[0]);
					map.put("rack", item.get("CAR"));
					map.put("location", factory+"-"+storage+"-"+item.get("CAR"));
					purchaseMapper.basicLocation(map);			// 기본위치가 없을때 공장-창고로 적재
		        }else if (barcode.split("_",-1).length == 6){	// 박스라벨
					String[] parts = barcode.split("_", -1);
					Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
					map.put("rack", item.get("CAR"));
					map.put("location", factory+"-"+storage+"-"+item.get("CAR"));
					purchaseMapper.basicLocation(map);
				}else if(barcode.split(",").length ==4){											//팔레트라벨
					Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(barcode.split(",")[1]);
					map.put("rack", item.get("CAR"));
					map.put("location", factory+"-"+storage+"-"+item.get("CAR"));
					purchaseMapper.basicLocation(map);
					List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
		        	for(int i = 0; i<bbarcode.size(); i++) {
		        		map.put("barcode", bbarcode.get(i));
		        		map.put("qty", resolveBarcodeQty(bbarcode.get(i)));
		        		purchaseMapper.insStock(map);
		        	}
		        }else{
					map.put("rack", "");
					purchaseMapper.basicLocation(map);
					purchaseMapper.insStock(map);
				}
			}else{
				map.put("previousLocation", location);
				map.put("dmemo", "BARCODECOUNT");
				//purchaseMapper.removeBarcode(map);		250930 바코드 재고실사는 위치에 영향을 주지 않음 
			}
			//purchaseMapper.saveLocation(map);					
			System.out.println("저장할 바코드: " + barcode);
			map.put("laststatus",20);
			if(barcode.split(",").length ==5 || barcode.split("_", -1).length ==6) {
	        	purchaseMapper.updateLaststatusPart(map);
	        	
	        }else {
	        	purchaseMapper.updateLaststatusPallet(map);
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		map.put("barcode", bbarcode.get(i));
	        		map.put("qty", resolveBarcodeQty(bbarcode.get(i)));
	        		purchaseMapper.updateLaststatusPart(map);
	        	}
	        }
		}
		result.put("response", "success");
//		return result;

		throw new RuntimeException("롤백");
	}
	
	// 재고실사 - 말일
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> insRealStockLastDay(BarcodeVO request) {
		String date = request.getDate();

		Map<String, Object> result = new HashMap<>();
		String factory = request.getFactory();
		String storage = request.getStorage();
		List<String> list = request.getBarcode();
		String loginid = request.getLoginid();
		String status = request.getStatus1();
		List<String> barcodes2 = request.getBarcode2();

		String labelType = "";

		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}

		// barcode 테이블에 존재 하지 않는 박스 바코드 insert
		Map<String, Object> lotCheckResult1 = barcodeValidator.lotCheck(request.getBarcode());
		if (!lotCheckResult1.isEmpty()) {
			return lotCheckResult1;
		}

		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(request.getBarcode());
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }

	    log.info("status : "+status);

		// ✅ 팔레트-파트 전처리
		FilterResult fr = palletFilter.removeChildPartsIfParentPresent(list);
		List<String> barcodes = fr.getFiltered();
		
		// 해당 창고에 없는지 체크
	    Map <String, Object> checkMap = new HashMap<String, Object>();
	    checkMap.put("list", request.getBarcode());
	    checkMap.put("factory", factory);
	    checkMap.put("storage", storage);
	    Map<String, Object> storageCheckOut = barcodeValidator.storageCheckOut(checkMap);		// 현재 재고에 없는 바코드
	    List<String> missingBarcodes = (List<String>) storageCheckOut.getOrDefault("barcode", new ArrayList<>());

	    // 다른창고에 있는 바코드
	    Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
	    List<String> storageBarcodes = (List<String>) inputStorageCheck.getOrDefault("barcode", new ArrayList<>());

	    // 4. Set으로 겹치는 바코드만 추출
	    Set<String> otherBarcodesSet = new HashSet<>();
	    otherBarcodesSet.addAll(storageBarcodes);

		// 현재 창고에 없는 바코드 중 다른 공장/창고에 있는 바코드 예외출고, location N 진행  ====> 예외출고 대신 창고이동으로 처리
	    List<String> exceptionOutBarcodes = missingBarcodes.stream()
	        .filter(otherBarcodesSet::contains)
	        .collect(Collectors.toList());
	    System.out.println("예외출고바코드: " + exceptionOutBarcodes);
	    
	    System.out.println("예외입고 바코드: " + missingBarcodes);
	    
	    // 예외출고작업
//	    if(!exceptionOutBarcodes.isEmpty()) {
//	    	for(String barcode: exceptionOutBarcodes) {
//	    		String labelType = "";
//	    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
//	    		String okyn = "Y";
//	    		if ("Defective".equals(labelType)) {
//	    			okyn = "N";
//	    		}
//	    		String preLocation = purchaseMapper.getPreviousLocation(barcode);
//	    		Map<String,Object> m = new HashMap<String, Object>();
//	    		m.put("date", date);
//	    		m.put("loginid", loginid);
//	    		m.put("source", "LOADEXCEPTION");
//	    		m.put("main", "OUT");
//	    		m.put("kind", "LOADEXCEPTION");
//	    		m.put("storage", preLocation.split("-")[1]);
//	    		m.put("factory", preLocation.split("-")[0]);
//	    		m.put("location", preLocation);
//	    		m.put("memo", "BARCODECOUNT");
//	    		m.put("barcode", barcode);
//	    		m.put("source2", "STOCKCOUNT");
//	    		m.put("okyn", okyn);
//	    		// 바코드 파싱 (인라인)
//		        if (barcode.split(",").length ==5) {
//		            String[] parts = barcode.split(",", -1);
//					Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(parts[0]);
//					m.put("oitemcode", item.get("OITEMCODE"));
//		            m.put("itemcode", parts[0]);
//		            m.put("bdate", parts[1]);
//		            m.put("seq", parts[2]);
//		            m.put("qty", resolveBarcodeQty(barcode));
//		            m.put("scmmex", parts[4]);
//		            m.put("type", "box");
//
//		        } else if (barcode.startsWith("P") && (barcode.endsWith("MEX") || barcode.endsWith("USA"))) {
//					PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
//					if (pal == null) {
//						result.put("response", "warning.pallet.infoNotFound");
//		                result.put("barcode", Arrays.asList(barcode));
//		                return result;
//						//throw new RuntimeException("NO_PALLET_INFO");
//					}
//					String[] parts = barcode.split(",", -1);
//					String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
//					String seq = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7) : "";
//					String scmmex = (parts.length >= 4) ? parts[3] : "";
//					Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(parts[0]);
//					m.put("oitemcode", item.get("OITEMCODE"));
//		            m.put("itemcode", pal.getItemcode());
//		            m.put("qty", formatQty(pal.getQty()));
//		            m.put("bdate", bdate);
//		            m.put("seq", seq);
//		            m.put("scmmex", scmmex);
//		            m.put("type", "pallet");
//
//
//				} else if (barcode.split("_", -1).length == 6){
//					String[] parts = barcode.split("_", -1);
//					Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
//
//					m.put("oitemcode", item.get("OITEMCODE"));
//					m.put("itemcode", item.get("ITEMCODE"));
//					m.put("bdate", parts[2].substring(2) + parts[1] + parts[0]);
//					m.put("seq", (parts != null && parts.length > 5) ? parts[5] : "");
//					m.put("qty", resolveBarcodeQty(barcode));
//				}else {
//					result.put("response", "fail4");
//					result.put("message", "지원되지 않는 바코드 형식: " + barcode);
//					throw new RuntimeException("INVALID_BARCODE_FORMAT");
//				}
//		        m.put("dmemo","OUTPUT");
//		        // INSERT: 예외출고
//		        int insOutbound = 0;
//	            System.out.println("month enddate!");
//	            insOutbound = purchaseMapper.insOutboundExceptionN(m);
//
//		        int affected = 0;
//		        purchaseMapper.removeBarcode(m);
//		        m.put("laststatus", 50);
//		        if(barcode.split(",").length ==5) {
//		        	affected = purchaseMapper.insertStockOutput(m);
//		        	purchaseMapper.updateLaststatusPart(m);
//		        }else if(barcode.split("_",-1).length ==6){
//					affected = purchaseMapper.insertStockOutput(m);
//					purchaseMapper.updateLaststatusPart(m);
//				}else {
//		        	purchaseMapper.updateLaststatusPallet(m);
//		        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
//		        	for(int i = 0; i<bbarcode.size(); i++) {
//		        		m.put("barcode", bbarcode.get(i));
//		        		m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
//		        		affected = purchaseMapper.insertStockOutput(m);
//		        		purchaseMapper.updateLaststatusPart(m);
//		        	}
//		        }
//
//	    	}
//	    }

		// 창고이동작업
		if(!exceptionOutBarcodes.isEmpty()) {
			for(String barcode: exceptionOutBarcodes) {
				labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
				String okyn = "Y";
				if ("Defective".equals(labelType)) {
					okyn = "N";
				}

				String preStorage = purchaseMapper.getPreviousStorage(barcode);
				Map<String,Object> m = new HashMap<String, Object>();
				m.put("mainkind", "MOVE");
				m.put("barcode", barcode);
				m.put("kind", "STORAGEMOVE");
				m.put("source", "STORAGEMOVE");
				m.put("storage", storage);
				m.put("storage1", preStorage);				// 이전 창고
				m.put("storage2", storage);					// 현재 창고
				m.put("factory", factory);
				m.put("factory2", factory);
				m.put("date", date);
				m.put("loginid", loginid);
				m.put("memo", "-");
				m.put("rack", "-");
				m.put("module", "-");
				m.put("levelcode", "-");
				m.put("position", "-");
				m.put("okyn", okyn );
				m.put("movesource", "RECEIVING");
				m.put("dmemo", "MOVE WAREHOUSE");
				m.put("laststatus", 10);

				// 바코드 파싱 (인라인)
				if (barcode.split("_", -1).length == 6){						// 박스 바코드만 가능
					String[] parts = barcode.split("_", -1);
					Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
					m.put("oitemcode", item.get("OITEMCODE"));
					m.put("itemcode", item.get("ITEMCODE"));
					m.put("qty", resolveBarcodeQty(barcode));
					m.put("location", factory+"-"+storage+"-"+item.get("CAR"));
					m.put("rack", item.get("CAR"));
				}else {
					result.put("response", "fail4");
					result.put("message", "지원되지 않는 바코드 형식: " + barcode);
					throw new RuntimeException("INVALID_BARCODE_FORMAT");
				}

				// 창고 이동 처리
				purchaseMapper.transferWarehouseStockMove(m);

				// 이동한 창고로 location 저장
				// 단품 OUT (from)
				m.put("outqty", resolveBarcodeQty(barcode));
				m.put("inqty", 0);
				purchaseMapper.transferWarehouseStock(m);

				// 단품 IN (to)
				m.put("outqty", 0);
				m.put("inqty", resolveBarcodeQty(barcode));
				m.put("storage1", storage);
				m.put("storage2", "");
				purchaseMapper.transferWarehouseStock(m);

				purchaseMapper.removeBarcode(m);
				purchaseMapper.saveLocation(m);
				purchaseMapper.updateLaststatusPart(m);
			}
		}
	    
	    // 예외입고작업
	    if (!missingBarcodes.isEmpty()) { 
	    	for(String barcode:missingBarcodes) {
	    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
	    		String okyn = "Y";
	    		if ("Defective".equals(labelType)) {
	    			okyn = "N";
	    		}
	    		Map<String,Object> m = new HashMap<String, Object>();
		    	m.put("date", date);
		    	m.put("barcode", barcode);
		    	m.put("loginid", loginid);
		    	m.put("source", "INCOMINGEXCEPTION");
		    	m.put("main", "IN");
		    	m.put("kind", "INCOMINGEXCEPTION");
		    	m.put("storage", storage);
		    	m.put("factory", factory);
		    	//m.put("custname", custname);
		    	m.put("location", factory+"-"+storage);
		    	m.put("rack", "");
		    	m.put("module", "");
		    	m.put("levelcode", "");
		    	m.put("position", "");
		    	m.put("memo", "BARCODECOUNT");
		    	m.put("okyn", okyn);
		    	
		    	//m.put("custcode", cucode);
		        // 바코드 파싱 (인라인)
		        if (barcode.split(",").length == 5) {		// 파트라벨바코드
		            String[] parts = barcode.split(",");
					Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(parts[0]);
					m.put("oitemcode", item.get("OITEMCODE"));
		            m.put("itemcode", parts[0]);
		            m.put("bdate", parts[1]);
		            m.put("seq", parts[2]);
		            m.put("qty", resolveBarcodeQty(barcode));
		            m.put("scmmex", parts[4]);
		            m.put("type", "box");
					m.put("rack", item.get("CAR"));
					m.put("location", factory+"-"+storage+"-"+item.get("CAR"));

		        } else if (barcode.startsWith("P") && (barcode.endsWith("MEX") || barcode.endsWith("USA"))) {				// 새로운 팔레트 라벨 바코드
		            PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
		            if (pal == null) {
		                result.put("response", "warning.pallet.infoNotFound");
		                result.put("barcode", Arrays.asList(barcode));
		                return result;
		                //throw new RuntimeException("NO_PALLET_INFO");
		            }else {
		            	String[] parts = barcode.split(",", -1);
			            String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
			            String seq   = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7)   : "";
			            String scmmex = (parts.length >= 4) ? parts[3] : "";
						Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(pal.getItemcode());
						m.put("oitemcode", item.get("OITEMCODE"));
			            m.put("itemcode", pal.getItemcode());
			            m.put("qty", formatQty(pal.getQty()));
			            m.put("bdate", bdate);
			            m.put("seq", seq);
			            m.put("scmmex", scmmex);
						m.put("type", "pallet");
						m.put("rack", item.get("CAR"));
						m.put("location", factory+"-"+storage+"-"+item.get("CAR"));
		            }
		        } else if (barcode.split("_",-1).length == 6){
					String[] parts = barcode.split("_", -1);
					Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
					m.put("oitemcode", item.get("OITEMCODE"));
					m.put("itemcode", item.get("ITEMCODE"));
					m.put("bdate", parts[2].substring(2) + parts[1] + parts[0]);
					m.put("seq", (parts != null && parts.length > 5) ? parts[5] : "");
					m.put("qty", resolveBarcodeQty(barcode));
					m.put("rack", item.get("CAR"));
					m.put("location", factory+"-"+storage+"-"+item.get("CAR"));
				} else {
		            result.put("response", "fail4");
		            result.put("message", "지원되지 않는 바코드 형식: " + barcode);
		            throw new RuntimeException("INVALID_BARCODE_FORMAT");
		        }

		     // INSERT: 재고실사 예외입고 
		        m.put("source2", "STOCKCOUNT");
		        int insInbound = 0;
	            System.out.println("month enddate!");
	            // 말일 재고실사하면 입고테이블에 N으로 insert
	            insInbound = purchaseMapper.insInboundExceptionN(m);
	        
		        
		        m.put("laststatus", 10);
		        m.put("dmemo", "BARCODECOUNT");
		        purchaseMapper.removeBarcode(m);
		        int inslocation = purchaseMapper.saveLocationOKYN(m);
		        int affected = 0;
		        if(barcode.split(",").length ==5) {
		        	affected = purchaseMapper.insStockInbound(m);
		        	purchaseMapper.updateLaststatusPart(m);
		        }else if(barcode.split("_",-1).length ==6){
					affected = purchaseMapper.insStockInbound(m);
					purchaseMapper.updateLaststatusPart(m);
				}else {
		        	purchaseMapper.updateLaststatusPallet(m);
		        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
		        	for(int i = 0; i<bbarcode.size(); i++) {
		        		m.put("barcode", bbarcode.get(i));
		        		m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
		        		affected = purchaseMapper.insStockInbound(m);
		        		purchaseMapper.updateLaststatusPart(m);
		        	}
		        }
	    	}
	    }
	    
	    
		
		for (String barcode : barcodes) {		// 바코드 실사
			Map<String, Object> map = new HashMap<>();
			map.put("date", date);
			map.put("barcode", barcode);
			map.put("loginid", loginid);
			map.put("storage", storage);
			map.put("scantype", "BARCODE");
			map.put("location", factory+"-"+storage);
			map.put("factory", factory);
			map.put("memo", " ");
			map.put("rack", " ");
			map.put("module", " ");
			map.put("levelcode", " ");
			map.put("position", " ");
			map.put("source", "BARCODECOUNT");
			map.put("kind", "COUNT");
			map.put("previousLocation", " ");
			map.put("stocksource", "LASTDAY");
			String location = purchaseMapper.searchRoomcodeyn(barcode);
			
			if (barcode.split(",").length == 5) {
				map.put("itemcode", barcode.split(",")[0]);
				map.put("lotdate", "20" + barcode.split(",")[1]);
				map.put("seq", barcode.split(",")[2]);
				map.put("qty", resolveBarcodeQty(barcode));
				map.put("scmmex", barcode.split(",")[4]);
				map.put("type", "box");
				map.put("pbarcode", "");
				purchaseMapper.insRealStock(map);
				// stockInService.intfInsert1(map);
				// stockInService.intfInsert2(map);
			} else if (barcode.startsWith("P") && (barcode.endsWith("MEX") || barcode.endsWith("USA"))) {
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				if (palletInfo != null) {			// 팔레트값이 없을때 fail2
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("lotdate", "20" + barcode.split(",")[0].substring(1, 7));
				    map.put("seq", barcode.split(",")[0].substring(7));
				    map.put("qty",formatQty( palletInfo.getQty()));
				    map.put("scmmex", barcode.split(",")[3]);
				} else {
					result.put("response", "warning.pallet.infoNotFound ");
					result.put("barcode", Arrays.asList(barcode));
					return result;
				}
				map.put("type", "pallet");
				map.put("pbarcode", "");
				purchaseMapper.insRealStock(map);
			} else if (barcode.split("_",-1).length == 6){
				String[] parts = barcode.split("_", -1);
				Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
				map.put("itemcode", item.get("ITEMCODE"));
				map.put("bdate", parts[2].substring(2) + parts[1] + parts[0]);
				map.put("seq", (parts != null && parts.length > 5) ? parts[5] : "");
				map.put("qty", resolveBarcodeQty(barcode));
				map.put("scmmex", "");
				map.put("lotdate",  parts[2] + parts[1] + parts[0]);
				map.put("type", "etc");
				map.put("pbarcode", "");
				purchaseMapper.insRealStock(map);
			} else {// 기타 바코드
				map.put("itemcode", "");
				map.put("lotdate", "");
				map.put("seq", "");
				map.put("qty", "");
				map.put("scmmex", "");
				map.put("type", "etc");
				map.put("pbarcode", "");
				purchaseMapper.insRealStock(map);
			}
			if (location == null || location.isEmpty()) {
				if(barcode.split(",").length ==5) {		// 파트라벨
					purchaseMapper.insStock(map);
					Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(barcode.split(",")[0]);
					map.put("rack", item.get("CAR"));
					map.put("location", factory+"-"+storage+"-"+item.get("CAR"));
					purchaseMapper.basicLocation(map);			// 기본위치가 없을때 공장-창고로 적재
				}else if (barcode.split("_",-1).length == 6){	// 박스라벨
					String[] parts = barcode.split("_", -1);
					Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
					map.put("rack", item.get("CAR"));
					map.put("location", factory+"-"+storage+"-"+item.get("CAR"));
					purchaseMapper.basicLocation(map);
				}else if(barcode.split(",").length ==4){											//팔레트라벨
					Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(barcode.split(",")[1]);
					map.put("rack", item.get("CAR"));
					map.put("location", factory+"-"+storage+"-"+item.get("CAR"));
					purchaseMapper.basicLocation(map);
					List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
					for(int i = 0; i<bbarcode.size(); i++) {
						map.put("barcode", bbarcode.get(i));
						map.put("qty", resolveBarcodeQty(bbarcode.get(i)));
						purchaseMapper.insStock(map);
					}
				}else{
					map.put("rack", "");
					purchaseMapper.basicLocation(map);
					purchaseMapper.insStock(map);
				}
			}else{
				map.put("previousLocation", location);
				map.put("dmemo", "BARCODECOUNT");
				//purchaseMapper.removeBarcode(map);		250930 바코드 재고실사는 위치에 영향을 주지 않음 
			}
			//purchaseMapper.saveLocation(map);					
			System.out.println("저장할 바코드: " + barcode);
			map.put("laststatus",20);
			if(barcode.split(",").length ==5) {
	        	purchaseMapper.updateLaststatusPart(map);
	        	
	        }else if(barcode.split("_",-1).length ==6){
				purchaseMapper.updateLaststatusPart(map);
			}else {
	        	purchaseMapper.updateLaststatusPallet(map);
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		map.put("barcode", bbarcode.get(i));
	        		map.put("qty", resolveBarcodeQty(bbarcode.get(i)));
	        		purchaseMapper.updateLaststatusPart(map);
	        	}
	        }
		}
		result.put("response", "success");
		return result;
	}

	public List<ItemLocationVO> selItemLocation(String itemcode) {
		return purchaseMapper.selItemLocation(itemcode);
	}

	public int existLocation(Map<String, Object> map) {
		return purchaseMapper.existLocation(map);
	}
	
	// 적재
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> saveLocation(BarcodeVO request) {
		Map<String, Object> result = new HashMap<String, Object>();
		
		// ====================================
		// 250909 바코드 걸러내는 작업 추가
		List<String> list = request.getBarcode();
		
		// ✅ 팔레트-파트 전처리: 팔레트와 함께 들어온 파트는 제외
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(list);

	    // ✅ 전처리된 목록으로 교체
	    List<String> barcodes = fr.getFiltered();

//	    // ✅ 제외된(무시된) 파트 목록은 응답에 담아 UI/로그에서 안내할 수 있음(선택)
//	    if (!fr.getExcluded().isEmpty()) {
//	        result.put("ignoredChildParts", fr.getExcluded());
//	    }
		// ====================================
	    
		String location = request.getLocation();
		String memo = request.getMemo();
		String date = request.getDate();
		String status1 = request.getStatus1();		// 로케이션에 적재된 물건이 있는지
		String status2 =request.getStatus2();		// 이미 적재된 바코드인지
		String realStock = request.getRealstock();
		String locationArr[] = location.split("-");
		String source = request.getSource();
		String main = request.getMain();
		String kind = request.getKind();
		String factory = request.getFactory();
		String loginid = request.getLoginid();
		String storage = request.getStorage();
		System.out.println("status1 : "+status1);
		System.out.println("status2 : "+status2);
		System.out.println("realStock : "+realStock);
		
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}

		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(request.getBarcode());
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }

	    Map<String, Object> checkMap = new HashMap<String, Object>();
	    checkMap.put("list", request.getBarcode());
	    checkMap.put("factory", request.getFactory());
	    checkMap.put("storage", storage);
	    
	    // 재고실사 아닐때
	    if(!realStock.equals("Y")) {
	    	// 입고시 다른 창고에 있으면 창고이동 하라고 메시지
		    Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
		    if (!inputStorageCheck.isEmpty()) {
		    	return inputStorageCheck; // 실패 시 리턴
		    }

	 	    // 출고된 바코드인지 체크
	  		Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(request.getBarcode());
	  	    if (!alreadyLoad.isEmpty()) {
	  	        return alreadyLoad; // 실패 시 리턴
	  	    }
	  	    
	  	    // 상태값 10~29사이인지 확인
	  	    Map<String, Object> factoryCheck = barcodeValidator.factoryCheck(checkMap);
	  	    if (!factoryCheck.isEmpty()) {
	  	    	return factoryCheck; // 실패 시 리턴
	  	    }
	    }
	    
	    String status = request.getStatus0();
	    List<String> barcodes2 = request.getBarcode2();
	    log.info("status : "+status);
		if("confirm".equals(status)) {		// 팔레트 바코드 해체작업 
			Set<String> palletList = new LinkedHashSet<>();
			log.info("location pallet unbound start");
			
			// 1) 고유 팔레트만 수집
		    Set<String> pallets = new LinkedHashSet<>();
		    for (String childBarcode : barcodes2) {
		        String pallet = purchaseMapper.searchPallet(childBarcode);
		        if (pallet != null && !pallet.isEmpty()) {
		            pallets.add(pallet);
		        }
		    }
		    // 팔레트로 for문
		    for (String pallet : pallets) {log.info("pallet unbound@@@@");
				Map<String, Object> m = new HashMap<String, Object>();
				
				if(pallet == null || pallet.isEmpty()) {
					
				}else {
					palletList.add(pallet);
					m.put("dmemo", "Location Unbound");
					String location0 = purchaseMapper.searchRoomcodeY(pallet);
					if (location0 == null || location.isEmpty()) {
						m.put("dmemo", "LC LOC NULL");						//250920 HJ 팔레트로 묶인 파트라벨을 적재 후 팔레트 해체될때 NULL이 되는 현상때문에 하드코딩 250923 현재 적재중인위치로이동
						// 바코드 재고실사, 로케이션 재고실사, 리시빙에서 사용
						// 251206 팔레트 purchaseMapper.searchRoomcode(pallet) -> purchaseMapper.searchRoomcodeY(pallet)로 변경
						// 그래서 null인 경우 팔레트테이블만 N으로 업데이트
						// 하단에서 스캔한 파트라벨은 예외입고 에정
						// 스캔안한 파트라벨들은 재고로 안잡히지만 추후 아무것도 안한상태면 예외입고 불출이나 출고했으면 재고실사로 잡아야함
						// 팔레트를 N으로 업데이트
						m.put("barcode", pallet);
						purchaseMapper.palletN(m);
					}else{												//251106 location이 있을때만 작동하도록 수정, 임의로 값을 넣어주면 재고실사때 예외입고가 안되는 현상때문에 수정함
						m.put("barcode", pallet);
						m.put("location", location0);
						String[] parts = location0.split("-");
						m.put("factory", parts.length > 0 ? parts[0] : "");
						m.put("storage", parts.length > 1 ? parts[1] : "");
						m.put("rack", parts.length > 2 ? parts[2] : "");
						m.put("module", parts.length > 3 ? parts[3] : "");
						m.put("levelcode", parts.length > 4 ? parts[4] : "");
						m.put("position", parts.length > 5 ? parts[5] : "");
						m.put("date", date);
						m.put("loginid", loginid);
						m.put("source", "LOCATION UNBOUND");
						// 기존 팔레트라벨 적재위치 정보로 파트라벨 적재
						purchaseMapper.selectLocationSave(m);
						Map<String, Object> map = new HashMap<String, Object>();
						map.put("barcode", pallet);
						map.put("dmemo", "LOCATION");
						purchaseMapper.removeBarcode(map);		// 적재된 팔레트바코드 제거
						// 팔레트 바코드 useyn = n
						purchaseMapper.palletN(map);				// 팔레트라벨 사용 N
					}
				}
			}
			
		}
		
		if(realStock.equals("Y")) {
			// 해당 창고에 없는지 체크
		    Map<String, Object> storageCheckOut = barcodeValidator.storageCheckOut(checkMap);		// 현재 재고에 없는 바코드
		    List<String> missingBarcodes = (List<String>) storageCheckOut.getOrDefault("barcode", new ArrayList<>());

		    // 다른창고에 있는 바코드
		    Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
		    List<String> storageBarcodes = (List<String>) inputStorageCheck.getOrDefault("barcode", new ArrayList<>());

		    // 4. Set으로 겹치는 바코드만 추출
		    Set<String> otherBarcodesSet = new HashSet<>();
		    otherBarcodesSet.addAll(storageBarcodes);

		    // 현재 창고에 없는 바코드 중 다른 창고에 있는 바코드 예외출고, location N 진행
		    List<String> exceptionOutBarcodes = missingBarcodes.stream()
		        .filter(otherBarcodesSet::contains)
		        .collect(Collectors.toList());
		    System.out.println("예외출고바코드: " + exceptionOutBarcodes);
		    
		    System.out.println("예외입고 바코드: " + missingBarcodes);
		    
		    // 예외출고작업
		    if(!exceptionOutBarcodes.isEmpty()) {
		    	for(String barcode: exceptionOutBarcodes) {
		    		String labelType = "";
		    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
		    		String okyn = "Y";
		    		if ("Defective".equals(labelType)) {
		    			okyn = "N";
		    		}
		    		String preLocation = purchaseMapper.getPreviousLocation(barcode);
		    		Map<String,Object> m = new HashMap<String, Object>();
		    		m.put("date", date);
		    		m.put("loginid", loginid);
		    		m.put("source", "LOADEXCEPTION");
		    		m.put("main", "OUT");
		    		m.put("kind", "LOADEXCEPTION");
		    		m.put("storage", preLocation.split("-")[1]);
		    		m.put("factory", preLocation.split("-")[0]);
		    		m.put("location",preLocation);
		    		m.put("memo", "LOCATIONCOUNT");
		    		m.put("barcode", barcode);
		    		m.put("source2", "STOCKCOUNT");
		    		m.put("okyn", okyn);
		    		// 바코드 파싱 (인라인)
					if (barcode.split(",").length == 5 && barcode.endsWith("USA")) {		// 파트라벨바코드
						String[] parts = barcode.split(",");
						Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(parts[0]);
						m.put("oitemcode", item.get("OITEMCODE"));
						m.put("itemcode", parts[0]);

						m.put("bdate", parts[1]);
						m.put("seq", parts[2]);
						m.put("qty", resolveBarcodeQty(barcode));
						m.put("scmmex", parts[4]);
						m.put("type", "box");

					}else if (barcode.split(",").length == 4 &&  barcode.endsWith("USA")) {
						PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
						if (pal == null) {
							result.put("response", "warning.pallet.infoNotFound");
			                result.put("barcode", Arrays.asList(barcode));
			                return result;
							//throw new RuntimeException("NO_PALLET_INFO");
						}
						String[] parts = barcode.split(",", -1);
						String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
						String seq = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7) : "";
						String scmmex = (parts.length >= 4) ? parts[3] : "";
						Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(pal.getItemcode());
						m.put("oitemcode", item.get("OITEMCODE"));
			            m.put("itemcode", pal.getItemcode());
			            m.put("qty", formatQty(pal.getQty()));
			            m.put("bdate", bdate);
			            m.put("seq", seq);
			            m.put("scmmex", scmmex);
			            m.put("type", "pallet");
			            

					} else if(barcode.split("_", -1).length == 6){		// 박스 바코드일때
						String[] parts = barcode.split("_", -1);
						Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
						m.put("itemcode", item.get("ITEMCODE"));
						m.put("oitemcode",parts[3]);
						m.put("bdate", parts[2].substring(2) + parts[1] + parts[0]);
						m.put("seq", (parts != null && parts.length > 5) ? parts[5] : "");
						m.put("qty", resolveBarcodeQty(barcode));

					} else {
						result.put("response", "fail4");
						result.put("message", "지원되지 않는 바코드 형식: " + barcode);
						throw new RuntimeException("INVALID_BARCODE_FORMAT");
					}
			        m.put("dmemo","OUTPUT");
			        // INSERT: 예외출고
			        int insOutbound = 0;

		            System.out.println("not month enddate!");
		            insOutbound = purchaseMapper.insOutboundException(m);

			        int affected = 0;
			        purchaseMapper.removeBarcode(m);
			        m.put("laststatus", 50);
			        if(barcode.split(",").length ==5) {
			        	affected = purchaseMapper.insertStockOutput(m);
			        	purchaseMapper.updateLaststatusPart(m);
			        }else {
			        	purchaseMapper.updateLaststatusPallet(m);
			        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
			        	for(int i = 0; i<bbarcode.size(); i++) {
			        		m.put("barcode", bbarcode.get(i));
			        		m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
			        		affected = purchaseMapper.insertStockOutput(m);
			        		purchaseMapper.updateLaststatusPart(m);
			        	}
			        }
		    		
		    	}
		    }
			
		    if (!missingBarcodes.isEmpty()) { // 예외입고작업
		    	for(String barcode:missingBarcodes) {
		    		String labelType = "";
		    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
		    		String okyn = "Y";
		    		if ("Defective".equals(labelType)) {
		    			okyn = "N";
		    		}
		    		Map<String,Object> m = new HashMap<String, Object>();
			    	m.put("date", date);
			    	m.put("barcode", barcode);
			    	m.put("loginid", loginid);
			    	m.put("source", "INCOMINGEXCEPTION");
			    	m.put("main", "IN");
			    	m.put("kind", "INCOMINGEXCEPTION");
			    	m.put("storage", location.split("-")[1]);
			    	m.put("factory", factory);
			    	//m.put("custname", custname);
			    	m.put("location", location);
			    	m.put("memo", "");
			    	m.put("rack", "");
			    	m.put("module", "");
			    	m.put("levelcode", "");
			    	m.put("position", "");
			    	m.put("memo", "BARCODECOUNT");
			    	m.put("okyn", okyn);
			    	
			    	//m.put("custcode", cucode);
			        // 바코드 파싱 (인라인)
					if (barcode.split(",").length == 5 && barcode.endsWith("USA")) {		// 파트라벨바코드
						String[] parts = barcode.split(",");
						Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(parts[0]);
						m.put("oitemcode", item.get("OITEMCODE"));
						m.put("itemcode", parts[0]);

						m.put("bdate", parts[1]);
						m.put("seq", parts[2]);
						m.put("qty", resolveBarcodeQty(barcode));
						m.put("scmmex", parts[4]);
						m.put("type", "box");

					}else if (barcode.split(",").length == 4 &&  barcode.endsWith("USA")) {				// 새로운 팔레트 라벨 바코드
			            PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
			            if (pal == null) {
			                result.put("response", "warning.pallet.infoNotFound");
			                result.put("barcode", Arrays.asList(barcode));
			                return result;
			                //throw new RuntimeException("NO_PALLET_INFO");
			            }else {
			            	String[] parts = barcode.split(",", -1);
				            String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
				            String seq   = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7)   : "";
				            String scmmex = (parts.length >= 4) ? parts[3] : "";
							Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(pal.getItemcode());
							m.put("oitemcode", item.get("OITEMCODE"));
				            m.put("itemcode", pal.getItemcode());
				            m.put("qty", formatQty(pal.getQty()));
				            m.put("bdate", bdate);
				            m.put("seq", seq);
				            m.put("scmmex", scmmex);
				            m.put("type", "pallet");
			            }
			        } else if(barcode.split("_", -1).length == 6){		// 박스 바코드일때
						String[] parts = barcode.split("_", -1);
						Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
						m.put("itemcode", item.get("ITEMCODE"));
						m.put("oitemcode",parts[3]);
						m.put("bdate", parts[2].substring(2) + parts[1] + parts[0]);
						m.put("seq", (parts != null && parts.length > 5) ? parts[5] : "");
						m.put("qty", resolveBarcodeQty(barcode));

					} else {
			            result.put("response", "fail4");
			            result.put("message", "지원되지 않는 바코드 형식: " + barcode);
			            throw new RuntimeException("INVALID_BARCODE_FORMAT");
			        }

			        // INSERT: 재고실사 예외입고 
			        m.put("source2", "STOCKCOUNT");
			        int insInbound = 0;

		            System.out.println("not month enddate!");
		            // 말일이 아니면 기존으로 예외 insert
		            insInbound = purchaseMapper.insInboundException(m);

			        m.put("laststatus", 10);
			        // INSERT: 재고(로그/조정 내역) — 영향행 수 체크(동시성 방어) , 음수 재고 방지
			        //int inslocation = purchaseMapper.saveLocation(m);		// 어차피 로케이션 실사니까 안넣어도 됨
			        int affected = 0;
			        if(barcode.split(",").length ==5) {
			        	affected = purchaseMapper.insStockInbound(m);
			        	//purchaseMapper.updateLaststatusPart(m);		// 밑에서 적재할때 상태값 변경하니까 안해도 됨
			        }else {
			        	//purchaseMapper.updateLaststatusPallet(m);
			        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
			        	for(int i = 0; i<bbarcode.size(); i++) {
			        		m.put("barcode", bbarcode.get(i));
			        		m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
			        		affected = purchaseMapper.insStockInbound(m);
			        		//purchaseMapper.updateLaststatusPart(m);
			        	}
			        }
		    	}
		    }
		}
		int index = 0;
		for(String barcode:barcodes) {
			String labelType = "";
    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
    		String okyn = "Y";
    		
    		if ("Defective".equals(labelType)) {
    			okyn = "N";
    		}
			Map<String, Object> map = new HashMap<String, Object>();
			
			// 🔹 이전 위치 조회 로직 추가
		    String previousLocation = null;
		    if(status2.equals("replace")) {
		        previousLocation = purchaseMapper.getPreviousLocation(barcode);
		    }
		    
			map.put("barcode", barcode);
			map.put("location", location);
			map.put("source", source);
			map.put("kind", kind);
			map.put("memo", memo);
			map.put("date", date);
			map.put("factory", locationArr[0]);
			map.put("storage", locationArr[1]);
			map.put("rack",       locationArr.length > 2 ? locationArr[2] : "");
			map.put("module",     locationArr.length > 3 ? locationArr[3] : "");
			map.put("levelcode",  locationArr.length > 4 ? locationArr[4] : "");
			map.put("position",   locationArr.length > 5 ? locationArr[5] : "");
			map.put("loginid", loginid);
			map.put("scantype", "LOCATION");
			map.put("main", "LOCATION");
			map.put("realstock", realStock);
			map.put("previousLocation", "");
			map.put("dmemo", "CHANGE");
			map.put("stocksource", "ALWAYS");
			map.put("okyn",okyn);
			purchaseMapper.removeBarcode(map);// 
			if (barcode.split(",").length == 5) {
				map.put("itemcode", barcode.split(",")[0]);
				map.put("lotdate", "20" + barcode.split(",")[1]);
				map.put("seq", barcode.split(",")[2]);
				map.put("qty", resolveBarcodeQty(barcode));
				map.put("scmmex", barcode.split(",")[4]);
				map.put("type", "box");
				map.put("pbarcode", "");
			} else if (barcode.split(",").length == 4 &&  barcode.endsWith("USA")) { // 새로운 바코드양식
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				if (palletInfo != null) {			// 팔레트값이 없을때 fail2
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("qty", palletInfo.getQty());
				    map.put("lotdate", "20" + barcode.split(",")[0].substring(1, 7));
					map.put("seq", barcode.split(",")[0].substring(7));
					map.put("scmmex", barcode.split(",")[3]);
					map.put("type", "pallet");
					map.put("pbarcode", "");
				} else {
					result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
				}
				
			} else if(barcode.split("_", -1).length == 6){		// 박스 바코드일때
				String[] parts = barcode.split("_", -1);
				Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
				map.put("itemcode", item.get("ITEMCODE"));
				map.put("lotdate", parts[2].substring(2) + parts[1] + parts[0]);
				map.put("seq", (parts != null && parts.length > 5) ? parts[5] : "");
				map.put("qty", resolveBarcodeQty(barcode));

			}
			
			if(index == 0 && status1.equals("replace")) {   // 처음만 실행
				map.put("source", "CHANGE");
				// 적재된게 어떤 바코드인지 모르니까 insert select
				purchaseMapper.insSelectLocation(map);
				purchaseMapper.removeLocation(map);		// 적재위치에 있는 상품 useyn =n
				map.put("laststatus",10);
				map.put("source", source);
		    }
			index++;
			if("Y".equals(realStock)) {
				purchaseMapper.insRealStock(map);// 실사 insert				
			}
			if ("replace".equals(status2)) {
			    if (previousLocation != null) {
			        map.put("previousLocation", previousLocation);
			    }
			    purchaseMapper.removeBarcode(map);
			}

			purchaseMapper.saveLocationOKYN(map);
			map.put("laststatus",20);
			if(barcode.split(",").length ==5) {
	        	purchaseMapper.updateLaststatusPart(map);
	        }else {
	        	purchaseMapper.updateLaststatusPallet(map);
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		map.put("barcode", bbarcode.get(i));
	        		map.put("qty", resolveBarcodeQty(bbarcode.get(i)));
	        		purchaseMapper.updateLaststatusPart(map);
	        	}
	        }
			
			
			
		}
		result.put("response", "success");
		return result;
	}
	
	// 말일 재고실사 - location
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> insRealStockLastDayLocation(BarcodeVO request) {
		Map<String, Object> result = new HashMap<String, Object>();		
		List<String> list = request.getBarcode();
		
		// ✅ 팔레트-파트 전처리: 팔레트와 함께 들어온 파트는 제외
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(list);
	    List<String> barcodes = fr.getFiltered();
	    
		String location = request.getLocation();
		String memo = request.getMemo();
		String date = request.getDate();
		String status1 = request.getStatus1();		// 로케이션에 적재된 물건이 있는지
		String status2 =request.getStatus2();		// 이미 적재된 바코드인지
		String realStock = request.getRealstock();
		String locationArr[] = location.split("-");
		String source = request.getSource();
		String main = request.getMain();
		String kind = request.getKind();
		String factory = request.getFactory();
		String loginid = request.getLoginid();
		String storage = request.getStorage();
		System.out.println("status1 : "+status1);
		System.out.println("status2 : "+status2);
		System.out.println("realStock : "+realStock);
		
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}

		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(request.getBarcode());
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }

	    Map<String, Object> checkMap = new HashMap<String, Object>();
	    checkMap.put("list", request.getBarcode());
	    checkMap.put("factory", request.getFactory());
	    checkMap.put("storage", storage);

	    String status = request.getStatus0();
	    List<String> barcodes2 = request.getBarcode2();
	    log.info("status : "+status);
		if("confirm".equals(status)) {		// 팔레트 바코드 해체작업 
			Set<String> palletList = new LinkedHashSet<>();
			log.info("location pallet unbound start");
			
			// 1) 고유 팔레트만 수집
		    Set<String> pallets = new LinkedHashSet<>();
		    for (String childBarcode : barcodes2) {
		        String pallet = purchaseMapper.searchPallet(childBarcode);
		        if (pallet != null && !pallet.isEmpty()) {
		            pallets.add(pallet);
		        }
		    }
		    // 팔레트로 for문
		    for (String pallet : pallets) {log.info("pallet unbound@@@@");
				Map<String, Object> m = new HashMap<String, Object>();
				
				if(pallet == null || pallet.isEmpty()) {
					
				}else {
					palletList.add(pallet);
					m.put("dmemo", "Location Unbound");
					String location0 = purchaseMapper.searchRoomcodeY(pallet);
					if (location0 == null || location.isEmpty()) {
						m.put("dmemo", "LC LOC NULL");						//250920 HJ 팔레트로 묶인 파트라벨을 적재 후 팔레트 해체될때 NULL이 되는 현상때문에 하드코딩 250923 현재 적재중인위치로이동
						// 바코드 재고실사, 로케이션 재고실사, 리시빙에서 사용
						// 251206 팔레트 purchaseMapper.searchRoomcode(pallet) -> purchaseMapper.searchRoomcodeY(pallet)로 변경
						// 그래서 null인 경우 팔레트테이블만 N으로 업데이트
						// 하단에서 스캔한 파트라벨은 예외입고 에정
						// 스캔안한 파트라벨들은 재고로 안잡히지만 추후 아무것도 안한상태면 예외입고 불출이나 출고했으면 재고실사로 잡아야함
						// 팔레트를 N으로 업데이트
						m.put("barcode", pallet);
						purchaseMapper.palletN(m);
					}else{												//251106 location이 있을때만 작동하도록 수정, 임의로 값을 넣어주면 재고실사때 예외입고가 안되는 현상때문에 수정함
						m.put("barcode", pallet);
						m.put("location", location0);
						String[] parts = location0.split("-");
						m.put("factory", parts.length > 0 ? parts[0] : "");
						m.put("storage", parts.length > 1 ? parts[1] : "");
						m.put("rack", parts.length > 2 ? parts[2] : "");
						m.put("module", parts.length > 3 ? parts[3] : "");
						m.put("levelcode", parts.length > 4 ? parts[4] : "");
						m.put("position", parts.length > 5 ? parts[5] : "");
						m.put("date", date);
						m.put("loginid", loginid);
						m.put("source", "LOCATION UNBOUND");
						// 기존 팔레트라벨 적재위치 정보로 파트라벨 적재
						purchaseMapper.selectLocationSave(m);
						Map<String, Object> map = new HashMap<String, Object>();
						map.put("barcode", pallet);
						map.put("dmemo", "LOCATION");
						purchaseMapper.removeBarcode(map);		// 적재된 팔레트바코드 제거
						// 팔레트 바코드 useyn = n
						purchaseMapper.palletN(map);				// 팔레트라벨 사용 N
					}
				}
			}
			
		}
		
		if(realStock.equals("Y")) {
			// 해당 창고에 없는지 체크
		    Map<String, Object> storageCheckOut = barcodeValidator.storageCheckOut(checkMap);		// 현재 재고에 없는 바코드
		    List<String> missingBarcodes = (List<String>) storageCheckOut.getOrDefault("barcode", new ArrayList<>());
		    
		    // 다른창고에 있는 바코드
		    Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
		    List<String> storageBarcodes = (List<String>) inputStorageCheck.getOrDefault("barcode", new ArrayList<>());

		    // 4. Set으로 겹치는 바코드만 추출
		    Set<String> otherBarcodesSet = new HashSet<>();
		    otherBarcodesSet.addAll(storageBarcodes);

		    // 현재 창고에 없는 바코드 중 다른 창고에 있는 바코드 예외출고, location N 진행
		    List<String> exceptionOutBarcodes = missingBarcodes.stream()
		        .filter(otherBarcodesSet::contains)
		        .collect(Collectors.toList());
		    System.out.println("예외출고바코드: " + exceptionOutBarcodes);
		    
		    System.out.println("예외입고 바코드: " + missingBarcodes);
		    
		    // 예외출고작업
		    if(!exceptionOutBarcodes.isEmpty()) {
		    	for(String barcode: exceptionOutBarcodes) {
		    		String labelType = "";
		    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
		    		String okyn = "Y";
		    		if ("Defective".equals(labelType)) {
		    			okyn = "N";
		    		}
		    		String preLocation = purchaseMapper.getPreviousLocation(barcode);
		    		Map<String,Object> m = new HashMap<String, Object>();
		    		m.put("date", date);
		    		m.put("loginid", loginid);
		    		m.put("source", "LOADEXCEPTION");
		    		m.put("main", "OUT");
		    		m.put("kind", "LOADEXCEPTION");
		    		m.put("storage", preLocation.split("-")[1]);
		    		m.put("factory", preLocation.split("-")[0]);
		    		m.put("location",preLocation);
		    		m.put("memo", "LOCATIONCOUNT");
		    		m.put("barcode", barcode);
		    		m.put("source2", "STOCKCOUNT");
		    		m.put("okyn", okyn);
		    		// 바코드 파싱 (인라인)
			        if (barcode.split(",").length ==5) {
			            String[] parts = barcode.split(",", -1);
			            m.put("itemcode", parts[0]);
			            m.put("bdate", parts[1]);
			            m.put("seq", parts[2]);
			            m.put("qty", resolveBarcodeQty(barcode));
			            m.put("scmmex", parts[4]);
			            m.put("type", "box");

			        }  else if (barcode.startsWith("P") && (barcode.endsWith("MEX") || barcode.endsWith("USA"))) {
						PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
						if (pal == null) {
							result.put("response", "warning.pallet.infoNotFound");
			                result.put("barcode", Arrays.asList(barcode));
			                return result;
							//throw new RuntimeException("NO_PALLET_INFO");
						}
						String[] parts = barcode.split(",", -1);
						String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
						String seq = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7) : "";
						String scmmex = (parts.length >= 4) ? parts[3] : "";

			            m.put("itemcode", pal.getItemcode());
			            m.put("qty", formatQty(pal.getQty()));
			            m.put("bdate", bdate);
			            m.put("seq", seq);
			            m.put("scmmex", scmmex);
			            m.put("type", "pallet");
			            

					} else {
						result.put("response", "fail4");
						result.put("message", "지원되지 않는 바코드 형식: " + barcode);
						throw new RuntimeException("INVALID_BARCODE_FORMAT");
					}
			        m.put("dmemo","OUTPUT");
			        // INSERT: 예외출고
			        int insOutbound = 0;
			        
		            System.out.println("month enddate!");
		            insOutbound = purchaseMapper.insOutboundExceptionN(m);

			        int affected = 0;
			        purchaseMapper.removeBarcode(m);
			        m.put("laststatus", 50);
			        if(barcode.split(",").length ==5) {
			        	affected = purchaseMapper.insertStockOutput(m);
			        	purchaseMapper.updateLaststatusPart(m);
			        }else {
			        	purchaseMapper.updateLaststatusPallet(m);
			        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
			        	for(int i = 0; i<bbarcode.size(); i++) {
			        		m.put("barcode", bbarcode.get(i));
			        		m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
			        		affected = purchaseMapper.insertStockOutput(m);
			        		purchaseMapper.updateLaststatusPart(m);
			        	}
			        }
		    		
		    	}
		    }
			
		    if (!missingBarcodes.isEmpty()) { // 예외입고작업
		    	for(String barcode:missingBarcodes) {
		    		String labelType = "";
		    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
		    		String okyn = "Y";
		    		if ("Defective".equals(labelType)) {
		    			okyn = "N";
		    		}
		    		Map<String,Object> m = new HashMap<String, Object>();
			    	m.put("date", date);
			    	m.put("barcode", barcode);
			    	m.put("loginid", loginid);
			    	m.put("source", "INCOMINGEXCEPTION");
			    	m.put("main", "IN");
			    	m.put("kind", "INCOMINGEXCEPTION");
			    	m.put("storage", location.split("-")[1]);
			    	m.put("factory", factory);
			    	//m.put("custname", custname);
			    	m.put("location", location);
			    	m.put("memo", "");
			    	m.put("rack", "");
			    	m.put("module", "");
			    	m.put("levelcode", "");
			    	m.put("position", "");
			    	m.put("memo", "BARCODECOUNT");
			    	m.put("okyn", okyn);
			    	
			    	//m.put("custcode", cucode);
			        // 바코드 파싱 (인라인)
			        if (barcode.split(",").length == 5) {		// 파트라벨바코드
			            String[] parts = barcode.split(",");
			            m.put("itemcode", parts[0]);
			            m.put("bdate", parts[1]);
			            m.put("seq", parts[2]);
			            m.put("qty", resolveBarcodeQty(barcode));
			            m.put("scmmex", parts[4]);
			            m.put("type", "box");

			        } else if (barcode.startsWith("P") && (barcode.endsWith("MEX") || barcode.endsWith("USA"))) {				// 새로운 팔레트 라벨 바코드
			            PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
			            if (pal == null) {
			                result.put("response", "warning.pallet.infoNotFound");
			                result.put("barcode", Arrays.asList(barcode));
			                return result;
			                //throw new RuntimeException("NO_PALLET_INFO");
			            }else {
			            	String[] parts = barcode.split(",", -1);
				            String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
				            String seq   = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7)   : "";
				            String scmmex = (parts.length >= 4) ? parts[3] : "";

				            m.put("itemcode", pal.getItemcode());
				            m.put("qty", formatQty(pal.getQty()));
				            m.put("bdate", bdate);
				            m.put("seq", seq);
				            m.put("scmmex", scmmex);
				            m.put("type", "pallet");
			            }
			        } else {
			            result.put("response", "fail4");
			            result.put("message", "지원되지 않는 바코드 형식: " + barcode);
			            throw new RuntimeException("INVALID_BARCODE_FORMAT");
			        }

			        // INSERT: 재고실사 예외입고 
			        m.put("source2", "STOCKCOUNT");
			        int insInbound = 0;
		            System.out.println("month enddate!");
		            // 말일 재고실사하면 입고테이블에 N으로 insert
		            insInbound = purchaseMapper.insInboundExceptionN(m);

			        m.put("laststatus", 10);
			        // INSERT: 재고(로그/조정 내역) — 영향행 수 체크(동시성 방어) , 음수 재고 방지
			        //int inslocation = purchaseMapper.saveLocation(m);		// 어차피 로케이션 실사니까 안넣어도 됨
			        int affected = 0;
			        if(barcode.split(",").length ==5) {
			        	affected = purchaseMapper.insStockInbound(m);
			        	//purchaseMapper.updateLaststatusPart(m);		// 밑에서 적재할때 상태값 변경하니까 안해도 됨
			        }else {
			        	//purchaseMapper.updateLaststatusPallet(m);
			        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
			        	for(int i = 0; i<bbarcode.size(); i++) {
			        		m.put("barcode", bbarcode.get(i));
			        		m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
			        		affected = purchaseMapper.insStockInbound(m);
			        		//purchaseMapper.updateLaststatusPart(m);
			        	}
			        }
		    	}
		    }
		}
		int index = 0;
		for(String barcode:barcodes) {
			
			String labelType = "";
    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
    		String okyn = "Y";
    		if ("Defective".equals(labelType)) {
    			okyn = "N";
    		}
			
			Map<String, Object> map = new HashMap<String, Object>();
			
			// 🔹 이전 위치 조회 로직 추가
		    String previousLocation = null;
		    if(status2.equals("replace")) {
		        previousLocation = purchaseMapper.getPreviousLocation(barcode);
		    }
		    
			map.put("barcode", barcode);
			map.put("location", location);
			map.put("source", source);
			map.put("kind", kind);
			map.put("memo", memo);
			map.put("date", date);
			map.put("factory", locationArr[0]);
			map.put("storage", locationArr[1]);
			map.put("rack",       locationArr.length > 2 ? locationArr[2] : "");
			map.put("module",     locationArr.length > 3 ? locationArr[3] : "");
			map.put("levelcode",  locationArr.length > 4 ? locationArr[4] : "");
			map.put("position",   locationArr.length > 5 ? locationArr[5] : "");
			map.put("loginid", loginid);
			map.put("scantype", "LOCATION");
			map.put("main", "LOCATION");
			map.put("realstock", realStock);
			map.put("previousLocation", "");
			map.put("dmemo", "CHANGE");
			map.put("stocksource", "LASTDAY");
			map.put("okyn", okyn);
			purchaseMapper.removeBarcode(map);// 
			if (barcode.split(",").length == 5) {
				map.put("itemcode", barcode.split(",")[0]);
				map.put("lotdate", "20" + barcode.split(",")[1]);
				map.put("seq", barcode.split(",")[2]);
				map.put("qty", resolveBarcodeQty(barcode));
				map.put("scmmex", barcode.split(",")[4]);
				map.put("type", "box");
				map.put("pbarcode", "");
			} else if (barcode.startsWith("P") && (barcode.endsWith("MEX") || barcode.endsWith("USA"))) { // 새로운 바코드양식
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				if (palletInfo != null) {			// 팔레트값이 없을때 fail2
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("qty", palletInfo.getQty());
				    map.put("lotdate", "20" + barcode.split(",")[0].substring(1, 7));
					map.put("seq", barcode.split(",")[0].substring(7));
					map.put("scmmex", barcode.split(",")[3]);
					map.put("type", "pallet");
					map.put("pbarcode", "");
				} else {
					result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
				}
				
			}
			
			if(index == 0 && status1.equals("replace")) {   // 처음만 실행
				map.put("source", "CHANGE");
				// 적재된게 어떤 바코드인지 모르니까 insert select
				purchaseMapper.insSelectLocation(map);
				purchaseMapper.removeLocation(map);		// 적재위치에 있는 상품 useyn =n
				map.put("laststatus",10);
				map.put("source", source);
		    }
			index++;
			if("Y".equals(realStock)) {
				purchaseMapper.insRealStock(map);// 실사 insert				
			}
			if ("replace".equals(status2)) {
			    if (previousLocation != null) {
			        map.put("previousLocation", previousLocation);
			    }
			    purchaseMapper.removeBarcode(map);
			}

			purchaseMapper.saveLocationOKYN(map);
			map.put("laststatus",20);
			if(barcode.split(",").length ==5) {
	        	purchaseMapper.updateLaststatusPart(map);
	        }else {
	        	purchaseMapper.updateLaststatusPallet(map);
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		map.put("barcode", bbarcode.get(i));
	        		map.put("qty", resolveBarcodeQty(bbarcode.get(i)));
	        		purchaseMapper.updateLaststatusPart(map);
	        	}
	        }
			
			
			
		}
		result.put("response", "success");
		return result;
	}

	public List<String> selFactory() {
		return purchaseMapper.selFactory();
	}

	public List<String> selStorage() {
		return purchaseMapper.selStorage();
	}

	public List<String> selRack() {
		return purchaseMapper.selRack();
	}

	public List<String> selModule() {
		return purchaseMapper.selModule();
	}

	public List<String> selLevelCode() {
		return purchaseMapper.selLevelCode();
	}

	public List<String> selPosition() {
		return purchaseMapper.selPosition();
	}

	public List<String> selCar() { return purchaseMapper.selCar(); }

	public int existItem(Map<String, Object> map) {
		return purchaseMapper.existItem(map);
	}

	// 불출	- 살티오구매
	public Map<String, Object> saveWorkmove(BarcodeVO request) {
		Map<String, Object> result = new HashMap<String, Object>();
		
		List<String> barcodes = request.getBarcode();
		//List<String> barcodes2 = request.getBarcode2();		// 팔레트가 있는 팔레트라벨바코드목록
		String memo = "";
		String date = request.getDate();
		String wccode = request.getWccode();
		//String status = request.getStatus1();
		String source = request.getSource();
		String factory = request.getFactory();
		String loginid = request.getLoginid();
		
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}
		
		// 언팩바코드 체크
		Map<String, Object> unpackCheck = barcodeValidator.unpackCheck(request.getBarcode());
		if (!unpackCheck.isEmpty()) {
			return unpackCheck; // 실패 시 리턴
		}
		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(request.getBarcode());
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }
	    
		 // 001,002 상품이나 제품인지 체크
		Map<String, Object> goodsCodeCheck = barcodeValidator.goodsCodeCheck(request.getBarcode());
	    if (!goodsCodeCheck.isEmpty()) {
	        return goodsCodeCheck; // 실패 시 리턴
	    }
	    
	    // 불출준비된거 확인하는 로직
  	    Map<String, Object> alreadyWIPReady = barcodeValidator.alreadyWIPReady(request.getBarcode());
  	    if (!alreadyWIPReady.isEmpty()) {
  	    	return alreadyWIPReady; // 실패 시 리턴
  	    }
	    
	    // 불출된 바코드인지 체크
	    Map<String, Object> alreadyWIP = barcodeValidator.alreadyWIP(request.getBarcode());
	    if (!alreadyWIP.isEmpty()) {
	    	return alreadyWIP; // 실패 시 리턴
	    }
	    
	    // 출고된 바코드인지 체크
	    Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(request.getBarcode());
	    if (!alreadyLoad.isEmpty()) {
	    	return alreadyLoad; // 실패 시 리턴
	    }
	    
	    
	    Map<String, Object> checkMap = new HashMap<String, Object>();
	    checkMap.put("list",  request.getBarcode());
	    checkMap.put("factory",  request.getFactory());
	    
	    // 바코드가 stockinfo에 있는지 확인, 팔레트에 속한 파트도 확인 가능
	    Map<String, Object> barcodeStockInfo = barcodeValidator.barcodeStockInfo(checkMap);
	    if (!barcodeStockInfo.isEmpty()) {
	    	return barcodeStockInfo; // 실패 시 리턴
	    }
	    
	    // 다른 공장에 있는걸 return
	    Map<String, Object> inputFactoryCheck = barcodeValidator.inputFactoryCheck(checkMap);
	    if (!inputFactoryCheck.isEmpty()) {
	    	return inputFactoryCheck; // 실패 시 리턴
	    }
	    
	    // 10~29사이인지
	    Map<String, Object> factoryCheck = barcodeValidator.factoryCheck(checkMap);
	    if (!factoryCheck.isEmpty()) {
	    	return factoryCheck; // 실패 시 리턴
	    }

	    // 불량라벨을 반환
	    Map<String, Object> defectiveCheck = barcodeValidator.defectiveCheck(checkMap);
	    if (!defectiveCheck.isEmpty()) {
	    	return defectiveCheck; // 실패 시 리턴
	    }
		
		Map<String, Object> map = new HashMap<String, Object>();
		
		// ✅ 팔레트-파트 전처리
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(request.getBarcode());
	    List<String> barcodess = fr.getFiltered();
		
	    map.put("list", barcodess);
		for(String barcode:barcodess) {
			String labelType = "";
    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
    		String okyn = "Y";
    		if ("Defective".equals(labelType)) {
    			okyn = "N";
    		}
			//바코드가 팔레트에 속해 있는지 확인
			String pbarcode = palletMapper.searchPallet(barcode);
			if (pbarcode != null && !pbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",pbarcode);
				palletMap.put("memo","WIP SENDING");
				// 팔레트테이블에서 해당 바코드 N처리
				int barcodeN = palletMapper.bbarcodeN(palletMap);
				// 수량 가져오기
				Double doublePQty = palletMapper.palletQty(pbarcode);
				palletMap.put("pqty",doublePQty);
				
				//바코드 수량 가져오기
				double doubleqty = palletMapper.partQty(barcode);
				palletMap.put("date",date);
				palletMap.put("qty",doubleqty);
				palletMap.put("loginid",loginid);
				// 팔레트 위치로 적재 insert
				int partLocation = palletMapper.partLocation(palletMap);
				
				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateLQtyN(palletMap);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateLQty(palletMap);
				}
			}
			
			
			String roomcode = purchaseMapper.searchRoomcode(barcode);
			if (roomcode == null || roomcode.isEmpty()) {
				if("Saltillo".equals(factory)) {
					roomcode = factory+"-"+"Material";
				}else {
					roomcode = factory+"-"+"MATERIAL";
				}
			}
			String[] parts = roomcode.split("-");
			map.put("barcode", barcode);
			map.put("factory", request.getFactory());
			map.put("storage", parts.length > 1 ? parts[1] : "");
			map.put("wccode", wccode);
			map.put("roomcode", roomcode);
			map.put("memo", memo);
			map.put("date", date);
			map.put("loginid", loginid);
			map.put("dmemo", "WIP SENDING");
			map.put("source", "WIPSENDING");
			map.put("kind", "WIPSENDING");
			map.put("okyn", okyn);
			
			if (barcode.length() == 12) {
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				map.put("itemcode", palletInfo.getItemcode());
				if (palletInfo != null) {
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("qty", formatQty(palletInfo.getQty()));
				} else {
					result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
				}
			} else if(barcode.split(",").length == 5) {
				map.put("itemcode", barcode.split(",")[0]);
				map.put("qty", resolveBarcodeQty(barcode));
				//String pbarcode = "";
				//pbarcode = purchaseMapper.searchPallet(barcode);
//				if(pbarcode != null && !pbarcode.equals("")) {
//					map.put("barcode",pbarcode);
//				}
			}else if (barcode.startsWith("P") && barcode.endsWith("MEX")) { // 새로운 바코드양식
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				if (palletInfo != null) {			// 팔레트값이 없을때 fail3
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("qty", formatQty(palletInfo.getQty()));
				} else {
					result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
				}
				
				
			}
			map.put("laststatus",35);
			purchaseMapper.removeBarcode(map);
			if(barcode.split(",").length ==5) {
	        	purchaseMapper.updateLaststatusPart(map);
	        }else {
	        	purchaseMapper.updateLaststatusPallet(map);
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		map.put("barcode", bbarcode.get(i));
	        		purchaseMapper.updateLaststatusPart(map);
	        	}
	        }
			String unloadParts[] = roomcode.split("-");
			map.put("location", unloadParts[0] +"-"+ unloadParts[1]);
			map.put("barcode", barcode);
			purchaseMapper.basicLocation(map);		// location에 insert
//			if (wccode != null && !wccode.isEmpty()) {
//			    String[] parts2 = wccode.split("-");
//			    map.put("factory", parts2.length > 0 ? parts2[0] : "");
//			    map.put("storage", parts2.length > 1 ? parts2[1] : "");
//			} else {
//			    map.put("factory", "");
//			    map.put("storage", "");
//			}
			purchaseMapper.saveWorkmove(map);		// workmove에 source wip sending으로 isnert
			
			
			// 더스크커버 작업 	V003
			String itemcode = (String)map.get("itemcode");
			if(itemcode.contains("V003")) {		// 더스트커버 품질로 이동하는건데 일단은 헤드레스트로 worklocation에 저장
				map.put("dmemo", "공정불출");
				map.put("main", "WIP");
				map.put("kind", "WIPINPUT");
				map.put("laststatus",40);
				map.put("location","SALTILLO-H/REST");
				map.put("factory","SALTILLO");
				map.put("source","WIPINPUT");
				// receiving 작업
				purchaseMapper.removeBarcode(map);		// location useyn = 'N'
				// workmove insert
				purchaseMapper.saveWorkmove(map);
				map.put("storage", "H/REST");
				purchaseMapper.insWorkLocationBasic(map);
				if(barcode.split(",").length ==5) {
					// stock재고 차감
					purchaseMapper.insStockMinus(map);
					// workstock재고 증가
		        	purchaseMapper.insWorkStockPlus(map);
		        	// 파트상태값 업데이트
		        	purchaseMapper.updateLaststatusPart(map);
		        }else {
		        	// 팔레트상태값 업데이트
		        	purchaseMapper.updateLaststatusPallet(map);
		        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
		        	for(int i = 0; i<bbarcode.size(); i++) {
		        		map.put("barcode", bbarcode.get(i));
		        		map.put("qty",resolveBarcodeQty(bbarcode.get(i)));
		        		// stock재고 차감
						purchaseMapper.insStockMinus(map);
						// workstock재고 증가
			        	purchaseMapper.insWorkStockPlus(map);
			        	// 파트상태값 업데이트
		        		purchaseMapper.updateLaststatusPart(map);
		        	}
		        }
				
			}
			
			
			
			result.put("response", "success");
		}
		
		return result;
	}
	
	// 불출 - 푸에블라	
	public Map<String, Object> saveWorkmovePuebla(BarcodeVO request) {
		Map<String, Object> result = new HashMap<String, Object>();
		
		List<String> barcodes = request.getBarcode();
		//List<String> barcodes2 = request.getBarcode2();		// 팔레트가 있는 팔레트라벨바코드목록
		String memo = "";
		String date = request.getDate();
		String wccode = request.getWccode();
		//String status = request.getStatus1();
		String source = request.getSource();
		String factory = request.getFactory();
		String loginid = request.getLoginid();
		
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}
		
		// 언팩바코드 체크
		Map<String, Object> unpackCheck = barcodeValidator.unpackCheck(request.getBarcode());
		if (!unpackCheck.isEmpty()) {
			return unpackCheck; // 실패 시 리턴
		}
		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(request.getBarcode());
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }
	    
		 // 001,002 상품이나 제품인지 체크
		Map<String, Object> goodsCodeCheck = barcodeValidator.goodsCodeCheck(request.getBarcode());
	    if (!goodsCodeCheck.isEmpty()) {
	        return goodsCodeCheck; // 실패 시 리턴
	    }
	    
	    // 불출된 바코드인지 체크
	    Map<String, Object> alreadyWIP = barcodeValidator.alreadyWIP(request.getBarcode());
	    if (!alreadyWIP.isEmpty()) {
	    	return alreadyWIP; // 실패 시 리턴
	    }
	    
	    // 출고된 바코드인지 체크
	    Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(request.getBarcode());
	    if (!alreadyLoad.isEmpty()) {
	    	return alreadyLoad; // 실패 시 리턴
	    }
	    
	    
	    Map<String, Object> checkMap = new HashMap<String, Object>();
	    checkMap.put("list",  request.getBarcode());
	    checkMap.put("factory",  request.getFactory());
	    
	    // 바코드가 stockinfo에 있는지 확인, 팔레트에 속한 파트도 확인 가능
	    Map<String, Object> barcodeStockInfo = barcodeValidator.barcodeStockInfo(checkMap);
	    if (!barcodeStockInfo.isEmpty()) {
	    	return barcodeStockInfo; // 실패 시 리턴
	    }
	    
	    // 다른 공장에 있는걸 return
	    Map<String, Object> inputFactoryCheck = barcodeValidator.inputFactoryCheck(checkMap);
	    if (!inputFactoryCheck.isEmpty()) {
	    	return inputFactoryCheck; // 실패 시 리턴
	    }
	    
	    // 10~29사이인지
	    Map<String, Object> factoryCheck = barcodeValidator.factoryCheck(checkMap);
	    if (!factoryCheck.isEmpty()) {
	    	return factoryCheck; // 실패 시 리턴
	    }
	    
	    // 불량라벨을 반환
	    Map<String, Object> defectiveCheck = barcodeValidator.defectiveCheck(checkMap);
	    if (!defectiveCheck.isEmpty()) {
	    	return defectiveCheck; // 실패 시 리턴
	    }
		
		Map<String, Object> map = new HashMap<String, Object>();
		
		// ✅ 팔레트-파트 전처리
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(request.getBarcode());
	    List<String> barcodess = fr.getFiltered();
		
	    map.put("list", barcodess);
		for(String barcode:barcodess) {
			String labelType = "";
    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
    		String okyn = "Y";
    		if ("Defective".equals(labelType)) {
    			okyn = "N";
    		}
			//바코드가 팔레트에 속해 있는지 확인
			String pbarcode = palletMapper.searchPallet(barcode);
			if (pbarcode != null && !pbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",pbarcode);
				palletMap.put("memo","WIP INPUT");
				// 팔레트테이블에서 해당 바코드 N처리
				int barcodeN = palletMapper.bbarcodeN(palletMap);
				// 수량 가져오기
				Double doublePQty = palletMapper.palletQty(pbarcode);
				palletMap.put("pqty",doublePQty);
				
				//바코드 수량 가져오기
				double doubleqty = palletMapper.partQty(barcode);
				palletMap.put("date",date);
				palletMap.put("qty",doubleqty);
				palletMap.put("loginid",loginid);
				// 팔레트 위치로 적재 insert
				int partLocation = palletMapper.partLocation(palletMap);
				
				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateLQtyN(palletMap);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateLQty(palletMap);
				}
			}
			
			
			String roomcode = purchaseMapper.searchRoomcode(barcode);
			if (roomcode == null || roomcode.isEmpty()) {
				if("Saltillo".equals(factory)) {
					roomcode = factory+"-"+"Material";
				}else {
					roomcode = factory+"-"+"MATERIAL";
				}
			}
			String[] parts = roomcode.split("-");
			map.put("barcode", barcode);
			map.put("factory", request.getFactory());
			map.put("storage", parts.length > 1 ? parts[1] : "");
			map.put("wccode", wccode);
			map.put("roomcode", roomcode);
			map.put("memo", memo);
			map.put("date", date);
			map.put("loginid", loginid);
			map.put("dmemo", "공정불출");
			map.put("main", "WIP");
			map.put("kind", "WIPINPUT");
			map.put("okyn", okyn);
			
			if (barcode.length() == 12) {
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				map.put("itemcode", palletInfo.getItemcode());
				if (palletInfo != null) {
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("qty", formatQty(palletInfo.getQty()));
				} else {
					result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
				}
			} else if(barcode.split(",").length == 5) {
				map.put("itemcode", barcode.split(",")[0]);
				map.put("qty", resolveBarcodeQty(barcode));
			}else if (barcode.startsWith("P") && barcode.endsWith("MEX")) { // 새로운 바코드양식
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				if (palletInfo != null) {			// 팔레트값이 없을때 fail3
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("qty", formatQty(palletInfo.getQty()));
				} else {
					result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
				}
			}
			int affected = 0;
			map.put("laststatus",40);
			purchaseMapper.removeBarcode(map);
			purchaseMapper.saveWorkmovePuebla(map);
			if(barcode.split(",").length ==5) {
	        	affected = purchaseMapper.insStockMinus(map);
	        	purchaseMapper.insWorkStockPlus(map);
	        	purchaseMapper.updateLaststatusPart(map);
	        }else {
	        	purchaseMapper.updateLaststatusPallet(map);
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		map.put("barcode", bbarcode.get(i));
	        		map.put("qty", resolveBarcodeQty(bbarcode.get(i)));
	        		affected = purchaseMapper.insStockMinus(map);
	        		purchaseMapper.insWorkStockPlus(map);
	        		purchaseMapper.updateLaststatusPart(map);
	        	}
	        }
			
			
			purchaseMapper.locationIssue(map);
			map.put("location", wccode);
			map.put("source", "WIPINPUT");
			if (wccode != null && !wccode.isEmpty()) {
			    String[] parts2 = wccode.split("-");
			    map.put("factory", parts2.length > 0 ? parts2[0] : "");
			    map.put("storage", parts2.length > 1 ? parts2[1] : "");
			} else {
			    map.put("factory", "");
			    map.put("storage", "");
			}
			purchaseMapper.insWorkLocationBasic(map);		// worklocation에 insert
			result.put("response", "success");
		}
		
		return result;
	}

//	public int pur_location_checkInbound(String barcode) {
//		return purchaseMapper.pur_location_checkInbound(barcode);
//	}

//	@Transactional
//	public void pur_location_insert(Map<String, Object> param) {
//		int insertCount = 0;
//		String inboundVal = (String) param.get("inboundVal");
//		if (inboundVal.equals("task")) {
//			insertCount += purchaseMapper.pur_location_insert_inbound(param);
//		}
//		insertCount += purchaseMapper.pur_location_insert_realStock(param);
//
//		System.out.println("INBOUND VAL -- " + inboundVal);
//		System.out.println("INSERT COUNT -- " + insertCount);
//		if (inboundVal.equals("task") && insertCount != 2) {
//			throw new RuntimeException("Task Error : Count Miss");
//		} else if (inboundVal.equals("none") && insertCount != 1) {
//			throw new RuntimeException("Task Error : Count Miss");
//		}
//	}

	public List<Map<String, Object>> getRackList(String storage, String factory, String searchType, String keyword) {

		// 🔸 매개변수 검증 및 기본값 설정
		Map<String, Object> params = new HashMap<>();
		params.put("storage", StringUtils.hasText(storage) ? storage : "default");
		params.put("factory", StringUtils.hasText(factory) ? factory : "default");
		params.put("searchType", StringUtils.hasText(searchType) ? searchType : "default");
		params.put("keyword", StringUtils.hasText(keyword) ? keyword.trim() : "");

		try {
			// 🔸 매퍼를 통해 RACK 목록 조회
			List<Map<String, Object>> rackList = new ArrayList<>();
			if ("H/REST".equals(storage)) {
				rackList = purchaseMapper.selectWorkRackList(params);			
				
			} else {
				rackList = purchaseMapper.selectRackList(params);				
			}

			// 🔸 데이터 후처리 (필요시)
			return processRackListData(rackList);

		} catch (Exception e) {
			throw new RuntimeException("RACK 목록 조회 중 오류 발생: " + e.getMessage(), e);
		}
	}
	public Map<String, Object> getRackDetail(String rackId, String storage, String factory) {
	    if (!StringUtils.hasText(rackId)) throw new IllegalArgumentException("RACK ID는 필수입니다.");

	    Map<String, Object> params = new HashMap<>();
	    String fx = StringUtils.hasText(factory) ? factory.trim() : "default";
	    String st = StringUtils.hasText(storage) ? storage.trim() : "default";
	    String rackIdNorm = rackId.toUpperCase().trim();

	    params.put("rackId", rackIdNorm);
	    params.put("storage", st);
	    params.put("factory", fx);

	    try {
	        Map<String, Object> rackInfo = new HashMap<>();
	        List<Map<String, Object>> positions = new ArrayList<>();
	        
	        if ("H/REST".equals(storage)) {
	        	rackInfo = purchaseMapper.selectWorkRackInfo(params);	        	
	        	positions = purchaseMapper.selectWorkRackDetail(params);
	        } else {
	        	rackInfo = purchaseMapper.selectRackInfo(params);	        	
	        	positions = purchaseMapper.selectRackDetail(params);
	        }
	        
	        if (rackInfo == null) throw new RuntimeException("RACK 정보를 찾을 수 없습니다: " + rackId);


	        // 👇 factory/storage/rackId 컨텍스트 넘김
	        rackInfo.put("factory", fx);
	        rackInfo.put("storage", st);
	        rackInfo.put("rackId", rackIdNorm);
	        rackInfo.put("modules", buildModuleStructure(positions, rackIdNorm, st, fx));

	        return rackInfo;
	    } catch (Exception e) {
	        throw new RuntimeException("RACK 상세 조회 중 오류 발생: " + e.getMessage(), e);
	    }
	}

	// ---- 스킴 도우미 ----
	private String[] getLevelScheme(String factory) {
	    if (factory != null && factory.equalsIgnoreCase("Puebla")) {
	        return new String[] { "4", "3", "2", "1" }; // 시각상 위→아래
	    }
	    return new String[] { "D", "C", "B", "A" };     // Saltillo 기본
	}
	private String[] getPositionScheme(String factory) {
	    if (factory != null && factory.equalsIgnoreCase("Puebla")) {
	        return new String[] { "L", "R" };
	    }
	    return new String[] { "1", "2" };
	}

	private List<Map<String, Object>> buildModuleStructure(
	        List<Map<String, Object>> positions,
	        String rackId, String storage, String factory) {

		boolean isRackOnly = positions.stream().allMatch(p ->
				p.get("MODULENUM") == null &&
						p.get("LEVELNAME") == null &&
						p.get("POSITIONNUM") == null
		);

		if (isRackOnly) {
			// 모듈/레벨/포지션 구조 없이 positions 그대로 반환
			Map<String, Object> module = new HashMap<>();
			module.put("moduleNumber", 1);
			module.put("positions", positions.stream()
					.map(p -> createPositionData(p, 1, rackId, storage, factory, null, null))
					.collect(Collectors.toList()));
			return Collections.singletonList(module);
		}

	    Map<Integer, List<Map<String, Object>>> moduleMap = positions.stream()
	        .collect(Collectors.groupingBy(p -> ensureInteger(p.get("MODULENUM"))));

	    int maxModuleFromData = moduleMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
	    int maxModule = Math.max(1, maxModuleFromData); // 최소 1 유지 (또는 정책대로)

	    List<Map<String, Object>> modules = new ArrayList<>();
	    for (int moduleNum = 1; moduleNum <= maxModule; moduleNum++) {
	        Map<String, Object> module = new HashMap<>();
	        module.put("moduleNumber", moduleNum);

	        List<Map<String, Object>> modulePositions = moduleMap.getOrDefault(moduleNum, Collections.emptyList());
	        List<Map<String, Object>> processed = processPositionData(modulePositions, moduleNum, rackId, storage, factory);

	        module.put("positions", processed);
	        modules.add(module);
	    }
	    return modules;
	}

	private List<Map<String, Object>> processPositionData(
	        List<Map<String, Object>> positions,
	        int moduleNum,
	        String rackId, String storage, String factory) {

	    String[] levels = getLevelScheme(factory);
	    String[] posScheme = getPositionScheme(factory);

	    List<Map<String, Object>> all = new ArrayList<>();
	    for (String level : levels) {
	        for (String pos : posScheme) {
	            final String lv = level;
	            final String pz = pos;

	            Map<String, Object> existing = positions.stream().filter(p ->
	                    Objects.toString(p.get("LEVELNAME"), "").equals(lv) &&
	                    Objects.toString(p.get("POSITIONNUM"), "").equals(pz))
	                .findFirst()
	                .orElse(null);

	            Map<String, Object> slot = createPositionData(existing, moduleNum, rackId, storage, factory, lv, pz);
	            all.add(slot);
	        }
	    }
	    return all;
	}

	private Map<String, Object> createPositionData(
	        Map<String, Object> dbData,
	        int moduleNum,
	        String rackId, String storage, String factory,
	        String level, String pos) {

	    Map<String, Object> m = new HashMap<>();
	    String positionId = String.format("%s", rackId);

	    if (dbData != null) {
	        m.put("iid", dbData.get("IID"));
	        m.put("positionId", positionId);
	        m.put("location", dbData.get("LOCATION"));
	        m.put("module", moduleNum);
	        m.put("level", level);
	        m.put("position", pos); // ⚠ Saltillo=1/2, Puebla=L/R 그대로
	        m.put("status", dbData.get("POSITIONSTATUS"));
	        m.put("useyn", dbData.get("USEYN"));
	        m.put("indate", dbData.get("INDATE"));
	        m.put("ymdhms", dbData.get("YMDHMS"));
	        m.put("ymdhmsD", dbData.get("YMDHMS_D"));
	        m.put("barcode", dbData.get("BARCODE"));
	        m.put("itemcode", dbData.get("ITEMCODE"));
	        m.put("qty", ensureInteger(dbData.get("QTY")));
	        m.put("memo", dbData.get("MEMO"));
	        m.put("loginid", dbData.get("LOGINID"));
	        m.put("delMemo", dbData.get("DEL_MEMO"));
	        m.put("itemcode_mi", dbData.get("ITEMCODE_MI"));
	        m.put("itemname_mi", dbData.get("ITEMNAME_MI"));
	        m.put("carname", dbData.get("CARNAME"));
	        m.put("indate_wms", dbData.get("INDATE_WMS"));
	    } else {
	        m.put("iid", null);
	        m.put("positionId", positionId);
	        m.put("location", String.format("%s-%s-%s",
	                factory, storage, rackId));
	        m.put("module", moduleNum);
	        m.put("level", level);
	        m.put("position", pos);
	        m.put("status", "empty");
	        m.put("useyn", "Y");
	        m.put("indate", null);
	        m.put("ymdhms", null);
	        m.put("ymdhmsD", null);
	        m.put("barcode", null);
	        m.put("itemcode", null);
	        m.put("qty", 0);
	        m.put("memo", null);
	        m.put("loginid", null);
	        m.put("delMemo", null);
	        m.put("carInfo", null);
	    }
	    return m;
	}

//	public Map<String, Object> getRackDetail(String rackId, String storage, String factory) {
//
//		// 🔸 매개변수 검증
//		if (!StringUtils.hasText(rackId)) {
//			throw new IllegalArgumentException("RACK ID는 필수입니다.");
//		}
//
//		Map<String, Object> params = new HashMap<>();
//		params.put("rackId", rackId.toUpperCase().trim());
//		params.put("storage", StringUtils.hasText(storage) ? storage : "default");
//		params.put("factory", StringUtils.hasText(factory) ? factory : "default");
//
//		try {
//			// 🔸 RACK 기본 정보 조회
//			Map<String, Object> rackInfo = purchaseMapper.selectRackInfo(params);
//			if (rackInfo == null) {
//				throw new RuntimeException("RACK 정보를 찾을 수 없습니다: " + rackId);
//			}
//
//			// 🔸 RACK 상세 포지션 정보 조회
//			List<Map<String, Object>> positions = purchaseMapper.selectRackDetail(params);
//
//			// 🔸 모듈별로 그룹핑하고 구조화
//			rackInfo.put("modules", buildModuleStructure(positions));
//
//			return rackInfo;
//
//		} catch (Exception e) {
//			throw new RuntimeException("RACK 상세 조회 중 오류 발생: " + e.getMessage(), e);
//		}
//	}
//
//	public Map<String, Object> getWarehouseStatistics() {
//		try {
//			return purchaseMapper.selectWarehouseStatistics();
//		} catch (Exception e) {
//			throw new RuntimeException("창고 통계 조회 중 오류 발생: " + e.getMessage(), e);
//		}
//	}
//
	public int checkLocationRow(Map<String, String> param) {
		return purchaseMapper.checkLocationRow(param);
	}
	public int checkWorkLocationRow(Map<String, String> param) {
		return purchaseMapper.checkWorkLocationRow(param);
	}
	// 🔸 **private 헬퍼 메소드들**

//	/**
//	 * RACK 목록 데이터 후처리
//	 */
	private List<Map<String, Object>> processRackListData(List<Map<String, Object>> rackList) {

		return rackList.stream().map(rack -> {
			// 🔸 데이터 타입 보정
			rack.put("utilizationRate", ensureInteger(rack.get("UTILIZATIONRATE")));
			rack.put("currentCount", ensureInteger(rack.get("CURRENTCOUNT")));
			rack.put("totalCapacity", ensureInteger(rack.get("TOTALCAPACITY")));
			rack.put("totalQty", ensureInteger(rack.get("TOTALQTY")));

			// 🔸 키 이름 정규화 (Oracle 대문자 → camelCase)
			rack.put("rackId", rack.get("RACKID"));
			rack.put("rackName", rack.get("RACKNAME"));
			rack.put("storage", rack.get("STORAGE"));
			rack.put("area", rack.get("AREA"));
			rack.put("lastUpdated", rack.get("LASTUPDATED"));

			return rack;
		}).collect(Collectors.toList());
	}
//
//	/**
//	 * 모듈 구조 빌드 (포지션을 모듈별로 그룹핑)
//	 */
//	private List<Map<String, Object>> buildModuleStructure(List<Map<String, Object>> positions) {
//
//		// 🔸 모듈별로 그룹핑
//		Map<Integer, List<Map<String, Object>>> moduleMap = positions.stream()
//				.collect(Collectors.groupingBy(pos -> ensureInteger(pos.get("MODULENUM"))));
//
//		// 🔸 1~9 모듈 구조 생성
//		List<Map<String, Object>> modules = new ArrayList<>();
//
//		// 데이터 기준 최대 모듈 구하기 (없어도 최소 9는 유지)
//		int maxModuleFromData = moduleMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
//		int maxModule = Math.max(9, maxModuleFromData);
//
//		for (int moduleNum = 1; moduleNum <= maxModule; moduleNum++) {
//			Map<String, Object> module = new HashMap<>();
//			module.put("moduleNumber", moduleNum);
//
//			// 🔸 해당 모듈의 포지션들 가져오기
//			List<Map<String, Object>> modulePositions = moduleMap.getOrDefault(moduleNum, new ArrayList<>());
//
//			// 🔸 포지션 데이터 정규화
//			List<Map<String, Object>> processedPositions = processPositionData(modulePositions, moduleNum);
//
//			module.put("positions", processedPositions);
//			modules.add(module);
//		}
//
//		return modules;
//	}
//
//	/**
//	 * Integer 타입 보장
//	 */
	private Integer ensureInteger(Object value) {
		if (value == null)
			return 0;
		if (value instanceof Integer)
			return (Integer) value;
		if (value instanceof Number)
			return ((Number) value).intValue();
		try {
			return Integer.valueOf(value.toString());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
//
//	/**
//	 * 포지션 데이터 처리 및 정규화
//	 */
//	private List<Map<String, Object>> processPositionData(List<Map<String, Object>> positions, int moduleNum) {
//
//		// 🔸 모든 가능한 포지션 생성 (Level D,C,B,A × Position 1,2 = 8개)
//		List<Map<String, Object>> allPositions = new ArrayList<>();
//		String[] levels = { "D", "C", "B", "A" };
//
//		for (String level : levels) {
//			for (int pos = 1; pos <= 2; pos++) {
//
//				// 🔸 final 변수로 복사 (Lambda에서 사용하기 위해)
//				final int currentPos = pos;
//
//				// 🔸 실제 데이터에서 해당 포지션 찾기
//				Map<String, Object> existingPosition = positions.stream().filter(
//						p -> level.equals(p.get("LEVELNAME")) && currentPos == ensureInteger(p.get("POSITIONNUM")))
//						.findFirst().orElse(null);
//
//				// 🔸 포지션 데이터 생성 또는 빈 포지션 생성
//				Map<String, Object> position = createPositionData(existingPosition, moduleNum, level, pos);
//				allPositions.add(position);
//			}
//		}
//
//		return allPositions;
//	}
//
//	/**
//	 * 개별 포지션 데이터 생성
//	 */
//	private Map<String, Object> createPositionData(Map<String, Object> dbData, int moduleNum, String level, int pos) {
//
//		Map<String, Object> position = new HashMap<>();
//
//		if (dbData != null) {
//			// 🔸 실제 DB 데이터가 있는 경우
//			position.put("iid", dbData.get("IID"));
//			position.put("positionId", String.format("A-%d-%s-%d", moduleNum, level, pos)); // 임시로 A 사용
//			position.put("location", dbData.get("LOCATION"));
//			position.put("module", moduleNum);
//			position.put("level", level);
//			position.put("position", pos);
//			position.put("status", dbData.get("POSITIONSTATUS"));
//			position.put("useyn", dbData.get("USEYN"));
//			position.put("indate", dbData.get("INDATE"));
//			position.put("ymdhms", dbData.get("YMDHMS"));
//			position.put("ymdhmsD", dbData.get("YMDHMS_D"));
//			position.put("barcode", dbData.get("BARCODE"));
//			position.put("itemcode", dbData.get("ITEMCODE"));
//			position.put("qty", ensureInteger(dbData.get("QTY")));
//			position.put("memo", dbData.get("MEMO"));
//			position.put("loginid", dbData.get("LOGINID"));
//			position.put("delMemo", dbData.get("DEL_MEMO"));
//			position.put("itemcode_mi", dbData.get("ITEMCODE_MI"));
//			position.put("itemname_mi", dbData.get("ITEMNAME_MI"));
//			position.put("carname", dbData.get("CARNAME"));
//			position.put("indate_wms", dbData.get("INDATE_WMS"));
//
//			// 🔸 carInfo 객체 생성
////			if ("occupied".equals(dbData.get("POSITIONSTATUS"))) {
////				position.put("carInfo", createCarInfo(dbData));
////			} else {
////				position.put("carInfo", null);
////			}
//
//		} else {
//			// 🔸 빈 포지션인 경우
//			position.put("iid", null);
//			position.put("positionId", String.format("A-%d-%s-%d", moduleNum, level, pos));
//			position.put("location", String.format("Saltillo-Material-A-%d-%s-%d", moduleNum, level, pos));
//			position.put("module", moduleNum);
//			position.put("level", level);
//			position.put("position", pos);
//			position.put("status", "empty");
//			position.put("useyn", "Y");
//			position.put("indate", null);
//			position.put("ymdhms", null);
//			position.put("ymdhmsD", null);
//			position.put("barcode", null);
//			position.put("itemcode", null);
//			position.put("qty", 0);
//			position.put("memo", null);
//			position.put("loginid", null);
//			position.put("delMemo", null);
//			position.put("carInfo", null);
//		}
//
//		return position;
//	}

	// 제품정보 품번으로 가져오기
	public Map<String, Object> getItemInfo(String itemcode) {
		Map<String, Object> result = new HashMap<>();
		List<Map<String, Object>> list = purchaseMapper.getItemInfo(itemcode);
		result.put("list", list);
		return result;
	}

	// 제품정보 품번으로 가져오기
	public Map<String, Object> getItemInfo2(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		List<Map<String, Object>> list = purchaseMapper.getItemInfo2(param);
		result.put("list", list);
		return result;
	}

	// 제품정보 바코드로 가져오기
	public Map<String, Object> getItemInfo_barcode(String barcode) {
		Map<String, Object> result = new HashMap<>();
		String itemcode;
		try {
			String[] data = barcode.split(",");

			if (data.length == 0 || data[0].trim().isEmpty()) {
				throw new IllegalArgumentException("바코드 형식 오류. 입력 : " + barcode);
			}

			if (data[0].contains("P")) {
				// 바코드 자른 두번째 값 - 품번
				itemcode = data[1];
			} else {
				// 바코드 자른 첫번째 값 - 품번
				itemcode = data[0];
			}

			List<Map<String, Object>> list = purchaseMapper.getItemInfo(itemcode);
			result.put("list", list);
		} catch (Exception e) {
			throw e;
		}
		return result;
	}

	// 파레트 정보 가져오기
	public Map<String, Object> getPalletInfo(String barcode, String type) {
		Map<String, Object> result = new HashMap<>();
		List<Map<String, Object>> list = Collections.emptyList();
		
		if("part".equals(type)) {
			log.info("파트 라벨 정보");
			list = purchaseMapper.getPartInfo(barcode);
		}else if("pallet".equals(type)) {
			log.info("파레트 라벨 정보");
			list = purchaseMapper.getPalletInfo(barcode);
		}
		
		result.put("list", list);
		return result;
	}
	

	// 재고실사현황 - summary
	public Map<String, Object> searchInventorySummary(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<InventoryVO> list = purchaseMapper.searchInventorySummary(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 재고실사현황 - Detail
	public Map<String, Object> searchInventoryDetail(Map<String, Object> map) {
		System.out.println("itemcode: " + map.get("itemcode"));
		Map<String, Object> result = new HashMap<>();
		try {
			List<InventoryVO> list = purchaseMapper.searchInventoryDetail(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 언팩
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> unpack(BarcodeVO request) {
		Map<String, Object> result = new HashMap<String, Object>();
		
		List<String> list = request.getBarcode();
		
		// ✅ 팔레트-파트 전처리
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(list);
	    List<String> barcodes = fr.getFiltered();
	    
		String memo = "";
		String date = request.getDate();
		String wccode = request.getWccode();
		String factory = request.getFactory();
		String loginid = request.getLoginid();
		
		Map<String, Object> map = new HashMap<String, Object>();
		System.out.println("barcodelist : "+barcodes.toString());
		map.put("list", barcodes);
		
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}
		
		// // 팔레트가 해체된 경우, 모든 메뉴에서 사용
//	    Map<String, Object> palletNCheck = barcodeValidator.palletNCheck(request.getBarcode());
//	    if (!palletNCheck.isEmpty()) {
//	    	return palletNCheck; // 실패 시 리턴
//	    }
		// 언팩바코드 체크
		Map<String, Object> unpackCheck = barcodeValidator.unpackCheck(request.getBarcode());
		if (!unpackCheck.isEmpty()) {
			return unpackCheck; // 실패 시 리턴
		}
		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(request.getBarcode());
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }
	    
	    // 불출된 바코드인지 체크
	    Map<String, Object> alreadyWIP = barcodeValidator.alreadyWIP(request.getBarcode());
	    if (!alreadyWIP.isEmpty()) {
	    	return alreadyWIP; // 실패 시 리턴
	    }
	    
	    // 출고된 바코드인지 체크
	    Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(request.getBarcode());
	    if (!alreadyLoad.isEmpty()) {
	    	return alreadyLoad; // 실패 시 리턴
	    }
	    
	    Map<String, Object> checkMap = new HashMap<String, Object>();
	    checkMap.put("list",  request.getBarcode());
	    checkMap.put("factory",  request.getFactory());
	    
	    // 바코드가 stockinfo에 있는지 확인, 팔레트에 속한 파트도 확인 가능
	    Map<String, Object> barcodeStockInfo = barcodeValidator.barcodeStockInfo(checkMap);
	    if (!barcodeStockInfo.isEmpty()) {
	    	return barcodeStockInfo; // 실패 시 리턴
	    }
	    
	    // 다른 공장에 있는걸 return
	    Map<String, Object> inputFactoryCheck = barcodeValidator.inputFactoryCheck(checkMap);
	    if (!inputFactoryCheck.isEmpty()) {
	    	return inputFactoryCheck; // 실패 시 리턴
	    }
	    
	    // 같은공장 내 있는지 확인
	    Map<String, Object> factoryCheck = barcodeValidator.factoryCheck(checkMap);
	    if (!factoryCheck.isEmpty()) {
	    	return factoryCheck; // 실패 시 리턴
	    }

		for(String barcode:barcodes) {
			
			//바코드가 팔레트에 속해 있는지 확인
			String pbarcode = palletMapper.searchPallet(barcode);
			if (pbarcode != null && !pbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",pbarcode);
				palletMap.put("memo","UNPACK");
				// 팔레트테이블에서 해당 바코드 N처리
				int barcodeN = palletMapper.bbarcodeN(palletMap);
				// 수량 가져오기
				Double doublePQty = palletMapper.palletQty(pbarcode);
				palletMap.put("pqty",doublePQty);
				
				//바코드 수량 가져오기
				double doubleqty = palletMapper.partQty(barcode);
				palletMap.put("date",date);
				palletMap.put("qty",doubleqty);
				palletMap.put("loginid",loginid);
				// 팔레트 위치로 적재 insert
				int partLocation = palletMapper.partLocation(palletMap);
				
				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateLQtyN(palletMap);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateLQty(palletMap);
				}
			}
			String roomcode = purchaseMapper.searchRoomcodeY(barcode);
			String storage = "";
			if (roomcode == null || roomcode.isEmpty()) {
				if("Saltillo".equals(factory)) {
					roomcode = factory+"-Material";
				}else {
					roomcode = factory+"-MATERIAL";
				}
			}else {
				factory =  roomcode.split("-")[0];
				storage = roomcode.split("-")[1];
			}
			map.put("date", date);
			map.put("loginid", loginid);
			map.put("factory", factory);
			map.put("storage", storage);
			map.put("location", roomcode);
			map.put("kind", "UNPACK");
			map.put("main", "UNPACK");
			map.put("dmemo", "UNPACK");
			map.put("barcode", barcode);
			;
			map.put("laststatus", 0);
			if(barcode.split(",").length ==5) {
				map.put("itemcode", barcode.split(",")[0]);
				map.put("qty", resolveBarcodeQty(barcode));
	        	purchaseMapper.insStockUnpack(map);
	        	purchaseMapper.updateLaststatusPart(map);
	        }else {
	        	purchaseMapper.updateLaststatusPallet(map);
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		map.put("itemcode", barcode.split(",")[0]);
	        		map.put("barcode", bbarcode.get(i));
	        		map.put("qty", resolveBarcodeQty(bbarcode.get(i)));
	        		purchaseMapper.insStockUnpack(map);
	        		purchaseMapper.updateLaststatusPart(map);
	        		System.out.println("pallet part barcode N : "+bbarcode.get(i));
	        		purchaseMapper.barcodeN(map);		// 팔레트에 속했던 파트라벨도 N처리
	        	}
	        }
			map.put("barcode", barcode);
			if(barcode.split(",").length == 5) {
				map.put("itemcode", barcode.split(",")[0]);
				map.put("qty", resolveBarcodeQty(barcode));
				map.put("type", "box");
				map.put("lotdate","20"+barcode.split(",")[1] );
				purchaseMapper.barcodeN(map);
			}else if (barcode.length() == 12) {
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				if (palletInfo != null) {			// 팔레트값이 없을때 fail2
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("qty", palletInfo.getQty());
				    map.put("lotdate","20"+palletInfo.getLotdate() );
				} else {
					result.put("response", "fail2");
					return result;
				}
				map.put("type", "pallet");
				purchaseMapper.palletN(map);
			} else if (barcode.startsWith("P") && barcode.endsWith("MEX")) { // 새로운 바코드양식
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				if (palletInfo != null) {			// 팔레트값이 없을때 fail2
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("qty", palletInfo.getQty());
				    map.put("lotdate","20"+palletInfo.getLotdate() );
				} else {
					result.put("response", "fail2");
					return result;
				}
				map.put("type", "pallet");
				purchaseMapper.palletN(map);
			}
			purchaseMapper.unpack(map);
			
			purchaseMapper.locationIssue(map);
			
			result.put("response", "success");
		}
		return result;
	}

	// 언팩 내역
	public Map<String, Object> searchUnpack(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<UnpackVO> list = purchaseMapper.searchUnpack(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}
	
	// 공정불출 미도착 내역
	public Map<String, Object> searchWIPInputNotArrived(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			System.out.println("factory"+map.get("factory"));
			List<WorkmoveVO> list = purchaseMapper.searchWIPInputNotArrived(map);
			
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}
	
	// 공정불출 완료 내역
	public Map<String, Object> searchWIPInputCompleted(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			System.out.println("factory"+map.get("factory"));
			List<WorkmoveVO> list = purchaseMapper.searchWIPInputCompleted(map);
			
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 공정 불출 내역 - detail
	public Map<String, Object> searchWIPInputDetail(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			System.out.println("factory"+map.get("factory"));
			List<WorkmoveVO> list = purchaseMapper.searchWIPInputDetail(map);
			
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 공정 불출 내역 - summary
	public Map<String, Object> searchWIPInputSummary(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<WorkmoveVO> list = purchaseMapper.searchWIPInputSummary(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 제품 정보 조회
	public Map<String, Object> stockInfo(Map<String, Object> map) {
		Map<String, Object> param = new HashMap<>();
		List<Map<String, Object>> list = Collections.emptyList();

		String type = "";
		String barcode =(String) map.get("barcode");
		String[] barcodeArr = barcode.split(",");

		
		try {
			if (barcodeArr.length == 5 && (barcode.endsWith("MEX") || barcode.endsWith("USA"))) { // 파트라벨 조회
				type = "part";
				list = purchaseMapper.searchPartLabel(map);
			} else if (barcode.length()==13) { // 아이템코드 조회 ,품번조회
				type = "itemcode";
				list = purchaseMapper.getItemInfo(barcode);
			}else if (barcode.startsWith("P") && (barcode.endsWith("MEX") || barcode.endsWith("USA"))) { // 팔레트 라벨 조회
				type = "pallet";
				list = purchaseMapper.searchPalletLabel(barcode);
			} else if (barcode.contains("-")) { // 로케이션 조회
				type = "location";
				list = purchaseMapper.searchLocation(barcode);
			} else if(barcode.split("_", -1).length == 6) {
				type = "box";
				list = purchaseMapper.searchBoxBarcode(barcode);
			}
			param.put("success", true);
			param.put("type", type);
			param.put("list", list);
		} catch (Exception e) {
			param.put("success", false);
			param.put("message", e.getMessage());
		}
		return param;
	}


	// 공정불출 반납
	public Map<String, Object> wipReturn( BarcodeVO request) {
		Map<String, Object> result = new HashMap<String, Object>();
		
		List<String> list = request.getBarcode();
		//List<String> barcodes2 = request.getBarcode2();		// 팔레트가 있는 팔레트라벨바코드목록
		// ✅ 팔레트-파트 전처리: 팔레트와 함께 들어온 파트는 제외
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(list);

	    // ✅ 전처리된 목록으로 교체
	    List<String> barcodes = fr.getFiltered();
	    
		String memo = "";
		String date = request.getDate();
		String factory = request.getFactory();
		String storage = request.getStorage();
		String loginid = request.getLoginid();
		String wccode = request.getWccode();
		//String status = request.getStatus1();
		
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}
		
		Map<String, Object> map = new HashMap<String, Object>();

		System.out.println("barcodelist : "+barcodes.toString());
		map.put("list", barcodes);
		map.put("wccode", wccode);
		// // 팔레트가 해체된 경우, 모든 메뉴에서 사용
//	    Map<String, Object> palletNCheck = barcodeValidator.palletNCheck(request.getBarcode());
//	    if (!palletNCheck.isEmpty()) {
//	    	return palletNCheck; // 실패 시 리턴
//	    }
		// 언팩바코드 체크
		Map<String, Object> unpackCheck = barcodeValidator.unpackCheck(request.getBarcode());
		if (!unpackCheck.isEmpty()) {
			return unpackCheck; // 실패 시 리턴
		}
		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(request.getBarcode());
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }
	    
	    // 등록된 바코드인지 체크 251215 푸에블라에서 파트로 불출반납하고 팔레트로 묶어서 다시 불출하고 파트로 불출반납을 하는 경우 불출반납이 안돼서 주석
// 		Map<String, Object> wipreturnCheck = barcodeValidator.wipreturnCheck(request.getBarcode());
// 	    if (!wipreturnCheck.isEmpty()) {
// 	        return wipreturnCheck; // 실패 시 리턴
// 	    }

 	    
 	    
 	    // 이미 해당 창고에 있는지 체크
	    Map <String, Object> checkMap = new HashMap<String, Object>();
	    checkMap.put("list", request.getBarcode());
	    checkMap.put("factory", factory);
	    checkMap.put("storage", storage);
	    Map<String, Object> storageCheckIn = barcodeValidator.storageCheckIn(checkMap);
	    if (!storageCheckIn.isEmpty()) {
	    	return storageCheckIn; // 실패 시 리턴
	    }
	    checkMap.put("workfactory", wccode.split("-")[0]);
	    checkMap.put("workstorage", wccode.split("-")[1]);
	    // 불출반납할때 사용자가 선택한 값이 맞는지 확인
 	    Map<String, Object> wccodeCheck = barcodeValidator.wccodeCheck(checkMap);
 	    if (!wccodeCheck.isEmpty()) {
 	    	return wccodeCheck; // 실패 시 리턴
 	    }
	    // 불출시 다른 창고나 다른공장에 있으면 창고이동이나 공장이송 하라고 메시지
	    Map<String, Object> inputFactoryCheck = barcodeValidator.inputFactoryCheck(checkMap);
	    if (!inputFactoryCheck.isEmpty()) {
	    	return inputFactoryCheck; // 실패 시 리턴
	    }
	    Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
	    if (!inputStorageCheck.isEmpty()) {
	    	return inputStorageCheck; // 실패 시 리턴
	    }
 	    // 출고반품, 불출반납 가능한지 10~29 사이의 바코드는 불가능 return
	    Map<String, Object> notStorageCheck = barcodeValidator.notStorageCheck(request.getBarcode());
	    if (!notStorageCheck.isEmpty()) {
	    	return notStorageCheck; // 실패 시 리턴
	    }
 	    
 	    
		for(String barcode:barcodes) {
			//바코드가 팔레트에 속해 있는지 확인
			String pbarcode = palletMapper.searchPallet(barcode);
			if (pbarcode != null && !pbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",pbarcode);
				palletMap.put("memo","WIP RETURN");
				// 팔레트테이블에서 해당 바코드 N처리
				int barcodeN = palletMapper.bbarcodeN(palletMap);
				// 수량 가져오기
				Double doublePQty = palletMapper.palletQty(pbarcode);
				palletMap.put("pqty",doublePQty);
				
				//바코드 수량 가져오기
				double doubleqty = palletMapper.partQty(barcode);
				palletMap.put("date",date);
				palletMap.put("qty",doubleqty);
				palletMap.put("loginid",loginid);
				// 팔레트 위치로 적재 insert
				int partLocation = palletMapper.partLocation(palletMap);
				
				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateLQtyN(palletMap);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateLQty(palletMap);
				}
			}

			if (wccode == null || wccode.contains("null")) {
				if("Saltillo".equals(factory)) {
					wccode = "Saltillo-H/REST";
				}else {
					wccode = "PUEBLA-Workshop";
				}
			}
			
			if (barcode.length() == 12) {
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				if (palletInfo != null) {			// 팔레트값이 없을때 fail2
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("qty", palletInfo.getQty());
				    map.put("inqty", palletInfo.getQty());
				} else {
					result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
				}
			} else if(barcode.split(",").length == 5) {
				map.put("itemcode", barcode.split(",")[0]);
				map.put("qty", formatQty(resolveBarcodeQty(barcode)));
				map.put("inqty", formatQty(resolveBarcodeQty(barcode)));
			}else if (barcode.startsWith("P") && barcode.endsWith("MEX")) { // 새로운 바코드양식
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				if (palletInfo != null) {			// 팔레트값이 없을때 fail2
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("qty", palletInfo.getQty());
				    map.put("inqty", palletInfo.getQty());
				} else {
					result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
				}
			}
			map.put("kind", "WIPRETURN");
			map.put("source", "WIPRETURN");
			map.put("barcode", barcode);
			map.put("factory", factory);
			map.put("storage", storage);
			map.put("location2", factory+"-"+storage);
			map.put("wccode", wccode);
			map.put("memo", memo);
			map.put("dmemo", "WIP RETURN");
			map.put("date", date);
			map.put("loginid", loginid);
			purchaseMapper.wipReturnWorkmove(map);
			
			map.put("laststatus",10);
			map.put("location", factory+"-"+storage);
			purchaseMapper.basicLocation(map);
			purchaseMapper.worklocationN(map);
			if(barcode.split(",").length ==5) {
				purchaseMapper.wipReturnWorkstock(map);
				purchaseMapper.wipReturn(map);
	        	purchaseMapper.updateLaststatusPart(map);
	        }else {
	        	purchaseMapper.updateLaststatusPallet(map);
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		map.put("barcode", bbarcode.get(i));
	        		map.put("qty", resolveBarcodeQty(bbarcode.get(i)));
	        		purchaseMapper.wipReturnWorkstock(map);	//WORKSTOCK INSERT
					purchaseMapper.wipReturn(map);		// STOCK INSERT
	        		purchaseMapper.updateLaststatusPart(map);
	        	}
	        }
			result.put("response", "success");
		}
		return result;
	}

	// 공정불출 반납 내역 - detail
	public Map<String, Object> searchWIPReturnDetail(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchWIPReturnDetail(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 공정불출 반납 내역 - summary
	public Map<String, Object> searchWIPReturnSummary(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchWIPReturnSummary(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	public String searchPallet(String barcode) {
		return purchaseMapper.searchPallet(barcode);
	}


	// 입고 Invocie List 조회
	public Map<String, Object> searchIncomingInvoiceList(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchIncomingInvoiceList(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}
	
	// 인보이스 조회
	public Map<String, Object> searchInvoice(HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		try {
			Map<String, Object> map = new HashMap<String, Object>();
			String invoiceNo = request.getParameter("invoiceNo");
			map.put("invoiceNo", invoiceNo);
			List<Map<String, Object>> list = purchaseMapper.searchInvoice(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}
	
	// 입고 인보이스 insert
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> insInboundInvoice(BarcodeVO vo) {
		Map<String, Object> result = new HashMap<>();
	    final String date = vo.getDate();
	    final String main = vo.getMain();
	    final String source = vo.getSource();
	    final String kind = vo.getKind();
	    final List<String> bc = vo.getBarcode();
	    //String status = vo.getStatus1();
	    //List<String> barcodes2 = vo.getBarcode2();
	    String factory = vo.getFactory();
	    final String loginId = vo.getLoginid();     // TODO: 세션/토큰에서 가져오기
	    final String userName = "username";
	    final String storage = vo.getStorage();
	    String memo = vo.getMemo();
	    
		// ✅ 팔레트-파트 전처리
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(bc);
	    List<String> barcodes = fr.getFiltered();
	    
	    Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginId);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}
	    
	    // // 팔레트가 해체된 경우, 모든 메뉴에서 사용
//	    Map<String, Object> palletNCheck = barcodeValidator.palletNCheck(vo.getBarcode());
//	    if (!palletNCheck.isEmpty()) {
//	    	return palletNCheck; // 실패 시 리턴
//	    }
	    
		 // 언팩바코드 체크
		Map<String, Object> unpackCheck = barcodeValidator.unpackCheck(vo.getBarcode());
		if (!unpackCheck.isEmpty()) {
			return unpackCheck; // 실패 시 리턴
		}
		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(vo.getBarcode());
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }
	    
	    // 공장이송중인 바코드 체크
	    Map<String, Object> factoryMoving = barcodeValidator.factoryMoving(vo.getBarcode());
	    if (!factoryMoving.isEmpty()) {
	        return factoryMoving; // 실패 시 리턴
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
	    
	    // 입고시 다른 창고나 다른공장에 있으면 창고이동이나 공장이송 하라고 메시지
	    Map<String, Object> inputFactoryCheck = barcodeValidator.inputFactoryCheck(checkMap);
	    if (!inputFactoryCheck.isEmpty()) {
	    	return inputFactoryCheck; // 실패 시 리턴
	    }
	    Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
	    if (!inputStorageCheck.isEmpty()) {
	    	return inputStorageCheck; // 실패 시 리턴
	    }
	    
	    if(!"INCOMINGEXCEPTION".equals(source)) {
	    	// 반제품인데 무상사급 (006 and freeofcharge =1) or 002(상품), 004(원재료), 005(부자재)만 입고가능
		    Map<String, Object> incomingCodeCheck = barcodeValidator.incomingCodeCheck(vo.getBarcode());
		    if (!incomingCodeCheck.isEmpty()) {
		    	return incomingCodeCheck; // 실패 시 리턴
		    }
		    
		    // 2. 10이상인거 가져옴
		    Map<String, Object> alreadyIncoming = barcodeValidator.alreadyIncoming(vo.getBarcode());
		    if (!alreadyIncoming.isEmpty()) {
		        return alreadyIncoming; // 실패 시 리턴
		    }
		    
		    // 불출된 바코드인지 체크 추후 예외입고에서도 조건 사용할 수 있음
		    Map<String, Object> alreadyWIP = barcodeValidator.alreadyWIP(vo.getBarcode());
		    if (!alreadyWIP.isEmpty()) {
		    	return alreadyWIP; // 실패 시 리턴
		    }
		    
		    // 출고된 바코드인지 체크
		    Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(vo.getBarcode());
		    if (!alreadyLoad.isEmpty()) {
		    	return alreadyLoad; // 실패 시 리턴
		    }
		    
		    List<String> list = purchaseMapper.forIncoming(barcodes);	// laststatus 1~9사이
		    List<String> missingBarcodes = new ArrayList<>(barcodes);
			missingBarcodes.removeAll(list);
			
			if(missingBarcodes.size()>0) {
				result.put("barcode",missingBarcodes);
				result.put("response","warning.barcode.cannotIncoming");
				return result;
			}
	    }
	    
	    // 평택 출고 테이블에 있는 바코드 인지
	    //log.info("pt t_wms_outbound check - 없으면 return");
//	    Map <String, Object> ptCheckMap = new HashMap<String, Object>();
//	    ptCheckMap.put("list", vo.getBarcode());
//	    ptCheckMap.put("memo", memo);		// 인보이스 번호
//	    Map<String, Object> ptCheck = barcodeValidator.ptCheck(ptCheckMap);
//	    if (!ptCheck.isEmpty()) {
//	    	return ptCheck; // 실패 시 리턴
//	    }	    
		
	    log.info("INVOCIE INBOUND START");
	    //final String custname = vo.getCustname();
	    //final String cucode = vo.getCust();
	    for (String barcode : barcodes) {
	    	String labelType = "";
			labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
			String okyn = "Y";
			if ("Defective".equals(labelType)) {
				okyn = "N";
			}
	    	//바코드가 팔레트에 속해 있는지 확인
			String pbarcode = palletMapper.searchPallet(barcode);
			if (pbarcode != null && !pbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",pbarcode);
				palletMap.put("memo","INCOMING INVOICE");
				// 팔레트테이블에서 해당 바코드 N처리
				int barcodeN = palletMapper.bbarcodeN(palletMap);
				// 수량 가져오기
				Double doublePQty = palletMapper.palletQty(pbarcode);
				palletMap.put("pqty",doublePQty);
				
				//바코드 수량 가져오기
				double doubleqty = palletMapper.partQty(barcode);
				palletMap.put("date",date);
				palletMap.put("qty",doubleqty);
				palletMap.put("loginid",loginId);
				// 팔레트 위치로 적재 insert  하단에 위치 삭제하는 코드가 있어서 살려둠
				int partLocation = palletMapper.partLocation(palletMap);
				
				// 입고는 거의 작동안하겠지만 혹시 몰라서 남겨둠
				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateLQtyN(palletMap);
					System.out.println("incoming update pqty0 :"+pqty0);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateLQty(palletMap);
					System.out.println("incoming update updateLQty :"+updateLQty);
				}
			}
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
	    	//m.put("custname", custname);
	    	m.put("location", factory+"-"+storage);
	    	m.put("rack", "");
	    	m.put("module", "");
	    	m.put("levelcode", "");
	    	m.put("position", "");
	    	m.put("memo", memo);
	    	m.put("okyn", okyn);
	    	
	    	//m.put("custcode", cucode);
	        // 바코드 파싱 (인라인)
	        if (barcode.split(",").length == 5) {		// 파트라벨바코드
	            String[] parts = barcode.split(",");
	            m.put("itemcode", parts[0]);
	            m.put("bdate", parts[1]);
	            m.put("seq", parts[2]);
	            m.put("qty", resolveBarcodeQty(barcode));
	            m.put("scmmex", parts[4]);
	            m.put("type", "box");

	        } else if (barcode.length() == 12 && barcode.startsWith("P")) {		// 12자리 팔레트 바코드
	            PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
	            if (pal == null) {
	            	result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
	                //throw new RuntimeException("NO_PALLET_INFO");
	            }else {
	            	m.put("itemcode", pal.getItemcode());
		            m.put("qty", formatQty(pal.getQty()));
		            m.put("bdate", "");
		            m.put("seq", "");
		            m.put("scmmex", "");
		            m.put("type", "pallet");
	            }
	            
	        } else if (barcode.startsWith("P") && barcode.endsWith("MEX")) {				// 새로운 팔레트 라벨 바코드
	            PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
	            if (pal == null) {
	                result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
	                //throw new RuntimeException("NO_PALLET_INFO");
	            }else {
	            	String[] parts = barcode.split(",", -1);
		            String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
		            String seq   = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7)   : "";
		            String scmmex = (parts.length >= 4) ? parts[3] : "";

		            m.put("itemcode", pal.getItemcode());
		            m.put("qty", formatQty(pal.getQty()));
		            m.put("bdate", bdate);
		            m.put("seq", seq);
		            m.put("scmmex", scmmex);
		            m.put("type", "pallet");
	            }
	        } else {
	            result.put("response", "fail4");
	            result.put("message", "지원되지 않는 바코드 형식: " + barcode);
	            throw new RuntimeException("INVALID_BARCODE_FORMAT");
	        }

	        // INSERT: 입고
	        int insInbound = purchaseMapper.insInbound(m);
	        m.put("laststatus", 10);
	        m.put("dmemo", "INCOMING");
	        purchaseMapper.removeBarcode(m);
	        int inslocation = purchaseMapper.saveLocation(m);
	        int affected = 0;
	        if(barcode.split(",").length ==5) {
	        	affected = purchaseMapper.insStockInbound(m);
	        	purchaseMapper.updateLaststatusPart(m);
	        }else {
	        	purchaseMapper.updateLaststatusPallet(m);
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		m.put("barcode", bbarcode.get(i));
	        		m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
	        		affected = purchaseMapper.insStockInbound(m);
	        		purchaseMapper.updateLaststatusPart(m);
	        	}
	        }
	        
	        if (insInbound==0 || affected == 0) {
	            result.put("response", "fail5");
	            result.put("message", "재고 반영 실패(동시성). 다시 시도: " + barcode);
	            throw new RuntimeException("STOCK_TXN_FAILED");
	        }
	    }

		result.put("response", "success");
		return result;
	}
	
	// 입고내역 조회 - detail
	public Map<String, Object> searchIncomingDetail(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchIncomingDetail(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 입고내역 조회 - summary
	public Map<String, Object> searchIncomingSummary(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchIncomingSummary(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 입고 반품
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> incomingReturn(BarcodeVO vo) {
		log.info("INCOMING RETURN insert");
		String date = vo.getDate();
		
		Map<String, Object> result = new HashMap<>();
		List<String> barcodes  = new ArrayList<>(vo.getBarcode());      // 원본 유지
		List<String> barcodes3  = new ArrayList<>(vo.getBarcode());      // 원본 유지
		String loginid = vo.getLoginid();
		String factory = vo.getFactory();
		String storage1 = vo.getStorage();
		
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}
		
		Map<String,Object> map2 = new HashMap<String, Object>();		// 창고같은지 확인하기 위함
		map2.put("storage1",storage1);
		map2.put("storage", storage1);
		map2.put("factory", factory);
		map2.put("list", barcodes3);
		// 1.존재하는 바코드인지 확인 생성된 바코드인지 확인
	    Map<String, Object> existBarcode = barcodeValidator.validateExist(vo.getBarcode());
	    if (!existBarcode.isEmpty()) {
	        return existBarcode; // 실패 시 바로 리턴
	    }

	    // 출고된 바코드인지 체크
	    Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(vo.getBarcode());
	    if (!alreadyLoad.isEmpty()) {
	    	return alreadyLoad; // 실패 시 리턴
	    }

		// 불량라벨 아닌걸 반환
		Map<String, Object> defectiveNReturn = barcodeValidator.defectiveNReturn(map2);
		if (!defectiveNReturn.isEmpty()) {
			return defectiveNReturn; // 실패 시 리턴
		}
		
		// 입고 반품된 바코드인지 체크
		Map<String, Object> alreadyIncomingReturn = barcodeValidator.alreadyIncomingReturn(map2);
		if (!alreadyIncomingReturn.isEmpty()) {
			return alreadyIncomingReturn; // 실패 시 리턴
		}

		// ✅ 팔레트-파트 전처리: 팔레트와 함께 들어온 파트는 제외
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(vo.getBarcode());

	    // ✅ 전처리된 목록으로 교체
	    List<String> barcodess = fr.getFiltered();

		for (String barcode : barcodess) {
	    	//바코드가 팔레트에 속해 있는지 확인
			String pbarcode = palletMapper.searchPallet(barcode);
			if (pbarcode != null && !pbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",pbarcode);
				palletMap.put("memo","INCOMING RETURN");
				// 팔레트테이블에서 해당 바코드 N처리
				int barcodeN = palletMapper.bbarcodeN(palletMap);
				// 수량 가져오기
				Double doublePQty = palletMapper.palletQty(pbarcode);
				palletMap.put("pqty",doublePQty);
				
				//바코드 수량 가져오기
				double doubleqty = palletMapper.partQty(barcode);
				palletMap.put("date",date);
				palletMap.put("qty",doubleqty);
				palletMap.put("loginid",loginid);
				// 팔레트 위치로 적재 insert  하단에 위치 삭제하는 코드가 있어서 살려둠
				int partLocation = palletMapper.partLocation(palletMap);
				
				// 입고는 거의 작동안하겠지만 혹시 몰라서 남겨둠
				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateLQtyN(palletMap);
					System.out.println("incoming update pqty0 :"+pqty0);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateLQty(palletMap);
					System.out.println("incoming update updateLQty :"+updateLQty);
				}
			}


			Map<String, Object> map = new HashMap<>();
			map.put("date", date);
			map.put("barcode", barcode);
			map.put("loginid", loginid);
			map.put("username", "username");
			map.put("source", vo.getSource());
			map.put("main", vo.getMain());
			map.put("kind", vo.getKind());
			map.put("storage", vo.getStorage());
			map.put("factory", vo.getFactory());
			map.put("location", vo.getFactory()+"-"+vo.getStorage());
			map.put("memo", "-");
			map.put("rack", "-");
			map.put("module", "-");
			map.put("levelcode", "-");
			map.put("position", "-");
			if (barcode.split(",").length == 5 && barcode.endsWith("USA")) {        // 파트라벨바코드
				String[] parts = barcode.split(",");
				Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(parts[0]);
				map.put("oitemcode", item.get("OITEMCODE"));
				map.put("itemcode", parts[0]);

				map.put("bdate", parts[1]);
				map.put("seq", parts[2]);
				map.put("qty", resolveBarcodeQty(barcode));
				map.put("scmmex", parts[4]);
				map.put("type", "box");
			} else if (barcode.split(",").length == 4 &&  barcode.endsWith("USA")) {				// 새로운 팔레트 라벨 바코드
	            PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
	            if (pal == null) {
	                result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
	                //throw new RuntimeException("NO_PALLET_INFO");
	            }else {
	            	String[] parts = barcode.split(",", -1);
		            String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
		            String seq   = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7)   : "";
		            String scmmex = (parts.length >= 4) ? parts[3] : "";
					Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(pal.getItemcode());
					map.put("oitemcode", item.get("OITEMCODE"));
		            map.put("itemcode", pal.getItemcode());
		            map.put("qty", formatQty(pal.getQty()));
		            map.put("bdate", bdate);
		            map.put("seq", seq);
		            map.put("scmmex", scmmex);
		            map.put("type", "pallet");
		            map.put("pbarcode", "");
	            }
	        } else if(barcode.split("_", -1).length == 6){		// 박스 바코드일때
				String[] parts = barcode.split("_", -1);
				Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
				map.put("itemcode", item.get("ITEMCODE"));
				map.put("oitemcode",parts[3]);
				map.put("bdate", parts[2].substring(2) + parts[1] + parts[0]);
				map.put("seq", (parts != null && parts.length > 5) ? parts[5] : "");
				map.put("qty", resolveBarcodeQty(barcode));
				map.put("scmmex", "WMSUSA");
				map.put("type", "");

			}
			
			// 바코드로 조회 해 최신 iid의 useyn을 n으로 업데이트
			//purchaseMapper.incomingReturn_updateYn(map);
			// inbound 테이블에 insert
			purchaseMapper.incomingReturn_insertInboundTable(map);
			map.put("dmemo", "INCOMING RETURN");
			purchaseMapper.removeBarcode(map);
			System.out.println("저장할 바코드: " + barcode);
			map.put("laststatus", 1);
			int affected = 0;
			if(barcode.split("_", -1).length == 6 || barcode.split(",").length == 5) {
	        	purchaseMapper.incomingReturn_insertStockTable(map);// stock 테이블에 insert
	        	purchaseMapper.updateLaststatusPart(map);
	        }else {
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	purchaseMapper.updateLaststatusPallet(map);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		map.put("barcode", bbarcode.get(i));
	        		map.put("qty", resolveBarcodeQty(bbarcode.get(i)));
	        		purchaseMapper.incomingReturn_insertStockTable(map);// stock 테이블에 insert
	        		purchaseMapper.updateLaststatusPart(map);
	        	}
	        }
		}
		result.put("response", "success");

		return result;
//		int resultCount = 0;
//		for(int i=0; i<param.size(); i++) {
//			Map<String,Object> taskParam = param.get(i);
//			resultCount += purchaseMapper.incomingReturn_updateYn(taskParam);
//			// IID 값으로 조회해서 그대로 넣을거라 입고안되어있는 제품이면 어차피 insert 안됨, 입고여부 체크할 필요없어보임 
//			//int inboundCheck = purchaseMapper.incomingReturn_inboundCheck(taskParam)
//			resultCount += purchaseMapper.incomingReturn_insertStockTable(taskParam);
//			
//		}
//		
//		
//		return resultCount;
	}

	// 입고 반품  - detail
	public Map<String, Object> searchIncomingReturnDetail(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchIncomingReturnDetail(map);
			result.put("list", list);
			result.put("success", true);
		}catch(Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}	
	
	public Map<String, Object> searchLoadDetail(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchLoadDetail(map);
			result.put("list", list);
			result.put("success", true);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}
	
	// 출고반품 - Detail
	public Map<String, Object> searchLoadReturnDetail(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchLoadReturnDetail(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 검사 목록 조회 (창고검사/반품검사/폐기)
	public Map<String, Object> searchInspectionList(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchInspectionList(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}
	
	// 언팩리스트
	public  Map<String, Object> unpackList(BarcodeVO request) {
		Map<String, Object> result = new HashMap<String, Object>();
		
		String barcodeOne = request.getBarcodeone();
		String date = request.getDate();
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("date", date);
		map.put("barcode", barcodeOne);
		map.put("factory", request.getFactory());
		List<String> list = purchaseMapper.unpackList(map);
		result.put("list",list);
		return result;
	}

	public List<Map<String, Object>> unpackCompleteDetail(String barcode) {
		return purchaseMapper.unpackCompleteDetail(barcode);
	}

	public Map<String, Object> unpackBarcodeList(String barcode) {
		Map<String, Object> result = new HashMap<>();
		List<Map<String, Object>> list = purchaseMapper.unpackBarcodeList(barcode);
		result.put("list", list);
		return result;
	}
	//언팩 상테 업데이트
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> updateUnpackStatus(BarcodeVO request) {
		Map<String, Object> result = new HashMap<String, Object>();
		System.out.println("barcode" + request.getBarcode());
		
		Map<String, Object> map = new HashMap<String, Object>();
		LocalDate today = LocalDate.now();
        String date = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		List<String> barcodes = request.getBarcode();
		String barcode = request.getBarcodeone();
		String factory = request.getFactory();
		String loginid = request.getLoginid();
		String labelType = "";
		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
		String okyn = "Y";
		if ("Defective".equals(labelType)) {
			okyn = "N";
		}
		map.put("list", barcodes);
		map.put("barcode", barcode);
		map.put("factory", factory);
		map.put("loginid", loginid);
		map.put("date", date);
		map.put("memo", " ");
		map.put("rack", " ");
		map.put("module", " ");
		map.put("levelcode", " ");
		map.put("position", " ");
		map.put("source", "UNPACKCOMPLETE");
		map.put("okyn", okyn);
		// unpack 테이블 completeyn y 업데이트
		purchaseMapper.updateUnpackStatus(map);
		// 스캔한 바코드 barcode 테이블 useyn y 업데이트
		purchaseMapper.updateUnpackBarcodeYN(map);
		
		String storage = "";
		if("Saltillo".equals(factory)) {	// 기본값
			storage =  "Material";
		}else {
			storage =  "MATERIAL";
		}
		for(String bc:barcodes) {
			map.put("barcode", bc);
			map.put("laststatus", 10);
			map.put("storage", storage);
			map.put("location", factory+"-"+storage);
			map.put("kind", "UNPACK");
			map.put("main", "UNPACK");
			
			String parts[] = bc.split(",");
			map.put("itemcode", parts[0]);
			map.put("qty", resolveBarcodeQty(bc));
			
			purchaseMapper.saveLocation(map);
        	purchaseMapper.insStockUnpackplus(map);
        	purchaseMapper.updateLaststatusPart(map);
	        
		}
		
		return result;
	}

	// 적재위치 detail
	public Map<String, Object> locationDetail(String barcode) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = new ArrayList<>();

			list = purchaseMapper.selectLocationDetail(barcode);

			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}
	

	// 입고 insert
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> insInbound(BarcodeVO vo) {
		Map<String, Object> result = new HashMap<>();
	    final String date = vo.getDate();
	    final String main = vo.getMain();
	    final String source = vo.getSource();
	    final String kind = vo.getKind();
	    final List<String> bc = vo.getBarcode();
	    String factory = vo.getFactory();
	    final String loginId = vo.getLoginid();
	    final String userName = "username";
	    final String storage = vo.getStorage();
	    String memo = vo.getMemo();
		// ✅ 팔레트-파트 전처리
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(bc);
	    List<String> barcodes = fr.getFiltered();

	    Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginId);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}

		// 등록이 안되어 있으면 T_SCM_BARCODE 테이블에 박스바코드 생성
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
	    
	    if(!"INCOMINGEXCEPTION".equals(source)) {
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
		    
		    List<String> list = purchaseMapper.forIncoming(barcodes);	// laststatus 1~9사이
		    List<String> missingBarcodes = new ArrayList<>(barcodes);
			missingBarcodes.removeAll(list);
			
			if(missingBarcodes.size()>0) {
				result.put("barcode",missingBarcodes);
				result.put("response","warning.barcode.cannotIncoming");
				return result;
			}
	    }

	    for (String barcode : barcodes) {


	    	//바코드가 팔레트에 속해 있는지 확인
			String pbarcode = palletMapper.searchPallet(barcode);
			if (pbarcode != null && !pbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",pbarcode);
				palletMap.put("memo","INCOMING");
				// 팔레트테이블에서 해당 바코드 N처리
				int barcodeN = palletMapper.bbarcodeN(palletMap);
				// 수량 가져오기
				Double doublePQty = palletMapper.palletQty(pbarcode);
				palletMap.put("pqty",doublePQty);
				
				//바코드 수량 가져오기
				double doubleqty = palletMapper.partQty(barcode);
				palletMap.put("date",date);
				palletMap.put("qty",doubleqty);
				palletMap.put("loginid",loginId);
				// 팔레트 위치로 적재 insert  하단에 위치 삭제하는 코드가 있어서 살려둠
				int partLocation = palletMapper.partLocation(palletMap);
				
				// 입고는 거의 작동안하겠지만 혹시 몰라서 남겨둠
				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateLQtyN(palletMap);
					System.out.println("incoming update pqty0 :"+pqty0);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateLQty(palletMap);
					System.out.println("incoming update updateLQty :"+updateLQty);
				}
			}


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
	        if (barcode.split(",").length == 5 && barcode.endsWith("USA")) {		// 파트라벨바코드
	            String[] parts = barcode.split(",");
				Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(parts[0]);
				m.put("oitemcode", item.get("OITEMCODE"));
	            m.put("itemcode", parts[0]);

	            m.put("bdate", parts[1]);
	            m.put("seq", parts[2]);
	            m.put("qty", resolveBarcodeQty(barcode));
	            m.put("scmmex", parts[4]);
	            m.put("type", "box");
				m.put("rack", item.get("CAR"));
				m.put("location", factory+"-"+storage+"-"+item.get("CAR"));

	        } else if (barcode.split(",").length == 4 &&  barcode.endsWith("USA")) {				// 새로운 팔레트 라벨 바코드
	            PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
	            if (pal == null) {
	                result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
	            }else {
	            	String[] parts = barcode.split(",", -1);
		            String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
		            String seq   = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7)   : "";
		            String scmmex = (parts.length >= 4) ? parts[3] : "";
					Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(pal.getItemcode());
					m.put("oitemcode", item.get("OITEMCODE"));
		            m.put("itemcode", pal.getItemcode());
		            m.put("qty", formatQty(pal.getQty()));
		            m.put("bdate", bdate);
		            m.put("seq", seq);
		            m.put("scmmex", scmmex);
		            m.put("type", "pallet");
					m.put("rack", item.get("CAR"));
					m.put("location", factory+"-"+storage+"-"+item.get("CAR"));
	            }
	        } else if(barcode.split("_", -1).length == 6){		// 박스 바코드일때
				String[] parts = barcode.split("_", -1);
				Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
				m.put("itemcode", item.get("ITEMCODE"));
				m.put("oitemcode",item.get("OITEMCODE"));
				m.put("bdate", parts[2].substring(2) + parts[1] + parts[0]);
				m.put("seq", (parts != null && parts.length > 5) ? parts[5] : "");
				m.put("qty", resolveBarcodeQty(barcode));
				m.put("rack", item.get("CAR"));
				m.put("location", factory+"-"+storage+"-"+item.get("CAR"));
			}else {
	            result.put("response", "fail4");
	            result.put("message", "지원되지 않는 바코드 형식: " + barcode);
	            System.out.println("INVALID_BARCODE_FORMAT: " + barcode);
	            throw new RuntimeException("INVALID_BARCODE_FORMAT");
	        }
	        
	        int insInbound = 0;
	        // INSERT: 입고
	        if("INCOMINGEXCEPTION".equals(source)) {		// 예외입고 쿼리 따로 사용
	        	m.put("source2",vo.getSource2());
	        	insInbound = purchaseMapper.insInboundException2(m);		//  source2, invoiceno 둘다 insert
	        }else {
	        	insInbound = purchaseMapper.insInbound(m);
	        }
	        
	        m.put("laststatus", 10);
	        m.put("dmemo", "INCOMING");
	        purchaseMapper.removeBarcode(m);
	        int inslocation = purchaseMapper.saveLocation(m);
	        int affected = 0;
	        if(barcode.split("_", -1).length == 6 || barcode.split(",").length == 5) {
	        	affected = purchaseMapper.insStockInbound(m);
	        	purchaseMapper.updateLaststatusPart(m);
	        }else {
	        	purchaseMapper.updateLaststatusPallet(m);
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		m.put("barcode", bbarcode.get(i));
	        		m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
	        		affected = purchaseMapper.insStockInbound(m);
	        		purchaseMapper.updateLaststatusPart(m);
	        	}
	        }
	        
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

	// 예외 입고 내역 - detail
	public Map<String, Object> searchexceptionInputDetail(Map<String, Object> map) {
		System.out.println("factory: "+map.get("factory"));
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchexceptionInputDetail(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 출고 insert
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> insOutputTransys(BarcodeVO vo) {
		Map<String, Object> result = new HashMap<>();
		final String date = vo.getDate();
		final String main = vo.getMain();
		final String source = vo.getSource();
		final String kind = vo.getKind();
		final List<String> bc = vo.getBarcode();
		String storage = vo.getStorage();
		String factory = vo.getFactory();
		String memo = vo.getMemo();
		String shipTo = vo.getShipTo();
		System.out.println("memo @@@@ :"+memo);
		final String loginId = vo.getLoginid();
		final String userName = "username";
		String custcode = "";
		if("TRANSYS_GA".equalsIgnoreCase(shipTo)){
			custcode = "A022";
		}else if("TRANSYS_AZ".equalsIgnoreCase(shipTo)){
			custcode = "0007";
		}else if("TRANSYS_AL".equalsIgnoreCase(shipTo)){
			custcode = "0005";
		}else if("TRANSYS_IL".equalsIgnoreCase(shipTo)){
			custcode = "0004";
		}else if("LEAR".equalsIgnoreCase(shipTo)){
			custcode = "0002";
		}else {
			custcode = "0006";
		}
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginId);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}

		// 출고된 바코드인지 체크
		Map<String, Object> alreadyOutbound = barcodeValidator.alreadyOutbound(vo.getBarcode());
		if (!alreadyOutbound.isEmpty()) {
			return alreadyOutbound; // 실패 시 리턴
		}
		
		// ✅ 팔레트-파트 전처리
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(bc);
	    List<String> barcodes = fr.getFiltered();		
		List<String> barcodes3 = new ArrayList<>(barcodes);      // 창고 체크용 별도 복사본
		
		Map<String,Object> map2 = new HashMap<String, Object>();		// 창고같은지 확인하기 위함
		map2.put("storage1", vo.getStorage());
		map2.put("storage", vo.getStorage());
		map2.put("factory", vo.getFactory());
		map2.put("list", barcodes3);

//		// 등록된 바코드인지 체크
//		Map<String, Object> notRegistered = barcodeValidator.notRegistered(vo.getBarcode());
//	    if (!notRegistered.isEmpty()) {
//	        return notRegistered; // 실패 시 리턴
//	    }
//
//		// laststatus 91이상 체크
//		Map<String, Object> check91 = barcodeValidator.check91(map2); 	// 91이상 확인
//		if(!check91.isEmpty()) {
//			return check91;
//		}
//
//	    // 출고된 바코드인지 체크
// 		Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(vo.getBarcode());
// 	    if (!alreadyLoad.isEmpty()) {
// 	        return alreadyLoad; // 실패 시 리턴
// 	    }

	    // 
//	    Map <String, Object> checkMap = new HashMap<String, Object>();
//	    checkMap.put("list", vo.getBarcode());
//	    checkMap.put("factory", factory);
//	    checkMap.put("storage", storage);
//	    // 출고시 다른 창고에 있으면 창고이동 하라고 메시지
//	    Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
//	    if (!inputStorageCheck.isEmpty()) {
//	    	return inputStorageCheck; // 실패 시 리턴
//	    }
//
//	    // 해당 창고에 있는지 체크
//	    Map<String, Object> storageCheckOut = barcodeValidator.storageCheckOut(checkMap);
//	    if (!storageCheckOut.isEmpty()) {
//	    	return storageCheckOut; // 실패 시 리턴
//	    }
//
//
//	    // 이창고에 없으면 출고 못함
//	    Map<String, Object> storageCheck = barcodeValidator.storageCheck(map2); 	// 창고같은지 확인하기 위함
//		if(!storageCheck.isEmpty()) {
//			return storageCheck;
//		}

		for (String barcode : barcodes) {
			//바코드가 팔레트에 속해 있는지 확인
			String pbarcode = palletMapper.searchPallet(barcode);
			if (pbarcode != null && !pbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",pbarcode);
				palletMap.put("memo","LOAD");
				// 팔레트테이블에서 해당 바코드 N처리
				int barcodeN = palletMapper.bbarcodeN(palletMap);
				// 수량 가져오기
				Double doublePQty = palletMapper.palletQty(pbarcode);
				palletMap.put("pqty",doublePQty);
				
				//바코드 수량 가져오기
				double doubleqty = palletMapper.partQty(barcode);
				palletMap.put("date",date);
				palletMap.put("qty",doubleqty);
				palletMap.put("loginid",loginId);
				// 팔레트 위치로 적재 insert
				int partLocation = palletMapper.partLocation(palletMap);
				
				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateLQtyN(palletMap);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateLQty(palletMap);
				}
			}


			Map<String, Object> m = new HashMap<>();
			m.put("factory", factory);
			m.put("storage", storage);
			m.put("date", date);
			m.put("barcode", barcode);
			m.put("loginid", loginId);
			m.put("username", userName);
			m.put("source", source);
			m.put("source2", "PURCHASE");
			//m.put("cust", custcode);
			m.put("main", main);
			m.put("kind", kind);
			m.put("memo", memo);
			m.put("custname", shipTo);
			m.put("custcode", custcode);

	        // 바코드 파싱 (인라인)
	        if (barcode.split(",").length ==5 && barcode.endsWith("USA")) {
	            String[] parts = barcode.split(",", -1);
				Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(parts[0]);
				m.put("oitemcode", item.get("OITEMCODE"));
	            m.put("itemcode", parts[0]);
	            m.put("bdate", parts[1]);
	            m.put("seq", parts[2]);
	            m.put("qty", resolveBarcodeQty(barcode));
	            m.put("scmmex", parts[4]);
	            m.put("type", "box");

	        } else if (barcode.split(",").length ==4 && barcode.endsWith("USA")) {
				PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
				if (pal == null) {
					result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
					//throw new RuntimeException("NO_PALLET_INFO");
				}
				String[] parts = barcode.split(",", -1);
				String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
				String seq = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7) : "";
				String scmmex = (parts.length >= 4) ? parts[3] : "";
				Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(parts[0]);
				m.put("oitemcode", item.get("OITEMCODE"));
	            m.put("itemcode", pal.getItemcode());
	            m.put("qty", formatQty(pal.getQty()));
	            m.put("bdate", bdate);
	            m.put("seq", seq);
	            m.put("scmmex", scmmex);
	            m.put("type", "pallet");
	            

			} else if (barcode.startsWith("[)>")) {								// 트랜시스 바코드
				String[] parts = barcode.split(":");

				String oitemcode = parts[2].substring(1);				// 고객사 품번
				String itemcode = purchaseMapper.getItemcode(oitemcode);		// 아이템 코드

				String qty = parts[3].substring(2);					// 수량

				m.put("itemcode", itemcode);
				m.put("oitemcode", oitemcode);
				m.put("qty", formatQty(qty));
				m.put("bdate", "");
				m.put("seq", "");
				m.put("scmmex", "WMSUSA");
				m.put("type", "pallet");

			} else {
				result.put("response", "fail4");
				result.put("message", "지원되지 않는 바코드 형식: " + barcode);
				throw new RuntimeException("INVALID_BARCODE_FORMAT");
			}
	        m.put("storage",vo.getStorage());
	        m.put("dmemo","OUTPUT");
	        // INSERT: 예외출고
	        int insOutbound = 0;
	        if("LOADEXCEPTION".equals(source)) {
	        	m.put("source2", vo.getSource2());
	        	insOutbound = purchaseMapper.insOutputException2(m);			// 예외출고 invoice, source2 insert
	        }else {
	        	insOutbound = purchaseMapper.insOutput(m);
	        }
	        int affected = 0;
	        // INSERT: 재고(로그/조정 내역) — 영향행 수 체크(동시성 방어)
	        purchaseMapper.removeBarcode(m);
	        m.put("laststatus", 50);
	        if(barcode.split(",").length ==5) {
	        	affected = purchaseMapper.insertStockOutput(m);
	        	purchaseMapper.updateLaststatusPart(m);
	        } else if ((barcode.endsWith("USA") || barcode.endsWith("MEX"))) {
	        	purchaseMapper.updateLaststatusPallet(m);
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		m.put("barcode", bbarcode.get(i));
	        		m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
	        		affected = purchaseMapper.insertStockOutput(m);
	        		purchaseMapper.updateLaststatusPart(m);
	        	}
	        } else if (barcode.startsWith("[)>")){
				affected = purchaseMapper.insertStockOutput(m);
			}
	        if (insOutbound == 0 || affected == 0) {
	            result.put("response", "fail5");
	            result.put("message", "재고 반영 실패(동시성). 다시 시도: " + barcode);
	            throw new RuntimeException("STOCK_TXN_FAILED");
	        }
	    }


		result.put("response", "success");
		return result;
	}

	// 출고 insert
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> insOutput(BarcodeVO vo) {
		Map<String, Object> result = new HashMap<>();
		final String date = vo.getDate();
		final String main = vo.getMain();
		final String source = vo.getSource();
		final String kind = vo.getKind();
		final List<String> bc = vo.getBarcode();
		String storage = vo.getStorage();
		String factory = vo.getFactory();
		String memo = vo.getMemo();
		String invoiceno = vo.getInvoiceno();
		System.out.println("memo @@@@ :"+memo);
		final String loginId = vo.getLoginid();
		final String userName = "username";

		String shipTo = vo.getShipTo();		// 출고처
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginId);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}

		// ✅ 팔레트-파트 전처리
		FilterResult fr = palletFilter.removeChildPartsIfParentPresent(bc);
		List<String> barcodes = fr.getFiltered();
		List<String> barcodes3 = new ArrayList<>(barcodes);      // 창고 체크용 별도 복사본

		Map<String,Object> map2 = new HashMap<String, Object>();		// 창고같은지 확인하기 위함
		map2.put("storage1", vo.getStorage());
		map2.put("storage", vo.getStorage());
		map2.put("factory", vo.getFactory());
		map2.put("list", barcodes3);
		if(!"LEAR".equalsIgnoreCase(shipTo) && !"LOADEXCEPTION".equalsIgnoreCase(source)) {
			// 출고된 바코드인지 체크
			Map<String, Object> alreadyOutbound = barcodeValidator.alreadyOutbound(vo.getBarcode());
			if (!alreadyOutbound.isEmpty()) {
				return alreadyOutbound; // 실패 시 리턴
			}
		}
		if("LEAR".equalsIgnoreCase(shipTo) || "LOADEXCEPTION".equalsIgnoreCase(source)){
			// 등록된 바코드인지 체크
			Map<String, Object> notRegistered = barcodeValidator.notRegistered(vo.getBarcode());
			if (!notRegistered.isEmpty()) {
				return notRegistered; // 실패 시 리턴
			}

			// laststatus 91이상 체크
			Map<String, Object> check91 = barcodeValidator.check91(map2); 	// 91이상 확인
			if(!check91.isEmpty()) {
				return check91;
			}

			// 출고된 바코드인지 체크
			Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(vo.getBarcode());
			if (!alreadyLoad.isEmpty()) {
				return alreadyLoad; // 실패 시 리턴
			}

			Map <String, Object> checkMap = new HashMap<String, Object>();
			checkMap.put("list", vo.getBarcode());
			checkMap.put("factory", factory);
			checkMap.put("storage", storage);
			// 출고시 다른 창고에 있으면 창고이동 하라고 메시지
			Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
			if (!inputStorageCheck.isEmpty()) {
				return inputStorageCheck; // 실패 시 리턴
			}

			if("LOADEXCEPTION".equalsIgnoreCase(source)){
				// 해당 창고에 있는지 체크
				Map<String, Object> storageCheckOut = barcodeValidator.storageCheckOut(checkMap);
				if (!storageCheckOut.isEmpty()) {
					return storageCheckOut; // 실패 시 리턴
				}
				// 이창고에 없으면 출고 못함
				Map<String, Object> storageCheck = barcodeValidator.storageCheck(map2); 	// 창고같은지 확인하기 위함
				if(!storageCheck.isEmpty()) {
					return storageCheck;
				}
			}

		}


		for (String barcode : barcodes) {
			//바코드가 팔레트에 속해 있는지 확인
			String pbarcode = palletMapper.searchPallet(barcode);
			if (pbarcode != null && !pbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",pbarcode);
				palletMap.put("memo","LOAD");
				// 팔레트테이블에서 해당 바코드 N처리
				int barcodeN = palletMapper.bbarcodeN(palletMap);
				// 수량 가져오기
				Double doublePQty = palletMapper.palletQty(pbarcode);
				palletMap.put("pqty",doublePQty);

				//바코드 수량 가져오기
				double doubleqty = palletMapper.partQty(barcode);
				palletMap.put("date",date);
				palletMap.put("qty",doubleqty);
				palletMap.put("loginid",loginId);
				// 팔레트 위치로 적재 insert
				int partLocation = palletMapper.partLocation(palletMap);

				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateLQtyN(palletMap);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateLQty(palletMap);
				}
			}
			String custcode = "";
			if("LEAR".equalsIgnoreCase(shipTo)){
				custcode = "0002";
			}else if("ADNT".equalsIgnoreCase(shipTo) || "ADIENT".equalsIgnoreCase(shipTo)){
				custcode = "0006";
			}else if("TRANSYS_AL".equalsIgnoreCase(shipTo)){
				custcode = "0005";
			}else if("TRANSYS_IL".equalsIgnoreCase(shipTo)){
				custcode = "0004";
			}else{
				custcode = "A022";
			}

			Map<String, Object> m = new HashMap<>();
			m.put("factory", factory);
			m.put("storage", storage);
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
			//m.put("custname", custname);

			// 바코드 파싱 (인라인)
			if (barcode.split(",").length ==5 && barcode.endsWith("USA")) {
				String[] parts = barcode.split(",", -1);
				Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[0]);
				m.put("oitemcode", item.get("OITEMCODE"));
				m.put("itemcode", item.get("ITEMCODE"));
				m.put("bdate", parts[1]);
				m.put("seq", parts[2]);
				m.put("qty", resolveBarcodeQty(barcode));
				m.put("scmmex", parts[4]);
				m.put("type", "PART");

			} else if (barcode.split(",").length ==4 && barcode.endsWith("USA")) {
				PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
				if (pal == null) {
					result.put("response", "warning.pallet.infoNotFound");
					result.put("barcode", Arrays.asList(barcode));
					return result;
					//throw new RuntimeException("NO_PALLET_INFO");
				}
				String[] parts = barcode.split(",", -1);
				String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
				String seq = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7) : "";
				String scmmex = (parts.length >= 4) ? parts[3] : "";
				Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(pal.getItemcode());
				m.put("oitemcode", item.get("OITEMCODE"));
				m.put("itemcode", pal.getItemcode());
				m.put("qty", formatQty(pal.getQty()));
				m.put("bdate", bdate);
				m.put("seq", seq);
				m.put("scmmex", scmmex);
				m.put("type", "PALLET");


			} else if(barcode.split("_").length == 6){
				String[] parts = barcode.split("_", -1);
				Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
				m.put("oitemcode", item.get("OITEMCODE"));
				m.put("itemcode", item.get("ITEMCODE"));
				m.put("bdate", parts[2].substring(2)+parts[1]+parts[0]);
				m.put("seq", parts[5]);
				m.put("qty", resolveBarcodeQty(barcode));
				m.put("scmmex", "");
				m.put("type", "BOX");
			}else if (barcode.startsWith("[)>")) {
				String oitemcode, qty;
				if (barcode.contains("|")) {						// Adient: [)>|06|P품번|Q수량|...
					String[] parts = barcode.split("\\|");
					oitemcode = parts[2].substring(1);
					qty = parts[3].substring(1);
					m.put("type", "ADIENT");
				} else {										// Transys: [)>*06:serial:P품번:XQ수량:...
					String[] parts = barcode.split(":");
					oitemcode = parts[2].substring(1);
					qty = parts[3].substring(2);
					m.put("type", "TRANSYS");
				}
				String itemcode = purchaseMapper.getItemcode(oitemcode);
				log.info("260419 : "+barcode);
				m.put("itemcode", itemcode);
				m.put("oitemcode", oitemcode);
				m.put("qty", formatQty(qty));
				m.put("bdate", "");
				m.put("seq", "");
				m.put("scmmex", "WMSUSA");

			} else {
				result.put("response", "fail4");
				result.put("message", "지원되지 않는 바코드 형식: " + barcode);
				throw new RuntimeException("INVALID_BARCODE_FORMAT");
			}
			m.put("storage",vo.getStorage());
			m.put("dmemo","OUTPUT");
			// INSERT: 예외출고
			int insOutbound = 0;
			if("LOADEXCEPTION".equals(source)) {
				m.put("source2", vo.getSource2());
				insOutbound = purchaseMapper.insOutputException2(m);			// 예외출고 invoice, source2 insert
			}else {
				insOutbound = purchaseMapper.insOutput(m);
			}
			int affected = 0;
			// INSERT: 재고(로그/조정 내역) — 영향행 수 체크(동시성 방어)
			purchaseMapper.removeBarcode(m);
			m.put("laststatus", 50);
			if(barcode.split(",").length ==5 || barcode.split("_").length == 6) {
				affected = purchaseMapper.insertStockOutput(m);
				purchaseMapper.updateLaststatusPart(m);
			} else if ((barcode.endsWith("USA") || barcode.endsWith("MEX"))) {
				purchaseMapper.updateLaststatusPallet(m);
				List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
				for(int i = 0; i<bbarcode.size(); i++) {
					m.put("barcode", bbarcode.get(i));
					m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
					affected = purchaseMapper.insertStockOutput(m);
					purchaseMapper.updateLaststatusPart(m);
				}
			} else if (barcode.startsWith("[)>")){
				affected = purchaseMapper.insertStockOutput(m);
			}
			if (insOutbound == 0 || affected == 0) {
				result.put("response", "fail5");
				result.put("message", "재고 반영 실패(동시성). 다시 시도: " + barcode);
				throw new RuntimeException("STOCK_TXN_FAILED");
			}
		}


		result.put("response", "success");
		return result;
	}

	// 일반 출고 insert
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> insOutputAll(BarcodeVO vo) {
		Map<String, Object> result = new HashMap<>();
		final String date = vo.getDate();
		final String main = vo.getMain();
		final String source = vo.getSource();
		final String kind = vo.getKind();
		final List<String> bc = vo.getBarcode();
		String storage = vo.getStorage();
		String factory = vo.getFactory();
		String memo = vo.getMemo();
		String invoiceno = vo.getInvoiceno();
		System.out.println("memo @@@@ :"+memo);
		final String loginId = vo.getLoginid();
		final String userName = "username";

		String shipTo = vo.getShipTo();		// 출고처
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginId);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}

		// ✅ 팔레트-파트 전처리
		FilterResult fr = palletFilter.removeChildPartsIfParentPresent(bc);
		List<String> barcodes = fr.getFiltered();

		// 바코드 형식별로 분류
		// WMS 바코드: 쉼표 구분(LEAR/WMS) 또는 언더스코어 6파트(박스)
		// 외부 바코드: [)> 시작 (ADIENT/TRANSYS)
		List<String> wmsBarcodes = barcodes.stream()
			.filter(b -> !b.startsWith("[)>"))
			.collect(Collectors.toList());
		List<String> externalBarcodes = barcodes.stream()
			.filter(b -> b.startsWith("[)>"))
			.collect(Collectors.toList());

		// WMS 등록 바코드 검증 (쉼표/언더스코어 형식)
		if (!wmsBarcodes.isEmpty()) {
			Map<String, Object> notRegistered = barcodeValidator.notRegistered(wmsBarcodes);
			if (!notRegistered.isEmpty()) {
				return notRegistered;
			}

			Map<String, Object> wmsMap2 = new HashMap<>();
			wmsMap2.put("storage1", vo.getStorage());
			wmsMap2.put("storage", vo.getStorage());
			wmsMap2.put("factory", vo.getFactory());
			wmsMap2.put("list", wmsBarcodes);

			Map<String, Object> check91 = barcodeValidator.check91(wmsMap2);
			if (!check91.isEmpty()) {
				return check91;
			}

			Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(wmsBarcodes);
			if (!alreadyLoad.isEmpty()) {
				return alreadyLoad;
			}

			Map<String, Object> checkMap = new HashMap<>();
			checkMap.put("list", wmsBarcodes);
			checkMap.put("factory", factory);
			checkMap.put("storage", storage);
			Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
			if (!inputStorageCheck.isEmpty()) {
				return inputStorageCheck;
			}
		}

		// 외부 바코드 검증 (ADIENT/TRANSYS: [)> 시작)
		if (!externalBarcodes.isEmpty()) {
			Map<String, Object> alreadyOutbound = barcodeValidator.alreadyOutbound(externalBarcodes);
			if (!alreadyOutbound.isEmpty()) {
				return alreadyOutbound;
			}
		}


		for (String barcode : barcodes) {
			//바코드가 팔레트에 속해 있는지 확인
			String pbarcode = palletMapper.searchPallet(barcode);
			if (pbarcode != null && !pbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",pbarcode);
				palletMap.put("memo","LOAD");
				// 팔레트테이블에서 해당 바코드 N처리
				int barcodeN = palletMapper.bbarcodeN(palletMap);
				// 수량 가져오기
				Double doublePQty = palletMapper.palletQty(pbarcode);
				palletMap.put("pqty",doublePQty);

				//바코드 수량 가져오기
				double doubleqty = palletMapper.partQty(barcode);
				palletMap.put("date",date);
				palletMap.put("qty",doubleqty);
				palletMap.put("loginid",loginId);
				// 팔레트 위치로 적재 insert
				int partLocation = palletMapper.partLocation(palletMap);

				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateLQtyN(palletMap);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateLQty(palletMap);
				}
			}
			String custcode = "";
			if("LEAR".equalsIgnoreCase(shipTo)){
				custcode = "0002";
			}else if("ADNT".equalsIgnoreCase(shipTo) || "ADIENT".equalsIgnoreCase(shipTo)){
				custcode = "0006";
			}else if("TRANSYS_AL".equalsIgnoreCase(shipTo)){
				custcode = "0005";
			}else if("TRANSYS_IL".equalsIgnoreCase(shipTo)){
				custcode = "0004";
			}else{
				custcode = "A022";
			}

			Map<String, Object> m = new HashMap<>();
			m.put("factory", factory);
			m.put("storage", storage);
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
			//m.put("custname", custname);

			// 바코드 파싱 (인라인)
			if (barcode.split(",").length ==5 && barcode.endsWith("USA")) {
				String[] parts = barcode.split(",", -1);
				Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[0]);
				m.put("oitemcode", item.get("OITEMCODE"));
				m.put("itemcode", item.get("ITEMCODE"));
				m.put("bdate", parts[1]);
				m.put("seq", parts[2]);
				m.put("qty", resolveBarcodeQty(barcode));
				m.put("scmmex", parts[4]);
				m.put("type", "PART");

			} else if (barcode.split(",").length ==4 && barcode.endsWith("USA")) {
				PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
				if (pal == null) {
					result.put("response", "warning.pallet.infoNotFound");
					result.put("barcode", Arrays.asList(barcode));
					return result;
					//throw new RuntimeException("NO_PALLET_INFO");
				}
				String[] parts = barcode.split(",", -1);
				String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
				String seq = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7) : "";
				String scmmex = (parts.length >= 4) ? parts[3] : "";
				Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(pal.getItemcode());
				m.put("oitemcode", item.get("OITEMCODE"));
				m.put("itemcode", pal.getItemcode());
				m.put("qty", formatQty(pal.getQty()));
				m.put("bdate", bdate);
				m.put("seq", seq);
				m.put("scmmex", scmmex);
				m.put("type", "PALLET");


			} else if(barcode.split("_").length == 6){
				String[] parts = barcode.split("_", -1);
				Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
				m.put("oitemcode", item.get("OITEMCODE"));
				m.put("itemcode", item.get("ITEMCODE"));
				m.put("bdate", parts[2].substring(2)+parts[1]+parts[0]);
				m.put("seq", parts[5]);
				m.put("qty", resolveBarcodeQty(barcode));
				m.put("scmmex", "");
				m.put("type", "BOX");
			}else if (barcode.startsWith("[)>")) {
				String oitemcode, qty;
				if (barcode.contains("|")) {						// Adient: [)>|06|P품번|Q수량|...
					String[] parts = barcode.split("\\|");
					oitemcode = parts[2].substring(1);
					qty = parts[3].substring(1);
					m.put("type", "ADIENT");
				} else {										// Transys: [)>*06:serial:P품번:XQ수량:...
					String[] parts = barcode.split(":");
					oitemcode = parts[2].substring(1);
					qty = parts[3].substring(2);
					m.put("type", "TRANSYS");
				}
				String itemcode = purchaseMapper.getItemcode(oitemcode);
				log.info("260419 : "+barcode);
				m.put("itemcode", itemcode);
				m.put("oitemcode", oitemcode);
				m.put("qty", formatQty(qty));
				m.put("bdate", "");
				m.put("seq", "");
				m.put("scmmex", "WMSUSA");

			} else {
				result.put("response", "fail4");
				result.put("message", "지원되지 않는 바코드 형식: " + barcode);
				throw new RuntimeException("INVALID_BARCODE_FORMAT");
			}
			m.put("storage",vo.getStorage());
			m.put("dmemo","OUTPUT");
			// INSERT: 예외출고
			int insOutbound = 0;
			if("LOADEXCEPTION".equals(source)) {
				m.put("source2", vo.getSource2());
				insOutbound = purchaseMapper.insOutputException2(m);			// 예외출고 invoice, source2 insert
			}else {
				insOutbound = purchaseMapper.insOutput(m);
			}
			int affected = 0;
			// INSERT: 재고(로그/조정 내역) — 영향행 수 체크(동시성 방어)
			purchaseMapper.removeBarcode(m);
			m.put("laststatus", 50);
			if(barcode.split(",").length ==5 || barcode.split("_").length == 6) {
				affected = purchaseMapper.insertStockOutput(m);
				purchaseMapper.updateLaststatusPart(m);
			} else if ((barcode.endsWith("USA") || barcode.endsWith("MEX"))) {
				purchaseMapper.updateLaststatusPallet(m);
				List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
				for(int i = 0; i<bbarcode.size(); i++) {
					m.put("barcode", bbarcode.get(i));
					m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
					affected = purchaseMapper.insertStockOutput(m);
					purchaseMapper.updateLaststatusPart(m);
				}
			} else if (barcode.startsWith("[)>")){
				affected = purchaseMapper.insertStockOutput(m);
			}
			if (insOutbound == 0 || affected == 0) {
				result.put("response", "fail5");
				result.put("message", "재고 반영 실패(동시성). 다시 시도: " + barcode);
				throw new RuntimeException("STOCK_TXN_FAILED");
			}
		}


		result.put("response", "success");
		return result;
	}

	// 예외 출고 내역 - detail
	public Map<String, Object> searchexceptionOutputDetail(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchexceptionOutputDetail(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}


	public Map<String, Object> searchStockDetail(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<InventoryVO> list = purchaseMapper.searchStockDetail(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	public Map<String, Object> stockHistory(String barcode) {
		Map<String, Object> result = new HashMap<>();
		StockHistoryVO main = purchaseMapper.stockHistoryMain(barcode);
		List<StockHistoryVO> list = purchaseMapper.stockHistoryList(barcode);

		if (list == null || list.isEmpty()) {
			result.put("error", "warning.barcode.notregistered");
			return result;
		}

		if (main.getLocation() == null) {
			// 최종 상태 값 설정
			String status = main.getLaststatus();
			switch (status) {
				case "0":
					status = "DISCARD";
					break;
				case "1":
					status = "INCOMING WAIT";
					break;
				case "2":
					status = "SEMI PRODUCTION";
					break;
				case "3":
					status = "PRODUCTION";
					break;
				case "9":
					status = "INCOMING RETURN";
					break;
				case "30":
					status = "UNPACK MOVE";
					break;
				case "40":
					status = "WIP-INPUT";
					break;
				case "50":
					status = "DELETE";
					break;
				default:
					break;
			}
			main.setLaststatus(status);
		}
		
		// 공정 불출일 경우 작업장 나오도록 추가
		if ("WIP-INPUT".equals(main.getLaststatus())){
			String workshop = purchaseMapper.getWorkshop(barcode);
			main.setLocation(workshop);
		}
		
		result.put("main", main);
		result.put("list", list);
		return result;
	}
	
	public Map<String, Object> show_stockHistory_sangho(String custCode) {
		return purchaseMapper.show_stockHistory_sangho(custCode);
	}
	
	// 창고 이송 바코드 조회
	public Map<String, Object> searchWarehouse(List<String> barcode) {
		Map<String, Object> result = new HashMap<>();
		try {
			if (barcode.size() == 0) {
				return result;
			}
			List<BarcodeVO> list = purchaseMapper.searchWarehouse(barcode);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	public Map<String, Object> sameWarehouseCheck(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();

		List<String> barcodes = (List<String>) map.get("barcode");
		map.put("list", barcodes);
		List<String> list = purchaseMapper.sameWarehouseCheck(map); // 출고창고에 있는 바코드들이 들어옴
		barcodes.removeAll(list);
		if (barcodes.size() > 0) {
			result.put("list", list);
			result.put("response", "warning.barcode.storagecheck");
		} else {
			result.put("response", "ok");
		}

		return result;
	}

	// 창고 이동 선입선출 체크
	public Map<String, Object> checkFifo(Map<String, Object> map) {
		List<Map<String, Object>> rows = purchaseMapper.checkFifo(map);

		// itemcode 기준으로 그룹핑 { itemcode: [{location, indate, qty}, ...] }
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map<String, Object> row : rows) {
			String itemcode = (String) row.get("ITEMCODE");
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> list = (List<Map<String, Object>>) result.computeIfAbsent(itemcode, k -> new java.util.ArrayList<>());
			list.add(row);
		}
		return result;
	}

	// 창고 이동 처리
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> transferWarehouse(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();

		try {
			@SuppressWarnings("unchecked")
			List<String> list = (List<String>) map.get("barcode"); 
			
			// ✅ 팔레트-파트 전처리
		    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(list);
		    List<String> barcodes = fr.getFiltered();
		    
			String loginId = (String) map.get("loginid");;
			String mainkind = (String) map.get("mainkind");
			String kind = (String) map.get("kind");
			String storage1 = (String) map.get("storage1");
			String storage2 = (String) map.get("storage2");
			String factory = (String) map.get("factory");
			String date = (String)map.get("date");
			String source = (String) map.get("source");

			Map<String, Object> magamMap = new HashMap<String, Object>();
			magamMap.put("date", date);
			magamMap.put("loginid", loginId);
			// 마감 확인
			Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
			if (!checkClosedMonth.isEmpty()) {
				return checkClosedMonth; // 실패 시 리턴
			}

			// 등록된 바코드인지 체크
			Map<String, Object> notRegistered = barcodeValidator.notRegistered((List<String>) map.get("barcode"));
		    if (!notRegistered.isEmpty()) {
		        return notRegistered; // 실패 시 리턴
		    }

		    // 출고된 바코드인지 체크
		    Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad((List<String>) map.get("barcode"));
		    if (!alreadyLoad.isEmpty()) {
		    	return alreadyLoad; // 실패 시 리턴
		    }

			if("SENDING".equalsIgnoreCase(source)){
				// 이미 샌딩된 바코드인지 확인
				Map<String, Object> alreadyStockmoveSending = barcodeValidator.alreadyStockmoveSending((List<String>) map.get("barcode"));
				if (!alreadyStockmoveSending.isEmpty()) {
					return alreadyStockmoveSending; // 실패 시 리턴
				}
			}

			// RECEIVING아닐때 선택한 창고가 같은 창고인지 확인
			if(!"RECEIVING".equalsIgnoreCase(source)) {
				Map<String, Object> checkMap = new HashMap<String, Object>();
				checkMap.put("list", (List<String>) map.get("barcode"));
				checkMap.put("factory", factory);
				checkMap.put("storage", storage1);

				// PRODUCT 창고는 같은 창고 내 있는지 검사 안함
				if (!"PRODUCT".equals(storage1)){
					// 같은창고 내 있는지 확인
					Map<String, Object> storageCheck = barcodeValidator.storageCheck(checkMap);
					if (!storageCheck.isEmpty()) {
						return storageCheck; // 실패 시 리턴
					}
				}
				System.out.println("같은 창고 통과");
			}
			Map<String, Object> checkMap = new HashMap<String, Object>();
			checkMap.put("list", (List<String>) map.get("barcode"));
			checkMap.put("factory", factory);
			checkMap.put("storage", storage2);
			// 해당창고에 이미 있는지 확인
			Map<String, Object> storageCheckIn = barcodeValidator.storageCheckIn(checkMap);
			if (!storageCheckIn.isEmpty()) {
				return storageCheckIn; // 실패 시 리턴
			}

				for (String barcode : barcodes) {
				System.out.println("for문 시작");
				String labelType = "";
	    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
	    		String okyn = "Y";
	    		if ("Defective".equals(labelType)) {
	    			okyn = "N";
	    		}
				if("RECEIVING".equalsIgnoreCase(source)){
					String selectedStorage = purchaseMapper.selectStorage(barcode);
					storage1 = (selectedStorage != null && !selectedStorage.isEmpty()) ? selectedStorage : "PRODUCT";
				}
				//바코드가 팔레트에 속해 있는지 확인
				String pbarcode = palletMapper.searchPallet(barcode);
				if (pbarcode != null && !pbarcode.isEmpty()) {
					Map<String, Object> palletMap = new HashMap<String, Object>();
					palletMap.put("barcode",barcode);
					palletMap.put("pbarcode",pbarcode);
					palletMap.put("memo","STORAGE MOVE");
					// 팔레트테이블에서 해당 바코드 N처리
					int barcodeN = palletMapper.bbarcodeN(palletMap);
					// 수량 가져오기
					Double doublePQty = palletMapper.palletQty(pbarcode);
					palletMap.put("pqty",doublePQty);
					
					//바코드 수량 가져오기
					double doubleqty = palletMapper.partQty(barcode);
					palletMap.put("date",date);
					palletMap.put("qty",doubleqty);
					palletMap.put("loginid",loginId);
					// 팔레트 위치로 적재 insert
					int partLocation = palletMapper.partLocation(palletMap);
					
					// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
					if(doublePQty == 0) {
						palletMap.put("dmemo","PALLET 0");
						// 로케이션에 위치한 팔레트 수량이 0이면 폐기
						int pqty0 = palletMapper.updateLQtyN(palletMap);
					}else {
						//location 수량 업데이트
						int updateLQty = palletMapper.updateLQty(palletMap);
					}
				}

				String qty = "";
				if (barcode == null || barcode.isEmpty())
					continue;

				String[] parts = barcode.split(",", -1);
				boolean isPallet = parts.length !=5 && parts[0].startsWith("P");
				
				Map<String, Object> row = new HashMap<>(map);				
				row.put("mainkind", mainkind);
				row.put("barcode", barcode);
				row.put("kind", kind);
				row.put("source", kind);
				row.put("storage", storage1);
				row.put("storage1", storage1);
				row.put("storage2", storage2);
				row.put("factory", factory);
				row.put("factory2", factory);
				row.put("date", map.get("date"));
				row.put("loginid", loginId);
				row.put("memo", "-");
				row.put("rack", "-");
				row.put("module", "-");
				row.put("levelcode", "-");
				row.put("position", "-");
				row.put("okyn", okyn);
				row.put("movesource", (source != null && !source.isEmpty()) ? source : "RECEIVING");

				if (parts.length == 5) {
					System.out.println("for문 시작 : 파트바코드");
					Map<String,Object> item = purchaseMapper.getItemInfoSItemcode(parts[0].trim());
					qty = resolveBarcodeQty(barcode);
					row.put("itemcode", parts[0].trim());
					row.put("qty", qty);
					row.put("sdate", parts[1].trim());
					row.put("rack", item.get("CAR"));
					row.put("location", factory+"-"+storage2+"-"+item.get("CAR"));
					// throw new IllegalArgumentException("잘못된 바코드 형식: " + bc);
				} else if(barcode.split("_", -1).length == 6){		// 박스 바코드일때
					String[] parts2 = barcode.split("_", -1);
					Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts2[3]);
					row.put("itemcode", item.get("ITEMCODE"));
					row.put("sdate", parts2[2].substring(2) + parts2[1] + parts2[0]);
					row.put("qty", resolveBarcodeQty(barcode));
					row.put("location", factory+"-"+storage2+"-"+item.get("CAR"));
					row.put("rack", item.get("CAR"));
				} else {
					System.out.println("for문 시작 : 팔레트 바코드@@@@@@@@");
					PalletDetailVO pvo = purchaseMapper.palletInfo(barcode);
					if (pvo != null) { // 팔레트값이 없을때 fail3
						System.out.println("250926 팔레트 정보있음@@@");
						Map<String,Object> item = purchaseMapper.getItemInfoSItemcode(pvo.getItemcode());
						qty = formatQty(pvo.getQty());
						row.put("itemcode", pvo.getItemcode());
						row.put("qty", qty);
						row.put("sdate", pvo.getMdate());
						row.put("location", factory+"-"+storage2+"-"+item.get("CAR"));
						row.put("rack", item.get("CAR"));
					} else {
						System.out.println("250926 정보없음");
						result.put("response", "warning.pallet.infoNotFound");
		                result.put("barcode", Arrays.asList(barcode));
		                return result;
					}
				}
				System.out.println("개별처리 전");
				// 개별 처리
				purchaseMapper.transferWarehouseStockMove(row);

				// receiving일때만 location변경
				if(!"SENDING".equalsIgnoreCase(source)){
					// 이동한 창고로 location 저장
					if (isPallet) {
						List<String> children = purchaseMapper.selectpbBarcode(barcode);
						for (String child : children) {
							if (child == null || child.isEmpty()) continue;

							String[] pc = child.split(",", -1);
							Map<String, Object> rc = new HashMap<>(row); // 부모 공통값 복사
							rc.put("barcode", child);
							String cq = "";
							if (pc.length == 5) {
								rc.put("itemcode", pc[0].trim());
								cq = resolveBarcodeQty(child);
							} else {
								PalletDetailVO cvo = purchaseMapper.palletInfo(child);
								if (cvo == null) throw new IllegalStateException("자식 라벨 정보가 없습니다: " + child);
								cq = formatQty(cvo.getQty());
								rc.put("itemcode", cvo.getItemcode());
							}
							rc.put("qty", cq);

							rc.put("outqty", cq);
							rc.put("inqty", 0);
							purchaseMapper.transferWarehouseStock(rc); // out

							rc.put("outqty", 0);
							rc.put("inqty", cq);
							rc.put("source", kind);
							row.put("storage1", storage2);
							row.put("storage2", "");
							row.put("factory2", "");
							purchaseMapper.transferWarehouseStock(rc); // in

							rc.put("location", factory + "-" + storage2);
							rc.put("dmemo", "MOVE WAREHOUSE");
							purchaseMapper.removeBarcode(rc);		// HJ없어도 되지만 혹시 남아 잇을경우를 대비해서 삭제안함
							//purchaseMapper.ins
							//purchaseMapper.saveLocation(rc);	 // HJ location에서는 팔레트로 묶여있는경우 파트라벨을 따로 넣지 않음
						}

					}else {
						// 단품 OUT (from)
						row.put("outqty", qty);
						row.put("inqty", 0);
						purchaseMapper.transferWarehouseStock(row);

						// 단품 IN (to)
						row.put("outqty", 0);
						row.put("inqty", qty);
						row.put("storage1", storage2);
						row.put("storage2", "");
						purchaseMapper.transferWarehouseStock(row);
					}

					row.put("dmemo", "MOVE WAREHOUSE");
					purchaseMapper.removeBarcode(row);
					row.put("storage", storage2);	// 이동한 창고로 location 저장
					purchaseMapper.saveLocation(row);
					row.put("laststatus", 10);
					if(isPallet){	// 팔레트인 경우
						List<String> children = purchaseMapper.selectpbBarcode(barcode);
						for (String child : children) {
							row.put("barcode", child);
							purchaseMapper.updateLaststatusPart(row);
						}
						row.put("barcode", barcode);
						purchaseMapper.updateLaststatusPallet(row);
					}else{
						purchaseMapper.updateLaststatusPart(row);
					}
				}else{	//sending인 경우  laststatus값 변경
					row.put("laststatus", 15);
					if(isPallet){	// 팔레트인 경우
						List<String> children = purchaseMapper.selectpbBarcode(barcode);
						for (String child : children) {
							row.put("barcode", child);
							purchaseMapper.updateLaststatusPart(row);
						}
						row.put("barcode", barcode);
						purchaseMapper.updateLaststatusPallet(row);
					}else{
						purchaseMapper.updateLaststatusPart(row);
					}
				}

            }

			result.put("response", "success");
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}
	
	// 품질 이동 처리
	public Map<String, Object> qualityMove(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();

		try {
			@SuppressWarnings("unchecked")
			List<String> list = (List<String>) map.get("barcode"); 
			
			// ✅ 팔레트-파트 전처리
		    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(list);
		    List<String> barcodes = fr.getFiltered();
		    
			String loginId = (String) map.get("loginid");; // TODO: 세션/토큰에서 가져오기
			String mainkind = (String) map.get("mainkind");
			String kind = (String) map.get("kind");
			String storage1 = (String) map.get("storage1");
			String storage2 = (String) map.get("storage2");
			String factory = (String) map.get("factory");
			String date = (String)map.get("date");
			
			Map<String, Object> magamMap = new HashMap<String, Object>();
			magamMap.put("date", date);
			magamMap.put("loginid", loginId);
			// 마감 확인
			Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
			if (!checkClosedMonth.isEmpty()) {
				return checkClosedMonth; // 실패 시 리턴
			}
			
			// 언팩바코드 체크
			Map<String, Object> unpackCheck = barcodeValidator.unpackCheck((List<String>) map.get("barcode"));
			if (!unpackCheck.isEmpty()) {
				return unpackCheck; // 실패 시 리턴
			}
			// 등록된 바코드인지 체크
			Map<String, Object> notRegistered = barcodeValidator.notRegistered((List<String>) map.get("barcode"));
		    if (!notRegistered.isEmpty()) {
		        return notRegistered; // 실패 시 리턴
		    }
		    
		    // 불출된 바코드인지 체크
		    Map<String, Object> alreadyWIP = barcodeValidator.alreadyWIP((List<String>) map.get("barcode"));
		    if (!alreadyWIP.isEmpty()) {
		    	return alreadyWIP; // 실패 시 리턴
		    }
		    
		    // 출고된 바코드인지 체크
		    Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad((List<String>) map.get("barcode"));
		    if (!alreadyLoad.isEmpty()) {
		    	return alreadyLoad; // 실패 시 리턴
		    }
		    
		    Map<String, Object> checkMap = new HashMap<String, Object>();
		    checkMap.put("list",(List<String>) map.get("barcode"));
		    checkMap.put("factory",factory);
		    checkMap.put("storage",storage1);
		    if(storage2.equalsIgnoreCase("H/REST") || storage2.equalsIgnoreCase("WORKSHOP")) {
		    	// 불량바코드는 작업장으로 이송할수 없음
			    Map<String, Object> defectiveCheck = barcodeValidator.defectiveCheck(checkMap);
			    if (!defectiveCheck.isEmpty()) {
			    	return defectiveCheck; // 실패 시 리턴
			    }
		    }
		    if(storage1.equalsIgnoreCase("H/REST") || storage1.equalsIgnoreCase("WORKSHOP")) {
		    	// 작업장 재고인지 확인 작업장에 재고가 있는 바코드 반환
		    	Map<String, Object> workStockInfoCheck = barcodeValidator.workStockInfoCheck(checkMap);
			    if (!workStockInfoCheck.isEmpty()) {
			    	return workStockInfoCheck; // 실패 시 리턴
			    }
			    
		    }else {
		    	// 같은공장 내 있는지 확인
			    Map<String, Object> factoryCheck = barcodeValidator.factoryCheck(checkMap);
			    if (!factoryCheck.isEmpty()) {
			    	return factoryCheck; // 실패 시 리턴
			    }
			    // 같은창고 내 있는지 확인
			    Map<String, Object> storageCheck = barcodeValidator.storageCheck(checkMap);
			    if (!storageCheck.isEmpty()) {
			    	return storageCheck; // 실패 시 리턴
			    }
		    }
		    
		    System.out.println("같은 창고 통과");
		    
			for (String barcode : barcodes) {
				System.out.println("for문 시작");
				String labelType = "";
	    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
	    		String okyn = "Y";
	    		if ("Defective".equals(labelType)) {
	    			okyn = "N";
	    		}
				//바코드가 팔레트에 속해 있는지 확인
				String pbarcode = palletMapper.searchPallet(barcode);
				if (pbarcode != null && !pbarcode.isEmpty()) {
					Map<String, Object> palletMap = new HashMap<String, Object>();
					palletMap.put("barcode",barcode);
					palletMap.put("pbarcode",pbarcode);
					palletMap.put("memo","QUALITY MOVE");
					// 팔레트테이블에서 해당 바코드 N처리
					int barcodeN = palletMapper.bbarcodeN(palletMap);
					// 수량 가져오기
					Double doublePQty = palletMapper.palletQty(pbarcode);
					palletMap.put("pqty",doublePQty);
					
					//바코드 수량 가져오기
					double doubleqty = palletMapper.partQty(barcode);
					palletMap.put("date",date);
					palletMap.put("qty",doubleqty);
					palletMap.put("loginid",loginId);
					// 팔레트 위치로 적재 insert
					int partLocation = palletMapper.partLocation(palletMap);
					
					// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
					if(doublePQty == 0) {
						palletMap.put("dmemo","PALLET 0");
						// 로케이션에 위치한 팔레트 수량이 0이면 폐기
						int pqty0 = palletMapper.updateLQtyN(palletMap);
					}else {
						//location 수량 업데이트
						int updateLQty = palletMapper.updateLQty(palletMap);
					}
				}

				String qty = "";
				if (barcode == null || barcode.isEmpty())
					continue;

				String[] parts = barcode.split(",", -1);
				boolean isPallet = parts.length !=5 && parts[0].startsWith("P");
				
				Map<String, Object> row = new HashMap<>(map);				
				row.put("mainkind", mainkind);
				row.put("barcode", barcode);
				row.put("kind", kind);
				row.put("source", kind);
				row.put("storage", storage1);
				row.put("storage1", storage1);
				row.put("storage2", storage2);
				row.put("factory", factory);
				row.put("factory2", factory);
				row.put("date", map.get("date"));
				row.put("loginid", loginId);
				row.put("memo", "-");
				row.put("rack", "-");
				row.put("module", "-");
				row.put("levelcode", "-");
				row.put("position", "-");
				row.put("okyn", okyn);
				
				if (parts.length == 5) {System.out.println("for문 시작 : 파트바코드");
					qty = resolveBarcodeQty(barcode);
					row.put("itemcode", parts[0].trim());
					row.put("qty", qty);
					row.put("sdate", parts[1].trim());
					// throw new IllegalArgumentException("잘못된 바코드 형식: " + bc);
				} else {System.out.println("for문 시작 : 팔레트 바코드@@@@@@@@");
					PalletDetailVO pvo = purchaseMapper.palletInfo(barcode);
					if (pvo != null) { // 팔레트값이 없을때 fail3
						System.out.println("250926 팔레트 정보있음@@@");
						qty = formatQty(pvo.getQty());
						row.put("itemcode", pvo.getItemcode());
						row.put("qty", qty);
						row.put("sdate", pvo.getMdate());
					} else {
						System.out.println("250926 정보없음");
						result.put("response", "warning.pallet.infoNotFound");
		                result.put("barcode", Arrays.asList(barcode));
		                return result;
					}
				}
				System.out.println("개별처리 전");
				Map<String,Object> transferMap = new HashMap<String, Object>(row);
				
				if(storage1.equalsIgnoreCase("H/REST") || storage1.equalsIgnoreCase("WORKSHOP")) {
					// 품질창고 -> 작업장
					transferMap.put("source", "TOREDCAGE");
					transferMap.put("dmemo", "TOREDCAGE");
					transferMap.put("wstorage", storage1);
					transferMap.put("storage", storage2);
					purchaseMapper.insWorkmove(transferMap);
				}else if(storage2.equalsIgnoreCase("H/REST") || storage2.equalsIgnoreCase("WORKSHOP")) {
					// 작업장 -> 품질창고
					transferMap.put("source", "FROMREDCAGE");
					transferMap.put("dmemo", "FROMREDCAGE");
					transferMap.put("wstorage", storage2);
					transferMap.put("storage", storage1);
					purchaseMapper.insWorkmove(transferMap);
				}else{
					// 창고이송
					purchaseMapper.transferWarehouseStockMove(row);
				}
				
				row.put("location", factory + "-" + storage2);	
				row.put("dmemo", "MOVE WAREHOUSE");
				purchaseMapper.removeBarcode(row);
				purchaseMapper.removeWorkLocationBarcode(row);
				row.put("storage", storage2);	// 이동한 창고로 location 저장
				if(storage2.equalsIgnoreCase("H/REST") || storage2.equalsIgnoreCase("WORKSHOP")) {
					purchaseMapper.insWorkLocationBasic(row);
				}else {
					purchaseMapper.saveLocationOKYN(row);
				}
				
			}

			result.put("response", "success");
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 창고 이동 처리 - detail
	public Map<String, Object> searchWarehouseDetail(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchWarehouseDetail(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 창고 이동 처리 - summary
	public Map<String, Object> searchWarehouseSummary(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchWarehouseSummary(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 공장 이동 처리
	public Map<String, Object> transferFactory(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		
		try {
	        @SuppressWarnings("unchecked")
			List<String> list = (List<String>) map.get("barcode"); 
			
			// ✅ 팔레트-파트 전처리
		    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(list);
		    List<String> barcodes = fr.getFiltered();
	       
	        String loginId = (String) map.get("loginid");      // TODO: 세션/토큰에서 가져오기
	        
	        String factory = (String) map.get("factory"); 
	        String moveFactory = (String) map.get("moveFactory"); 
	        String kind = (String) map.get("kind");
	        String mainkind = (String) map.get("mainkind");
	        String storage = (String)map.get("storage");
	        String date = (String)map.get("date");
	        
	        Map<String, Object> magamMap = new HashMap<String, Object>();
			magamMap.put("date", date);
			magamMap.put("loginid", loginId);
			// 마감 확인
			Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
			if (!checkClosedMonth.isEmpty()) {
				return checkClosedMonth; // 실패 시 리턴
			}
			
			// 팔레트가 해체된 경우, 모든 메뉴에서 사용
//		    Map<String, Object> palletNCheck = barcodeValidator.palletNCheck((List<String>) map.get("barcode"));
//		    if (!palletNCheck.isEmpty()) {
//		    	return palletNCheck; // 실패 시 리턴
//		    }
	        // 언팩바코드 체크
			Map<String, Object> unpackCheck = barcodeValidator.unpackCheck((List<String>) map.get("barcode"));
			if (!unpackCheck.isEmpty()) {
				return unpackCheck; // 실패 시 리턴
			}
			// 등록된 바코드인지 체크
			Map<String, Object> notRegistered = barcodeValidator.notRegistered((List<String>) map.get("barcode"));
		    if (!notRegistered.isEmpty()) {
		        return notRegistered; // 실패 시 리턴
		    }
		    
		    // 불출된 바코드인지 체크
		    Map<String, Object> alreadyWIP = barcodeValidator.alreadyWIP((List<String>) map.get("barcode"));
		    if (!alreadyWIP.isEmpty()) {
		    	return alreadyWIP; // 실패 시 리턴
		    }
		    
		    // 출고된 바코드인지 체크
		    Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad((List<String>) map.get("barcode"));
		    if (!alreadyLoad.isEmpty()) {
		    	return alreadyLoad; // 실패 시 리턴
		    }
		    
		    Map<String, Object> checkMap = new HashMap<String, Object>();
		    checkMap.put("list",(List<String>) map.get("barcode"));
		    checkMap.put("factory",factory);
		    // 상태값 10~29사이인지 확인
		    Map<String, Object> factoryCheck = barcodeValidator.factoryCheck(checkMap);
		    if (!factoryCheck.isEmpty()) {
		    	return factoryCheck; // 실패 시 리턴
		    }
		    
		    // 다른공장에 있으면 공장이송 하라고 메시지
		    Map<String, Object> inputFactoryCheck = barcodeValidator.inputFactoryCheck(checkMap);
		    if (!inputFactoryCheck.isEmpty()) {
		    	return inputFactoryCheck; // 실패 시 리턴
		    }

		    Map<String, Object> sendingCheck = barcodeValidator.sendingCheck((List<String>) map.get("barcode"));
		    if (!sendingCheck.isEmpty()) {
		    	return sendingCheck; // 실패 시 리턴
		    }
		    
//		    String status = (String) map.get("status1");
//		    List<String> barcodes2 = (List<String>) map.get("barcode2");
//		    log.info("status : "+status);
//			if("confirm".equals(status)) {		// 팔레트 바코드 해체작업 
//				Set<String> palletList = new LinkedHashSet<>();
//				log.info("location pallet unbound start");
//				
//				// 1) 고유 팔레트만 수집
//			    Set<String> pallets = new LinkedHashSet<>();
//			    for (String childBarcode : barcodes2) {
//			        String pallet = purchaseMapper.searchPallet(childBarcode);
//			        if (pallet != null && !pallet.isEmpty()) {
//			            pallets.add(pallet);
//			        }
//			    }
//			    // 팔레트로 for문
//			    for (String pallet : pallets) {log.info("pallet unbound@@@@");
//					Map<String, Object> m = new HashMap<String, Object>();
//					
//					if(pallet == null || pallet.isEmpty()) {
//						
//					}else {
//						palletList.add(pallet);
//						m.put("dmemo", "FACTORY SEND Unbound");
//						String location0 = purchaseMapper.searchRoomcode(pallet);
//						if (location0 == null || location0.isEmpty()) {
//							m.put("dmemo", "FACTORY SEND NULL");						//250920 HJ 팔레트로 묶인 파트라벨을 적재 후 팔레트 해체될때 NULL이 되는 현상때문에 하드코딩 250923 현재 적재중인위치로이동
//							location0 = factory+"-"+storage;
//						}
//						m.put("barcode", pallet);
//						m.put("location", location0);
//						String[] parts = location0.split("-");
//						m.put("factory", parts.length > 0 ? parts[0] : "");
//						m.put("storage", parts.length > 1 ? parts[1] : "");
//						m.put("rack", parts.length > 2 ? parts[2] : "");
//						m.put("module", parts.length > 3 ? parts[3] : "");
//						m.put("levelcode", parts.length > 4 ? parts[4] : "");
//						m.put("position", parts.length > 5 ? parts[5] : "");
//						m.put("date",  map.get("date"));
//						m.put("loginid", loginId);
//						m.put("source", "FACTORY SEND UNBOUND");
//						// 기존 팔레트라벨 적재위치 정보로 파트라벨 적재
//						purchaseMapper.selectLocationSave(m);
//						Map<String, Object> map2 = new HashMap<String, Object>();
//						map2.put("barcode", pallet);
//						map2.put("dmemo", "FACTORY SEND");
//						purchaseMapper.removeBarcode(map2);		// 적재된 팔레트바코드 제거
//						// 팔레트 바코드 useyn = n
//						purchaseMapper.palletN(map2);				// 팔레트라벨 사용 N
//						
//					}
//				}
//				
//			}
	        for (String barcode : barcodes) {
	        	String labelType = "";
	    		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
	    		String okyn = "Y";
	    		if ("Defective".equals(labelType)) {
	    			okyn = "N";
	    		}
	        	//바코드가 팔레트에 속해 있는지 확인
				String pbarcode = palletMapper.searchPallet(barcode);
				if (pbarcode != null && !pbarcode.isEmpty()) {
					Map<String, Object> palletMap = new HashMap<String, Object>();
					palletMap.put("barcode",barcode);
					palletMap.put("pbarcode",pbarcode);
					palletMap.put("memo","FACTORY MOVE");
					// 팔레트테이블에서 해당 바코드 N처리
					int barcodeN = palletMapper.bbarcodeN(palletMap);
					// 수량 가져오기
					Double doublePQty = palletMapper.palletQty(pbarcode);
					palletMap.put("pqty",doublePQty);
					
					//바코드 수량 가져오기
					double doubleqty = palletMapper.partQty(barcode);
					palletMap.put("date",date);
					palletMap.put("qty",doubleqty);
					palletMap.put("loginid",loginId);
					// 팔레트 위치로 적재 insert
					int partLocation = palletMapper.partLocation(palletMap);
					
					// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
					if(doublePQty == 0) {
						palletMap.put("dmemo","PALLET 0");
						// 로케이션에 위치한 팔레트 수량이 0이면 폐기
						int pqty0 = palletMapper.updateLQtyN(palletMap);
					}else {
						//location 수량 업데이트
						int updateLQty = palletMapper.updateLQty(palletMap);
					}
				}

	        	
	        	String qty = "";
	            if (barcode == null || barcode.isEmpty()) continue;  
	            
	            String[] parts = barcode.split(",", -1);
	            boolean isPallet = parts.length !=5 && parts[0].startsWith("P");
	            
	            String storage2 = purchaseMapper.selectStorage(barcode);
	            if(storage2 == null || storage2.isEmpty()) {
	            	if("Saltillo".equals(factory)) {
	            		storage2 = "Material";
	            	}else {
	            		storage2 = "PRODUCT";
	            	}
	            }
	            
	            if (isPallet) {
	                int invalid = purchaseMapper.palletExceptionCheckStatus(barcode);
	                if (invalid > 0) {
	                	result.put("response", "fail3");
	                	result.put("message", "팔레트 내 허용되지 않는 상태가 포함되어 있습니다.");
	                	return result; 
	                }
	            } else {
	                int invalid = purchaseMapper.partExceptionCheckStatus(barcode);
	                if (invalid > 0) {
	                	result.put("response", "fail4");
	                	result.put("message", "이동 가능 상태(10,20)가 아닙니다.");
	                	return result; 
	                }
	            }
	            
	            Map<String, Object> row = new HashMap<>(map);          	            
	            row.put("loginid", loginId);	            
	            row.put("date", map.get("date"));
	            row.put("barcode", barcode);       
	            row.put("factory", factory);
	            row.put("moveFactory", moveFactory);
	            row.put("storage", storage2);
	            row.put("inqty", 0);
	            row.put("kind", kind);
	            row.put("main", mainkind);
	            row.put("memo", "-");
	            row.put("rack", "-");
	            row.put("module", "-");
	            row.put("levelcode", "-");
	            row.put("position", "-");
	            row.put("source", "SENDING");
	            row.put("factory",factory);
	            row.put("location",factory+"-"+storage2);
	            row.put("okyn",okyn);
	            
	            if (parts.length == 5) {
	            	qty = resolveBarcodeQty(barcode);
	            	row.put("itemcode", parts[0].trim());
	            	row.put("qty", qty);          
		            row.put("outqty", qty);
	            	row.put("sdate", parts[1].trim());
	                //throw new IllegalArgumentException("잘못된 바코드 형식: " + bc);
	            }else {
	            	PalletDetailVO pvo = purchaseMapper.palletInfo(barcode);
	            	qty = formatQty(pvo.getQty());
	            	row.put("itemcode", pvo.getItemcode());
	            	row.put("qty", qty);
		            row.put("outqty", qty);
	            	row.put("sdate", barcode.split(",")[0].substring(1, 7));
	            }	            

	            // 이동 로그
	            purchaseMapper.transferFactoryFactoryMove(row);	            
	            row.put("laststatus", 5);
	            // 파레트 라벨이면 파트 라벨 가져오기
	            if (isPallet) {
	            	List<String> children = purchaseMapper.selectpbBarcode(barcode);
	                for (String child : children) {
	                	if (child == null || child.isEmpty()) continue;

	                    String[] pc = child.split(",", -1);
	                    Map<String, Object> rc = new HashMap<>(row); // 부모 공통값 복사
	                    rc.put("barcode", child);

	                    if (pc.length == 5) {
	                        rc.put("itemcode", pc[0].trim());
	                        String cq = resolveBarcodeQty(child);
	                        rc.put("qty", cq);
	                        rc.put("outqty", cq);
	                    } else {
	                        PalletDetailVO cvo = purchaseMapper.palletInfo(child);
	                        if (cvo == null) throw new IllegalStateException("자식 라벨 정보가 없습니다: " + child);
	                        String cq = formatQty(cvo.getQty());
	                        rc.put("itemcode", cvo.getItemcode());
	                        rc.put("qty", cq);
	                        rc.put("outqty", cq);
	                    }
	                    rc.put("dmemo", "MOVE FACTORY");
	                    purchaseMapper.transferFactoryStock(rc);       // 자식 OUT
	                    purchaseMapper.removeBarcode(rc);              // 자식 라벨 정리(사용종료 등)
	                    
	                    rc.put("laststatus", 5);
	                    purchaseMapper.updateLaststatusPart(rc);
	                }
	                row.put("dmemo", "MOVE FACTORY");
	                // 팔레트 라벨도 정리
	                purchaseMapper.updateLaststatusPallet(row);
	                purchaseMapper.removeBarcode(row);
	                purchaseMapper.basicLocation(row);
	            } else {
	            	row.put("dmemo", "MOVE FACTORY");
		            // 재고 입력
		            purchaseMapper.transferFactoryStock(row);		
	                purchaseMapper.removeBarcode(row);
	                purchaseMapper.basicLocation(row);
                    purchaseMapper.updateLaststatusPart(row);
	            }
	        }

	        result.put("response", "success");
	    } catch (Exception e) {
	        result.put("success", false);
	        result.put("message", e.getMessage());
	    }
	    return result;
	}	
	
	// 공장 이동 처리 - detail
	public Map<String, Object> searchFactoryDetail(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchFactoryDetail(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 공장 이동 처리 - summary
	public Map<String, Object> searchFactorySummary(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<Map<String, Object>> list = purchaseMapper.searchFactorySummary(map);
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// 공장 이동 처리 - complete
	public Map<String, Object> completeFactoryMove(BarcodeVO request) {
		Map<String, Object> result = new HashMap<>();
		List<String> list = request.getBarcode();
		String date = request.getDate();
		String factory = request.getFactory();
		String storage = request.getStorage();
		String status = request.getStatus1();
		String loginid = request.getLoginid();
		List<String> barcodes2 = request.getBarcode2();
		String mainkind = "MOVE";
		String kind = "FACTORYRECEIVING";
		String movefactory = "";
		
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}
		
		// ✅ 팔레트-파트 전처리
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(list);
	    List<String> barcodes = fr.getFiltered();
		
	    System.out.println("date@@@ : "+date);
	    // // 팔레트가 해체된 경우, 모든 메뉴에서 사용
//	    Map<String, Object> palletNCheck = barcodeValidator.palletNCheck(request.getBarcode());
//	    if (!palletNCheck.isEmpty()) {
//	    	return palletNCheck; // 실패 시 리턴
//	    }
	    // 언팩바코드 체크
 		Map<String, Object> unpackCheck = barcodeValidator.unpackCheck(request.getBarcode());
 		if (!unpackCheck.isEmpty()) {
 			return unpackCheck; // 실패 시 리턴
 		}
 		// 등록된 바코드인지 체크
 		Map<String, Object> notRegistered = barcodeValidator.notRegistered(request.getBarcode());
 	    if (!notRegistered.isEmpty()) {
 	        return notRegistered; // 실패 시 리턴
 	    }
 	    
 	    // 공장이송 - 받은내역확인
 	   Map<String, Object> receivingCheck = barcodeValidator.receivingCheck((List<String>) request.getBarcode());
	    if (!receivingCheck.isEmpty()) {
	    	return receivingCheck; // 실패 시 리턴
	    }
	    // 이미 해당 창고에 있는지 체크
	    Map <String, Object> checkMap = new HashMap<String, Object>();
	    checkMap.put("list", (List<String>)request.getBarcode());
	    checkMap.put("factory", factory);
	    checkMap.put("storage", storage);
	    Map<String, Object> storageCheckIn = barcodeValidator.storageCheckIn(checkMap);
	    if (!storageCheckIn.isEmpty()) {
	    	return storageCheckIn; // 실패 시 리턴
	    }
	    
	    // 해당공장에 있는지 체크
	    Map<String, Object> factoryCheckIn = barcodeValidator.factoryCheckIn(checkMap);
	    if (!factoryCheckIn.isEmpty()) {
	    	return factoryCheckIn; // 실패 시 리턴
	    }
	    
	    // 모두 존재하는지 확인 (부분 존재 방지)
	    int inserted = 0;
//	    int existCnt = purchaseMapper.factoryMoveOk(barcodes); 
//	    if (existCnt < 1) {
//	        result.put("response", "fail7");
//	        result.put("message", "FactoryMove 테이블에 없는 바코드가 포함되어 있습니다.");
//	        return result;
//	    }
    
	    log.info("status : "+status);
		if("confirm".equals(status)) {		// 팔레트 바코드 해체작업 
			Set<String> palletList = new LinkedHashSet<>();
			log.info("receiving pallet unbound start");
			
			// 1) 고유 팔레트만 수집
		    Set<String> pallets = new LinkedHashSet<>();
		    for (String childBarcode : barcodes2) {
		        String pallet = purchaseMapper.searchPallet(childBarcode);
		        if (pallet != null && !pallet.isEmpty()) {
		            pallets.add(pallet);
		        }
		    }
		    // 팔레트로 for문
		    for (String pallet : pallets) {log.info("pallet unbound@@@@");
				Map<String, Object> m = new HashMap<String, Object>();
				
				if(pallet == null || pallet.isEmpty()) {
					
				}else {
					palletList.add(pallet);
					String location = purchaseMapper.searchRoomcodeY(pallet);		//251104 팔레트 LOCATION을 N으로 안해주고 있어서 추가
					if (location == null || location.isEmpty()) {
						m.put("dmemo","FAC RECEIVE LOC NULL");
						// 바코드 재고실사, 로케이션 재고실사, 리시빙에서 사용
						// 251206 팔레트 purchaseMapper.searchRoomcode(pallet) -> purchaseMapper.searchRoomcodeY(pallet)로 변경
						// 그래서 null인 경우 팔레트테이블만 N으로 업데이트
						// 하단에서 스캔한 파트라벨은 예외입고 에정
						// 스캔안한 파트라벨들은 재고로 안잡히지만 추후 아무것도 안한상태면 예외입고 불출이나 출고했으면 재고실사로 잡아야함
						// 팔레트를 N으로 업데이트
						m.put("barcode", pallet);
						purchaseMapper.palletN(m);
					}else {
						m.put("barcode", pallet);
						m.put("location", location);
						String[] parts = location.split("-");
						m.put("factory", parts.length > 0 ? parts[0] : "");
						m.put("storage", parts.length > 1 ? parts[1] : "");
						m.put("rack", parts.length > 2 ? parts[2] : "");
						m.put("module", parts.length > 3 ? parts[3] : "");
						m.put("levelcode", parts.length > 4 ? parts[4] : "");
						m.put("position", parts.length > 5 ? parts[5] : "");
						m.put("date", date);
						m.put("loginid", loginid);
						m.put("source", "RECEIVING UNBOUND");
						// 기존 팔레트라벨 적재위치 정보로 파트라벨 적재
						purchaseMapper.selectLocationSave(m);

					}
					Map<String, Object> map = new HashMap<String, Object>();
					map.put("barcode", pallet);
					map.put("dmemo", "RECEIVING UNBOUND");
					purchaseMapper.removeBarcode(map);		// 적재된 팔레트바코드 제거

					// 팔레트 바코드 useyn = n
					purchaseMapper.palletN(map);				// 팔레트라벨 사용 N
					
				}
			}
			
		}
	    for(String barcode :barcodes) {
	    	String labelType = "";
			labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
			String okyn = "Y";
			if ("Defective".equals(labelType)) {
				okyn = "N";
			}
	    	Map<String, Object> map = new HashMap<String, Object>();
	    	String[] parts = barcode.split(",");
	    	boolean isPallet = parts.length !=5;
	    	map.put("loginid",loginid);
	    	map.put("barcode",barcode);
	    	map.put("factory",factory);
	    	map.put("storage",storage);
	    	map.put("location",factory+"-"+storage);
	    	map.put("date",date);
	    	map.put("kind",kind);
	    	map.put("mainkind",mainkind);
	    	map.put("source","RECEIVING");
	    	if (parts.length == 5) {
            	map.put("itemcode", parts[0].trim());
            	map.put("qty", resolveBarcodeQty(barcode));          
            	map.put("inqty", resolveBarcodeQty(barcode));
            	map.put("type","box");
                //throw new IllegalArgumentException("잘못된 바코드 형식: " + bc);
            }else {
            	PalletDetailVO pvo = purchaseMapper.palletInfo(barcode);
            	map.put("itemcode", pvo.getItemcode());
            	map.put("qty", formatQty(pvo.getQty()));
            	map.put("inqty",formatQty(pvo.getQty()));
            	map.put("type","pallet");
            }
	    	// 에외입고
	    	map.put("dmemo","FACTORY MOVE");
		    map.put("memo","FACTORY RECEIVE");
		    map.put("rack","");
		    map.put("module","");
		    map.put("levelcode","");
		    map.put("position","");
		    String factory2 = "";
		    String storage2 = "";
		    if("Saltillo".equals(factory)) {
		    	factory2 = "PUEBLA";
		    	storage2 = "PRODUCT";
		    }else{
		    	factory2 = "Saltillo";
		    	storage2 = "Fabric";
		    }
		    
		    
		    map.put("factory2", factory2);
		    map.put("storage2", storage2);
		    map.put("location2", factory2+"-"+storage2);
		    map.put("okyn", okyn);
		    //상대 공장에 해당 바코드가 없으면 예외입고 작업
		    // 상대공장에 해당 바코드 없으면 location에 insert작업 아니면 ok값이 0
		    int ok = purchaseMapper.insertExceptionReceiving(map);
		    if(ok>0) {
		    	// 예외입고 insert
		    	int insInbound = purchaseMapper.insInboundExceptionReceiving(map);
		    }
		    // 바코드 조회해서 공장, 창고 위치 가져옴
		    PalletDetailVO pvo = purchaseMapper.locationInfo(barcode);
		    if (pvo == null || pvo.getStorage() == null) {
		    	
		    }else {
		    	factory2 = pvo.getFactory();
		    	storage2 = pvo.getStorage();
		    }
		    map.put("factory2", factory2);
		    map.put("storage2", storage2);
	    	// 전부 존재 → insert 실행
		    inserted = purchaseMapper.completeFactoryMove(map);
		    map.put("laststatus", "10");
		    if (isPallet) {
            	List<String> children = purchaseMapper.selectpbBarcode(barcode);
                for (String child : children) {
                	if (child == null || child.isEmpty()) continue;

                    String[] pc = child.split(",", -1);
                    Map<String, Object> rc = new HashMap<>(map); // 부모 공통값 복사
                    rc.put("barcode", child);
                    rc.put("factory", factory);
                    rc.put("storage", storage);
                    rc.put("date", date);
                    rc.put("kind", kind);
                    rc.put("mainkind", mainkind);
                    rc.put("loginid", loginid);

                    if (pc.length == 5) {
                        rc.put("itemcode", pc[0].trim());
                        String cq = resolveBarcodeQty(child);
                        rc.put("qty", cq);
                        rc.put("inqty", cq);
                    } else {
                        PalletDetailVO cvo = purchaseMapper.palletInfo(child);
                        if (cvo == null) throw new IllegalStateException("자식 라벨 정보가 없습니다: " + child);
                        String cq = formatQty(cvo.getQty());
                        rc.put("itemcode", cvo.getItemcode());
                        rc.put("qty", cq);
                        rc.put("inqty", cq);
                    }
                    
                    rc.put("laststatus", "10");
                    purchaseMapper.factoryReceivingStock(rc);
                    purchaseMapper.updateLaststatusPart(rc);
                }
                purchaseMapper.updateLaststatusPallet(map);
            } else {
                
            	purchaseMapper.factoryReceivingStock(map);
                purchaseMapper.updateLaststatusPart(map);
            }
		    
		    
		    purchaseMapper.removeBarcode(map);
		    
            purchaseMapper.saveLocation(map);
	    }
	    result.put("response", "success");
	    result.put("updated", inserted);
	    return result;
	}

	// 로케이션 언로드
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> locationUnload(Map<String, Object> param) {
		System.out.println(param);
		String factory = (String) param.get("factory");
		String location1 = (String) param.get("location1");
		String storage = location1.split("-")[1];
		
		String basicLocation = factory+"-"+storage;
		Map<String, Object> map = new HashMap<String, Object>();
		LocalDateTime now = LocalDateTime.now();
		String barcode = (String) param.get("barcode");
		String labelType = "";
		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
		String okyn = "Y";
		if ("Defective".equals(labelType)) {
			okyn = "N";
		}
		String dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		if (barcode.split(",").length == 5) {
			map.put("itemcode", barcode.split(",")[0]);
			map.put("qty", resolveBarcodeQty(barcode));
		} else {
			PalletDetailVO palletInfo = purchaseMapper.palletInfoUnload(barcode);
			map.put("itemcode", palletInfo.getItemcode());
			map.put("qty", palletInfo.getQty());
		}
		System.out.println("loginid" + param.get("loginid"));
		map.put("loginid", param.get("loginid"));
		map.put("barcode", param.get("barcode"));
		map.put("location", basicLocation);
		map.put("source", "UNLOAD");
		map.put("date", dateStr);
		map.put("factory", factory);
		map.put("storage", storage);
		map.put("rack", "");
		map.put("module", "");
		map.put("levelcode", "");
		map.put("position", "");
		map.put("memo", "-");
		map.put("dmemo", "UNLOAD");
		map.put("okyn", okyn);
		int removed = purchaseMapper.removeBarcode(map); // 기존바코드 useyn= n
		int saved = purchaseMapper.saveLocation(map); // 적재

		map.put("location1", param.get("location1"));
		map.put("kind", "UNLOAD");
		map.put("main", "MOVE");
		// stock table관련
		int stocked = purchaseMapper.stockInsertUnload(map);

		Map<String, Object> result = new HashMap<String, Object>();
		// 최소 보장 조건 체크 (정책에 맞게 조정)
		if (saved < 1 || stocked < 1) {
			TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); // ← 수동 롤백 표시
			result.put("response", "fail");
			result.put("message", "DB write failed: saved=" + saved + ", stocked=" + stocked);
			return result; // HTTP 200
		}
		result.put("response", "ok");

		return result;
	}
	
	// 로케이션 삭제 - 예외출고기능
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> locationDeleteExload(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<String, Object>();
		String location = (String) map.get("location");
		String locationParts[] = location.split("-");
		String  barcode =  (String) map.get("barcode");
    	Map<String,Object> m = new HashMap<String, Object>();
    	LocalDate today = LocalDate.now();
        String date = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        m.put("date", date);
    	m.put("barcode", barcode);
    	m.put("loginid", map.get("loginid"));
    	m.put("source", "LOADEXCEPTION");		// 츨고테이블
    	m.put("main", "OUT");			// stock테이블
    	m.put("kind", "LOADEXCEPTION");			// stock테이블
    	m.put("storage", locationParts[1]);
    	m.put("factory", locationParts[0]);
    	m.put("location", location);
    	m.put("memo", "LOCATION DELETE");
        // 바코드 파싱 (인라인)
        if (barcode.split(",").length == 5) {		// 파트라벨바코드
            String[] parts = barcode.split(",");
            m.put("itemcode", parts[0]);
            m.put("bdate", parts[1]);
            m.put("seq", parts[2]);
            m.put("qty", resolveBarcodeQty(barcode));
            m.put("scmmex", parts[4]);
            m.put("type", "box");

        } else if (barcode.length() == 12 && barcode.startsWith("P")) {		// 12자리 팔레트 바코드
            PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
            if (pal == null) {
            	result.put("response", "warning.pallet.infoNotFound");
                result.put("barcode", Arrays.asList(barcode));
                return result;
                //throw new RuntimeException("NO_PALLET_INFO");
            }else {
            	m.put("itemcode", pal.getItemcode());
	            m.put("qty", formatQty(pal.getQty()));
	            m.put("bdate", "");
	            m.put("seq", "");
	            m.put("scmmex", "");
	            m.put("type", "pallet");
            }
            
        } else if (barcode.startsWith("P") && barcode.endsWith("MEX")) {				// 새로운 팔레트 라벨 바코드
            PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
            if (pal == null) {
                result.put("response", "warning.pallet.infoNotFound");
                result.put("barcode", Arrays.asList(barcode));
                return result;
                //throw new RuntimeException("NO_PALLET_INFO");
            }else {
            	String[] parts = barcode.split(",", -1);
	            String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
	            String seq   = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7)   : "";
	            String scmmex = (parts.length >= 4) ? parts[3] : "";

	            m.put("itemcode", pal.getItemcode());
	            m.put("qty", formatQty(pal.getQty()));
	            m.put("bdate", bdate);
	            m.put("seq", seq);
	            m.put("scmmex", scmmex);
	            m.put("type", "pallet");
            }
        } else {
            result.put("response", "fail4");
            result.put("message", "지원되지 않는 바코드 형식: " + barcode);
            throw new RuntimeException("INVALID_BARCODE_FORMAT");
        }
        String labelType = "";
		labelType = purchaseMapper.barcodeLabelType(barcode); // 바코드 타입 가져오기 양불판정
		String okyn = "Y";
		if ("Defective".equals(labelType)) {
			okyn = "N";
		}
        m.put("source2", "LOCATION DELETE");
        m.put("okyn", okyn);
        // INSERT: 예외출고
        int insOutbound = purchaseMapper.insOutboundException(m);
        int affected = 0;
        m.put("dmemo", "LOCATION DELETE");
        // INSERT: 재고(로그/조정 내역) — 영향행 수 체크(동시성 방어)
        purchaseMapper.removeBarcode(m);
        m.put("laststatus", 50);
        if(barcode.split(",").length ==5) {
        	affected = purchaseMapper.insertStockOutput(m);
        	purchaseMapper.updateLaststatusPart(m);
        }else {
        	purchaseMapper.updateLaststatusPallet(m);
        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
        	for(int i = 0; i<bbarcode.size(); i++) {
        		m.put("barcode", bbarcode.get(i));
        		m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
        		affected = purchaseMapper.insertStockOutput(m);
        		purchaseMapper.updateLaststatusPart(m);
        	}
        }
        if (insOutbound == 0 || affected == 0) {
            result.put("response", "fail5");
            result.put("message", "재고 반영 실패(동시성). 다시 시도: " + barcode);
            throw new RuntimeException("STOCK_TXN_FAILED");
        }
		result.put("response", "ok");
		
		return result;
	}
	
	// 팔레트라벨로 묶여있는지 확인
	public Map<String, Object> barcodeTypeCheck(BarcodeVO request) {
		Map<String, Object> result = new HashMap<String, Object>();
		List<String> partList = new ArrayList<String>();
		// ✅ 팔레트-파트 전처리
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(request.getBarcode());
	    List<String> barcodes = fr.getFiltered();

		for(String bc : barcodes) {
			if(bc.split(",").length == 5) {
				partList.add(bc);
			}
		}
		if(!partList.isEmpty()) {
			List<String> partListPallet = purchaseMapper.palletBarcodeCheck(partList);		// 파트라벨 리스트
			if(!partListPallet.isEmpty()) {
				result.put("list",partListPallet);
				result.put("response","confirm");
			}else {
				result.put("response","ok");	
			}
			
		}else {
			result.put("response","ok");	
		}
		return result;
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

	public List<String> incomingSanghoLocal() {
		return purchaseMapper.incomingSanghoLocal();
	}
	
	public List<String> incomingSanghoCkd() {
		return purchaseMapper.incomingSanghoCkd();
	}
	
	public List<String> incomingSanghoException() {
		return purchaseMapper.incomingSanghoException();
	}
	
	// 출고 반품
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String,Object> updateExceptionCheckStatus(Map<String, Object> param) {
		Map<String, Object> resultMap = new HashMap<String,Object>(); 
		int insertCount = 0;
		String loginid = (String) param.get("loginid");
		String storage = (String) param.get("storage");
		String factory = (String) param.get("factory");
		String date = (String)param.get("date");
		String source = (String)param.get("source");
		String main = (String)param.get("main");
		String kind = (String)param.get("kind");
		
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}
		
		List<String> barcodeList = (List<String>)param.get("barcode");

//		// 등록된 바코드인지 체크
//		Map<String, Object> notRegistered = barcodeValidator.notRegistered((List<String>)param.get("barcode"));
//	    if (!notRegistered.isEmpty()) {
//	        return notRegistered; // 실패 시 리턴
//	    }
	    
	    // 이미 해당 창고에 있는지 체크
	    Map <String, Object> checkMap = new HashMap<String, Object>();
	    checkMap.put("list", (List<String>) param.get("barcode"));
	    checkMap.put("factory", factory);
	    checkMap.put("storage", storage);
//	    Map<String, Object> storageCheckIn = barcodeValidator.storageCheckIn(checkMap);
//	    if (!storageCheckIn.isEmpty()) {
//	    	return storageCheckIn; // 실패 시 리턴
//	    }
//
//	    // 출고반품시 다른 창고에 있으면 창고이동 하라고 메시지
//	    Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
//	    if (!inputStorageCheck.isEmpty()) {
//	    	return inputStorageCheck; // 실패 시 리턴
//	    }
//
//	    // 출고 반품가능한지 10~29 사이의 바코드는 불가능 return
//	    Map<String, Object> notStorageCheck = barcodeValidator.notStorageCheck((List<String>)param.get("barcode"));
//	    if (!notStorageCheck.isEmpty()) {
//			return notStorageCheck; // 실패 시 리턴
//		}
		
		// ✅ 팔레트-파트 전처리: 팔레트와 함께 들어온 파트는 제외
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(barcodeList);

	    // ✅ 전처리된 목록으로 교체
	    List<String> barcodes = fr.getFiltered();
	    for(String barcode: barcodes) {
	    	//바코드가 팔레트에 속해 있는지 확인
			String pbarcode = palletMapper.searchPallet(barcode);
			if (pbarcode != null && !pbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",pbarcode);
				palletMap.put("memo","LOAD RETURN");
				// 팔레트테이블에서 해당 바코드 N처리
				int barcodeN = palletMapper.bbarcodeN(palletMap);
				// 수량 가져오기
				Double doublePQty = palletMapper.palletQty(pbarcode);
				palletMap.put("pqty",doublePQty);
				
				//바코드 수량 가져오기
				double doubleqty = palletMapper.partQty(barcode);
				palletMap.put("date",date);
				palletMap.put("qty",doubleqty);
				palletMap.put("loginid",loginid);
				// 팔레트 위치로 적재 insert
				int partLocation = palletMapper.partLocation(palletMap);
				
				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateLQtyN(palletMap);
					System.out.println("LOAD RETURN update pqty0 :"+pqty0);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateLQty(palletMap);
					System.out.println("LOAD RETURN update updateLQty :"+updateLQty);
				}
			}

	    	
	    	Map<String, Object> m = new HashMap<>();
			m.put("barcode", barcode);
			m.put("loginid", loginid);
			m.put("source", source);
			m.put("main", main);
			m.put("kind", kind);
			m.put("location",factory+"-"+storage);
			m.put("storage",storage);
			m.put("factory",factory);
			m.put("date",date);
	        // 바코드 파싱 (인라인)
	        if (barcode.split(",").length ==5) {
	            String[] parts = barcode.split(",", -1);
				Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(parts[0]);
				m.put("oitemcode", item.get("OITEMCODE"));
	            m.put("itemcode", parts[0]);
	            m.put("bdate", parts[1]);
	            m.put("seq", parts[2]);
	            m.put("qty", resolveBarcodeQty(barcode));
	            m.put("scmmex", parts[4]);
	            m.put("type", "box");

			} else if (barcode.split(",").length == 4 &&  barcode.endsWith("USA")) {
				PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
				if (pal == null) {
					resultMap.put("response", "warning.pallet.infoNotFound");
					resultMap.put("barcode",Arrays.asList(barcode));
					return resultMap;
					//throw new RuntimeException("NO_PALLET_INFO");
				}
				String[] parts = barcode.split(",", -1);
				String bdate = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(1, 7) : "";
				String seq = (parts.length >= 1 && parts[0].length() >= 8) ? parts[0].substring(7) : "";
				String scmmex = (parts.length >= 4) ? parts[3] : "";
				Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(pal.getItemcode());
				m.put("oitemcode", item.get("OITEMCODE"));
	            m.put("itemcode", pal.getItemcode());
	            m.put("qty", formatQty(pal.getQty()));
	            m.put("bdate", bdate);
	            m.put("seq", seq);
	            m.put("scmmex", scmmex);
	            m.put("type", "pallet");
			} else if(barcode.split("_", -1).length == 6){		// 박스 바코드일때
				String[] parts = barcode.split("_", -1);
				Map<String, Object> item = purchaseMapper.getItemInfoSpec(parts[3]);
				m.put("itemcode", item.get("ITEMCODE"));
				m.put("oitemcode",item.get("OITEMCODE"));
				m.put("bdate", parts[2].substring(2) + parts[1] + parts[0]);
				m.put("seq", (parts != null && parts.length > 5) ? parts[5] : "");
				m.put("qty", resolveBarcodeQty(barcode));
				m.put("scmmex", "");
				m.put("type", "");

			} else {
				resultMap.put("response", "warning.barcode.invalid");
				resultMap.put("barcode",Arrays.asList(barcode));
				throw new RuntimeException("INVALID_BARCODE_FORMAT");
			}
	        m.put("dmemo","LOAD RETURN");
	        // INSERT: 출고반품
	        int insOutbound = purchaseMapper.insOutputReturn(m);
	        // INSERT: 재고(로그/조정 내역) — 영향행 수 체크(동시성 방어)
			Map<String, Object> item = purchaseMapper.getItemInfoSItemcode(barcode.split(",")[0]);
			m.put("rack", item.get("CAR"));
			m.put("location", factory+"-"+storage+"-"+item.get("CAR"));
	        purchaseMapper.basicLocation(m);		// 미적재로 전재
	        m.put("laststatus", 10);

			/*if(barcode.split("_", -1).length == 6 || barcode.split(",").length == 5) { 260422 출고반품은 파트만 가능
	        	purchaseMapper.insertStockOutputReturn(m);
	        	purchaseMapper.updateLaststatusPart(m);
	        }else {
	        	purchaseMapper.updateLaststatusPallet(m);
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		m.put("barcode", bbarcode.get(i));
	        		m.put("qty", resolveBarcodeQty(bbarcode.get(i)));
	        		 purchaseMapper.insertStockOutputReturn(m);
	        		purchaseMapper.updateLaststatusPart(m);
	        	}
	        }*/
	    	
	    }

		resultMap.put("response", "success");
		return resultMap;
	}
	

	public List<String> loadSangho() {
		return purchaseMapper.loadSangho();
	}

	public Map<String, Object> unloadedList(Map<String, String> param) {
		Map<String, Object> result = new HashMap<>();
		try {
			 // 데이터 조회
		    List<Map<String, Object>> list = purchaseMapper.unloadedList(param);
		    
		    // 전체 개수 조회
//		    int totalCount = purchaseMapper.unloadedListCount();
		    
		    result.put("list", list);
		    result.put("total", list.size());
		    result.put("success", true);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}
	
	// 파트라벨중에 팔레트라벨로 이미 입고 된게 있는지 확인
	public Map<String, Object> palletCheckInbound(BarcodeVO vo, HttpServletRequest request2) {
		Map<String, Object> result = new HashMap<String, Object>();
		List<String> barcodes = vo.getBarcode();
		List<String> partList = new ArrayList<String>();
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("list", partList);
		map.put("factory", vo.getFactory());
		for(String bc : barcodes) {
			if(bc.split(",").length == 5) {
				partList.add(bc);
			}
		}
		if(!partList.isEmpty()) {
			List<String> partListPallet = purchaseMapper.palletInboundCheck(map);		// 파트라벨 리스트
			if(!partListPallet.isEmpty()) {
				result.put("barcode",partListPallet);
				result.put("response","warning.barcode.duplicate");
			}else {
				result.put("response","ok");	
			}
			
		}else {
			result.put("response","ok");	
		}
		return result;
	}

	public Map<String, List<ItemLocationVO>> wipFifo(BarcodeVO vo) {
		Map<String, Object> map = new HashMap<String, Object>();
		List<String> barcodes = vo.getBarcode();
		map.put("factory", vo.getFactory());
		map.put("list", barcodes);
		//List<String> itemcodeList = purchaseMapper.itemcodeList(barcodes);		// 아이템코드 가져오기 중복제거
		//map.put("itemcodes", itemcodeList);
		List<ItemLocationVO> rows = purchaseMapper.wipFifo(map);
		 Map<String, List<ItemLocationVO>> grouped = new LinkedHashMap<>();
	    for (ItemLocationVO r : rows) {
	        grouped.computeIfAbsent(r.getItemcode(), k -> new ArrayList<>(5)).add(r);
	    }
		return grouped;
	}

	// 현황판
	public Map<String, Object> getStatus(HttpServletRequest request) {
		Map<String, Object> result = new HashMap<String, Object>();
		String date = request.getParameter("date");
		String factory = request.getParameter("factory");
		
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("date", date);
		param.put("factory", factory);
		
		try {			
		    List<Map<String, Object>> list = purchaseMapper.getStatus(param);
		    
		    log.info(""+list);
		    
		    Map<String, Map<String, Object>> bySource = new LinkedHashMap<>();
		    long totalSum = 0;
		    long totalCount = 0;
		    
		    for(Map<String, Object> row : list) {
		    	BigDecimal  sumval = (BigDecimal)row.get("SUM(QTY)");
		    	BigDecimal  countval = (BigDecimal)row.get("COUNT(QTY)");
		    	
		    	long sumQty = sumval != null ? sumval.longValue() : 0L;
	            long count = countval != null ? countval.longValue() : 0L;
		    	String source = (String)row.get("SOURCE");
		    	
		    	
		    	Map<String, Object> stat = new LinkedHashMap<>();
		    	stat.put("sum", sumQty);
		    	stat.put("count", count);
		    	
		    	bySource.put(source, stat);
		    	
		    	totalSum += sumQty;
		    	totalCount += count;
		    }
		    
		    result.put("list", bySource);
		    result.put("totalSum", totalSum);
		    result.put("totalCount", totalCount);		    
		}catch(Exception e) {
			log.error("getStatus error", e);
			result.put("error", true);
	        result.put("message", e.getMessage());
		}
		
		return result;
	}
	
	// 공정불출 피딩
	public Map<String, Object> wipFeeding(BarcodeVO request) {
		Map<String, Object> result = new HashMap<String, Object>();
		
		List<String> barcodes = request.getBarcode();
		String date = request.getDate();
		String wccode = request.getWccode();
		String source = request.getSource();		// feeding
		String loginid = request.getLoginid();
		
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}

		// 언팩바코드 체크
		Map<String, Object> unpackCheck = barcodeValidator.unpackCheck(request.getBarcode());
		if (!unpackCheck.isEmpty()) {
			return unpackCheck; // 실패 시 리턴
		}
		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(request.getBarcode());
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }
	    
		 // 001,002 상품이나 제품인지 체크
		Map<String, Object> goodsCodeCheck = barcodeValidator.goodsCodeCheck(request.getBarcode());
	    if (!goodsCodeCheck.isEmpty()) {
	        return goodsCodeCheck; // 실패 시 리턴
	    }
	    
	    
	    // 출고된 바코드인지 체크
//	    Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(request.getBarcode());
//	    if (!alreadyLoad.isEmpty()) {
//	    	return alreadyLoad; // 실패 시 리턴
//	    }
	    
	    Map<String, Object> checkMap = new HashMap<String, Object>();
	    checkMap.put("list",  request.getBarcode());
	    checkMap.put("factory",  request.getFactory());
	    
	    // 바코드가 stockinfo에 있는지 확인, 팔레트에 속한 파트도 확인 가능
	    Map<String, Object> barcodeStockInfo = barcodeValidator.barcodeStockInfo(checkMap);
	    if (!barcodeStockInfo.isEmpty()) {
	    	return barcodeStockInfo; // 실패 시 리턴
	    }
	    
	    // 다른 공장에 있는걸 return
	    Map<String, Object> inputFactoryCheck = barcodeValidator.inputFactoryCheck(checkMap);
	    if (!inputFactoryCheck.isEmpty()) {
	    	return inputFactoryCheck; // 실패 시 리턴
	    }
	    
	    // 해당 작업장에 있는지 확인 없으면 불출 안내
	    Map<String, Object> workLocationCheck = barcodeValidator.workLocationCheck(checkMap);
	    if (!workLocationCheck.isEmpty()) {
	    	return workLocationCheck; // 실패 시 리턴
	    }
	    
		Map<String, Object> map = new HashMap<String, Object>();
		
		// ✅ 팔레트-파트 전처리
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(barcodes);
	    List<String> barcodess = fr.getFiltered();
		
	    map.put("list", barcodess);
		for(String barcode:barcodess) {
			//바코드가 팔레트에 속해 있는지 확인
			String pbarcode = palletMapper.searchPallet(barcode);
			if (pbarcode != null && !pbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",pbarcode);
				palletMap.put("memo","FEEDING");
				// 팔레트테이블에서 해당 바코드 N처리
				int barcodeN = palletMapper.bbarcodeN(palletMap);
				// 수량 가져오기
				Double doublePQty = palletMapper.palletQty(pbarcode);
				palletMap.put("pqty",doublePQty);
				
				//바코드 수량 가져오기
				double doubleqty = palletMapper.partQty(barcode);
				palletMap.put("date",date);
				palletMap.put("qty",doubleqty);
				palletMap.put("loginid",loginid);
				// 팔레트 위치로 적재 insert
				int partLocation = palletMapper.partWorkLocation(palletMap);
				
				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateWLQtyN(palletMap);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateWLQty(palletMap);
				}
			}
			
			String[] parts = wccode.split("-");
			map.put("barcode", barcode);
			map.put("factory", parts.length > 0 ? parts[0] : "");
			map.put("storage", parts.length > 1 ? parts[1] : "");
			map.put("rack", parts.length > 2 ? parts[2] : "");
			map.put("module", parts.length > 3 ? parts[3] : "");
			map.put("levelcode", parts.length > 4 ? parts[4] : "");
			map.put("position", parts.length > 5 ? parts[5] : "");
			map.put("location", wccode);
			map.put("date", date);
			map.put("loginid", loginid);
			map.put("dmemo", "FEEDING");
			map.put("source", source);
			if (barcode.length() == 12) {
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				map.put("itemcode", palletInfo.getItemcode());
				if (palletInfo != null) {
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("qty", formatQty(palletInfo.getQty()));
				} else {
					result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
				}
			} else if(barcode.split(",").length == 5) {
				map.put("itemcode", barcode.split(",")[0]);
				map.put("qty", resolveBarcodeQty(barcode));
			}else if (barcode.startsWith("P") && barcode.endsWith("MEX")) { // 새로운 바코드양식
				PalletDetailVO palletInfo = purchaseMapper.palletInfo(barcode);
				if (palletInfo != null) {			// 팔레트값이 없을때 fail3
				    map.put("itemcode", palletInfo.getItemcode());
				    map.put("qty", formatQty(palletInfo.getQty()));
				} else {
					result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
				}
				
				
			}
			int affected = 0;
			map.put("laststatus",40);
			purchaseMapper.removeWorkLocationBarcode(map);			// worklocation에 있는 기존값 N처리 해야함
			purchaseMapper.insWorkLocationFeeding(map);				// worklocation에 피딩 값 insert
			if(barcode.split(",").length ==5) {
	        	purchaseMapper.updateLaststatusPart(map);
	        }else {
	        	purchaseMapper.updateLaststatusPallet(map);
	        	List<String> bbarcode = purchaseMapper.selectpbBarcode(barcode);
	        	for(int i = 0; i<bbarcode.size(); i++) {
	        		map.put("barcode", bbarcode.get(i));
	        		map.put("qty", resolveBarcodeQty(bbarcode.get(i)));
	        		purchaseMapper.updateLaststatusPart(map);
	        	}
	        }
			result.put("response", "success");
		}
		
		return result;
	}

	public Map<String, Object> searchWIPFeedingDetail(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<WorkmoveVO> list = purchaseMapper.searchWIPFeedingDetail(param);			
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	public Map<String, Object> searchWIPFeedingSummary(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<WorkmoveVO> list = purchaseMapper.searchWIPFeedingSummary(param);			
			result.put("list", list);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}


	// 트랜시스 검증 저장
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
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
				purchaseMapper.insValidation(m);
			}

			result.put("response", "success");
		} catch (Exception e) {
			result.put("response", "fail");
			result.put("message", e.getMessage());
			throw e;
		}
		return result;
	}

	// 반품검사 - 입고반품 / 폐기 공통 (kind: RETURN=입고반품, SCRAP=폐기)
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> inspectionReturn(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();

		String date = (String) param.get("date");
		String loginid = (String) param.get("loginid");
		String kind = (String) param.get("kind");
		String factory = (String) param.get("factory");
		String storage = (String) param.get("storage");
		List<String> barcodeList = (List<String>) param.get("barcode");
		String oitemcode = "";

		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);

		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth;
		}

		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(barcodeList);
		if (!notRegistered.isEmpty()) {
			return notRegistered;
		}

		// 언팩바코드 체크
		Map<String, Object> unpackCheck = barcodeValidator.unpackCheck(barcodeList);
		if (!unpackCheck.isEmpty()) {
			return unpackCheck;
		}


		// 현재 창고에 있으면 RETURN
		Map <String, Object> checkMap = new HashMap<String, Object>();
		checkMap.put("list", (List<String>) param.get("barcode"));
		checkMap.put("factory", factory);
		checkMap.put("storage", storage);

		// 불량라벨이 아니면 RETURN
		Map<String, Object> defectiveNReturn = barcodeValidator.defectiveNReturn(checkMap);
		if (!defectiveNReturn.isEmpty()) {
			return defectiveNReturn;
		}
		Map<String, Object> storageCheckIn = barcodeValidator.storageCheckIn(checkMap);
		if (!storageCheckIn.isEmpty()) {
			return storageCheckIn; // 실패 시 리턴
		}
		// 현재 재고에 있으면 RETURN
		Map<String, Object> barcodeStockInfoForReturn = barcodeValidator.barcodeStockInfoForReturn(checkMap);
		if (!barcodeStockInfoForReturn.isEmpty()) {
			return barcodeStockInfoForReturn; // 실패 시 리턴
		}

		// 출고반품이 된 바코드인지 확인 이미 출고반품된건 안됨
		Map<String, Object> alreadyLoadReturn = barcodeValidator.alreadyLoadReturn(barcodeList);
		if (!alreadyLoadReturn.isEmpty()) {
			return alreadyLoadReturn; // 실패 시 리턴
		}

		// 이미 검사된 바코드인지 확인
		Map<String, Object> inspectionCheck = barcodeValidator.inspectionCheck(checkMap);
		if (!inspectionCheck.isEmpty()) {
			return inspectionCheck; // 실패 시 리턴
		}

		Map<String, Object> map = new HashMap<>(param);

		for(String barcode : barcodeList){

			String[] parts = barcode.split("_", -1);
			oitemcode = parts[3];
			Map<String, Object> item = purchaseMapper.getItemInfoSpec(oitemcode);
			if (item == null) {
				result.put("response", "warning.item.notFound");
				result.put("barcode", Arrays.asList(barcode));
				return result;
			}
			map.put("barcode", barcode);
			map.put("itemcode", item.get("ITEMCODE"));
			map.put("oitemcode", item.get("OITEMCODE"));
			map.put("rack", item.get("CAR"));

			map.put("bdate", parts.length > 1 ? parts[1] : "");
			map.put("seq",   parts.length > 2 ? parts[2] : "");
			map.put("qty", resolveBarcodeQty(barcode));

			// 출고반품 등록
			purchaseMapper.insOutputReturn(map);
			// location에 저장
			map.put("location", factory+"-"+storage+"-"+item.get("CAR"));
			purchaseMapper.basicLocation(map);

			// 검사 등록 postyn = 'Y'
			purchaseMapper.insInspection(map);

			if ("SCRAP".equals(kind)) {
				map.put("dmemo", "SCRAP");
				purchaseMapper.removeBarcode(map);
				map.put("laststatus",91);
			}else{
				map.put("laststatus",10);
			}
			purchaseMapper.updateLaststatusPart(map);

		}

		/*if(true){
			TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			return result;
		}*/
		result.put("response", "success");
		return result;
	}

	// 창고검사 - 입고반품 / 폐기 공통 (kind: RETURN=입고반품, SCRAP=폐기)
	@Transactional(transactionManager = "usaTransactionManager", rollbackFor = Exception.class)
	public Map<String, Object> inspectionStorage(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();

		String date = (String) param.get("date");
		String loginid = (String) param.get("loginid");
		String kind = (String) param.get("kind");
		String factory = (String) param.get("factory");
		String storage = "PRODUCT";
		List<String> barcodeList = (List<String>) param.get("barcode");
		String oitemcode = "";

		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);

		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth;
		}

		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(barcodeList);
		if (!notRegistered.isEmpty()) {
			return notRegistered;
		}

		// 언팩바코드 체크
		Map<String, Object> unpackCheck = barcodeValidator.unpackCheck(barcodeList);
		if (!unpackCheck.isEmpty()) {
			return unpackCheck;
		}


		// 현재 창고에 없으면 RETURN
		Map <String, Object> checkMap = new HashMap<String, Object>();
		checkMap.put("list", (List<String>) param.get("barcode"));
		checkMap.put("factory", factory);
		checkMap.put("storage", storage);

		// 불량라벨이면 RETURN
		Map<String, Object> defectiveCheck = barcodeValidator.defectiveCheck(checkMap);
		if (!defectiveCheck.isEmpty()) {
			return defectiveCheck;
		}

		// 출고시 다른 창고에 있으면 창고이동 하라고 메시지
	    /*Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
	    if (!inputStorageCheck.isEmpty()) {
	    	return inputStorageCheck; // 실패 시 리턴
	    }*/

	    // 해당 창고에 있는지 체크
	    /*Map<String, Object> storageCheckOut = barcodeValidator.storageCheckOut(checkMap);
	    if (!storageCheckOut.isEmpty()) {
	    	return storageCheckOut; // 실패 시 리턴
	    }*/

		// 이미 검사된 바코드인지 확인
		Map<String, Object> inspectionCheck = barcodeValidator.inspectionCheck(checkMap);
		if (!inspectionCheck.isEmpty()) {
			return inspectionCheck; // 실패 시 리턴
		}

		Map<String, Object> map = new HashMap<>(param);

		for(String barcode : barcodeList){

			String[] parts = barcode.split("_", -1);
			oitemcode = parts[3];
			Map<String, Object> item = purchaseMapper.getItemInfoSpec(oitemcode);
			if (item == null) {
				result.put("response", "warning.item.notFound");
				result.put("barcode", Arrays.asList(barcode));
				return result;
			}


			map.put("barcode", barcode);
			map.put("itemcode", item.get("ITEMCODE"));
			map.put("oitemcode", item.get("OITEMCODE"));
			map.put("rack", item.get("CAR"));



			map.put("bdate", parts.length > 1 ? parts[1] : "");
			map.put("seq",   parts.length > 2 ? parts[2] : "");
			map.put("qty", resolveBarcodeQty(barcode));

			// 양불전환
			map.put("oldokyn", "Y");
			map.put("newokyn", "N");
			// 양불 전환 insert
			purchaseMapper.conditionChange(map);
			// 불량라벨로 업데이트
			purchaseMapper.updateLabelType(barcode);

			// location에 저장 - USEYN='Y' 없을 때만
			Map<String, Object> locationInfo = purchaseMapper.getPartInfoForAdjust(barcode);
			if (locationInfo == null) {
				map.put("location", factory+"-"+storage+"-"+item.get("CAR"));
				purchaseMapper.basicLocation(map);
			}

			// 검사 등록 postyn = 'Y'
			purchaseMapper.insInspection(map);

			if ("SCRAP".equals(kind)) {
				map.put("dmemo", "SCRAP");
				purchaseMapper.removeBarcode(map);
				map.put("laststatus",91);
			}else{
				map.put("laststatus",10);
			}
			purchaseMapper.updateLaststatusPart(map);

		}

		/*if(true){
			TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			return result;
		}*/
		result.put("response", "success");
		return result;
	}

}
