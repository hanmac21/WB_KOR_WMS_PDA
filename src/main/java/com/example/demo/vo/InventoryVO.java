package com.example.demo.vo;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor @AllArgsConstructor
@Builder
@Data
public class InventoryVO{
	private String scantype;
	private String sdate;
	private String car;
	private String itemcode;
	private String itemname;
	private String qty;
	private String type;
	private String barcode;
	private String lotdate;
	private String ymdhms;
	private String factory;
	private String storage;
	private String location;
	
	private BigDecimal sumqty;
}