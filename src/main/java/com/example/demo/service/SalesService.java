package com.example.demo.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.mapper.usa.PalletMapper;
import com.example.demo.mapper.usa.PurchaseMapper;
import com.example.demo.mapper.mexico.SalesMapper;
import com.example.demo.utils.FilterResult;
import com.example.demo.utils.PalletFilter;
import com.example.demo.validator.BarcodeValidator;
import com.example.demo.validator.DeleteValidator;
import com.example.demo.vo.BarcodeVO;
import com.example.demo.vo.PalletDetailVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesService  {
    private final DataSource usaDataSource;
	private final SalesMapper salesMapper;
	private final PurchaseMapper purchaseMapper;
	private final PalletMapper palletMapper;
    private final CloseService closeService;
	private final PalletFilter palletFilter;
	
	private final BarcodeValidator barcodeValidator;
	private final DeleteValidator deleteValidator;
	
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
	
	public List<String> existOutbound(List<String> barcodes) {
		return salesMapper.existOutbound(barcodes);
	}


	public int insOutbound(Map<String, Object> map) {
		return salesMapper.insOutbound(map);
	}


	public List<String> selectpbBarcode(String barcode) {
		return salesMapper.selectpbBarcode(barcode);
	}


	public List<String> selCust() {
		return salesMapper.selCust();
	}

	// 영업에서 입고 메뉴 전체 사용안함 추후 사용하게 되면 제품 반제품 검증 확인해야함

	// 입고 insert		
	@Transactional
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
		
		// 제품으로 생성된 바코드인지 그리고 laststatus가 10인지 확인
//	    Map<String, Object> productionBarcodeProductionCheck = barcodeValidator.productionBarcodeProductionCheck(vo.getBarcode());
//	    if (!productionBarcodeProductionCheck.isEmpty()) {
//	        return productionBarcodeProductionCheck; // 실패 시 바로 리턴
//	    }
//	    
//	    // 제품으로 생성된 바코드인지 확인 laststatus = 3
//	    Map<String, Object> existBarcode = barcodeValidator.productionBarcodeCheck(vo.getBarcode());
//	    if (!existBarcode.isEmpty()) {
//	        return existBarcode; // 실패 시 바로 리턴
//	    }
		
	    // 제품 품번인지 확인
	    Map<String, Object> productionCheck = barcodeValidator.productionCheck(vo.getBarcode());
	    if (!productionCheck.isEmpty()) {
	    	return productionCheck; // 실패 시 바로 리턴
	    }
	    
	    // // 팔레트가 해체된 경우, 모든 메뉴에서 사용 251107 일부 해체된 팔레트 라벨 사용불가 완전 해체된건 notRegistered 여기서 걸러짐
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
	    
	    // 불출된 바코드인지 체크 불출반납 안내메시지
	    Map<String, Object> alreadyWIP = barcodeValidator.alreadyWIPMessage(vo.getBarcode());
	    if (!alreadyWIP.isEmpty()) {
	    	return alreadyWIP; // 실패 시 리턴
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
		    
		    // 불출된 바코드인지 체크 추후 예외입고에서도 조건 사용할 수 있음 251106 주석
//		    Map<String, Object> alreadyWIP = barcodeValidator.alreadyWIP(vo.getBarcode());
//		    if (!alreadyWIP.isEmpty()) {
//		    	return alreadyWIP; // 실패 시 리턴
//		    }
		    
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
	    	m.put("okyn", okyn);
	    	
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
	
	// 영업이송 메뉴에서 바코드정보 가져오기
	public Map<String, Object> selectTrasferBarcodeInfo(Map<String, Object> param) {
		String barcode = (String)param.get("barcode");
		System.out.println("barcode : "+barcode);
		String itemcode = "";
		String barParts[] = barcode.split(",");
		if(barParts.length == 5) {		// 파트바코드 처리
			itemcode = barParts[0];
		}else {
			// 아세이바코드 처리
		}
		//salesMapper.selectTrasferBarcodeInfo
		return null;
	}
	
	// 영업이송 생산품이동
	public Map<String, Object> insSalesTransfer(Map<String, Object> map) {
		Map<String, Object> result = new HashMap<>();
		
		String date = (String) map.get("date");
		String loginid = (String) map.get("loginid");
		String factory = (String) map.get("factory");
		String storage1 = (String) map.get("storage1");			// storage컬럼에 들어감
		String storage2 = (String) map.get("storage2");			// custcode컬럼에 들어감 제품창고
		String source = (String) map.get("source");
		String barcode1 = (String)map.get("barcode1");
		String barcode2 = (String)map.get("barcode2");
		String factoryno = (String)map.get("factoryno");
		List<String> list = Arrays.asList(barcode1);
		String itemcode = "";
		String barParts[] = barcode1.split(",");
		if(barParts.length == 5) {		// 파트바코드 처리
			itemcode = barParts[0];
		}else {
			result.put("response","warning.barcode.invalid");
			result.put("barcode", list);
			return result;
		}
		
		// 아세이바코드, 파트바코드 비교
		String pItemcode = salesMapper.getPItemcode(itemcode);
		System.out.println("barcode2 : "+barcode2);
		System.out.println("pItemcode : "+pItemcode);
		if (barcode2 == null || pItemcode == null || !barcode2.contains(pItemcode)) {
		    result.put("response","warning.barcode.assy");
		    result.put("barcode", list);
		    return result;
		}
		
		//List<String> list = (List<String>) map.get("barcode");
		
		// ✅ 팔레트-파트 전처리
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(list);
	    List<String> barcodes = fr.getFiltered();
	    
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}
	    
		 // 언팩바코드 체크
		Map<String, Object> unpackCheck = barcodeValidator.unpackCheck(list);
		if (!unpackCheck.isEmpty()) {
			return unpackCheck; // 실패 시 리턴
		}
		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(list);
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }
	    
	    Map<String, Object> checkMap = new HashMap<String, Object>();
	    checkMap.put("list", list);
	    checkMap.put("factory", factory);
	    checkMap.put("wstorage", storage1);
	    checkMap.put("storage", storage2);
	    // worklocation에 있는지 체크
	    Map<String, Object> workLocationY = barcodeValidator.workLocationY(checkMap);
  	    if (!workLocationY.isEmpty()) {
  	        return workLocationY; // 실패 시 리턴
  	    }

  	    // work테이블에 있는지 체크
  	    Map<String, Object> workCheck = barcodeValidator.workCheck(checkMap);
  	    if (!workCheck.isEmpty()) {
  	    	return workCheck; // 실패 시 리턴
  	    }
  	  
	    // 이미 창고에 있는지 체크
  		Map<String, Object> storageCheckIn = barcodeValidator.storageCheckIn(checkMap);
  	    if (!storageCheckIn.isEmpty()) {
  	        return storageCheckIn; // 실패 시 리턴
  	    }
		
  	    // 출고된 바코드인지 체크
  	    Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(list);
  	    if (!alreadyLoad.isEmpty()) {
  	    	return alreadyLoad; // 실패 시 리턴
  	    }
  	    
		for(String barcode : barcodes) {
			Map<String, Object> param = new HashMap<>();
			
			param.put("date", date);
			param.put("loginid", loginid);
			param.put("factory", factory);
			param.put("storage1", storage1);
			param.put("custcode", storage2);
			param.put("storage", storage2);
			param.put("location", factory+"-"+storage2);
			param.put("workcenter", factory+"-"+storage1);
			param.put("mainkind", "MOVE");
			param.put("source", source);
			param.put("factoryno", factoryno);
			
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
				palletMap.put("loginid",loginid);
				// 팔레트 위치로 적재 insert
				int partLocation = palletMapper.partWorkLocation(palletMap);
				
				// 팔레트 수량이 0일때 N으로 미리 업데이트 하면 partWorkLocation이 작동안하므로 순서 변경
				if(doublePQty == 0) {
					palletMap.put("dmemo","PALLET 0");
					// 로케이션에 위치한 팔레트 수량이 0이면 폐기
					int pqty0 = palletMapper.updateWLQtyN(palletMap);
				}else {
					//location 수량 업데이트
					int updateLQty = palletMapper.updateWLQty(palletMap);
				}
			}
			
			String[] parts = barcode.split(",", -1);
			boolean isPallet = parts.length !=5 && parts[0].startsWith("P");
			
			
			param.put("barcode", barcode);
			
			// 바코드 파싱 (인라인)
			if (parts.length == 5) {		// 파트라벨바코드
	            param.put("itemcode", parts[0]);
	            param.put("qty", resolveBarcodeQty(barcode));

	        } else {									// 팔레트바코드
	        	PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
	            if (pal == null) {
	                result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
	            }else {
		            param.put("itemcode", pal.getItemcode());
		            param.put("qty", formatQty(pal.getQty()));
	            }
	        }
			// outbound테이블에 insert 생산품이동
			int salesTransfer = salesMapper.insSalesTransfer(param);
			
			param.put("dmemo", source);
			// 기존 worklocation에 있는 데이터 N처리
			int updateN = salesMapper.updateWorkLocationN(param);
			int insertLocation = salesMapper.insBasicLocation(param);
			int insWorkStock = 0;
			// workstock작업
			if (isPallet) {			// 팔레트면 파트라벨별로 workstock마이너스처리
				List<String> children = purchaseMapper.selectpbBarcode(barcode);
				for (String child : children) {
					param.put("barcode", child);
					param.put("qty", resolveBarcodeQty(child));
					insWorkStock = salesMapper.insWorkStockMinus(param);
				}
			}else {				// 파트라벨이면 바로 workstock마이너스처리
				insWorkStock = salesMapper.insWorkStockMinus(param);
				
			}
		}
		result.put("response", "success");
		return result;
	}
	
	// 생산품이동 detail
	public Map<String, Object> searchSalesTransferDetail(BarcodeVO vo, HttpServletRequest request2) {
		Map<String,Object> result = new HashMap<String, Object>();
		System.out.println("date : "+vo.getDate());
		System.out.println("factory : "+vo.getFactory());
		Map<String,Object> map = new HashMap<String, Object>();
		map.put("date", vo.getDate());
		map.put("factory", vo.getFactory());
		
		List<Map<String, Object>>list = salesMapper.searchSalesTransferDetail(map);
		result.put("list",list);
		return result;
	}
	
	// 영업이송내역 삭제
	public Map<String, Object> searchTransferDetailDel(Map<String, String> paramMap) {
		Map<String, Object> result = new HashMap<String, Object>();
		String barcode = paramMap.get("barcode");
		String meskey = paramMap.get("meskey"); // 예: "AB12345"
		String factory = paramMap.get("factory");
		String date = paramMap.get("date");
		String loginid = paramMap.get("loginid");
		
		
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginid);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}
		
		String ifno	= "";		
		// null 체크, 길이 체크 후 substring
		if (meskey != null && meskey.length() > 2) {
			ifno = "D"+meskey.substring(1);  // 앞 첫글자 D로 변경 → "D12345"
		}
		paramMap.put("ifno", ifno);
		Map<String,Object> m = new HashMap<String, Object>();
		m.put("laststatus", 10);
		m.put("factory", factory);
		m.put("storage", "");
		m.put("barcode", barcode);
		m.put("loginid", loginid);
		m.put("dmemo", "SALE TRANSFER DELETE");
		m.put("kind", "PRODUCT MOVE");		// 후처리 확인 용
		
		// 후처리 확인
		Map<String, Object> checkPostProcessing = deleteValidator.checkDeletable(m);
		if (!checkPostProcessing.isEmpty()) {
			return checkPostProcessing;
		}
		
		purchaseMapper.updateLaststatusPart(m);		// 최종 상태값 10으로 변경
		purchaseMapper.removeBarcode(m);
		
		
		System.out.println("ifno : "+paramMap.get("ifno"));
		System.out.println("meskey : "+paramMap.get("meskey"));
		System.out.println("barcode : "+paramMap.get("barcode"));
		salesMapper.deleteEntersub(paramMap);		// 생산품이동 삭제
		
		//wms workoutbound테이블에서 N으로 업데이트
		salesMapper.searchTransferDetailDel(m);
		
		// wms worklocation useyn = 'Y' 업데이트
		salesMapper.updateWorkLocationY(m);
		result.put("response","success");
		return result;
	}

	// 영업출고 insert
	@Transactional
	public Map<String, Object> insSaleOutput(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		final String date = (String) param.get("date");
		final String main = (String) param.get("main");
		final String source = (String) param.get("source");
		final String kind = (String) param.get("kind");
		String storage = (String) param.get("storage1");
		String factory = (String) param.get("factory");
		String barcode1 = (String) param.get("barcode1");
		String barcode2 = (String) param.get("barcode2");
		String factoryno = (String) param.get("factoryno");
		String cust[] = ((String) param.get("cust")).split("_");
		String custcode = cust[0];
		String custname = cust[1];
		String dock = (String)param.get("dock");
		//String status = vo.getStatus1();
		//List<String> barcodes2 = vo.getBarcode2();
		String memo = "";
		System.out.println("factoryno @@ :"+factoryno);
		final String loginId =(String) param.get("loginid");// TODO: 세션/토큰에서 가져오기
		final String userName = "username";

		List<String> list = Arrays.asList(barcode1);
		String itemcode = "";
		String barParts[] = barcode1.split(",");
		if(barParts.length == 5) {		// 파트바코드 처리
			itemcode = barParts[0];
		}else if(barParts.length == 4) {		// 파트바코드 처리
			itemcode = barParts[1];
		}else {
			System.out.println("invalid barcode format : "+barcode1);
			result.put("response","warning.barcode.invalid");
			result.put("barcode", list);
			return result;
		}
		
		// 아세이바코드, 파트바코드 비교
		String pItemcode = salesMapper.getPItemcode(itemcode);
		System.out.println("barcode2 : "+barcode2);
		System.out.println("pItemcode : "+pItemcode);
		if (barcode2 == null || pItemcode == null || !barcode2.contains(pItemcode)) {
		    result.put("response","warning.barcode.assy");
		    result.put("barcode", list);
		    return result;
		}
		
		Map<String, Object> magamMap = new HashMap<String, Object>();
		magamMap.put("date", date);
		magamMap.put("loginid", loginId);
		// 마감 확인
		Map<String, Object> checkClosedMonth = closeService.checkClosedMonth(magamMap);
		if (!checkClosedMonth.isEmpty()) {
			return checkClosedMonth; // 실패 시 리턴
		}
		
		// ✅ 팔레트-파트 전처리
	    FilterResult fr = palletFilter.removeChildPartsIfParentPresent(list);
	    List<String> barcodes = fr.getFiltered();		
		List<String> barcodes3 = new ArrayList<>(barcodes);      // 창고 체크용 별도 복사본
		
		Map<String,Object> map2 = new HashMap<String, Object>();		// 창고같은지 확인하기 위함
		map2.put("storage1", storage);
		map2.put("storage", storage);
		map2.put("factory", factory);
		map2.put("list", barcodes3);
		
		// 언팩바코드 체크
		Map<String, Object> unpackCheck = barcodeValidator.unpackCheck(list);
		if (!unpackCheck.isEmpty()) {
			return unpackCheck; // 실패 시 리턴
		}
		
		// 등록된 바코드인지 체크
		Map<String, Object> notRegistered = barcodeValidator.notRegistered(list);
	    if (!notRegistered.isEmpty()) {
	        return notRegistered; // 실패 시 리턴
	    }
	    
	    // 불출된 바코드인지 체크
 		Map<String, Object> alreadyWIP = barcodeValidator.alreadyWIP(list);
 	    if (!alreadyWIP.isEmpty()) {
 	        return alreadyWIP; // 실패 시 리턴
 	    }
	    
	    // 출고된 바코드인지 체크
 		Map<String, Object> alreadyLoad = barcodeValidator.alreadyLoad(list);
 	    if (!alreadyLoad.isEmpty()) {
 	        return alreadyLoad; // 실패 시 리턴
 	    }
	    
	    // 
	    Map <String, Object> checkMap = new HashMap<String, Object>();
	    checkMap.put("list", list);
	    checkMap.put("factory", factory);
	    checkMap.put("storage", storage);
	    // 출고시 다른 창고나 다른공장에 있으면 창고이동이나 공장이송 하라고 메시지
	    Map<String, Object> inputFactoryCheck = barcodeValidator.inputFactoryCheck(checkMap);
	    if (!inputFactoryCheck.isEmpty()) {
	    	return inputFactoryCheck; // 실패 시 리턴
	    }
	    Map<String, Object> inputStorageCheck = barcodeValidator.inputStorageCheck(checkMap);
	    if (!inputStorageCheck.isEmpty()) {
	    	return inputStorageCheck; // 실패 시 리턴
	    }
	    
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
	    
    	// 원자재, 부자재. 반제품은 출고 안됨
	    Map<String, Object> materialCodeCheck = barcodeValidator.materialCodeCheck(list);
	    if (!materialCodeCheck.isEmpty()) {
	    	return materialCodeCheck; // 실패 시 리턴
	    }
	    
	    // 반제품인지 확인
	    Map<String, Object> semiProduction = barcodeValidator.semiProduction(list);
	    if (!semiProduction.isEmpty()) {
	    	return semiProduction; // 실패 시 리턴
	    }
		    
		for (String barcode : barcodes) {
			//바코드가 팔레트에 속해 있는지 확인
			String pbarcode = palletMapper.searchPallet(barcode);
			if (pbarcode != null && !pbarcode.isEmpty()) {
				Map<String, Object> palletMap = new HashMap<String, Object>();
				palletMap.put("barcode",barcode);
				palletMap.put("pbarcode",pbarcode);
				palletMap.put("memo","LOAD");
				param.put("factoryno", factoryno);
				param.put("custcode", custcode);
				param.put("custname", custname);
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
	    	m.put("source2", "SALES");
			m.put("custcode", custcode);
			m.put("main", main);
			m.put("kind", kind);
			m.put("memo", memo);
			m.put("factoryno", factoryno);
			m.put("custname", custname);
			m.put("dock", dock);

	        // 바코드 파싱 (인라인)
	        if (barcode.split(",").length ==5) {
	            String[] parts = barcode.split(",", -1);
	            m.put("itemcode", parts[0]);
	            m.put("bdate", parts[1]);
	            m.put("seq", parts[2]);
	            m.put("qty", resolveBarcodeQty(barcode));
	            m.put("scmmex", parts[4]);
	            m.put("type", "box");

	        } else if (barcode.length() == 12 && barcode.startsWith("P")) {
	            PalletDetailVO pal = purchaseMapper.palletInfo(barcode);
	            if (pal == null) {
	            	result.put("response", "warning.pallet.infoNotFound");
	                result.put("barcode", Arrays.asList(barcode));
	                return result;
	            }
	            m.put("itemcode", pal.getItemcode());
	            m.put("qty", formatQty(pal.getQty()));
	            m.put("bdate", "");
	            m.put("seq", "");
	            m.put("scmmex", "");
	            m.put("type", "pallet");

			} else if (barcode.startsWith("P") && barcode.endsWith("MEX")) {
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
	        m.put("storage",storage);
	        m.put("dmemo","OUTPUT");
	        // INSERT: 예외출고
	        int insOutbound = 0;
	        if("LOADEXCEPTION".equals(source)) {
	        	m.put("source2", param.get("source2"));
	        	insOutbound = purchaseMapper.insOutputException2(m);			// 예외출고 invoice, source2 insert
	        }else {
	        	insOutbound = salesMapper.insSalesOutput(m);
	        }
	        int affected = 0;
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
	    }

		result.put("response", "success");
		return result;
	}

	
}
