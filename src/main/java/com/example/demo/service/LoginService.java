package com.example.demo.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.mapper.usa.LoginMapper;
import com.example.demo.vo.LoginVO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LoginService  {

	@Autowired
	private LoginMapper loginMapper;

	public int loginChk(Map<String, Object> map) {
		return loginMapper.loginChk(map);
	}

	public LoginVO selectLoginVO(String loginid) {
		return loginMapper.selectLoginVO(loginid);
	}

//	public Map<String, String> getUserMenuAccess(String loginid) {
//		return loginMapper.getUserMenuAccess(loginid);
//	}
}
