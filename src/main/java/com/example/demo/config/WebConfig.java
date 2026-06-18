package com.example.demo.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {			//250821 임시 주석
    	
        registry.addInterceptor(new LoginCheckInterceptor())
                .addPathPatterns("/**")                // 모든 요청에 적용
                .excludePathPatterns("/", "/login", "/loginCheck", "/css/**", "/js/**", "/images/**", "/error"); 
               // 로그인 관련 경로와 정적 리소스는 제외
        registry.addInterceptor(localeChangeInterceptor());
    }
    
 // 메시지 번들 위치 지정
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasenames(
        	"classpath:/i18n/common/message",
        	"classpath:/i18n/alert/message",
        	"classpath:/i18n/menu/message"
        ); // messages.properties, messages_ko.properties ...
        ms.setDefaultEncoding("UTF-8");
        ms.setUseCodeAsDefaultMessage(true);
        ms.setFallbackToSystemLocale(false);
        ms.setCacheSeconds(1); // 개발 중 즉시 반영
        //ms.setCacheSeconds(-1); // 운영 시 설정
        return ms;
    }

    // 기본 로케일
    @Bean
    public LocaleResolver localeResolver() {
    	// 세션 방식
//        SessionLocaleResolver slr = new SessionLocaleResolver();
//        slr.setDefaultLocale(Locale.KOREA);
//        return slr;
        
        // 쿠키 방식
        CookieLocaleResolver clr = new CookieLocaleResolver();
        clr.setDefaultLocale(Locale.KOREAN);
        clr.setCookieName("lang");
        clr.setCookieMaxAge(60 * 60 * 24 * 365); // 1년 유지
        return clr;
    }

    // 언어 전환
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor i = new LocaleChangeInterceptor();
        i.setParamName("lang");
        return i;
    }
}

