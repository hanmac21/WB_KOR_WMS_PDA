package com.example.demo.utils;

public final class BarcodeDetect {
	private BarcodeDetect(){}
	
	public static BarcodeKind kindOf(String barcode){		
	 if (barcode == null) return 
			 BarcodeKind.UNKNOWN;
	 
	 String bc = barcode.trim();
	 
	 // 예) 팔레트: "P"로 시작 & 길이 12  또는  "P....MEX" 또는 "P....USA"
	 if ((bc.startsWith("P") && bc.length()==12) || (bc.startsWith("P") && (bc.endsWith("MEX") || bc.endsWith("USA"))))
		 return BarcodeKind.PALLET;
	 
	 // 예) 파트(박스): 콤마 4개로 5필드 item,lot,seq,qty,scmmex
	 if (bc.chars().filter(ch -> ch==',').count() == 4) 
		 return BarcodeKind.PART;
	 
	 return BarcodeKind.UNKNOWN;
	}
}