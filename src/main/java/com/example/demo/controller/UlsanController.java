package com.example.demo.controller;

import com.example.demo.service.UlsanService;
import com.example.demo.vo.BarcodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/ulsan")
public class UlsanController {

    private final UlsanService ulsanService;

    // 입고 처리
    @PostMapping("/insInbound")
    public Map<String, Object> insInbound(@RequestBody BarcodeVO vo, HttpServletRequest request2) {
        HttpSession session = request2.getSession(false);
        if (session != null) {
            String loginid = (String) session.getAttribute("loginid");
            vo.setLoginid(loginid); // 예: 등록자 세팅
        }
        return ulsanService.insInbound(vo);
    }

    // 입고현황 - Detail
    @PostMapping("/search-incoming-detail")
    public Map<String, Object> incomingDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
        log.info("search incoming detail");
        return ulsanService.searchIncomingDetail(param);
    }

    // 입고현황 - Summary
    @PostMapping("/search-incoming-summary")
    public Map<String, Object> incomingSummary(@RequestBody Map<String, Object> param, HttpServletRequest request){
        log.info("search incoming summary");
        return ulsanService.searchIncomingSummary(param);
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

        return ulsanService.insRealStock(request);
    }

    // 재고실사현황 - Detail
    @PostMapping("/search-stock-count/detail")
    public Map<String, Object> search_inventory(@RequestBody Map<String, Object> param, HttpServletRequest request){
        log.info("search_inventory_detail");
        return ulsanService.searchInventoryDetail(param);
    }

    // 재고실사현황 - summary
    @PostMapping("/search-stock-count/summary")
    public Map<String, Object> search_inventory_summary(@RequestBody Map<String, Object> param, HttpServletRequest request){
        log.info("search_inventory_summary");
        return ulsanService.searchInventorySummary(param);
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
        return ulsanService.insOutput(vo);
    }


    // 출고 - Detail
    @PostMapping("/search-load/detail")
    public Map<String, Object> loadDetail(@RequestBody Map<String, Object> param, HttpServletRequest request){
        log.info("search load detail");
        return ulsanService.searchLoadDetail(param);
    }


    // 검증 insert
    @PostMapping("/validation/save")
    public Map<String, Object> saveValidation(@RequestBody Map<String, Object> param) {
        return ulsanService.saveValidation(param);
    }

    @PostMapping("/getStroage")
    public List<String> getStorage(@RequestParam(required = false) String type){
        return ulsanService.getStorage(type);
    }
}
