package com.example.demo.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/*
26.07.08 SJ 작성

[작성 의도]
다중 프로젝트가 같은 브라우저(동일 도메인/포트)에서 실행될 때, 쿠키의 이름이 중복되어서 덮어씌워지는 상황을 방지하고자 함.

[로직 설명]
document.cookie에 대한 읽기/쓰기 로직을 커스터마이즈하여,
쿠키를 저장할 때는 'POL_WEB_' 같은 프로젝트별 접두사를 강제로 붙여서 저장하고,
읽을 때는 내 프로젝트 접두사가 붙은 쿠키만 찾아 접두사를 제거하여 반환한다.

[사용시 주의 사항]
- 개발 시 접두사를 신경 쓸 필요 없이 기존 순수 쿠키명 그대로 코드를 작성하면 됨.
- 다른 프로젝트에 적용 시, 코드 내부의 PREFIX 변수값을 해당 프로젝트명에 맞게 수정할 것.
*/
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CookieNamespaceFilter implements Filter {

    private static final String PREFIX = "KOR_PDA_";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Tomcat 8.5 호환용 빈 구현체
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 내 프로젝트 쿠키만 추출
        HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(httpRequest) {
            @Override
            public Cookie[] getCookies() {
                Cookie[] cookies = super.getCookies();
                if (cookies == null) return null;

                List<Cookie> result = new ArrayList<>();
                for (Cookie cookie : cookies) {
                    String name = cookie.getName();
                    // 내 접두사가 붙은 쿠키만 애플리케이션으로 통과시킴
                    if (name.startsWith(PREFIX)) {
                        result.add(new Cookie(name.substring(PREFIX.length()), cookie.getValue()));
                    }
                }
                return result.toArray(new Cookie[0]);
            }
        };

        // 내 접두사 강제 부착
        HttpServletResponseWrapper wrappedResponse = new HttpServletResponseWrapper(httpResponse) {

            @Override
            public void addCookie(Cookie cookie) {
                // 이미 접두사가 붙어있지 않은 경우에만 접두사 추가 (context.xml의 세션 쿠키 등은 제외됨)
                if (!cookie.getName().startsWith(PREFIX)) {
                    Cookie prefixedCookie = new Cookie(PREFIX + cookie.getName(), cookie.getValue());
                    prefixedCookie.setMaxAge(cookie.getMaxAge());
                    prefixedCookie.setPath(cookie.getPath());
                    if (cookie.getDomain() != null) prefixedCookie.setDomain(cookie.getDomain());
                    prefixedCookie.setSecure(cookie.getSecure());
                    prefixedCookie.setHttpOnly(cookie.isHttpOnly());
                    super.addCookie(prefixedCookie);
                } else {
                    super.addCookie(cookie);
                }
            }

            @Override
            public void addHeader(String name, String value) {
                if ("Set-Cookie".equalsIgnoreCase(name)) value = injectPrefix(value);
                super.addHeader(name, value);
            }

            @Override
            public void setHeader(String name, String value) {
                if ("Set-Cookie".equalsIgnoreCase(name)) value = injectPrefix(value);
                super.setHeader(name, value);
            }

            private String injectPrefix(String headerValue) {
                if (headerValue == null) return null;
                int eqIndex = headerValue.indexOf('=');
                if (eqIndex < 0) return headerValue;

                String cookieName = headerValue.substring(0, eqIndex).trim();
                if (!cookieName.startsWith(PREFIX)) {
                    return PREFIX + headerValue.trim();
                }
                return headerValue;
            }
        };

        chain.doFilter(wrappedRequest, wrappedResponse);
    }

    @Override
    public void destroy() {
        // Tomcat 8.5 호환용 빈 구현체
    }
}