package com.example.demo.mapper.mexico;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeleteCheckMapper {

	// 후처리된게 있는데 체크
	int checkHistory(Map<String, Object> map);
	

}