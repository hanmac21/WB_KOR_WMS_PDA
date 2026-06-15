package com.example.demo.vo;

import java.util.List;

import lombok.Data;

@Data
public class PalletDetailVO{
	private String pbarcode;
	private String bbarcode;		// 사용X
	private String mdate;
	private String sdate;
	private String printyn;
	//private String cucode;		// 사용X
	private String custcode;
	private String custname;
	private String itemcode;
	private String qty;
	private String lotdate;
	private String storage;
	private String factory;
	private String laststatus;
}