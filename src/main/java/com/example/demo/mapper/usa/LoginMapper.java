package com.example.demo.mapper.usa;

import com.example.demo.vo.LoginVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface LoginMapper {

	int loginChk(Map<String, Object> map);

	LoginVO selectLoginVO(String loginid);

//	Map<String, String> getUserMenuAccess(String loginid);

}