package com.example.demo.vo;

import java.util.List;

import lombok.Data;

@Data
public class BarcodeVO{
	private List<String> barcode;
	private List<String> barcode2;
	private String barcodeone;
	private String date;
	private String indate;
	private String source;
	private String cust;
	private String storage;
	private String location;
	private String itemcode;
	private String itemname;
	private String qty;
	private String memo;
	private String status1;
	private String status2;
	private String realstock;
	private String wccode;
	private String factory;
	private String custname;
	
	private String main;
	private String kind;
	private String status0;
	
	private String loginid;
	private String source2;
	private String bacode1;

	private String okyn;
	private String shipTo;
	private String invoiceno;

	private List<String> itemcodes;
	private List<String> qtys;
}