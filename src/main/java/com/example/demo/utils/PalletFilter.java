package com.example.demo.utils;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.mapper.usa.PurchaseMapper;

import lombok.RequiredArgsConstructor;

//팔레트-파트 관계로 파트 바코드 제거
@Service
@RequiredArgsConstructor
public class PalletFilter {

private final PurchaseMapper purchaseMapper; 

/**
* 입력에 팔레트가 있으면, 그 팔레트에 포함된 파트 바코드를 제거한다.
*/
// 읽기 전용 트랜잭션으로 조회만 함
@Transactional(readOnly = true)
public FilterResult removeChildPartsIfParentPresent(List<String> input) {
	// 입력이 없으면 빈 결과 반환
	if (input == null || input.isEmpty()) 
		return FilterResult.empty();

	// 1) 정규화 & 분류
	List<String> list = input.stream()
			.filter(Objects::nonNull)			// null 제거
			.map(String::trim)					// 공백 제거 
			.filter(s->!s.isEmpty())			// 빈 문자열 제거
			.distinct()							// 중복 제거
			.collect(Collectors.toList());							// 리스트 반환
	
	// 바코드가 팔레트인지 파트인지 판별(BarcodeDetect 클래스)하고 팔레트만 Set에 모음
	Set<String> pallets = list.stream()
			.filter(b -> BarcodeDetect.kindOf(b)==BarcodeKind.PALLET)
			.collect(Collectors.toSet());
	
	// 팔레트가 아예 없다면 그대로 진행
	if (pallets.isEmpty())
		return FilterResult.of(list, Collections.emptyList());

	// 2) 팔레트가 하나라도 있으면
	//	  팔레트 → 자식(파트) 바코드 집합 조회 (한 번에)
	Set<String> childParts = new HashSet<String>(
		purchaseMapper.findChildBarcodesForPallets(new ArrayList<>(pallets))
	);

	// 3) 입력에서 자식 파트를 제거
	List<String> filtered = new ArrayList<>(list.size());
	List<String> excluded = new ArrayList<>();
	for (String barcode : list) {
		if (BarcodeDetect.kindOf(barcode)==BarcodeKind.PART && childParts.contains(barcode)) {	// 파트 이면서 팔레트에 포함되어있으면
			excluded.add(barcode);          // 같은 팔레트가 있으므로 파트는 무시
		} else {
			filtered.add(barcode);			// 나머지는 처리대상으로 유지
		}
	}
	return FilterResult.of(filtered, excluded);
	}
}
