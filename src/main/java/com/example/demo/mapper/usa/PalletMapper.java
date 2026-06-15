package com.example.demo.mapper.usa;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PalletMapper {
	
	// 바코드가 팔레트에 속해 있는지 확인
	String searchPallet(String barcode);
	
	// 팔레트에 속한 해다 파트바코드 N 업데이트
	int bbarcodeN(Map<String, Object> palletMap);

	// 팔레트 라벨 수량
	Double palletQty(String pbarcode);
	
	// 파트 수량 가져오기
	double partQty(String barcode);
	
	
	/////// 로케이션 전용/////////
	// location테이블에 qty= 0, useyn = N 업데이트
	int updateLQtyN(Map<String, Object> palletMap);

	// location테이블에 qty 업데이트
	int updateLQty(Map<String, Object> palletMap);
	
	// 파트라벨 팔레트라벨 위치에 insert
	int partLocation(Map<String, Object> palletMap);
	
	
	/////// 작업장 전용/////////
	// location테이블에 qty= 0, useyn = N 업데이트
	int updateWLQtyN(Map<String, Object> palletMap);

	// location테이블에 qty 업데이트
	int updateWLQty(Map<String, Object> palletMap);
	
	// 파트라벨 팔레트라벨 위치에 insert
	int partWorkLocation(Map<String, Object> palletMap);
}