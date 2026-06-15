package com.example.demo.controller;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

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
public class DownloadController {
    
	@GetMapping("/download/app")
	public void downloadApk(HttpServletResponse response) throws IOException {
	    System.out.println("=== APK 다운로드 요청 시작 ===");
	    
	    String apkUrl = "http://food114.co.kr/appupdate/wbmexwms.apk";
	    
	    InputStream in = null;
	    OutputStream out = null;
	    HttpURLConnection connection = null;
	    
	    try {
	        // 외부 URL에서 APK 파일 가져오기
	        URL url = new URL(apkUrl);
	        connection = (HttpURLConnection) url.openConnection();
	        connection.setRequestMethod("GET");
	        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
	        connection.connect();
	        
	        int responseCode = connection.getResponseCode();
	        System.out.println("외부 서버 응답 코드: " + responseCode);
	        
	        if (responseCode == HttpURLConnection.HTTP_OK) {
	            int fileSize = connection.getContentLength();
	            System.out.println("파일 크기: " + fileSize + " bytes");
	            
	            // 응답 헤더 설정
	            response.setContentType("application/vnd.android.package-archive");
	            response.setHeader("Content-Disposition", "attachment; filename=\"wbmexwms.apk\"");
	            if (fileSize > 0) {
	                response.setContentLength(fileSize);
	            }
	            
	            // 스트림 열기
	            in = new BufferedInputStream(connection.getInputStream());
	            out = response.getOutputStream();
	            
	            // 파일 복사
	            byte[] buffer = new byte[8192];
	            int bytesRead;
	            int totalBytes = 0;
	            
	            while ((bytesRead = in.read(buffer)) != -1) {
	                out.write(buffer, 0, bytesRead);
	                totalBytes += bytesRead;
	            }
	            
	            out.flush();
	            System.out.println("전송 완료: " + totalBytes + " bytes");
	            System.out.println("=== APK 다운로드 완료 ===");
	            
	        } else {
	            System.err.println("외부 서버 오류: " + responseCode);
	            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "APK 파일을 가져올 수 없습니다.");
	        }
	        
	    } catch (Exception e) {
	        System.err.println("다운로드 오류: " + e.getMessage());
	        e.printStackTrace();
	        
	    } finally {
	        // 리소스 정리
	        if (in != null) {
	            try { in.close(); } catch (Exception e) { }
	        }
	        if (out != null) {
	            try { out.close(); } catch (Exception e) { }
	        }
	        if (connection != null) {
	            connection.disconnect();
	        }
	    }
	}
}