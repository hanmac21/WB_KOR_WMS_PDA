package com.example.demo.controller.sales;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.PurchaseService;
import com.example.demo.service.SalesService;
import com.example.demo.vo.BarcodeVO;
import com.example.demo.vo.ItemLocationVO;
import com.example.demo.vo.PalletDetailVO;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping("/sales")
public class SalesController{
	
	@Autowired
	SalesService salesService;
	
	@Autowired
	PurchaseService purchaseService;
	
	// 예외 입고
 	@PostMapping("/insInbound")
 	public Map<String, Object> insInbound(@RequestBody BarcodeVO vo, HttpServletRequest request2) {
 		HttpSession session = request2.getSession(false);
		if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        vo.setLoginid(loginid); // 예: 등록자 세팅
	    }

 		return salesService.insInbound(vo);
 	}
 	
 	// 영업이송 메뉴에서 바코드정보 가져오기
 	@PostMapping("/selectTrasferBarcodeInfo")
 	public Map<String, Object> selectTrasferBarcodeInfo(@RequestBody Map<String, Object> param, HttpServletRequest request2) {
 		HttpSession session = request2.getSession(false);
 			if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        param.put("loginid",loginid); // 예: 등록자 세팅
	    }
		return salesService.selectTrasferBarcodeInfo(param);
	}
 	
 	// 영업이송	
 	@PostMapping("/insSalesTransfer")
 	public Map<String, Object> insSalesTransfer(@RequestBody Map<String, Object> param, HttpServletRequest request2) {
 		HttpSession session = request2.getSession(false);
 			if (session != null) {
	        String loginid = (String) session.getAttribute("loginid");
	        param.put("loginid",loginid); // 예: 등록자 세팅
	    }
		return salesService.insSalesTransfer(param);
	}
 	
 	// 영업이송 - detail
  	@PostMapping("/sales-transfer/detail")
  	public Map<String, Object> searchSalesTransferDetail(@RequestBody BarcodeVO vo, HttpServletRequest request2) {
  		System.out.println("sales tranfer datail search");
  		return salesService.searchSalesTransferDetail(vo, request2);
  	}
  	
  	// 영업이송 내역 - detail 삭제
  	@PostMapping("/search-transfer/detail/del")
  	public Map<String, Object> searchTransferDetailDel(@RequestParam Map<String, String> paramMap, HttpServletRequest request2) {
  		HttpSession session = request2.getSession(false);
 		if (session != null) {
 	        String loginid = (String) session.getAttribute("loginid");
 	        paramMap.put("loginid",loginid); // 예: 등록자 세팅
 	    }
  		return salesService.searchTransferDetailDel(paramMap);
  	}
 	
 // 출고 insert
 	@PostMapping("/insSaleOutput")
 	public Map<String, Object> insSaleOutput(@RequestBody Map<String, Object> param, HttpServletRequest request2) {
 		log.info("sale Outbound start");
 		HttpSession session = request2.getSession(false);
 		if (session != null) {
 	        String loginid = (String) session.getAttribute("loginid");
 	       param.put("loginid",loginid); // 예: 등록자 세팅
 	    }
 		return salesService.insSaleOutput(param);
 	}
 	
// 	// 예외 입고 내역 - detail
// 	@PostMapping("/searchException/input-detail")
// 	public Map<String, Object> searchExceptionInputDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
// 		log.info("search Exception Input Detail");
// 		return salesService.searchExceptionInputDetail(param);
// 	}
// 	
// 	// 예외 출고
//  	@PostMapping("/insOutbound")
//  	public Map<String, Object> insWorkOutbound(@RequestBody BarcodeVO vo, HttpServletRequest request2) {
//  		HttpSession session = request2.getSession(false);
// 		if (session != null) {
// 	        String loginid = (String) session.getAttribute("loginid");
// 	        vo.setLoginid(loginid); // 예: 등록자 세팅
// 	    }
//
//  		return salesService.insOutbound(vo, request2);
//  	}
//
// 	// 예외 출고 내역 - detail
// 	@PostMapping("/searchException/output-detail")
// 	public Map<String, Object> searchExceptionOutputDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
// 		log.info("search Exception Output Detail");
// 		return salesService.searchExceptionOutputDetail(param);
// 	}
}