package com.example.demo.config;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.web.servlet.HandlerInterceptor;

public class LoginCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("loginid") == null) {
        	String ajaxHeader = request.getHeader("X-Requested-With");

            if ("XMLHttpRequest".equals(ajaxHeader)) {
                // Ajax 요청일 때 → JSON 응답 + 401
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"Your session has expired. Please log in again.\"}");
                return false;
            } else {
                // 일반 요청일 때 → 로그인 페이지로 리다이렉트
                response.sendRedirect("/login?expired=true");
                return false;
            }
        }

        return true; // 세션 있으면 요청 계속 진행
    }
}
