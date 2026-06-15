let manualTouch = false;
$(document).ready(function() {
	hideLoading();
	$(document).on('click', '.ui-datepicker-current', function() {
		const today = new Date();
		$("#datepicker").datepicker("setDate", today);

		// 오늘 날짜 설정 후 키보드 안뜨게 포커스
//		if (typeof focusWithoutKeyboard === 'function') {
//			$("#datepicker").datepicker("hide");
//			focusWithoutKeyboard();
//		}
	});
})


function renderTable(list) {		//테이블그리기
	console.log("테이블그리기")
	let table = $("#dataTableBody");
	let totalqty = 0;
	table.empty();
	for(let i = 0; i<list.length; i++){
		let barcode = list[i].PBARCODE.split(",").slice(0, -2).join(",");
			let tbody = `
				<tr class = 'unpack-row' data-barcode="${list[i].PBARCODE}" style="cursor: pointer;">
					<td class="barcode-cell">${(list[i]?.PBARCODE == null || list[i]?.PBARCODE === 'null') ? '' : barcode}</td>
					<td>${(list[i]?.QTY == null || list[i]?.QTY === 'null') ? '' : list[i].QTY}</td>
		        </tr>
			`;
			table.append(tbody)
			totalqty = totalqty + Number(list[i].QTY);
	}
	
	$("#count").text(list.length);
	$("#totalqty").text(formatNumber(totalqty));
	hideLoading();
}

function addEntry(){
	const sdate = $("#datepicker").val();
	const barcode = $("#barcodeInput").val();		// barcode로 변수명 바꿔야함 250901
	console.log(barcode);
	if((barcode.split(",").length === 4 && barcode[0][0] === "P" && (barcode.endsWith("MEX") || barcode.endsWith("USA")))){
		let data = {
			date: sdate,
			factory:localStorage.getItem('rememberedFactory'),
			barcode: barcode
		}
		showLoading();
		$.ajax({
			url: "/purchase/pallet_unbound_search",		
	        method: 'POST',
			data: data,
			success: function(result) {
				console.log(result.list.list);
				$("#barcodeInput").val("");
				$("#barcodeVal").val(barcode);
				renderTable(result.list.list);
				hideLoading();
			},
			error: function(xhr, status, error) {
				hideLoading();
				console.error("요청 실패");
				console.error("Status:", status);       // 예: "error"
				console.error("Error:", error);         // 예: 서버 응답 메시지
				console.error("Response:", xhr.responseText); // 서버 응답 본문
				alert("오류가 발생했습니다: " + error);
			}
		});
	}else{
		playSound('error')
		Utils.showAlert(m("warning.barcode.invalid")+"<br>"+m("warning.check") , "#FF0000", barcode)
		$("#barcodeInput").val("");
		$("#barcodeVal").val("");
		$("#dataTableBody").empty();
		$("#count").text(0);
		$("#totalqty").text(0);
	}
}

function saveBarcode(){
	// 중복 방지: 이미 저장 중이면 바로 종료
	console.log("isaving : "+isSaving);
    if (isSaving) {
        console.log("⚠ 중복 클릭 방지됨");
        return;
    }
	isSaving = true;
	let barcode = $("#barcodeVal").val()
	let data = {
		factory:localStorage.getItem('rememberedFactory'),
		barcode: barcode
	}
	if (barcode.trim()=="") {
		Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
		focusWithoutKeyboard()
		isSaving = false;
		return;
	}
	showLoading();
	$.ajax({
		url: "/purchase/pallet_unbound",		
        method: 'POST',
		data: data,
		success: function(result) {
			let response = result.list.response;
			console.log(response);
			if (response == "success") {
				$("#barcodeInput").val("");
				$("#barcodeVal").val("");
				$("#dataTableBody").empty();
				playSound("complete");
			}else{
                const barcodeHtml = makeBarcodesClickable(result.list.barcode);
                showAlert("", barcodeHtml + `<br>${m(response)}`, "warning");
				playSound('error')
			}
			hideLoading();
		},
		error: function(xhr, status, error) {
			hideLoading();
			console.error("요청 실패");
			console.error("Status:", status);       // 예: "error"
			console.error("Error:", error);         // 예: 서버 응답 메시지
			console.error("Response:", xhr.responseText); // 서버 응답 본문
			alert("오류가 발생했습니다: " + error);
		},
        complete: function() {
            hideLoading();
			// ❗ AJAX 끝나면 초기화
            isSaving = false;
			console.log("isaving false 1 : "+isSaving);
        }
	});
}