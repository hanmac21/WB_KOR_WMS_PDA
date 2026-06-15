let manualTouch = false;
$(document).ready(function() {
	hideLoading();

	//focusWithoutKeyboard();
	$(document).on("pointerdown click", "#barcodeInput", function() {
		$(this).removeAttr("readonly");
		inputMode = 'manual';
	});

})

function addEntry() {
	// 바코드 값 가져오기
	const barcodeInput = document.getElementById('barcodeInput');
	const barcode = barcodeInput.value.trim();

	// 바코드가 없으면 알림
	if (!barcode) {
		Utils.showAlert(m("warning.barcode.required"));
		return;
	}


	// ✅ 바코드 체크
	if (!barcodeCheck(barcode)) {
		return;
	}

	// 로딩 표시
	showLoading();

	// 에이작스로 바코드 전송 및 데이터 가져오기
	$.ajax({
		url: "/purchase/stock-history-barcode",
		method: 'POST',
		contentType: "application/json",
		data: barcode,
		success: function(result) {
			console.log(result);

			if (result.error) {
				hideLoading();
				playSound('error');
				Utils.showAlert(m(result.error), "warning");
				return;
			}

			const data = result.main;
			// 바코드 입력 창 초기화
			$("#barcodeInput").val("");
			if (data && data.itemname) {
				$("#itemname").text(data.itemname);
				$("#itemcode").text(data.spec);
				$("#infoBarcode").text(barcode);

				if (data.location && data.location.trim() !== "") {
					// 공정불출일 때만 상태값이랑 위치(작업장) 출력
					if (data.laststatus === 'WIP-INPUT'){
						$("#location").text(data.location);
						$("#location-wrap").show();
						$("#laststatus").text(data.laststatus);
						$("#status-wrap").show();	
					
					// 그 외 상태
					} else {
						$("#location").text(data.location);
						$("#location-wrap").show();
						$("#laststatus").text("");
						$("#status-wrap").hide();						
					}
				} else {
					$("#location").text("");
					$("#location-wrap").hide();
					$("#laststatus").text(data.laststatus);
					$("#status-wrap").show();
				}
			}

			// 테이블 그리기
			renderHistory(result.list);
		}
	});
}
// 전역 변수로 renderTable에서 사용된 데이터를 저장
let mainTableData = [];
let locationMain = "";
async function renderHistory(list) {		//테이블그리기
	$('.history-div').empty();
	let historyHtml = '';
	console.log(list.length)
	console.log(list)
	for (let i = 0; i < list.length; i++) {
		const item = list[i];

		let checkBarcode = item.barcode.charAt(0);  // 첫 번째 글자
		let checkBarcodeKor = item.barcode.split(",")[3];  // 콤마 맨 뒤 값 (WMSMEX)
		let checkBarcodeMex = item.barcode.split(",")[4];  // 콤마 맨 뒤 값 (WMSMEX)

		//console.log(item);
		// QTY 포맷팅
		let qtyFormatted = '-';
		if (item.qty) {
			let num = Number(item.qty); // 숫자 변환
			qtyFormatted = Number.isInteger(num)
				? num.toLocaleString()
				: num.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 });
		}


		// 공통 필드: 날짜/시간, 수량
		historyHtml += `<div class="history-card">`
		if (item.kind == 'PALLET') {

			console.log(checkBarcode + " -- " + checkBarcodeKor);
			if (checkBarcode != "P") {
				return;
			} else {
				if (checkBarcodeKor == "SCMMEX") {
					historyHtml += `
			                <div class="history-header korBackground">
								<span class = "quantity-badge">${list.length - i} </span>
			                    <span>${item.kind}</span>
			                </div>
		               `
				} else if (checkBarcodeKor == "WMSUSA") {
					historyHtml += `
			                <div class="history-header usaBackground">
								<span class = "quantity-badge">${list.length - i} </span>
			                    <span>${item.kind}</span>
			                </div>
		               `
				} else {
					historyHtml += `
			                <div class="history-header mexBackground">
								<span class = "quantity-badge">${list.length - i} </span>
			                    <span>${item.kind}</span>
			                </div>
		               `
				}
			}
		} else if (item.kind == 'BARCODE' || item.kind == 'PALLET_BARCODE' || item.kind == 'PALLET_BARCODE_INCLUDE') {
			console.log(checkBarcode + " -- " + checkBarcodeKor + "--" + checkBarcodeMex);
			if (checkBarcodeMex == "WMSMEX") {
				historyHtml += `
	                <div class="history-header mexBackground">
						<span class = "quantity-badge">${list.length - i} </span>
	                    <span>${item.kind}</span>
	                </div>`
			} else if (checkBarcodeMex == "WMSUSA") {
				historyHtml += `
	                <div class="history-header usaBackground">
						<span class = "quantity-badge">${list.length - i} </span>
	                    <span>${item.kind}</span>
	                </div>`
			} else{
				historyHtml += `
	                <div class="history-header korBackground">
						<span class = "quantity-badge">${list.length - i} </span>
	                    <span>${item.kind}</span>
	                </div>`
			}
		} else {
			historyHtml += `
	                <div class="history-header">
						<span class = "quantity-badge">${list.length - i} </span>
	                    <span>${item.kind}</span>
	                </div>
               `
		}
		
		historyHtml += `
            <div class="history-items">
                <div class="history-item">
					<!-- <div class="item-detail">
                        <div class="detail-label">YN</div>
                        <div class="detail-value">${item.useyn || '-'}</div>
                    </div> -->`
		if (item.loginid && item.loginid != ' ') {
			historyHtml += `
		            <div class="item-detail">
		                <div class="detail-label">${m('table.user')}</div>
		                <div class="detail-value">${item.loginid || '-'}</div>
		            </div>`;
		}
		
	    historyHtml += `
		            <div class="item-detail">
		                <div class="detail-label">${m('table.date')}</div>
		                <div class="detail-value">${item.sdate || '-'}</div>
		            </div>
		            <div class="item-detail">
		                <div class="detail-label">${m('table.worktime')}</div>
		                <div class="detail-value">${item.time || '-'}</div>
		            </div>
		            <div class="item-detail">
		                <div class="detail-label">${m('table.qty')}</div>
		                <div class="detail-value">
		                    <span class="detail-value">${qtyFormatted ? qtyFormatted : '-'}</span>
		                </div>
		            </div>`;
		if (item.kind == 'PALLET' && checkBarcode == "P" && checkBarcodeKor == "SCMMEX") {
			try {
				const data = await $.ajax({
					url: "/purchase/show_stockHistory_sangho",
					type: "POST",
					data: {
						custCode: item.custcode
					}
				});

				historyHtml += `
						<div class="item-detail">
				            <div class="detail-label">${m('table.factory')}</div>
				                <div class="detail-value">
				                <span class="detail-value">KOREA - ${data.CU_SANGHO}</span>
				            </div>
				        </div>
			    `;
			} catch (error) {
				console.error("CUSTNAME NOT FOUND:", error);
			}

		} else {
			historyHtml += `
						<div class="item-detail">
                            <div class="detail-label">${m('table.factory')}</div>
                            <div class="detail-value">
                                <span class="detail-value">${item.factory?.replaceAll(/(\r\n|\n|\r)/g, "<br>") ?? '-'}</span>
                            </div>
                        </div>
                    `;
		}
		// kind 값에 따라 추가 필드 표시
		if (item.kind && (item.kind.includes('WIP') || item.kind.includes('LOCATION'))) {
			// location 필드 추가
			if (item.location) {
				historyHtml += `
	                    <div class="item-detail">
	                        <div class="detail-label">${m('table.location')}</div>
	                        <div class="detail-value">${item.location}</div>
	                    </div>`;
			}
		}

		if (item.kind && (item.kind.includes('BARCODE') || item.kind.includes('PALLET'))) {
			// location 필드 추가
			if (item.laststatus) {
				historyHtml += `
	                    <div class="item-detail">
	                        <div class="detail-label">Laststatus</div>
	                        <div class="detail-value">${item.laststatus}</div>
	                    </div>
						`;
			}
			if (item.labeltype) {
				historyHtml += `
	                   
						<div class="item-detail">
	                        <div class="detail-label">Label Type</div>
	                        <div class="detail-value">${item.labeltype}</div>
	                    </div>`;
			}
		}
		if (item.kind && item.kind.includes('PALLET')) {
			if((item.partbarcode).split(",").length === 4){
				historyHtml += `
					<div class="item-detail">
	                    <div class="detail-label">Pallet Barcode</div>
	                    <div class="detail-value clickable-barcode">${item.partbarcode}</div>
	                </div>`;
			} else {
				const parts = item.partbarcode.match(/.*?USA/g) || [];
				
				historyHtml += `
					<div class="item-detail">
	                    <div class="detail-label">Part Barcode</div>
	                    <div class="detail-barcodes">`
				for(let j = 0; j < parts.length; j++){
					historyHtml += `
						<div class="detail-value clickable-barcode">${parts[j]}</div>
						`
				}
				
				historyHtml += `
	                    </div>
	                </div>`;
			}
		}

		if (item.kind && item.kind.includes('CHANGE ITEMCODE')) {
			const parts = item.partbarcode.split('_');

			historyHtml += `
				<div class="item-detail">
					<div class="detail-label">OLD ITEMCODE</div>
					<div class="detail-value">${parts[0] || ''}</div>
				</div>				
				<div class="item-detail">
					<div class="detail-label">NEW ITEMCODE</div>
					<div class="detail-value">${parts[1] || ''}</div>
				</div>
				`;
		}

		if (item.kind && (item.kind.includes('WORKMOVE'))) {
			// work 필드 추가
			if (item.work) {
				historyHtml += `
	                    <div class="item-detail">
	                        <div class="detail-label">Work</div>
	                        <div class="detail-value">${item.work}</div>
	                    </div>`;
			}
		}

		/*// 공장간이송 추가
		if(item.kind== 'FACTORY MOVE SENDING'){
			// factory 필드 추가
			if (item.factory) {
				historyHtml += `
					<div class="item-detail">
						<div class="detail-label">${m('table.factory')}</div>
						<div class="detail-value">${item.factory}</div>
					</div>`;
			}
		}
		if(item.kind== 'FACTORY MOVE RECEIVE'){
			// factory 필드 추가
			if (item.factory) {
				historyHtml += `
					<div class="item-detail">
						<div class="detail-label">${m('table.factory')}</div>
						<div class="detail-value">${item.factory}</div>
					</div>
					<div class="item-detail">
						<div class="detail-label">${m('table.storage')}</div>
						<div class="detail-value">${item.storage}</div>
					</div>`;
			}
		}*/

		if (item.kind == 'STOCK MOVE' || item.kind == 'FACTORY MOVE' || item.kind == 'FACTORY SENDING' || item.kind == 'FACTORY RECEIVE') {
			const factory = item.factory || '';
			const storage = item.storage || '';
			const custcode = item.custcode || '';
			const custname = item.custname || '';

			historyHtml += `
               	   	<div class="item-detail">
        	            <div class="detail-label">${m('table.location')}</div>
    	        		<div class="detail-value">${custcode} ${custname}<br>-> ${factory} ${storage} </div>
	            	</div>
	            `
		} else if (item.kind == 'LOAD') {

			const custcode = item.custcode || '';
			const storage = item.storage || '';
			try {

				const data = await $.ajax({
					url: "/purchase/show_stockHistory_sangho",
					type: "POST",
					data: {
						custCode: custcode
					}
				});

				/*historyHtml += `
						  <div class="item-detail">
						<div class="detail-label">${m('table.location')}</div>
						<div class="detail-value">${storage} <br>-> ${data.CU_SANGHO || ''}</div>
						  </div>
				`*/
				let lastStatus = $("#laststatus").text();
				lastStatus += "\n[" + storage + " -> " + data.CU_SANGHO + "]";
				$("#laststatus").text(lastStatus);
			} catch (error) {
				console.error("CUSTNAME NOT FOUND:", error);
			}

		}
		historyHtml += `
       	   	<div class="item-detail">
	            <div class="detail-label">${m('table.memo')}</div>
        		<div class="detail-value">${item.memo}</div>
        	</div>
        `
		// 카드 닫기
		historyHtml += `
	                </div>
	            </div>
	        </div>`;
	}
	$('.history-div').html(historyHtml);
	$('.history-div').css("display", "block")
	hideLoading();
}


$(document).on("click", ".dataInfo", function() {
	// 클릭한 tr중 dataInfo클래스를 찾아 첫번째 텍스트 반환
	const itemCode = $(this).closest('tr').find('td.dataInfo').first().text().trim();

	// 파레트라벨인지 
	const isPallet = itemCode.startsWith("(P)");

	// 정규식으로 pno만 추출
	const itemcode = isPallet ? itemCode.replace(/^\s*\(\s*p\s*\)\s*/i, "") : itemCode;


	$.ajax({
		url: "/purchase/getItemInfo",
		type: "GET",
		data: { itemcode },
		dataType: "json",
		success: function(result) {
			console.log(result);
			showPopup(result.list);
		}
	})
});
