package com.example.demo.utils;

import java.util.Collections;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "of")
public class FilterResult{
	  private List<String> filtered;   // 실제로 처리할 바코드
	  private List<String> excluded; // 제외된 파트 바코드 - 안내용 바코드 목록
	  public static FilterResult empty(){ 
		  return of(Collections.emptyList(), Collections.emptyList()); 
	  }	
}