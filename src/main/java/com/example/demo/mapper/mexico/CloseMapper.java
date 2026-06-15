package com.example.demo.mapper.mexico;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

import com.example.demo.vo.LoginVO;

@Mapper
public interface CloseMapper {

	int checkClosedMonth(Map<String, Object> map);

}