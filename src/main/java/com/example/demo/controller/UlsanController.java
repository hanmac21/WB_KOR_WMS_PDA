package com.example.demo.controller;

import com.example.demo.service.UlsanService;
import com.example.demo.vo.BarcodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
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
}
