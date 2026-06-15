package com.example.demo.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.mapper.mexico.CloseMapper;
import com.example.demo.validator.BarcodeValidator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CloseService  {

    private final BarcodeValidator barcodeValidator;

	@Autowired
	private CloseMapper closeMapper;

    CloseService(BarcodeValidator barcodeValidator) {
        this.barcodeValidator = barcodeValidator;
    }

	public Map<String, Object> checkClosedMonth(Map<String, Object> map) {
		
		Map<String, Object> result = new HashMap<String, Object>();
		
		String loginid = (String)map.get("loginid");		// wms로 로그인하면 마감 무시
		String date = ((String) map.get("date")).replaceAll("-","");
		if(date.length()==8) {
			date = date.substring(0, 6);
		}else if(date.length()==6) {
			date = "20"+date;
		}
		map.put("date", date);
		System.out.println("magam date : "+date);
		int close = closeMapper.checkClosedMonth(map);
		if (!"wms".equalsIgnoreCase(loginid) && close > 0) {
			result.put("barcode", "");
			result.put("response", "warning.close.month");
		}
		return result;
	}

}
