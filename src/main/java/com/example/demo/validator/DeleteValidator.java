package com.example.demo.validator;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.demo.mapper.mexico.DeleteCheckMapper;

@Component
public class DeleteValidator {
	
	@Autowired 
	private DeleteCheckMapper deleteMapper;
	public Map<String, Object> checkDeletable(Map<String, Object> map){
		Map<String, Object> result = new HashMap<String, Object>();
		// 공통 1차 검증 : 후처리 존재 여부
		int postProcessing = deleteMapper.checkHistory(map);
		if (postProcessing > 0) { // ❗ 에러가 있을 때만 fail1 세팅
			result.put("barcode", map.get("barcode"));
			result.put("response", "warning.barcode.post-processing");
		}
		
		return result;
	}
}