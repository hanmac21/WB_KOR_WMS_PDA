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


function renderTable(result) {		//테이블그리기
	$("#barcode").text(result.BARCODE);
	$("#itemcode").text(result.ITEMCODE);
	$("#itemname").text(result.ITEMNAME);
	$("#nowqty").text(result.QTY);
	hideLoading();
}


function addEntry(){
	const barcode = $("#barcodeInput").val();		// barcode로 변수명 바꿔야함 250901
	console.log(barcode);
	let data = {
		factory:localStorage.getItem('rememberedFactory'),
		barcode: barcode
	}
	// 파트라벨만 가능
	if((barcode.split(",").length == 5) || barcode.split("_",-1).length == 6){
		showLoading();
		$.ajax({
			url: "/purchase/part_info_search",		
	        method: 'GET',
			data: data,
			success: function(data) {
				if(data.result.result== null){
					const barcodeHtml = makeBarcodesClickable(barcode.split(" "));
					showAlert("", barcodeHtml + `<br>${m("warning.barcode.storagecheck")}`, "warning");
					hideLoading();
					$("#barcodeInput").val("");
				}else{
					$("#barcodeInput").val("");
					renderTable(data.result.result);
				}
				
				
			},
			error: function(xhr, status, error) {
				console.error("요청 실패");
				console.error("Status:", status);       // 예: "error"
				console.error("Error:", error);         // 예: 서버 응답 메시지
				console.error("Response:", xhr.responseText); // 서버 응답 본문
				alert("오류가 발생했습니다: " + error);
				hideLoading();
			}
		});
	}else{
		playSound('error')
		Utils.showAlert(m("warning.barcode.invalid")+"<br>"+m("warning.check") , "warning", barcode)
		$("#barcodeInput").val("");
		$("#dataTableBody").empty();
		$("#count").text(0);
		$("#totalqty").text(0);
	}
}

function adjustment(){
	const barcode = $("#barcode").text();		
	const nowqty = $("#nowqty").text();
	const adjustqty = $("#adjustqty").val()
	const memo = $("#memo").val()
	
	
	if(memo.trim() === ""){
		showAlert("", m("warning.memo.empty"), "warning");
        return;
	}	
	if(adjustqty == "" || isNaN(adjustqty)){
		showAlert("", m("warning.input.adjustqty"), "warning");
	    return;
	}
	
	if(Number(adjustqty) < 0){
		showAlert("", m("warning.adjustqty.negative"), "warning");
		return;
	}
	
	if(Number(adjustqty) === 0){
		showAlert("", m("warning.adjustqty.zero"), "warning");
		return;
	}
	
	if(Number(adjustqty) === Number(nowqty)){
		showAlert("", m("warning.adjustqty.same"), "warning");
		return;
	}

	Utils.showConfirm(m("confirm.adjustment"), () => {
	console.log(memo);
	let data = {
		factory:localStorage.getItem('rememberedFactory'),
		barcode: barcode,
		nowqty : nowqty,
		adjustqty : adjustqty,
		memo : memo
	}
	showLoading();
	$.ajax({
		url: "/purchase/part_adjustment_update",		
        method: 'POST',
		contentType: "application/json",
		data: JSON.stringify(data),
		success: function(result) {
			$("#barcodeInput").val("");
			$("#adjustqty").val("");
			$("#memo").val("");
			$("#barcode").text("");
			$("#itemcode").text("");
			$("#itemname").text("");
			$("#nowqty").text("");
            showAlert("", m("info.barcode.saved"), "success");
		    hideLoading();
		},
		error: function(xhr, status, error) {
			console.error("요청 실패");
			console.error("Status:", status);       // 예: "error"
			console.error("Error:", error);         // 예: 서버 응답 메시지
			console.error("Response:", xhr.responseText); // 서버 응답 본문
			alert("오류가 발생했습니다: " + error);
			hideLoading();
		}
	});
	});
}