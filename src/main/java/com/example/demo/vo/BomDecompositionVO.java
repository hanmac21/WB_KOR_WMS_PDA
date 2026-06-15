package com.example.demo.vo;

import java.util.List;

import lombok.Data;

@Data
public class BomDecompositionVO {
	
	// 공통
    private String parentItemCode;
    private String barcode;
    private String loginId;
    // 행 단위
    private String childItemCode;
    private double qty;
    private double basicqty;
    private double maxqty;

    // 요청용 (프론트 → 서버)
    private List<BomDecompositionVO> items;
}
