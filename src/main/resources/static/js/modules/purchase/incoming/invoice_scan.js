let manualTouch = false;
$(document).ready(function() {
	//hideLoading();
	var today = new Date();

	function dateFormat(date) {
		let month = date.getMonth() + 1;
		let day = date.getDate();

		month = month >= 10 ? month : '0' + month;
		day = day >= 10 ? day : '0' + day;

		return date.getFullYear() + '-' + month + '-' + day;
	}

	today = dateFormat(today);

	$.datepicker.setDefaults({
		dateFormat: 'yy-mm-dd',
		prevText: '이전 달',
		nextText: '다음 달',
		monthNames: ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'],
		monthNamesShort: ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'],
		dayNames: ['일', '월', '화', '수', '목', '금', '토'],
		dayNamesShort: ['일', '월', '화', '수', '목', '금', '토'],
		dayNamesMin: ['일', '월', '화', '수', '목', '금', '토'],
		showMonthAfterYear: true,
		yearSuffix: '년',
		changeMonth: true,
		changeYear: true,
		showButtonPanel: true,
		currentText: '오늘 날짜',
		onClose: function(dateText, inst) {
			focusWithoutKeyboard();
			if ($(window.event.target).hasClass('ui-datepicker-current')) {
				$(this).datepicker('setDate', new Date());

			}
		}
	});
	$("#datepicker").val(today);
	$("#datepicker").datepicker();

	$(document).on('click', '.ui-datepicker-current', function() {
		const today = new Date();
		$("#datepicker").datepicker("setDate", today);

		// 오늘 날짜 설정 후 키보드 안뜨게 포커스
		if (typeof focusWithoutKeyboard === 'function') {
			$("#datepicker").datepicker("hide");
			focusWithoutKeyboard();
		}
	});
	
	selectInvoice();

	renderTable();
	
	focusWithoutKeyboard();
	// input창에서 포커스 없어질때 세팅
	$('#barcodeInput').on('blur', function() {
		manualTouch = false;
		inputMode = 'readonly'
	});
})

// 전역 스캔 수량
let scanQty = 0;

function selectInvoice(){
	const params = new URLSearchParams(window.location.search);
	let invoice = params.get('invoice');
	$('#memo').val(invoice);

	showLoading();	
	
	$.ajax({
		url: "/purchase/search_invoice",
		type: "GET",
		data:{
			invoiceNo:invoice
		},
		success: function(data) {
			//console.log(data)
			const list = data.list;
						
			let table = $("#dataTableBody-invoice");
			table.empty();
			$("#invoiceCount").text(list.length);
			
			let totalQty = 0;
			
			for(let i = 0; i<list.length; i++){
				let tbody = "";				
				let totalScanQty = Number(list[i].SCANQTY) + scanQty;
				
				tbody = `
					<tr data-itemcode=${list[i].ITEMCODE}>
						<td>${list[i].ITEMCODE}</td>
						<td>${Number(list[i].QTY).toLocaleString()}</td>
						<td>${Number(totalScanQty).toLocaleString()}</td>
					</tr>
				`				
				table.append(tbody);
				totalQty += Number(list[i].QTY);
			}
			
			$("#invoiceTotalqty").text(totalQty.toLocaleString());

			// scanQty 복원
			rebuildScanQtyFromLocal();
			checkScanQtyColor();
			hideLoading()
		},
		error: function(xhr, status, error) {
			console.error("요청 실패");
			console.error("Status:", status);       // 예: "error"
			console.error("Error:", error);         // 예: 서버 응답 메시지
			console.error("Response:", xhr.responseText); // 서버 응답 본문
			alert("오류가 발생했습니다: " + error);
		}
	});
}

function addEntry() {			// 로컬스토리지 저장
	const barcodeInput = document.getElementById('barcodeInput');
	const barcode = barcodeInput.value.trim();

	if (!barcode) {
		Utils.showAlert(m("warning.barcode.required"));
		return;
	}
	// 🔹 저장 제한 체크
	if (!savelimitCheck("barcodeListInCkd", 500)) {
		return; // 500개 초과 시 여기서 중단 → showLoading() 실행 안 됨
	}
	// 로딩 표시
	showLoading();

	if (inputMode === 'manual') {
		if (barcode) {
			$('#barcodeInput').val('');
			$('#barcodeInput').attr('readonly', true);
			inputMode = 'readonly';
		}
	} else {
		console.warn("현재 스캔 모드입니다.");
	}

	if ((barcode.split(",").length == 5 && (barcode.split(",")[4] == "SCMMEX" || barcode.split(",")[4] == "WMSMEX")) || (barcode[0][0] == "P" && barcode.endsWith("MEX") || barcode.split(",").length == 1 && barcode[0][0] == "P")) {
		
		// 바코드에서 itemcode, qty 추출
		const { bcItem, bcQty } = parseBarcodeInfo(barcode);

		// 인보이스 테이블에 품번이 없으면 return
		if (bcItem){
			const invoiceItem = $(`#dataTableBody-invoice tr[data-itemcode="${bcItem}"]`);
			
			if (invoiceItem.length === 0){
				let audio = new Audio('/sounds/buzzer.wav');
				audio.play();
				$("#barcodeInput").val("");
				hideLoading();
				Utils.showAlert(`${barcode}<br>인보이스에 없는 품번입니다.`, "warning");
				return;
			} else {
				let stored = [];

				stored = getBarcodeList();

				console.log(stored);
				
				if (stored.includes(barcode)) {
					console.log("barcode : " + barcode)
					let audio = new Audio('/sounds/buzzer2.wav');
					audio.play();
					$("#barcodeInput").val("");
					Utils.showAlert(`${barcode}<br>${m("warning.barcode.duplicate")}`);
					hideLoading();
					return;
				} else {
					let audio = new Audio('/sounds/complete.wav');
					audio.play();
					stored.push(barcode);
					setBarcodeList(stored);
					$("#barcodeInput").val("");
					renderTable();
					hideLoading();
					Utils.showAlert(`${barcode}<br>${m("info.barcode.saved")}`, "#008000", barcode)
				}
				
				const scanTd = invoiceItem.find('td').eq(2);
				const curQty = Number(String(scanTd.text()).replace(/,/g, '')) || 0;
				const addQty = curQty + (Number(bcQty) || 0);
				scanTd.text(addQty.toLocaleString());
			}		
		}
		
		checkScanQtyColor();
	} else {
		let audio = new Audio('/sounds/buzzer.wav');
		audio.play();
		$("#barcodeInput").val("");
		hideLoading();
		Utils.showAlert(`${m("warning.barcode.invalid")}<br>${m("warning.check")}`, "warning")
		return;
	}
}

function palletCheck(){
	const barcodeList = getBarcodeList();

		if (barcodeList.length === 0) {
			Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
			focusWithoutKeyboard()
			return;
		}
		
		// 인보이스 수량 초과 여부
		let overItems = [];
		
		$('#dataTableBody-invoice tr').each(function(){
			const tds = $(this).find('td');
			if (tds.length >= 3){
				const itemcode = tds.eq(0).text().trim();
				const invoiceQty = Number(String(tds.eq(1).text()).replace(/,/g, '')) || 0;
				const scanQty = Number(String(tds.eq(2).text()).replace(/,/g, '')) || 0;
				
				if (scanQty > invoiceQty) {
					overItems.push(mf('warning.qtyOver.itemLine', itemcode, invoiceQty, scanQty));
				}
			}
		});
		
		if (overItems.length > 0) {
			const msg = mhtml('warning.qtyOver.message', overItems.join('<br>'));
			Utils.showAlert(msg, 'warning'); 
			focusWithoutKeyboard();
			return;
		}
		
		
		let data = {
			date: $("#datepicker").val(),
			barcode: barcodeList,
			source: "INCOMINGCKD",
			storage: $('.storage-select').val(),
			factory: localStorage.getItem('rememberedFactory'),
			main:'IN'
		}
		Utils.showConfirm(m("confirm.send.all"), () => {
			$.ajax({
				url: `/purchase/palletCheckInbound`,
				method: 'POST',
				contentType: "application/json",
				data: JSON.stringify(data),
				success: function(result) {
					let response = result.response;
					console.log("response : " + response)
					if (response == "ok") {
						saveBarcode();
					}else{
//						showAlert("", result.barcode.join("\n") + `<br>${m(result.response)}`, "warning");
                        const barcodeHtml = makeBarcodesClickable(result.barcode);
                        showAlert("", barcodeHtml + `<br>${m(result.response)}`, "warning");	
						highlightErrorRows(result.barcode);		// 에러바코드 배경 빨간색으로 바꿔주는 함수					
						playSound('error')
					}
					hideLoading();
				},
				error: function(request, status, error) {
					console.log(error);
					if (request.status == 200) {

					} else if (request.status == 500) {
						Utils.showAlert(m("error.generic"), 'warning');
					} else if (request.status == 0) {
						Utils.showAlert(m("error.offline"), 'warning');
					}if (request.status === 401) {
						Utils.showAlert("Your session has expired. Please log in again.", 'warning');
			            window.location.href = "/login";
			        }else{
						Utils.showAlert("code: " + request.status + "<br>message: " + request.responseText + "<br>error: " + error, "warning");
					}
					hideLoading();
				}
			});
		},
		() => {
			Utils.showAlert(m("success.cancel.sendAll"), "#008000");
			hideLoading();
		})
}

/*function saveBarcode(){	// 파트바코드가 팔레트로 묶여진건지 확인
	const date = $("#datepicker").val();
	
	const barcodeList = getBarcodeList();

	if (barcodeList.length === 0) {
		  Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
		  focusWithoutKeyboard()
		  return;
	}
	let data = {
		date: $("#datepicker").val(),
		barcode: barcodeList,
		source: "INCOMINGCKD",
		storage: $('.storage-select').val(),
		factory: localStorage.getItem('rememberedFactory'),
		main:'IN'
	}
	console.log(data.barcode)
	
		try {
	showLoading();
	$.ajax({
	        url: `/purchase/barcodeTypeCheck`,
	        method: 'POST',
			contentType: "application/json",
			data: JSON.stringify(data),
			success:function(result){
				let response = result.response;
				console.log("response : "+response)
				if(response == "ok"){		// 팔레트로 묶인 파트라벨이 없을때 
					saveBarcode2('ok',result.list);
					$("#storageBtn").blur();
				}else if(response == "confirm"){
					Utils.showConfirmWithImage( '/images/palletunbound.png',result.list.join("<br>")+"<br><br>"+ m("warning.pallet.part.barcode"),() =>{
						saveBarcode2('confirm',result.list);
					},
					() =>{
						Utils.showAlert(m("success.cancel.sendAll"),"#008000");
						hideLoading();
					});
					$("#storageBtn").blur();
				}
				hideLoading();
			},
			error:function(request,status, error){
				console.log(error);
				if(request.status==200){
					
				}else if(request.status==500){
					Utils.showAlert(m("error.generic"), 'warning');
				}else if(request.status==0){
					Utils.showAlert(m("error.offline"), 'warning');
				}else{
					Utils.showAlert("code: " + request.status + "<br>message: " + request.responseText + "<br>error: " + error, "warning");
				}
				hideLoading();
			}
	    });
	        return result;
	    } catch (error) {
	        return null;
	    }
		hideLoading();
    }*/

function saveBarcode() {					// 전체전송
	// 중복 방지: 이미 저장 중이면 바로 종료
	console.log("isaving : "+isSaving);
    if (isSaving) {
        console.log("⚠ 중복 클릭 방지됨");
        return;
    }
	isSaving = true;

	const barcodeList = getBarcodeList();

	if (barcodeList.length === 0) {
		Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
		focusWithoutKeyboard()
		isSaving = false;
		return;
	}
	/*let custname2 =  $(".customer-select").val() || '';
	let custname = "";
	let custcode = "";
	if(custname2.includes("_")){
		custname = custname2.split("_")[1];
		custcode = custname2.split("_")[0];
	}*/
	let data = {
		date: $("#datepicker").val(),
		barcode: barcodeList,
		source: "INCOMINGCKD",
		storage: $('.storage-select').val(),
		/*custname: custname,
		cust: custcode,*/
		factory: localStorage.getItem('rememberedFactory'),
		main:'IN',
		memo:$("#memo").val()	// 인보이스
	}
	
		showLoading();
		$.ajax({
			url: `/purchase/insInboundInvoice`,
			method: 'POST',
			contentType: "application/json",
			data: JSON.stringify(data),
			success: function(result) {
				let response = result.response;
				console.log("response : " + response)
				if (response == "success") {
					removeBarcodeList();
					$("#dataTableBody-scan").empty();
					$("#count").text("0");
					$("#totalqty").text("0");
					Utils.showAlert(m("info.barcode.sent"), "info");
					playSound("complete");
				}else{
//					showAlert("", result.barcode.join("\n") + `<br>${m(result.response)}`, "warning");
                    const barcodeHtml = makeBarcodesClickable(result.barcode);
                    showAlert("", barcodeHtml + `<br>${m(result.response)}`, "warning");					
					highlightErrorRows(result.barcode);		// 에러바코드 배경 빨간색으로 바꿔주는 함수
					playSound('error')
				}
				hideLoading();
			},
			error: function(request, status, error) {
				console.log(error);
				if (request.status == 200) {

				} else if (request.status == 500) {
					Utils.showAlert(m("error.generic"), 'warning');
				} else if (request.status == 0) {
					Utils.showAlert(m("error.offline"), 'warning');
				} else {
					Utils.showAlert("code: " + request.status + "<br>message: " + request.responseText + "<br>error: " + error, "warning");
				}
				hideLoading();
			},
	        complete: function() {
	            hideLoading();
				// ❗ AJAX 끝나면 초기화
	            isSaving = false;
				console.log("isaving false 1 : "+isSaving);
	        }
		});
		return result;
	hideLoading();
}

function renderTable() {		//테이블그리기
	console.log("테이블그리기")
	let table = $("#dataTableBody-scan");
	let totalqty = 0;
	let barcodeArray = [];
	barcodeArray = getBarcodeList();
	
	table.empty();
	for (let i = 0; i < barcodeArray.length; i++) {
		let barcodeStr = barcodeArray[i];	// 전체 문자열
		let barcodeOneArr = barcodeArray[i].split(",");	// 내부 필드 배열
		let tbody = "";
		if (barcodeOneArr.length == 5 && barcodeStr.endsWith("MEX")) {	// 정상 바코드
			tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
							<td class = "dataInfo">${barcodeOneArr[0]}</td>
							<td class = "dataInfo">${Number(barcodeOneArr[3])}</td>
							<td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}', '${Number(barcodeOneArr[3])}')">${m('btn.delete')}</button></td>
						</tr>`
			totalqty = totalqty + Number(barcodeOneArr[3]);
		} else if (barcodeOneArr.length == 1 && barcodeOneArr[0][0] == "P") {
			tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
							<td>-</td>
							<td>-</td>
							<td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}')">${m('btn.delete')}</button></td>
						</tr>`
		} else if (barcodeOneArr[0][0] == "P" && barcodeStr.endsWith("MEX")) {
			tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
							<td class = "dataInfo">(P)${barcodeOneArr[1]}</td>
							<td class = "dataInfo">${Number(barcodeOneArr[2])}</td>
							<td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}', '${Number(barcodeOneArr[2])}')">${m('btn.delete')}</button></td>
						</tr>`
			totalqty = totalqty + Number(barcodeOneArr[2]);
		}
		table.append(tbody);
	}
	$("#count").text(+barcodeArray.length);
	$("#totalqty").text(formatNumber(totalqty));
}


function deleteEntry(bar, qty) {		// localstorage에서 특정데이터 삭제
	let className = "bar_" + bar;
	console.log("삭제 바코드 : " + bar)
	Utils.showConfirm(m("confirm.delete.item"), () => {
		let barcodeArray = getBarcodeList();
		let newArray = barcodeArray.filter(item => item.toString().trim() !== bar.toString().trim());
		setBarcodeList(newArray);
		$("." + CSS.escape(className)).remove();
		$("#count").text(newArray.length);
		
		// 바코드에서 itemcode, qty 추출
		const { bcItem, bcQty } = parseBarcodeInfo(bar);
		
		// 스캔 수량 반영
		if (bcItem){
			const invoiceItem = $(`#dataTableBody-invoice tr[data-itemcode="${bcItem}"]`);
			
			const scanTd = invoiceItem.find('td').eq(2);
			const curQty = Number(String(scanTd.text()).replace(/,/g, '')) || 0;
		    const newQty = Math.max(0, curQty - bcQty);
			scanTd.text(newQty.toLocaleString());
		}
		
		let totalqty = $("#totalqty").text();
		totalqty = Number(totalqty) - Number(qty);
		$("#totalqty").text(formatNumber(totalqty));
		Utils.showAlert(m("success.deleted"), 'success');
		checkScanQtyColor();
	})
	focusWithoutKeyboard()
}

function clearAll() {			//localstorage 전체삭제
	Utils.showConfirm(m("confirm.delete.all"), () => {
		const $tb = $('#dataTableBody-scan');
		console.log($tb);
		console.log($tb.children('tr'));
		console.log($tb.children('tr').length);
		if ($tb.children('tr').length === 0) {
			Utils.showAlert(m('warning.data.delete.all'), "warning");
			return;
		}

		removeBarcodeList();
		$("#dataTableBody-scan").empty();
		$("#totalqty").text("0");
		$("#count").text("0");
		selectInvoice();
		Utils.showAlert(m("success.deleted.all"), "success");
	})
	focusWithoutKeyboard()
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
/*$(document).on("focus", ".customer-select", function() {
    showLoading();
});*/

$(document).on("change blur", ".customer-select", function() {
    hideLoading();
});




//바코드 문자열에서 itemcode, qty 추출하는 공용 함수
function parseBarcodeInfo(barcode) {
	const parts = barcode.trim().toUpperCase().split(",");
	let bcItem = null;
	let bcQty  = 0;

	// 파트라벨
	if (parts.length === 5 && (parts[4] === "WMSMEX" || parts[4] === "SCMMEX")) {
		bcItem = parts[0];
		bcQty  = Number(parts[3]) || 0;
	}
	// 팔레트 라벨
	else if (parts.length > 1 && barcode.endsWith("MEX") && parts[0].startsWith("P")) {
		bcItem = parts[1];
		bcQty  = Number(parts[2]) || 0;
	}
	// 팔레트 ID 단독
	else if (parts.length === 1 && parts[0][0] === "P") {
		bcItem = null;
		bcQty  = 0;
	}
	
	return { bcItem, bcQty };
}

//스캔 수량 색상 변경
function checkScanQtyColor(){
	$('#dataTableBody-invoice tr').each(function() {
		const tds = $(this).find('td');
		if (tds.length >= 3) {
			const itemcode   = tds.eq(0).text().trim();
			const invoiceQty = Number(String(tds.eq(1).text()).replace(/,/g, '')) || 0;
			const scanQty    = Number(String(tds.eq(2).text()).replace(/,/g, '')) || 0;
			
			const qtyCell = tds.eq(2); 

			if (invoiceQty === scanQty){
				qtyCell.css('color', 'green');
			} else if (invoiceQty < scanQty){
				qtyCell.css('color', 'orange');
			} else {
				qtyCell.css('color', 'red');
			}
		}
	});
}

//로컬스토리지에 저장된 바코드 기준으로 scanQty 재계산
function rebuildScanQtyFromLocal() {
	// 로컬스토리지에 저장된 바코드 배열 불러오기
	const barcodeArray = getBarcodeList();
	if (barcodeArray.length === 0) {
		console.log("로컬스토리지에 저장된 바코드가 없습니다.");
		return;
	}

	// 각 바코드에서 품번/수량 추출 후 해당 품번 행의 scanQty 누적
	for (let i = 0; i < barcodeArray.length; i++) {
		const barcode = barcodeArray[i];
		const { bcItem, bcQty } = parseBarcodeInfo(barcode); // 기존 함수 재활용

		if (bcItem) {
			const invoiceRow = $(`#dataTableBody-invoice tr[data-itemcode="${bcItem}"]`);
			if (invoiceRow.length > 0) {
				const scanTd = invoiceRow.find('td').eq(2);
				const curQty = Number(String(scanTd.text()).replace(/,/g, '')) || 0;
				const newQty = curQty + (Number(bcQty) || 0);
				scanTd.text(newQty.toLocaleString());
			}
		}
	}
}





/* =============================================================================
 * 로컬 스토리지 만료 기간 설정 및
 * ============================================================================= */
//인보이스별 네임스페이스 키 (쿼리의 ?invoice= 값으로 분리 저장)
function getInvoiceKey() {
	const invoice = new URLSearchParams(location.search).get('invoice') || 'default';
	return `barcodeListInInvoice:${invoice}`;
}

// 로컬스토리지에 만료시간(expiry) 메타와 함께 값을 저장
function setWithExpiry(key, value, ttlMs) {
	const item = { value, expiry: Date.now() + ttlMs };
	localStorage.setItem(key, JSON.stringify(item));
}

function getWithExpiry(key) {
	const s = localStorage.getItem(key);
	if (!s) return null;
	try {
		const item = JSON.parse(s);
		if (item && typeof item.expiry === 'number') {
			if (Date.now() > item.expiry) {
				localStorage.removeItem(key);
				return null;
			}
			return item.value;
		}
		// (과거 포맷 대비) expiry 없으면 그대로 value/원본 반환
		return item.value ?? null;
	} catch {
		// 파싱 실패: 데이터 손상으로 판단하고 삭제
		localStorage.removeItem(key);
		return null;
	}
}

// 리스트 전용 래퍼 (읽기/쓰기/삭제)
function getBarcodeList() {
	return getWithExpiry(getInvoiceKey()) || [];
}

// 현재 인보이스 네임스페이스로 바코드 리스트를 저장
function setBarcodeList(arr) {
	setWithExpiry(getInvoiceKey(), arr, 24 * 60 * 60 * 1000); // 24시간
}

// 현재 인보이스 네임스페이스의 바코드 리스트를 완전히 삭제
function removeBarcodeList() {
	localStorage.removeItem(getInvoiceKey());
}






