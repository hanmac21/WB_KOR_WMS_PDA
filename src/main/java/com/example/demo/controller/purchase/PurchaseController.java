package com.example.demo.controller.purchase;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.KorwebappApplication;
import com.example.demo.service.PurchaseService;
import com.example.demo.utils.PalletFilter;
import com.example.demo.vo.BarcodeVO;
import com.example.demo.vo.ItemLocationVO;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping("/purchase")
public class PurchaseController {

    private final KorwebappApplication korwebappApplication;

	@Autowired
	PurchaseService purchaseService;
	
	@Autowired
	PalletFilter palletFilter;

    PurchaseController(KorwebappApplication korwebappApplication) {
        this.korwebappApplication = korwebappApplication;
    }

	@PostMapping("/make-pallet-barcode")
	public Map<String, Object> makeBarcode(@RequestBody BarcodeVO request, HttpServletRequest request2) {
		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        request.setLoginid(loginid); // 예: 등록자 세팅
	    }
		return purchaseService.makePalletBarcode(request);
	}
	
	// 팔레트 관리 search
	@PostMapping("/pallet_management_search")
	public Map<String, Object> palletManagementSearch(HttpServletRequest request){
		log.info("pallet_management_search");
		String barcode = request.getParameter("barcode");
		Map<String,Object> result = new HashMap<String, Object>();
		log.info("pallet barcode : "+barcode);
		result.put("list", purchaseService.palletManagementSearch(barcode));
		return result;
	}
	
	// 팔레트 라벨에 파트라벨 추가
	@PostMapping("/insPalletBarcode")
	public Map<String, Object> insPalletBarcode(@RequestBody BarcodeVO request, HttpServletRequest request2) {
		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        request.setLoginid(loginid); // 예: 등록자 세팅
	    }
		return purchaseService.insPalletBarcode(request);
	}
	
	// 팔레트 해체 파트 조회
	@PostMapping("/pallet_unbound_search")
	public Map<String, Object> palletUnboundSearch(HttpServletRequest request){
		log.info("pallet_unbound_search");
		String barcode = request.getParameter("barcode");
		Map<String,Object> result = new HashMap<String, Object>();
		log.info("pallet barcode : "+barcode);
		result.put("list", purchaseService.palletUnboundSearch(barcode));
		return result;
	}

	@GetMapping("/part-itemname-search")
	public Map<String, Object> partItemnameSearch(HttpServletRequest request){
		String itemcode = request.getParameter("itemcode");
		log.info("part_itemname_search: " + itemcode);
		Map<String, Object> result = new HashMap<>();
		result.put("items", purchaseService.partItemnameSearch(itemcode));
		return result;
	}
	
	// 파트라벨 수량 조정에서 파트정보 가져오기
	@GetMapping("/part_info_search")
	public Map<String, Object> partInfoSearch(HttpServletRequest request){
		String barcode = request.getParameter("barcode");
		log.info("part_info_search" + barcode);
		Map<String,Object> result = new HashMap<String, Object>();
		result.put("result", purchaseService.partInfoSearch(barcode));
		return result;
	}
	
	
	// 파트라벨 수량 조정 업데이트
	@PostMapping("/part_adjustment_update")
	public Map<String, Object> partAdjustmentUpdate(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("part_adjustment_update");
		HttpSession session = request.getSession(false);
		Map<String, Object> map = new HashMap<String, Object>();
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        map.put("loginid", loginid);
	    }
		System.out.println("request.getParamteter(nowqty) : " + param.get("nowqty"));
		String barcode = param.get("barcode").toString();
		map.put("barcode", barcode);
		map.put("nowqty", param.get("nowqty"));
		map.put("qty", param.get("adjustqty"));
		map.put("factory", param.get("factory"));
		map.put("memo", param.get("memo"));
		return purchaseService.partAdjustmentUpdate(map);
	}

	// 파트라벨 품번 조정 업데이트
	@PostMapping("/part_adjustment_itemcode_update")
	public Map<String, Object> partAdjustmentItemcodeUpdate(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("part_adjustment_itemcode_update");
		HttpSession session = request.getSession(false);
		if (session != null) {
			String loginid = (String) session.getAttribute("loginid");
			param.put("loginid", loginid);
		}
		return purchaseService.partAdjustmentItemcodeUpdate(param);
	}

	// 팔레트 해체
	@PostMapping("/pallet_unbound")
	public Map<String, Object> palletUnbound(HttpServletRequest request){
		log.info("pallet_unbound");
		HttpSession session = request.getSession(false);
		Map<String, Object> map = new HashMap<String, Object>();
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        map.put("loginid", loginid);
	    }
		String barcode = request.getParameter("barcode");
		map.put("barcode", barcode);
		Map<String,Object> result = new HashMap<String, Object>();
		result.put("list", purchaseService.palletUnbound(map));
		return result;
	}

	// 입고 insert
	@PostMapping("/validation/save")
	public Map<String, Object> saveValidation(@RequestBody Map<String, Object> param) {
		return purchaseService.saveValidation(param);
	}

	@PostMapping("/insInbound")
	public Map<String, Object> insInbound(@RequestBody BarcodeVO vo, HttpServletRequest request2) {
		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        vo.setLoginid(loginid); // 예: 등록자 세팅
	    }
		return purchaseService.insInbound(vo);
	}
	
	@PostMapping("/palletCheckInbound")
	public Map<String, Object> palletCheckInbound(@RequestBody BarcodeVO vo, HttpServletRequest request2) {
		return purchaseService.palletCheckInbound(vo, request2);
	}
		
	// 예외 입고 내역 - detail
	@PostMapping("/search-exception/input-detail")
	public Map<String, Object> searchexceptionInputDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search Exception Inbound Detail");
		return purchaseService.searchexceptionInputDetail(param);
	}
	
	// 출고 insert
	@PostMapping("/insOutputTransys")
	public Map<String, Object> insOutputTransys(@RequestBody BarcodeVO vo, HttpServletRequest request2) {
		log.info(" Outbound transys start");
		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        vo.setLoginid(loginid); // 예: 등록자 세팅
	    }
		return purchaseService.insOutputTransys(vo);
	}

	// 출고 insert
	@PostMapping("/insOutput")
	public Map<String, Object> insOutput(@RequestBody BarcodeVO vo, HttpServletRequest request2) {
		log.info(" Outbound start");
		HttpSession session = request2.getSession(false);
		if (session != null) {
			String loginid = (String) session.getAttribute("loginid");
			vo.setLoginid(loginid); // 예: 등록자 세팅
		}
		return purchaseService.insOutput(vo);
	}
	
	// 전 거래처 통합 출고 insert
	@PostMapping("/insOutputAll")
	public Map<String, Object> insOutputAll(@RequestBody BarcodeVO vo, HttpServletRequest request2) {
		log.info(" Outbound All start");
		HttpSession session = request2.getSession(false);
		if (session != null) {
			String loginid = (String) session.getAttribute("loginid");
			vo.setLoginid(loginid);
		}
		return purchaseService.insOutputAll(vo);
	}

	// 예외 출고 내역 - detail
	@PostMapping("/search-exception/output-detail")
	public Map<String, Object> searchexceptionOutputDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search Exception Outbound Detail");
		return purchaseService.searchexceptionOutputDetail(param);
	}
	
	// 재고실사 insert
	@PostMapping("/insertRealStock")
	public Map<String, Object> insertRealStock(@RequestBody BarcodeVO request, HttpServletRequest request2) {
		log.info("realstock insert controller");
		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        request.setLoginid(loginid); // 예: 등록자 세팅
	    }

		return purchaseService.insRealStock(request);
	}
	
	// 재고실사 insert
	@PostMapping("/insertRealStockLastDay")
	public Map<String, Object> insertRealStockLastDay(@RequestBody BarcodeVO request, HttpServletRequest request2) {
		log.info("realstock Last day insert controller");
		HttpSession session = request2.getSession(false);
		if (session != null) {
			String loginid = (String) session.getAttribute("loginid");
			request.setLoginid(loginid); // 예: 등록자 세팅
		}
		
		return purchaseService.insRealStockLastDay(request);
	}

	// 재고적재 250922 hj 사용안하는지 확인하고 지울예정
	@PostMapping("/selItem-location")
	public Map<String, Object> selItemLocation(HttpServletRequest request) {
		Map<String, Object> result = new HashMap<String, Object>();
		String itemcode = request.getParameter("itemcode");
		List<ItemLocationVO> list = purchaseService.selItemLocation(itemcode);
		result.put("list", list);
		return result;
	}
	// 적재 언로드
	@PostMapping("/location-unload")
	public Map<String,Object> locationUnload(HttpServletRequest request){
		HttpSession session = request.getSession(false);
		String loginid = "";
		if (session != null) {
	        loginid = (String) session.getAttribute("loginid");
	    }
		String barcode = request.getParameter("barcode");
		String location1 = request.getParameter("location");
		String factory = request.getParameter("factory");
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("loginid", loginid);
		map.put("barcode", barcode);
		map.put("location1", location1);
		map.put("factory", factory);
		return purchaseService.locationUnload(map);
	}
	
	// 적재 삭제 - 예외출고기능
	@PostMapping("/location-delete-exload")
	public Map<String,Object> locationDeleteExload(HttpServletRequest request){
		HttpSession session = request.getSession(false);
		String loginid = "";
		if (session != null) {
	        loginid = (String) session.getAttribute("loginid");
	    }
		String barcode = request.getParameter("barcode");
		String location1 = request.getParameter("location");
		String factory = request.getParameter("factory");
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("loginid", loginid);
		map.put("barcode", barcode);
		map.put("location", location1);
		map.put("factory", factory);
		return purchaseService.locationDeleteExload(map);
	}
	
	// 재고적재 로케이션 사용여부
	@PostMapping("/exist-location")
	public int existLocation(HttpServletRequest request) {
		int result = 0;
		String location = request.getParameter("location");
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("location", location);
		result = purchaseService.existLocation(map);
		
		return result;
	}
	
	// 재고적재 이미 적재된 바코드인지 확인
	@PostMapping("/exist-location-barcode")
	public int existLocationBarcode(@RequestBody BarcodeVO request) {
		List<String> barcodes = request.getBarcode();
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("barcode", barcodes);
		int itemExist = purchaseService.existItem(map);
		
		return itemExist;
	}
	
	// 재고적재
	@PostMapping("/save-location")
	public Map<String, Object> saveLocation(@RequestBody BarcodeVO request, HttpServletRequest request2) {
		log.info("/save-location start");
		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        request.setLoginid(loginid); // 예: 등록자 세팅
	    }
		return purchaseService.saveLocation(request);
	}
	
	// 재고적재
	@PostMapping("/insertRealStockLastDayLocation")
	public Map<String, Object> insertRealStockLastDayLocation(@RequestBody BarcodeVO request, HttpServletRequest request2) {
		log.info("/save-location start");
		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        request.setLoginid(loginid); // 예: 등록자 세팅
	    }
		return purchaseService.insRealStockLastDayLocation(request);
	}
	
	// 파트라벨중에 팔레트라벨로 묶인게 잇는지 확인(불출, 팔레트에서 사용중)
	@PostMapping("/barcodeTypeCheck")
	public Map<String, Object> barcodeTypeCheck(@RequestBody BarcodeVO request){
		return purchaseService.barcodeTypeCheck(request);
	}

	// 공정불출
	@PostMapping("/save-workmove")
	public Map<String, Object> saveOutbound(@RequestBody BarcodeVO request, HttpServletRequest request2) {
		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        request.setLoginid(loginid); // 예: 등록자 세팅
	    }
		return purchaseService.saveWorkmove(request);
	}

	// 공정불출 - 푸에블라
	@PostMapping("/save-workmove-puebla")
	public Map<String, Object> saveWorkmovePuebla(@RequestBody BarcodeVO request, HttpServletRequest request2) {
		HttpSession session = request2.getSession(false);
		if (session != null) {
			String loginid = (String) session.getAttribute("loginid");
			request.setLoginid(loginid); // 예: 등록자 세팅
		}
		return purchaseService.saveWorkmovePuebla(request);
	}
	
	// 공정불출내역 - 미도착
	@PostMapping("/search-wip/input-notarrived")
	public Map<String, Object> searchWIPInputNotArrived(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search wip/input not arrived");
		return purchaseService.searchWIPInputNotArrived(param);
	}
	
	// 공정불출내역 - 완료
	@PostMapping("/search-wip/input-completed")
	public Map<String, Object> searchWIPInputCompleted(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search wip/input completed");
		return purchaseService.searchWIPInputCompleted(param);
	}

	// 공정불출내역 - detail
	@PostMapping("/search-wip/input-detail")
	public Map<String, Object> searchWIPInputDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search wip/input Detail");
		return purchaseService.searchWIPInputDetail(param);
	}
	
	// 공정불출내역 - summary
	@PostMapping("/search-wip/input-summary")
	public Map<String, Object> searchWIPInputSummary(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search wip/input Summary");
		return purchaseService.searchWIPInputSummary(param);
	}
	
	// 공정불출반납
	@PostMapping("/wip/return")
	public Map<String, Object> wipReturn(@RequestBody BarcodeVO request, HttpServletRequest request2) {
		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        request.setLoginid(loginid); // 예: 등록자 세팅
	    }
		return purchaseService.wipReturn(request);
	}
	
	// 공정불출 반납 내역 - detail
	@PostMapping("/search-wip/return-detail")
	public Map<String, Object> searchWIPReturnDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search wip/return detail");
		return purchaseService.searchWIPReturnDetail(param);
	}
	
	// 공정불출 반납 내역 - summary
	@PostMapping("/search-wip/return-summary")
	public Map<String, Object> searchWIPReturnSummary(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search wip/return Summary");
		return purchaseService.searchWIPReturnSummary(param);
	}
	
	
	// 언팩리스트 불러오기unpack
	@PostMapping("/unpack_list")
	public Map<String, Object> unpacklist(@RequestBody BarcodeVO request) {
		
		return  purchaseService.unpackList(request);
	}
	
	// 공정불출 피딩
	@PostMapping("/wip/feeding")
	public Map<String, Object> wipFeeding(@RequestBody BarcodeVO request, HttpServletRequest request2) {
		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        request.setLoginid(loginid); // 예: 등록자 세팅
	    }
		return purchaseService.wipFeeding(request);
	}
	
	// 공정불출 피딩내역 - detail
	@PostMapping("/search-wip/feeding-detail")
	public Map<String, Object> searchWIPFeedingDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search wip/feeding Detail");
		return purchaseService.searchWIPFeedingDetail(param);
	}
	
	// 공정불출 피딩내역 - sunnary
	@PostMapping("/search-wip/feeding-summary")
	public Map<String, Object> searchWIPFeedingSummary(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search wip/feeding Detail");
		return purchaseService.searchWIPFeedingSummary(param);
	}
//	// 언팩 확정
//	@PostMapping("/unpack-_complete")
//	public Map<String, Object> unpackComplete(@RequestBody BarcodeVO request) {
//        List<String> barcodes = request.getBarcode();
//	}
	
	
	// 언팩 unpack
	@PostMapping("/unpack")
	public Map<String, Object> unpack(@RequestBody BarcodeVO request, HttpServletRequest request2) {
		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        request.setLoginid(loginid); // 예: 등록자 세팅
	    }

		return purchaseService.unpack(request);
	}
	
	// 입고 Invocie List 조회
	@PostMapping("/search-incoming/invoiceList")
	public Map<String, Object> incomingInvoiceList(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search incoming invoiceList");
		return purchaseService.searchIncomingInvoiceList(param);
	}	
	
	// 인보이스 조회
	@GetMapping("/search_invoice")
	public Map<String, Object> searchInvoice(HttpServletRequest request){
		log.info("search invoice");
		return purchaseService.searchInvoice(request);
	}
	
	// 입고 인보이스 insert
	@PostMapping("/insInboundInvoice")
	public Map<String, Object> insInboundInvoice(@RequestBody BarcodeVO vo, HttpServletRequest request2) {
		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        vo.setLoginid(loginid); // 예: 등록자 세팅
	    }
		return purchaseService.insInboundInvoice(vo);
	}
	
	// 입고현황 - Detail
	@PostMapping("/search-incoming-detail")
	public Map<String, Object> incomingDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search incoming detail");
		return purchaseService.searchIncomingDetail(param);
	}	

	// 입고현황 - Summary
	@PostMapping("/search-incoming-summary")
	public Map<String, Object> incomingSummary(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search incoming summary");
		return purchaseService.searchIncomingSummary(param);
	}
	
	// 입고반품 -
	@PostMapping("/incoming-return")
	public Map<String, Object> incomingReturn(@RequestBody BarcodeVO vo, HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        vo.setLoginid(loginid); // 예: 등록자 세팅
	    }

		return purchaseService.incomingReturn(vo);
	}
	
	// 입고반품 - Detail
	@PostMapping("/search-incoming-return/detail")
	public Map<String, Object> incomingReturnDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search incoming-return detail");
		return purchaseService.searchIncomingReturnDetail(param);
	}	
	
	// 출고 - Detail
	@PostMapping("/search-load/detail")
	public Map<String, Object> loadDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search load detail");
		return purchaseService.searchLoadDetail(param);
	}	
	
	// 출고반품 - Detail
	@PostMapping("/search-load-return/detail")
	public Map<String, Object> loadReturnDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search load-return detail");
		return purchaseService.searchLoadReturnDetail(param);
	}		
	
	// 재고실사현황 - Detail
	@PostMapping("/search-stock-count/detail")
	public Map<String, Object> search_inventory(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search_inventory_detail");
		return purchaseService.searchInventoryDetail(param);
	}
	
	// 재고실사현황 - summary
	@PostMapping("/search-stock-count/summary")
	public Map<String, Object> search_inventory_summary(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search_inventory_summary");
		return purchaseService.searchInventorySummary(param);
	}	
	
	// 언팩 리스트
	@PostMapping("/search-unpack")
	public Map<String, Object> searchUnpack(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search Unpack List");
		return purchaseService.searchUnpack(param);
	}
	
//	@GetMapping("/unpack/complete-detail")
//	public Map<String, Object> unpackCompleteDetail(String barcode){
//		log.info("search Unpack complete-detail");
//		Map<String,Object> result = new HashMap<String, Object>();
//		
//		Map<String,Object> map = new HashMap<String, Object>();
//		//List<Map<String, Object>> list = purchaseService.unpackCompleteDetail(barcode);
//		
//		
//		return result; 
//	}
	
	// 언팩 확정 검수리스트
	@PostMapping("/unpack_barcode_list")
	public Map<String, Object> unpackBarcodeList(@RequestParam("barcode") String barcode, HttpServletRequest request){
		log.info("unpack barcode List");
		Map<String,Object> result = new HashMap<String, Object>();
		log.info("unpack barcode : "+barcode);
		result.put("list", purchaseService.unpackBarcodeList(barcode));
		return result;
	}
	
	// 언팩완료 unpack complete
	@PostMapping("/update_unpack_status")
	public Map<String, Object> updateUnpackStatus(@RequestBody BarcodeVO request, HttpServletRequest request2) {
		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        request.setLoginid(loginid); // 예: 등록자 세팅
	    }

		return purchaseService.updateUnpackStatus(request);
	}
	
	// 재고 조회 - Detail
	@PostMapping("/search-stock/detail")
	public Map<String, Object> search_stockDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search_stock_detail");
		return purchaseService.searchStockDetail(param);
	}
	
	// 재고정보
	@PostMapping("/stock-info-barcode")
	public Map<String, Object> stockInfo(@RequestBody Map<String, Object> map){
		log.info("search Stock Info List");
		return purchaseService.stockInfo(map);
	}
	// 재고내역
	@PostMapping("/stock-history-barcode")
	public Map<String, Object> stockHistory(@RequestBody String barcode){
		log.info("sstockHistory List");
		return purchaseService.stockHistory(barcode);
	}

	@PostMapping("/show_stockHistory_sangho")
	public Map<String,Object> show_stockHistory_sangho(@RequestParam String custCode){
		return purchaseService.show_stockHistory_sangho(custCode);
	}
	
//	@PostMapping("/location_checkInbound") // 호출안되는거확인
//	public int location_checkInbound(@RequestBody String barcode) {
//		System.out.println("PARAM 1 -- " + barcode);
//		return purchaseService.pur_location_checkInbound(barcode);
//	}

//	@PostMapping("/location_insert")
//	public Map<String, Object> location_insert(@RequestBody Map<String, Object> param, HttpServletRequest request) {
//		String loginId = getCookie(request, "loginId");
//		System.out.println("COOKIE GET -- " + loginId);
//		param.put("loginId", "master");
//		Map<String, Object> result = new HashMap<>();
//		try {
//			purchaseService.pur_location_insert(param);
//			result.put("success", true);
//			result.put("message", "Completed Successfully.");
//		} catch (Exception e) {
//			result.put("success", false);
//			result.put("message", e.getMessage());
//		}
//		return result;
//	};

	public static String getCookie(HttpServletRequest request, String name) {
		if (request.getCookies() == null)
			return null;
		for (Cookie c : request.getCookies()) {
			if (name.equals(c.getName())) {
				return c.getValue();
			}
		}
		return null;
	}

	// 🔹 **1. RACK 목록 조회 API**
	@PostMapping("/rack/list")
	public ResponseEntity<Map<String, Object>> getRackList(@RequestParam Map<String, String> filters) {

		log.info(" -- rack/list -- Enter!");
		try {
			// 🔸 필터 조건 추출
			String storage = filters.getOrDefault("storage", "default");
			String factory = filters.getOrDefault("factory", "default");
			String searchType = filters.getOrDefault("searchType", "default");
			String keyword = filters.getOrDefault("keyword", "");

			// 🔸 서비스에서 데이터 조회
			List<Map<String, Object>> rackList = purchaseService.getRackList(storage, factory, searchType, keyword);

			// 🔸 전체 통계 계산
			Map<String, Object> summary = calculateSummary(rackList);

			// 🔸 응답 데이터 구성
			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("message", "RACK 목록 조회 성공");
			response.put("data", rackList);
			response.put("summary", summary);
			response.put("timestamp", new Date());

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			// 🔸 에러 응답
			Map<String, Object> errorResponse = new HashMap<>();
			errorResponse.put("success", false);
			errorResponse.put("message", "RACK 목록 조회 실패: " + e.getMessage());
			errorResponse.put("data", new ArrayList<>());
			errorResponse.put("error", e.getClass().getSimpleName());
			errorResponse.put("timestamp", new Date());

			return ResponseEntity.status(500).body(errorResponse);
		}
	}

	// 🔹 **2. RACK 상세 조회 API**
	@PostMapping("/rack/detail")
	public ResponseEntity<Map<String, Object>> getRackDetail(@RequestParam Map<String, String> request) {

		log.info(" -- rack/detail -- Enter!");
		System.err.println(request);

		int checkVal = 0;
		if ("H/REST".equals(request.get("storage"))) {
			checkVal = purchaseService.checkWorkLocationRow(request);
		} else {
			checkVal = purchaseService.checkLocationRow(request);			
		}

		if (checkVal == 0) {
			// 옵션 1: 데이터가 없는 경우 - 빈 데이터와 함께 성공 응답
			Map<String, Object> emptyResponse = new HashMap<>();
			emptyResponse.put("success", true);
			emptyResponse.put("message", "조회된 데이터가 없습니다");
			emptyResponse.put("data", new HashMap<>()); // 빈 데이터
			emptyResponse.put("timestamp", new Date());
			return ResponseEntity.ok(emptyResponse);
		} else {

			try {
				String rackId = request.get("rackId");
				String storage = request.getOrDefault("storage", "default");
				String factory = request.getOrDefault("factory", "default");

				if (rackId == null || rackId.trim().isEmpty()) {
					Map<String, Object> errorResponse = new HashMap<>();
					errorResponse.put("success", false);
					errorResponse.put("message", "RACK ID가 필요합니다");
					return ResponseEntity.badRequest().body(errorResponse);
				}

				// 🔸 서비스에서 상세 데이터 조회
				Map<String, Object> rackDetail = purchaseService.getRackDetail(rackId, storage, factory);

				// 🔸 응답 데이터 구성
				Map<String, Object> response = new HashMap<>();
				response.put("success", true);
				response.put("message", "RACK 상세 정보 조회 성공");
				response.put("data", rackDetail);
				response.put("timestamp", new Date());

				return ResponseEntity.ok(response);

			} catch (Exception e) {
				Map<String, Object> errorResponse = new HashMap<>();
				errorResponse.put("success", false);
				errorResponse.put("message", "RACK 상세 조회 실패: " + e.getMessage());
				errorResponse.put("data", null);
				errorResponse.put("error", e.getClass().getSimpleName());
				errorResponse.put("timestamp", new Date());

				return ResponseEntity.status(500).body(errorResponse);
			}
		}
	}
	
	@PostMapping("/rack/locationDetail")
	public Map<String, Object> locationDetail(@RequestParam String barcode) {
		log.info("locationDetail");
		return purchaseService.locationDetail(barcode);
	}
	
	@PostMapping("/rack/unloaded")
	public Map<String, Object> uunloadedList(@RequestBody Map<String, String> param) {
		return purchaseService.unloadedList(param);
	}

	// 🔸 **통계 계산 헬퍼 메소드**
	private Map<String, Object> calculateSummary(List<Map<String, Object>> rackList) {
		Map<String, Object> summary = new HashMap<>();

		int totalRacks = rackList.size();
		int totalCurrentCount = 0;
		int totalCapacity = 0;

		for (Map<String, Object> rack : rackList) {
			totalCurrentCount += (Integer) rack.getOrDefault("currentCount", 0);
			totalCapacity += (Integer) rack.getOrDefault("totalCapacity", 0);
		}

		int overallUtilizationRate = totalCapacity > 0 ? Math.round(totalCurrentCount * 100.0f / totalCapacity) : 0;

		summary.put("totalRacks", totalRacks);
		summary.put("overallUtilizationRate", overallUtilizationRate);
		summary.put("totalCurrentCount", totalCurrentCount);
		summary.put("totalCapacity", totalCapacity);

		return summary;
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
	
	// 제품정보 품번으로 가져오기
	@GetMapping("/getItemInfo")
	@ResponseBody
	public Map<String, Object> getItemInfo(Model model, HttpServletRequest request, @RequestParam String itemcode){
		return purchaseService.getItemInfo(itemcode);
	}
	
	// 제품정보 품번으로 가져오기
	@GetMapping("/getItemInfo2")
	@ResponseBody
	public Map<String, Object> getItemInfo2(Model model, HttpServletRequest request, @RequestParam String itemcode,
	        @RequestParam String factory){
		Map<String, Object> param = new HashMap<>();
	    param.put("itemcode", itemcode);
	    param.put("factory", factory);
		return purchaseService.getItemInfo2(param);
	}
	
	// 제품정보 바코드로 가져오기
	@GetMapping("/getItemInfo_barcode")
	@ResponseBody
	public Map<String, Object> getItemInfo_barcode(Model model, HttpServletRequest request, @RequestParam String barcode){
		return purchaseService.getItemInfo_barcode(barcode);
	}
	
	// 파레트 정보 가져오기
	@GetMapping("/getPalletInfo")
	@ResponseBody
	public Map<String, Object> getPalletInfo(Model model, HttpServletRequest request, @RequestParam String barcode, @RequestParam String type){
		log.info("파레트 정보 가져오기");
		return purchaseService.getPalletInfo(barcode, type);
	}
	
	// 창고 이동 조회
	@PostMapping("/search-warehouse")
	@ResponseBody
	public Map<String, Object> searchWarehouse(@RequestBody BarcodeVO vo) {
	    List<String> barcodes = vo.getBarcode(); 
	    return purchaseService.searchWarehouse(barcodes);
	}
	
	// 창고 이동 처리 전 창고체크
	@PostMapping("/sameWarehouseCheck")
	@ResponseBody
	public Map<String, Object> sameWarehouseCheck(Model model, HttpServletRequest request, @RequestBody Map<String, Object> map){
		return purchaseService.sameWarehouseCheck(map);
	}
	
	// 창고 이동 선입선출 체크
	@PostMapping("/checkFifo")
	@ResponseBody
	public Map<String, Object> checkFifo(HttpServletRequest request, @RequestBody Map<String, Object> map){
		return purchaseService.checkFifo(map);
	}

	// 창고 이동 처리
	@PostMapping("/transferWarehouse")
	@ResponseBody
	public Map<String, Object> transferWarehouse(Model model, HttpServletRequest request, @RequestBody Map<String, Object> map){
		HttpSession session = request.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        map.put("loginid",loginid); // 예: 등록자 세팅
	    }

		return purchaseService.transferWarehouse(map);
	}

	// 창고 이동 처리 - Detail
	@PostMapping("/search-movement/warehouse-detail")
	public Map<String, Object> searchWarehouseDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search_warehouse_detail");
		return purchaseService.searchWarehouseDetail(param);
	}
	
	// 창고 이동 처리  - Detail
	@PostMapping("/search-movement/warehouse-summary")
	public Map<String, Object> searchWarehouseSummary(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search_warehouse_summary");
		return purchaseService.searchWarehouseSummary(param);
	}
	
	// 창고 이동 처리
	@PostMapping("/transferFactory")
	@ResponseBody
	public Map<String, Object> transferFactory(Model model, HttpServletRequest request, @RequestBody Map<String, Object> map){
		HttpSession session = request.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        map.put("loginid",loginid); // 예: 등록자 세팅
	    }
		return purchaseService.transferFactory(map);
	}
	
	// 공장 이동 처리 - Detail
	@PostMapping("/search-movement/factory-detail")
	public Map<String, Object> searchFactoryDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search_factory_detail");
		return purchaseService.searchFactoryDetail(param);
	}
	
	// 공장 이동 처리  - Detail
	@PostMapping("/search-movement/factory-summary")
	public Map<String, Object> searchFactorySummary(@RequestBody Map<String, Object> param, HttpServletRequest request){
		log.info("search_factory_summary");
		return purchaseService.searchFactorySummary(param);
	}
	
	// 공장 이동 처리  - complete
	@PostMapping("/completeFactoryMove")
	public Map<String, Object> completeFactoryMove(@RequestBody BarcodeVO vo, HttpServletRequest request){
		log.info("search_factory_complete");
		HttpSession session = request.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        vo.setLoginid(loginid); // 예: 등록자 세팅
	    }
		return purchaseService.completeFactoryMove(vo);
	}
	
	// 불출 선입선출
	@PostMapping("/wip-fifo")
	public Map<String, List<ItemLocationVO>> wipFifo(@RequestBody BarcodeVO vo, HttpServletRequest request){
		log.info("wipFifo check");
		return purchaseService.wipFifo(vo);
	}
	
	@PostMapping("/incoming_sangho_local")
	public List<String> incomingSanghoLocal(){
		return purchaseService.incomingSanghoLocal();
	}
	
	@PostMapping("/incoming_sangho_ckd")
	public List<String> incomingSanghoCkd(){
		return purchaseService.incomingSanghoCkd();
	}
	
	@PostMapping("/incoming_sangho_exception")
	public List<String> incomingSanghoException(){
		return purchaseService.incomingSanghoException();
	}
	
	@PostMapping("/updateExceptionCheckStatus")
	public Map<String, Object> updateExceptionCheckStatus(@RequestBody Map<String, Object> param, HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        param.put("loginid",loginid); // 예: 등록자 세팅
	    }
		return purchaseService.updateExceptionCheckStatus(param);
	}

	// 반품검사 - 입고반품 / 폐기 공통
	@PostMapping("/inspectionReturn")
	public Map<String, Object> inspectionReturn(@RequestBody Map<String, Object> param, HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			String loginid = (String) session.getAttribute("loginid");
			param.put("loginid", loginid);
		}
		return purchaseService.inspectionReturn(param);
	}

	// 창고검사 - 입고반품 / 폐기 공통
	@PostMapping("/inspectionStorage")
	public Map<String, Object> inspectionStorage(@RequestBody Map<String, Object> param, HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			String loginid = (String) session.getAttribute("loginid");
			param.put("loginid", loginid);
		}
		return purchaseService.inspectionStorage(param);
	}

	// 검사 목록 조회 (창고검사/반품검사/폐기 List)
	@PostMapping("/inspection/search-list")
	public Map<String, Object> inspectionSearchList(@RequestBody Map<String, Object> param, HttpServletRequest request) {
		log.info("inspection search list");
		return purchaseService.searchInspectionList(param);
	}
	
	@PostMapping("/load_sangho")
	public List<String> loadSangho(){
		log.info("load_sangho start");
		return purchaseService.loadSangho();
	}
	
	
}