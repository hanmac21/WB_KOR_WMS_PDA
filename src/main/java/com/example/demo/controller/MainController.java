package com.example.demo.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.example.demo.service.UlsanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.demo.service.LoginService;
import com.example.demo.service.PurchaseService;
import com.example.demo.service.SalesService;
import com.example.demo.vo.LoginVO;

import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
public class MainController {

	@Autowired
	LoginService loginService;

	@Autowired
	PurchaseService purchaseService;

	@Autowired
    UlsanService ulsanService;

	@Autowired
	SalesService salesService;

	@GetMapping({ "/", "/login" })
	public String login(Model model, HttpSession session) {
		log.info("Login Page Load");
		String loginid = (String) session.getAttribute("loginid");
		model.addAttribute("loginid", loginid);
		return "/login";
	}


	@PostMapping("/loginCheck")
	@ResponseBody
	public String loginCheck(Model model, HttpServletRequest request) {
		log.info("loginCheck ▶▶▶▶▶▶▶▶");
		String loginid = request.getParameter("loginid").trim();
		String loginpw = request.getParameter("loginpw").trim();
		Map<String, Object> map = new HashMap<>();
		map.put("loginid", loginid);
		map.put("loginpw", loginpw);
		int loginChk = loginService.loginChk(map);
		HttpSession session = request.getSession();
		String result = "";
		if (loginid.equals("master") && loginpw.equals("woo#*")) { // DB연결 미연동 / 임시 로그인
			session.setAttribute("loginid", "master");
			session.setAttribute("sabun", "master");
			session.setAttribute("name", "master");
			session.setAttribute("inuser", "master");
			session.setMaxInactiveInterval(-1);
			result = "success";
		} else if (loginChk > 0) {

			session.setAttribute("loginid", loginid);
			session.setMaxInactiveInterval(-1);
			result = "success";

		} else {
			LoginVO lVo = loginService.selectLoginVO(loginid);
			if(lVo == null) {
				result = "idfail";
			}else if(!loginpw.equals(lVo.getKs_passwd())) {
				result = "pwfail";
			}else {
				result = "fail";
			}
		}
		return result;
	}

	@GetMapping({ "/index" })
	public String index(Model model, HttpSession session) {
		log.info("index Page Load");
		return "/index";
	}


	@GetMapping("/menu-ulsan")
	public String menuPurchase() {
		log.info("menu-ulsan Page Load");
		return "ulsan/menu/menu";
	}

	// 입고 중분류
	@GetMapping("/menu-ulsan/incoming")
	public String menuUlsanIncoming() {
		log.info("menu-ulsan/incoming Page Load");
		return "ulsan/menu/incoming";
	}

	// 입고등록(CKD)
	@GetMapping("/ulsan/incoming/ckd")
	public String ulsaneIncomingCKD() {
		log.info("incoming-ckd Page Load");
		return "ulsan/incoming/ckd";
	}

	// 입고 내역 - detail
	@GetMapping("/ulsan/incoming/detail")
	public String incomingDetail(Model model) {
		log.info("inbound detail page Load");
		return "ulsan/incoming/detail";
	}

	// 입고 내역 - summary
	@GetMapping("/ulsan/incoming/summary")
	public String incomingSummary(Model model) {
		log.info("inbound summary page Load");
		return "ulsan/incoming/summary";
	}

	// 입고 내역 - return
	@GetMapping("/ulsan/incoming/return")
	public String incomingReturn() {
		log.info("inbound return page Load");
		return "ulsan/incoming/return";
	}

	// 입고 내역 - return_detail
	@GetMapping("/ulsan/incoming/return_detail")
	public String incomingReturnDetail() {
		log.info("inbound return_detail page Load");
		return "ulsan/incoming/return_detail";
	}

	// 재고실사 대분류
	@GetMapping("/menu-ulsan/stock-count")
	public String menuPurchaseStockCount() {
		log.info("menu-purchase/stockCount Page Load");
		return "ulsan/menu/stockCount";
	}

	// 사내 재고실사 중분류
	@GetMapping("/ulsan/menu/stockCount-in")
	public String ulsanStockCountIn() {
		log.info("ulsan/menu/stockCount-in Page Load");
		return "ulsan/menu/stockCount-in";
	}

	// 사외 재고실사 중분류
	@GetMapping("/ulsan/menu/stockCount-out")
	public String ulsanStockCountOut() {
		log.info("ulsan/menu/stockCount-out Page Load");
		return "ulsan/menu/stockCount-out";
	}

	// 사내 재고실사
	@GetMapping("/ulsan/stock-count/in-barcode")
	public String ulsanStockCountInBarocde() {
		log.info("stock-count/in-barcode Page Load");
		return "ulsan/stock-count/in-barcode";
	}

	// 사외 재고실사
	@GetMapping("/ulsan/stock-count/out-barcode")
	public String ulsanStockCountOutBarocde() {
		log.info("stock-count/out-barcode Page Load");
		return "ulsan/stock-count/out-barcode";
	}

	// 재고현황 - detail
	@GetMapping("/ulsan/stock-count/in-detail")
	public String ulsanStockCountInDetail() {
		log.info("stock-count/detail Page Load");
		return "ulsan/stock-count/in-detail";
	}

	// 재고현황 - summary
	@GetMapping("/ulsan/stock-count/in-summary")
	public String ulsanStockCountInSummary() {
		log.info("stock-count/summary Page Load");
		return "ulsan/stock-count/in-summary";
	}

	// 재고현황 - detail
	@GetMapping("/ulsan/stock-count/out-detail")
	public String ulsanStockCountOutDetail() {
		log.info("stock-count/detail Page Load");
		return "ulsan/stock-count/out-detail";
	}

	// 재고현황 - summary
	@GetMapping("/ulsan/stock-count/out-summary")
	public String ulsanStockCountOutSummary() {
		log.info("stock-count/summary Page Load");
		return "ulsan/stock-count/out-summary";
	}




























	@GetMapping({ "/puebla-menu" })
	public String pueblaMenu(Model model, HttpSession session) {
		log.info("puebla-menu Page Load");
		String loginid = (String) session.getAttribute("loginid");
		
		model.addAttribute("loginid", loginid);
		return "/puebla/index";
	}

	@GetMapping("/menu-logistics")
	public String menuLogistics(Model model, HttpServletRequest request) {
		log.info("menu-logistics Page Load");
		return "puebla/logistics/menu";
	}
	
	@GetMapping("/menu-production")
	public String menuProduction(Model model, HttpServletRequest request) {
		log.info("menu-production Page Load");
		return "production/menu/menu";
	}
	
	@GetMapping("/menu-quality")
	public String menuQuality(Model model, HttpServletRequest request) {
		log.info("menu-quality Page Load");
		return "/quality/menu/menu";
	}


	@GetMapping("/menu-purchase")
	public String menuPurchase(Model model, HttpServletRequest request) {
		log.info("menu-purchase Page Load");
		return "purchase/menu/menu";
	}

	@GetMapping("/menu-purchase/labelPrint")
	public String menuPurchaseLabelPrint(Model model, HttpServletRequest request) {
		log.info("menu-purchase/labelPrint Page Load");
		return "purchase/menu/label";
	}
	
	@GetMapping("/menu-purchase/incoming")
	public String menuPurchaseIncoming(Model model, HttpServletRequest request) {
		log.info("menu-purchase/incoming Page Load");
		return "purchase/menu/incoming";
	}
	
	@GetMapping("/menu-purchase/load")
	public String menuPurchaseLoad(Model model, HttpServletRequest request) {
		log.info("menu-purchase/load Page Load");
		return "purchase/menu/load";
	}
	
	@GetMapping("/menu-purchase/locationManagement")
	public String menuPurchaseLocationManagement(Model model, HttpServletRequest request) {
		log.info("menu-purchase/locationManagement Page Load");
		return "purchase/menu/location";
	}
	
	@GetMapping("/menu-purchase/unpack")
	public String menuPurchaseUnpack(Model model, HttpServletRequest request) {
		log.info("menu-purchase/unpack Page Load");
		return "purchase/menu/unpack";
	}
	
	@GetMapping("/menu-purchase/wipManagement")
	public String menuPurchaseWIP(Model model, HttpServletRequest request) {
		log.info("menu-purchase/wip Page Load");
		return "purchase/menu/wip";
	}

	@GetMapping("/menu-purchase/winsor")
	public String menuPurchaseWIPWInsor(Model model, HttpServletRequest request) {
		log.info("menu-purchase/winsor Page Load");
		return "purchase/menu/winsor";
	}
	
	@GetMapping("/menu-purchase/movement")
	public String menuPurchaseMovement(Model model, HttpServletRequest request) {
		log.info("menu-purchase/movement Page Load");
		return "purchase/menu/movement";
	}
	
	@GetMapping("/menu-purchase/exception")
	public String menuPurchaseException(Model model, HttpServletRequest request) {
		log.info("menu-purchase/exception Page Load");
		return "purchase/menu/exception";
	}
	
	@GetMapping("/menu-purchase/stock-count")
	public String menuPurchaseStockCount(Model model, HttpServletRequest request) {
		log.info("menu-purchase/stockCount Page Load");
		return "purchase/menu/stockCount";
	}	

	@GetMapping("/menu-sales/stock-count")
	public String menuSalesStockCount(Model model, HttpServletRequest request) {
		log.info("menu-sales/stockCount Page Load");
		return "sales/menu/stockCount";
	}	

	// 팔레트 라벨 생성
	@GetMapping("/purchase/label/make-pallet")
	public String makePallet(Model model, HttpServletRequest request) {
		log.info("make-palletl Page Load");
		return "purchase/label/make_pallet";
	}

	// 팔레트 라벨에 파트라벨 추가
	@GetMapping("/purchase/label/pallet-management")
	public String palletManagement(Model model, HttpServletRequest request) {
		log.info("pallet-management Page Load");
		return "purchase/label/pallet_management";
	}
	
	// 팔레트 라벨 - 파트라벨 스캔
	@GetMapping("/purchase/label/pallet-management-partscan")
	public String palletManagementPartScan(Model model,String barcode) {
		log.info("pallet-management-partscan Page Load");
		model.addAttribute("barcode", barcode);
		return "purchase/label/pallet_management_partscan";
	}
	
	// 팔레트 라벨 - 팔레트 해체
	@GetMapping("/purchase/label/pallet-unbound")
	public String palletUnbound(Model model,String barcode) {
		log.info("pallet-unbound Page Load");
		return "purchase/label/pallet_unbound";
	}

	// 라벨 - 라벨 조정
	@GetMapping("/purchase/label/part-adjustment")
	public String partAdjunstment(Model model,String barcode) {
		log.info("part adjustment Page Load");
		return "purchase/label/part_adjustment";
	}

	// 라벨 - 라벨 품번 조정
	@GetMapping("/purchase/label/part-adjustment-itemcode")
	public String partAdjunstmentItemcode(Model model,String barcode) {
		log.info("part adjustment Page Load");
		return "purchase/label/part_adjustment_itemcode";
	}
	
	// 언팩
	@GetMapping("/purchase/unpack/move")
	public String unpack(Model model, HttpServletRequest request) {
		log.info("unpack Page Load");
		return "purchase/unpack/move";
	}
	
	// 언팩 확정
	@GetMapping("/purchase/unpack/complete")
	public String unpackComplete(Model model, HttpServletRequest request) {
		log.info("unpack/complete Page Load");
		return "purchase/unpack/complete";
	}
	
	@GetMapping("/purchase/unpack/complete-detail")
	public String unpackCompleteDetail(Model model,String barcode){
		log.info("unpack/complete Detail Page Load");
		model.addAttribute("barcode", barcode);
		return "purchase/unpack/complete_detail";
	}
	
	// 언팩 내역
	@GetMapping("/purchase/unpack/list")
	public String unpackList(Model model, HttpServletRequest request) {
		log.info("unpack List page Load");
		return "purchase/unpack/list";
	}
	
	// 입고등록(local)
	@GetMapping("/purchase/incoming/local")
	public String purchaseIncomingLocal(Model model, HttpServletRequest request) {
		log.info("incoming-local Page Load");
		return "purchase/incoming/local";
	}

	// 입고등록(CKD)
	@GetMapping("/purchase/incoming/ckd")
	public String purchaseIncomingCKD(Model model, HttpServletRequest request) {
		log.info("incoming-ckd Page Load");
		return "purchase/incoming/ckd";
	}
	
	// 입고 인보이스 내역 조회
	@GetMapping("/purchase/incoming/invoice")
	public String purchaseIncomingInvoice(Model model, HttpServletRequest request) {
		log.info("incoming-invoice Page Load");
		return "purchase/incoming/invoice";
	}
	
	// 입고 인보이스 조회
	@GetMapping("/purchase/incoming/invoiceScan")
	public String purchaseIncomingInvoiceScan(Model model, HttpServletRequest request) {
		log.info("incoming-invoice Page Load");
		return "purchase/incoming/invoice_scan";
	}

	// 입고 내역 - detail
	@GetMapping("/purchase/incoming/detail")
	public String incomingDetail(Model model, HttpServletRequest request) {
		log.info("inbound detail page Load");
		List<String> cust = purchaseService.incomingSanghoException();
		model.addAttribute("cust", cust);
		return "purchase/incoming/detail";
	}

	// 입고 내역 - summary
	@GetMapping("/purchase/incoming/summary")
	public String incomingSummary(Model model, HttpServletRequest request) {
		log.info("inbound summary page Load");
		List<String> cust = purchaseService.incomingSanghoException();
		model.addAttribute("cust", cust);
		return "purchase/incoming/summary";
	}
	
	// 입고 내역 - return
	@GetMapping("/purchase/incoming/return")
	public String incomingReturn(Model model, HttpServletRequest request) {
		log.info("inbound return page Load");
		return "purchase/incoming/return";
	}
	
	// 입고 내역 - return_detail
	@GetMapping("/purchase/incoming/return_detail")
	public String incomingReturnDetail(Model model, HttpServletRequest request) {
		log.info("inbound return_detail page Load");
		return "purchase/incoming/return_detail";
	}
	
	// 출고 - detail
	@GetMapping("/purchase/load/load-detail")
	public String purchaseLoadDetail(Model model, HttpServletRequest request) {
		log.info("load - detail page Load");
		return "purchase/load/detail";
	}
	
	// 출고 반품 - detail
	@GetMapping("/purchase/load/return-detail")
	public String purchaseLoadReturnDetail(Model model, HttpServletRequest request) {
		log.info("load return - detail page Load");
		return "purchase/load/return_detail";
	}
	
	// 예외 출고
	@GetMapping("/purchase/exception/input")
	public String exceptionInput(Model model, HttpServletRequest request) {
		log.info("exception input page Load");
		return "purchase/exception/input";
	}
		
	// 예외 입고 내역 - detail
	@GetMapping("/purchase/exception/input-detail")
	public String exceptionInputDetail(Model model, HttpServletRequest request) {
		log.info("exception input page Load");
		return "purchase/exception/input_detail";
	}
	
	// 예외 출고
	@GetMapping("/purchase/exception/output")
	public String exceptionOutput(Model model, HttpServletRequest request) {
		log.info("exception output page Load");
		return "purchase/exception/output";
	}
	
	// 예외 출고 내역 - detail
	@GetMapping("/purchase/exception/output-detail")
	public String exceptionOutputDetail(Model model, HttpServletRequest request) {
		log.info("exception output page Load");
		return "purchase/exception/output_detail";
	}
	
	// 1. 페이지 렌더링용 컨트롤러
	@GetMapping("/purchase/location/status")
	public String purchaseLocationViewPage(Model model, HttpServletRequest request) {
		log.info("Location Status Page Load");
		// 필요한 초기 데이터나 설정값들을 model에 추가
		return "purchase/location/status"; // HTML 페이지 반환
	}

	@GetMapping("/purchase/location/unloaded")
	public String locationUnloaded(Model model, HttpServletRequest request) {
		log.info("Location unloaded Page Load");
		// 필요한 초기 데이터나 설정값들을 model에 추가
		return "purchase/location/unloaded_item"; // HTML 페이지 반환
	}

//	// 2. 데이터 조회용 API
//	@GetMapping("/api/purchase/location-data")
//	@ResponseBody
//	public ResponseEntity<Map<String, Object>> getPurchaseLocationData(@RequestParam Map<String, String> filters) {
//
//		log.info("Location data API called with filters: {}", filters);
//
//		try {
//			String storage = filters.getOrDefault("storage", "default");
//			String factory = filters.getOrDefault("factory", "default");
//			String searchType = filters.getOrDefault("searchType", "default");
//			String keyword = filters.getOrDefault("keyword", "");
//
//			List<Map<String, Object>> rackList = purchaseService.getRackList(storage, factory, searchType, keyword);
//			Map<String, Object> summary = calculateSummary(rackList);
//
//			Map<String, Object> response = new HashMap<>();
//			response.put("success", true);
//			response.put("message", "RACK 목록 조회 성공");
//			response.put("data", rackList);
//			response.put("summary", summary);
//			response.put("timestamp", new Date());
//
//			return ResponseEntity.ok(response);
//
//		} catch (Exception e) {
//			Map<String, Object> errorResponse = new HashMap<>();
//			errorResponse.put("success", false);
//			errorResponse.put("message", "RACK 목록 조회 실패: " + e.getMessage());
//			errorResponse.put("data", new ArrayList<>());
//			errorResponse.put("error", e.getClass().getSimpleName());
//			errorResponse.put("timestamp", new Date());
//
//			return ResponseEntity.status(500).body(errorResponse);
//		}
//	}

	@GetMapping("/purchase/location/load")
	public String purchaseLocationStorage(Model model, HttpServletRequest request) {
		log.info("location/load Page Load");
//		Map<String, Object> result = new HashMap<String, Object>();
//		List<String> factory = purchaseService.selFactory();
//		List<String> storage = purchaseService.selStorage();
//		List<String> rack = purchaseService.selRack();
//		List<String> module = purchaseService.selModule();
//		List<String> levelcode = purchaseService.selLevelCode();
//		List<String> position = purchaseService.selPosition();
//		model.addAttribute("factory", factory);
//		model.addAttribute("storage", storage);
//		model.addAttribute("rack", rack);
//		model.addAttribute("module", module);
//		model.addAttribute("levelcode", levelcode);
//		model.addAttribute("position", position);

		List<String> car = purchaseService.selCar();
		model.addAttribute("rack", car);
		return "purchase/location/load";
	}

	// 재고현황 - detail
	@GetMapping("/purchase/stock-count/detail")
	public String purchaseStockCountDetail(Model model, HttpServletRequest request) {
		log.info("stock-count/detail Page Load");
		List<String> factory = purchaseService.selFactory();
		List<String> storage = purchaseService.selStorage();
		model.addAttribute("factory", factory);
		model.addAttribute("storage", storage);
		return "purchase/stock-count/detail";
	}

	// 재고현황 - summary
	@GetMapping("/purchase/stock-count/summary")
	public String purchaseStockCountSummary(Model model, HttpServletRequest request) {
		log.info("stock-count/summary Page Load");
		List<String> factory = purchaseService.selFactory();
		List<String> storage = purchaseService.selStorage();
		model.addAttribute("factory", factory);
		model.addAttribute("storage", storage);
		return "purchase/stock-count/summary";
	}
	
	@GetMapping("/purchase/menu/stockCount-always")
	public String purchaseStockCountAlways(Model model, HttpServletRequest request) {
		log.info("purchase/menu/stockCount-always Page Load");
		return "purchase/menu/stockCount-always";
	}

	@GetMapping("/purchase/menu/stockCount-lastday")
	public String purchaseStockCountLastday(Model model, HttpServletRequest request) {
		log.info("purchase/menu/stockCount-lastday Page Load");
		return "purchase/menu/stockCount-lastday";
	}

	@GetMapping("/purchase/stock-count/barcode")
	public String purchaseStockCountBarocde(Model model, HttpServletRequest request) {
		log.info("stock-count/barcode Page Load");
		return "purchase/stock-count/barcode";
	}
	
	@GetMapping("/purchase/stock-count/location")
	public String purchaseStockCountLocation(Model model, HttpServletRequest request) {
		log.info("stock-count/location Page Load");
//		Map<String, Object> result = new HashMap<String, Object>();
//		List<String> factory = purchaseService.selFactory();
//		List<String> storage = purchaseService.selStorage();
//		List<String> rack = purchaseService.selRack();
//		List<String> module = purchaseService.selModule();
//		List<String> levelcode = purchaseService.selLevelCode();
//		List<String> position = purchaseService.selPosition();
//		model.addAttribute("factory", factory);
//		model.addAttribute("storage", storage);
//		model.addAttribute("rack", rack);
//		model.addAttribute("module", module);
//		model.addAttribute("levelcode", levelcode);
//		model.addAttribute("position", position);

		List<String> car = purchaseService.selCar();
		model.addAttribute("rack", car);
		return "purchase/stock-count/location";
	}
	
	@GetMapping("/purchase/stock-count/lastDay_barcode")
	public String purchaseStockCountLastDayBarcode(Model model, HttpServletRequest request) {
		log.info("stock-count/lastDay Page Load");
		return "purchase/stock-count/lastDay_barcode";
	}
	
	@GetMapping("/purchase/stock-count/lastDay_location")
	public String purchaseStockCountLastDayLocation(Model model, HttpServletRequest request) {
		log.info("stock-count/lastDay Page Load");
		Map<String, Object> result = new HashMap<String, Object>();
		List<String> factory = purchaseService.selFactory();
		List<String> storage = purchaseService.selStorage();
		List<String> rack = purchaseService.selRack();
		List<String> module = purchaseService.selModule();
		List<String> levelcode = purchaseService.selLevelCode();
		List<String> position = purchaseService.selPosition();
		model.addAttribute("factory", factory);
		model.addAttribute("storage", storage);
		model.addAttribute("rack", rack);
		model.addAttribute("module", module);
		model.addAttribute("levelcode", levelcode);
		model.addAttribute("position", position);

		return "purchase/stock-count/lastDay_location";
	}
	
	// 공정불출
	@GetMapping("/purchase/wip/input")
	public String purchaseMaterialIssue(Model model, HttpServletRequest request) {
		log.info("wip/input Page Load");

//		Map<String, Object> result = new HashMap<String, Object>();
//		List<String> factory = purchaseService.selFactory();
//		List<String> storage = purchaseService.selStorage();
//		List<String> rack = purchaseService.selRack();
//		List<String> module = purchaseService.selModule();
//		List<String> levelcode = purchaseService.selLevelCode();
//		List<String> position = purchaseService.selPosition();
//		model.addAttribute("factory", factory);
//		model.addAttribute("storage", storage);
//		model.addAttribute("rack", rack);
//		model.addAttribute("module", module);
//		model.addAttribute("levelcode", levelcode);
//		model.addAttribute("position", position);
		return "purchase/wip/input";
	}
	
	// 공정불출(푸에블라용)
	@GetMapping("/puebla/purchase/wip/input")
	public String pueblaPurchaseMaterialIssue(Model model, HttpServletRequest request) {
		log.info("puebla/purchase/wip/input Page Load");
		return "puebla/purchase/wip/input";
	}
	
	
	// 공정불출 미도착
	@GetMapping("/purchase/wip/input-notarrived")
	public String purchaseWIPInputNotArrived(Model model, HttpServletRequest request) {
		return "purchase/wip/input_notarrived";
	}
	
	// 공정불출 완료
	@GetMapping("/purchase/wip/input-completed")
	public String purchaseWIPInputCompleted(Model model, HttpServletRequest request) {
		return "purchase/wip/input_completed";
	}

	// 공정불출 내역 - Detail
	@GetMapping("/purchase/wip/input-detail")
	public String purchaseWIPInputDetail(Model model, HttpServletRequest request) {
		return "purchase/wip/input_detail";
	}
	
	// 공정불출 내역 - Summary
	@GetMapping("/purchase/wip/input-summary")
	public String purchaseWIPInputSummary(Model model, HttpServletRequest request) {
		return "purchase/wip/input_summary";
	}
	
	// 공정불출반납
	@GetMapping("/purchase/wip/return")
	public String purchaseWIPReturn(Model model, HttpServletRequest request) {
		log.info("WIP return Page Load");

		Map<String, Object> result = new HashMap<String, Object>();
		List<String> factory = purchaseService.selFactory();
		List<String> storage = purchaseService.selStorage();
		model.addAttribute("factory", factory);
		model.addAttribute("storage", storage);
		return "purchase/wip/return";
	}
	
	// 공정불출 반납내역 - detail
	@GetMapping("/purchase/wip/return-detail")
	public String purchaseWIPReturnDetail(Model model, HttpServletRequest request) {
		return "purchase/wip/return_detail";
	}
	
	// 공정불출 반납내역 - summary
	@GetMapping("/purchase/wip/return-summary")
	public String purchaseWIPReturnSummary(Model model, HttpServletRequest request) {
		return "purchase/wip/return_summary";
	}
	
	// 불출 피딩
	@GetMapping("/purchase/wip/feeding")
	public String purchaseWIPFeeding(Model model, HttpServletRequest request) {
		log.info("WIP return Page Load");

		Map<String, Object> result = new HashMap<String, Object>();
		List<String> factory = purchaseService.selFactory();
		List<String> storage = purchaseService.selStorage();
		model.addAttribute("factory", factory);
		model.addAttribute("storage", storage);
		return "purchase/wip/feeding";
	}
	
	// 불출 피딩내역 - detail
	@GetMapping("/purchase/wip/feeding-detail")
	public String purchaseWIPFeedingDetail(Model model, HttpServletRequest request) {
		return "purchase/wip/feeding_detail";
	}
	
	// 불출 피딩내역 - summary
	@GetMapping("/purchase/wip/feeding-summary")
	public String purchaseWIPFeedingSummary(Model model, HttpServletRequest request) {
		return "purchase/wip/feeding_summary";
	}
	
	// 창고 이동 처리
	@GetMapping("/purchase/movement/warehouse")
	public String purchaseMovementWarehouse(Model model, HttpServletRequest request) {
		return "purchase/movement/warehouse";
	}

	// 외부 창고 이동 출고
	@GetMapping("/purchase/movement/warehouseout/out")
	public String purchaseMovementWarehouseoutOut(Model model, HttpServletRequest request) {
		return "purchase/movement/warehouseoutOut";
	}

	// 외부 창고 이동 입고
	@GetMapping("/purchase/movement/warehouseout/in")
	public String purchaseMovementWarehouseoutIn(Model model, HttpServletRequest request) {
		return "purchase/movement/warehouseoutIn";
	}
	
	// 품질 이동 처리
	@GetMapping("/quality/movement/warehouse")
	public String qualityMovementWarehouse(Model model, HttpServletRequest request) {
		return "quality/movement/warehouse";
	}
	
	// 창고 이동 처리 - detail
	@GetMapping("/purchase/movement/warehouse-detail")
	public String purchaseMovementWarehouseDetail(Model model, HttpServletRequest request) {
		return "purchase/movement/warehouse_detail";
	}
	
	// 창고 이동 처리 - summary
	@GetMapping("/purchase/movement/warehouse-summary")
	public String purchaseMovementWarehouseSummary(Model model, HttpServletRequest request) {
		return "purchase/movement/warehouse_summary";
	}
	
	// 공장 이동 
	@GetMapping("/purchase/movement/factory")
	public String purchaseMovementFactory(Model model, HttpServletRequest request) {
		return "purchase/movement/factory";
	}
	
	// 공장 이동 처리 - detail
	@GetMapping("/purchase/movement/factory-detail")
	public String purchaseMovementFactoryDetail(Model model, HttpServletRequest request) {
		return "purchase/movement/factory_detail";
	}
	
	// 공장 이동 처리 - summary
	@GetMapping("/purchase/movement/factory-summary")
	public String purchaseMovementFactorySummary(Model model, HttpServletRequest request) {
		return "purchase/movement/factory_summary";
	}
	
	// 공장 이동 처리 - complete
	@GetMapping("/purchase/movement/factory-complete")
	public String purchaseMovementFactoryComplete(Model model, HttpServletRequest request) {
		return "purchase/movement/factory_complete";
	}
		
	// 재고 조회 - detail
	@GetMapping("/purchase/stock-detail")
	public String purchaseStockDetail(Model model, HttpServletRequest request) {
		log.info("stock-detail Page Load");
		return "purchase/stock_detail";
	}

	// 재고 조회 - detail
	@GetMapping("/quality/stock-detail")
	public String qualityStockDetail(Model model, HttpServletRequest request) {
		log.info("quality stock-detail Page Load");
		return "quality/stock/stock_detail";
	}
		
	// 재고정보
	@GetMapping("/purchase/stock-info")
	public String purchaseStockInfo(Model model, HttpServletRequest request) {
		log.info("stock-info Page Load");
		return "purchase/stock_info";
	}

	// 품질 이송처리 메뉴
	@GetMapping("/quality/menu/movement")
	public String qualityMovement(Model model, HttpServletRequest request) {
		log.info("quality movement menu Page Load");
		return "/quality/menu/movement";
	}

	// 품질 불량샌상실적 중메뉴
	@GetMapping("/quality/menu/production")
	public String qualityProductionMenu(Model model, HttpServletRequest request) {
		log.info("quality product menu Page Load");
		return "/quality/menu/production";
	}
	
	// 품질 불량샌상실적 등록 메뉴
	@GetMapping("/quality/production")
	public String qualityProduction(Model model, HttpServletRequest request) {
		log.info("quality product Page Load");
		return "/quality/product-ng/production";
	}

	// NG재고정보
	@GetMapping("/quality/stock-info-ng")
	public String qualityStockInfoNG(Model model, HttpServletRequest request) {
		log.info("stock-info-ng Page Load");
		return "/puebla/quality/stock_info_ng";
	}
	
	// 품질 출고반품 불량 중메뉴
	@GetMapping("/quality/menu/return")
	public String qualityReturnMenu(Model model, HttpServletRequest request) {
		log.info("quality return menu Page Load");
		return "/quality/menu/return";
	}
	// 품질 출고반품 불량 등록 메뉴
	@GetMapping("/quality/return")
	public String qualityReturn(Model model, HttpServletRequest request) {
		log.info("quality return Page Load");
		return "/quality/return-ng/return";
	}
	
	// 재고변동내역
	@GetMapping("/purchase/stock-history")
	public String purchaseStockHistory(Model model, HttpServletRequest request) {
		log.info("stock-History Page Load");
		return "purchase/stock_history";
	}	
	
	// 원단
	@GetMapping("/menu-fabric")
	public String fabricMenu(Model model, HttpServletRequest request) {
		log.info("menu-fabric Page Load");
		return "fabric/menu/menu";
	}
	
	// 출고
	@GetMapping("/fabric/product-shipment")
	public String productShipment(Model model, HttpServletRequest request) {
		log.info("product-shipment Page Load");
		List<String> cust = salesService.selCust();
		model.addAttribute("cust", cust);
		return "fabric/product_shipment";
	}
	
	// 원단 - 출고 반납
	@GetMapping("/fabric/load/return")
	public String loadReturn(Model model, HttpServletRequest request) {
		log.info("load return Page Load");
		return "fabric/load/return";
	}
	
	// 구매 - 출고 반납
	@GetMapping("/purchase/load/return")
	public String purchaseLoadReturn(Model model, HttpServletRequest request) {
		log.info("purchaseLoadReturn Page Load");
		return "purchase/load/return";
	}
	
	// 구매 - 출고
	@GetMapping("/purchase/load/load")
	public String purchaseLoadLoad(Model model, HttpServletRequest request) {
		log.info("Load Page Load");
		return "purchase/load/load";
	}

	// 구매 - transys GA 출고
	@GetMapping("/purchase/load/loadTransysGA")
	public String purchaseLoadLoadTransysGA(Model model, HttpServletRequest request) {
		log.info("Load Page LoadTransys - GA");
		return "purchase/load/load_transys_ga";
	}

	// 구매 - transys AL 출고
	@GetMapping("/purchase/load/loadTransysAL")
	public String purchaseLoadLoadTransysAL(Model model, HttpServletRequest request) {
		log.info("Load Page LoadTransys - AL");
		return "purchase/load/load_transys_al";
	}

	// 구매 - transys IL 출고
	@GetMapping("/purchase/load/loadTransysIL")
	public String purchaseLoadLoadTransysIL(Model model, HttpServletRequest request) {
		log.info("Load Page LoadTransys - IL");
		return "purchase/load/load_transys";
	}

	// 구매 - LEAR 출고
	@GetMapping("/purchase/load/loadLear")
	public String purchaseLoadLoadLear(Model model, HttpServletRequest request) {
		log.info("Load Page Load Lear");
		return "purchase/load/load_lear";
	}

	// 품질 중메뉴
	@GetMapping("/menu-purchase/inspection")
	public String menuPurchaseIspection(Model model, HttpServletRequest request) {
		log.info("menu-purchase/inspection Page Load");
		return "purchase/menu/inspection";
	}

	// 품질 - 창고검사
	@GetMapping("/purchase/inspection/storage")
	public String purchaseInsepctionStorage(Model model, HttpServletRequest request) {
		log.info("/purchase/inspection/storage Page Load");
		return "purchase/inspection/inspection_storage";
	}

	// 품질 - 반품검사
	@GetMapping("/purchase/inspection/return")
	public String purchaseInsepctionReturn(Model model, HttpServletRequest request) {
		log.info("/purchase/inspection/return Page Load");
		return "purchase/inspection/inspection_return";
	}

	// 품질 - 창고검사 List
	@GetMapping("/purchase/inspection/storage/list")
	public String purchaseInspectionStorageList(Model model, HttpServletRequest request) {
		log.info("/purchase/inspection/storage/list Page Load");
		return "purchase/inspection/inspection_storage_list";
	}

	// 품질 - 반품검사 List
	@GetMapping("/purchase/inspection/return/list")
	public String purchaseInspectionReturnList(Model model, HttpServletRequest request) {
		log.info("/purchase/inspection/return/list Page Load");
		return "purchase/inspection/inspection_return_list";
	}

	// 품질 - 폐기 List
	@GetMapping("/purchase/inspection/dispose/list")
	public String purchaseInspectionDisposeList(Model model, HttpServletRequest request) {
		log.info("/purchase/inspection/dispose/list Page Load");
		return "purchase/inspection/inspection_dispose_list";
	}

	// 구매 - ADIENT 출고
	@GetMapping("/purchase/load/loadAdient")
	public String purchaseLoadLoadAdient(Model model, HttpServletRequest request) {
		log.info("Load Page Load Adient");
		return "purchase/load/load_adient";
	}
	
	// 생산 - 메뉴/생산
	@GetMapping("/menu-production/production")
	public String menuProductionProduction(Model model, HttpServletRequest request) {
		log.info("menuProductionProduction Html Load");
		return "/production/menu/production";
	}
	
	// 생산 - 메뉴/로케이션관리
	@GetMapping("/menu-production/locationManagement")
	public String menuProductionLocationManagement(Model model, HttpServletRequest request) {
		log.info("menu-production/locationManagement Page Load");
		return "production/menu/location";
	}
	
	// 생산 - 메뉴/공정불출
	@GetMapping("/menu-production/wipManagement")
	public String menuProductionWip(Model model, HttpServletRequest request) {
		log.info("menu-production/wip Page Load");
		return "/production/menu/wip";
	}
	
	// 생산 - 메뉴/예외처리
	@GetMapping("/menu-production/exception")
	public String menuProductionException(Model model, HttpServletRequest request) {
		log.info("menu-production/exception Page Load");
		return "/production/menu/exception";
	}
	
	// 생산 - 메뉴/재고실사
	@GetMapping("/menu-production/stockCount")
	public String menuProductionStockCount(Model model, HttpServletRequest request) {
		log.info("menu-production/stockCount Page Load");
		return "/production/menu/stockCount";
	}
		
	// 생산 - 생산/제품 생산
	@GetMapping("/production/production")
	public String productionProduction(Model model, HttpServletRequest request) {
		log.info("Production Load");
		return "/production/product/production";
	}
	
	// 생산 - 생산/제품 생산 실적 - detail
	@GetMapping("/production/production-detail")
	public String productionProductionDetail(Model model, HttpServletRequest request) {
		log.info("Production detail Load");
		return "/production/product/production_detail";
	}	
	
	// 생산 - 생산/제품 생산 실적 - summary
	@GetMapping("/production/production-summary")
	public String productionProductionSummary(Model model, HttpServletRequest request) {
		log.info("Production summary Load");
		return "/production/product/production_summary";
	}	
	
	// 생산 반제품 실적
	@GetMapping("/production/semiproduction")
	public String semiProductionHtml(Model model, HttpServletRequest request) {
		log.info("semiProduction Html Load");
		return "/production/product/semiProduction";
	}
	
	// 생산실적 디테일
	@GetMapping("/production/semiproduction-detail")
	public String semiProductionHtmlDetail(Model model, HttpServletRequest request) {
		log.info("semiProduction Html detail Load");
		return "/production/product/semiProduction_detail";
	}
	
	// 생산실적 요약
	@GetMapping("/production/semiproduction-summary")
	public String semiProductionHtmlSummary(Model model, HttpServletRequest request) {
		log.info("semiProduction Html summary Load");
		return "/production/product/semiProduction_summary";
	}
	
	// 생산 - 로케이션/적재
	@GetMapping("/production/location/load")
	public String productionLocationLoad(Model model, HttpServletRequest request) {
		log.info("Location Load Page Load");
//		List<String> factory = purchaseService.selFactory();
//		List<String> storage = purchaseService.selStorage();
//		List<String> rack = purchaseService.selRack();
//		List<String> module = purchaseService.selModule();
//		List<String> levelcode = purchaseService.selLevelCode();
//		List<String> position = purchaseService.selPosition();
//		model.addAttribute("factory", factory);
//		model.addAttribute("storage", storage);
//		model.addAttribute("rack", rack);
//		model.addAttribute("module", module);
//		model.addAttribute("levelcode", levelcode);
//		model.addAttribute("position", position);
		return "/production/location/load";
	}
	
	// 생산 - 로케이션/현황
	@GetMapping("/production/location/status")
	public String productionLocationStatus(Model model, HttpServletRequest request) {
		log.info("Location Status Load");
		return "/production/location/status";
	}
	
	// 생산 - 로케이션/미적재
	@GetMapping("/production/location/unloaded")
	public String productionLocationUnloadedItems(Model model, HttpServletRequest request) {
		log.info("Location Unloaded Items Load");
		return "/production/location/unloaded_item";
	}
	
	// 생산 - 예외 입고
	@GetMapping("/production/exception/input")
	public String productionExceptionInput(Model model, HttpServletRequest request) {
		log.info("exception input page Load");
		return "production/exception/input";
	}
		
	// 생산 - 예외 입고 내역 - detail
	@GetMapping("/production/exception/input-detail")
	public String productionExceptionInputDetail(Model model, HttpServletRequest request) {
		log.info("exception input page Load");
		return "production/exception/input_detail";
	}
	
	// 생산 - 예외 출고
	@GetMapping("/production/exception/output")
	public String productionExceptionOutput(Model model, HttpServletRequest request) {
		log.info("exception output page Load");
		return "production/exception/output";
	}
	
	// 생산 - 예외 출고 내역 - detail
	@GetMapping("/production/exception/output-detail")
	public String productionExceptionOutputDetail(Model model, HttpServletRequest request) {
		log.info("exception output page Load");
		return "production/exception/output_detail";
	}
	
	// 생산 - 재고실사/바코드
	@GetMapping("/production/stock-count/barcode")
	public String productionStockCountBarcode(Model model, HttpServletRequest request) {
		log.info("Production Stock Count Barcode page Load");
		return "production/stock-count/barcode";
	}
	
	// 생산 - 재고실사/로케이션
	@GetMapping("/production/stock-count/location")
	public String productionStockCountLocation(Model model, HttpServletRequest request) {
		log.info("Production Stock Count Location page Load");
		return "production/stock-count/location";
	}
	
	// 생산 - 재고실사/재고현황 - detail
	@GetMapping("/production/stock-count/detail")
	public String productionStockCountDetail(Model model, HttpServletRequest request) {
		log.info("stock-count/detail Page Load");
		return "production/stock-count/detail";
	}

	// 생산 - 재고실사/재고현황 - summary
	@GetMapping("/production/stock-count/summary")
	public String productionStockCountSummary(Model model, HttpServletRequest request) {
		log.info("stock-count/summary Page Load");
		return "production/stock-count/summary";
	}
	
	// 생산 - 재고조회 - detail
	@GetMapping("/production/stock-detail")
	public String productionStockDetail(Model model, HttpServletRequest request) {
		log.info("Stock Detail Page Load");
		return "production/stock_detail";
	}
	
	// 생산 - 재고정보
	@GetMapping("/production/stock-info")
	public String productionStockInfo(Model model, HttpServletRequest request) {
		log.info("Stock Info Page Load");
		return "production/stock_info";
	}
	
	// 영업 - 메뉴/영업
	@GetMapping("/menu-sales")
	public String salesMenu(Model model, HttpServletRequest request) {
		log.info("menu-sales Page Load");
		return "sales/menu/menu";
	}
	
	// 영업 - 메뉴/재고이송
	@GetMapping("/menu-sales/movement")
	public String menuSalesMovement(Model model, HttpServletRequest request) {
		log.info("menu-sales/movement Page Load");
		return "/sales/menu/movement";
	}

	// 영업 - 메뉴/재고이송
	@GetMapping("/menu-sales/load")
	public String menuSalesLoad(Model model, HttpServletRequest request) {
		log.info("menu-sales/load Page Load");
		return "/sales/menu/load";
	}

	// 영업 - 메뉴/재고이송
	@GetMapping("/menu-sales/incoming")
	public String menuSalesIncoming(Model model, HttpServletRequest request) {
		log.info("menu-sales/incoming Page Load");
		return "/sales/menu/incoming";
	}
	
	// 영업 - 메뉴/예외처리
	@GetMapping("/menu-sales/exception")
	public String menuSalesException(Model model, HttpServletRequest request) {
		log.info("menu-sales/exception Page Load");
		return "/sales/menu/exception";
	}
	
	// 영업 - 예외 입고
	@GetMapping("/sales/exception/input")
	public String salesExceptionInput(Model model, HttpServletRequest request) {
		log.info("exception input page Load");
		return "sales/exception/input";
	}
	
	// 영업 - 메뉴/영업이송
	@GetMapping("/menu-sales/sales-transfer")
	public String salesTransferMenu(Model model, HttpServletRequest request) {
		log.info("sales transfer menu page Load");
		return "sales/menu/salesTransfer";
	}
	
	// 영업 - 영업이송
	@GetMapping("/sales/salesTranfer")
	public String salesTransfer(Model model, HttpServletRequest request) {
		log.info("sales transfer page Load");
		return "sales/sales-transfer/salesTransfer";
	}

	// 영업 - 영업이송
	@GetMapping("/sales/salesTranfer/detail")
	public String salesTransferDetail(Model model, HttpServletRequest request) {
		log.info("sales transfer detail page Load");
		return "sales/sales-transfer/detail";
	}

	// 영업 - 출고 - 출고
	@GetMapping("/sales/load/load")
	public String salesLoad(Model model, HttpServletRequest request) {
		log.info("sales load page Load");
		return "sales/load/load";
	}
	
	// 품질/ 불량메뉴
	@GetMapping("/quality/defective")
	public String detectiveMenu(Model model, HttpServletRequest request) {
		log.info("defective menu Load");
		return "/quality/menu/defective";
	}

	// 품질/ 판정메뉴
	@GetMapping("/quality/judgment")
	public String judgmentMenu(Model model, HttpServletRequest request) {
		log.info("judgment menu Load");
		return "/quality/menu/inspection";
	}

	// 품질/ 재고실사메뉴
	@GetMapping("/quality/stock-count")
	public String qualityStockCount(Model model, HttpServletRequest request) {
		log.info("qualityStockCount menu Load");
		return "/puebla/quality/menu/stockCount";
	}

	// 품질 푸에블라 판정메뉴
	@GetMapping("/quality/saltillo/inspection")
	public String saltilloInspectionMenu(Model model, HttpServletRequest request) {
		log.info("/quality/saltillo/inspection menu Load");
		return "/quality/menu/inspection";
	}

	// 품질 푸에블라 판정메뉴
	@GetMapping("/quality/puebla/inspection")
	public String pueblaInspectionMenu(Model model, HttpServletRequest request) {
		log.info("/quality/puebla/inspection menu Load");
		return "/puebla/quality/menu/inspection";
	}

	@GetMapping("/quality/inspection/importinspection")
	public String importinspection(Model model, HttpServletRequest request) {
		log.info("inspection/importinspection Load");
		return "/quality/inspection/importinspection";
	}

	// 푸에블라 수입검사메뉴
	@GetMapping("/puebla/quality/inspection/incominginspection")
	public String pueblaIncomingInspection(Model model, HttpServletRequest request) {
		log.info("puebla/quality/inspection/incominginspection Load");
		return "/puebla/quality/inspection/incominginspection";
	}
	
	// 푸에블라 공정검사메뉴
	@GetMapping("/puebla/quality/inspection/processinspection")
	public String pueblaProcessInspection(Model model, HttpServletRequest request) {
		log.info("puebla/quality/inspection/processinspection Load");
		return "/puebla/quality/inspection/processinspection";
	}
	
	// 푸에블라 창고검사메뉴
	@GetMapping("/puebla/quality/inspection/storageinspection")
	public String pueblaStorageInspection(Model model, HttpServletRequest request) {
		log.info("puebla/quality/inspection/storageinspection Load");
		return "/puebla/quality/inspection/storageinspection";
	}
	
	// 푸에블라 반품검사메뉴
	@GetMapping("/puebla/quality/inspection/returninspection")
	public String pueblaReturnInspection(Model model, HttpServletRequest request) {
		log.info("puebla/quality/inspection/returninspection Load");
		return "/puebla/quality/inspection/returninspection";
	}

	@GetMapping("/quality/inspection/incominginspection")
	public String incomimginspection(Model model, HttpServletRequest request) {
		log.info("inspection/incoming inspection Load");
		return "/quality/inspection/incominginspection";
	}
	@GetMapping("/quality/inspection/processinspection")
	public String processinspection(Model model, HttpServletRequest request) {
		log.info("inspection/process inspection Load");
		return "/quality/inspection/processinspection";
	}
	
	@GetMapping("/quality/inspection/storageinspection")
	public String warehouseinspection(Model model, HttpServletRequest request) {
		log.info("inspection/warehouseinspection Load");
		return "/quality/inspection/storageinspection";
	}
	
	@GetMapping("/quality/inspection/returninspection")
	public String returninspection(Model model, HttpServletRequest request) {
		log.info("inspection/returninspection Load");
		return "/quality/inspection/returninspection";
	}
	
	// 품질/ 불량 - detail
	@GetMapping("/quality/inspection/detail")
	public String inspectionDetail(Model model, HttpServletRequest request) {
		log.info("Inspection Detail Load");
		return "/quality/inspection/detail";
	}
	
	// 품질/ 불량메뉴
	@GetMapping("/quality/defective/defective")
	public String detective(Model model, HttpServletRequest request) {
		log.info("defective Load");
		return "/quality/defective/defective";
	}
	
	// 품질/ 불량 - detail
	@GetMapping("/quality/defective/detail")
	public String detectiveDetail(Model model, HttpServletRequest request) {
		log.info("Defective Detail Load");
		return "/quality/defective/detail";
	}
	
	// 품질/ 불량 - summary
	@GetMapping("/quality/defective/summary")
	public String detectiveSummary(Model model, HttpServletRequest request) {
		log.info("Defective Summary Load");
		return "/quality/defective/summary";
	}
	
	// 원단/ 아운데메뉴 창고이동
	@GetMapping("/fabric/aunde")
	public String aundeMenu(Model model, HttpServletRequest request) {
		log.info("aundeMenu Load");
		return "/fabric/menu/aunde";
	}
	
	// 원단/ 푸에블라메뉴 공장이송
	@GetMapping("/fabric/winsor")
	public String winsorMenu(Model model, HttpServletRequest request) {
		log.info("winsorMenu Load");
		return "/fabric/menu/winsor";
	}

	// 원단/ 윈저메뉴 불출
	@GetMapping("/fabric/puebla")
	public String pueblaMenu(Model model, HttpServletRequest request) {
		log.info("pueblaMenu Load");
		return "/fabric/menu/puebla";
	}
	@PostMapping("/getStatus")
	@ResponseBody 
	public Map<String, Object> getStatus(Model model, HttpServletRequest request) {
		return purchaseService.getStatus(request);
	}

	@GetMapping("/purchase/validation")
	public String validation(Model model, HttpServletRequest request) {
		log.info("validation");
		return "/purchase/validation/validation_transys";
	}
	
}