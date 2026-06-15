package com.example.demo.vo;

import java.util.List;

import lombok.Data;

@Data
public class ItemLocationVO{
	private int iid;
	private String indate;
	private String itemcode;
	private String itemname;
	private String qty;
	private String location;
	private String barcode;
	private String total_qty;
}