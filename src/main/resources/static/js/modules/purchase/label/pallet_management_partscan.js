let manualTouch = false;
$(document).ready(function() {
	hideLoading();
	// 페이지 로드 시 조회
	//search();
	//renderTable();
	localStorage.removeItem("barcodeListpart");
})
let scanBarcodeList = [];

function renderTable() {		//테이블그리기
	    
    // data.list가 실제 배열 데이터
	let table = $("#dataTableBody");
	table.empty();
	let barcodeArray = [];

	if (typeof localStorage !== 'undefined' && localStorage.getItem("barcodeListpart")) {
		barcodeArray = JSON.parse(localStorage.getItem("barcodeListpart"));
	} else {
		barcodeArray = [];
	}
	
	totalcnt = barcodeArray.length;
	let scanqty = 0;
	let cnt = 0;
	if(barcodeArray.length>0){
		for(let i = 0; i < barcodeArray.length; i++){
			let barcodeOneArr = barcodeArray[i].split(",");
			let tbody = `
			<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
				<td class = "dataInfo">${barcodeOneArr[0]}</td>
				<td class = "dataInfo">${barcodeOneArr[1]}</td>
				<td class = "dataInfo">${barcodeOneArr[2]}</td>
				<td class = "dataInfo">${Number(barcodeOneArr[3])}</td>
				<td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}', '${Number(barcodeOneArr[3])}')">${m('btn.delete')}</button></td>
			</tr>
			`;
			table.append(tbody);
		}
	}else{
		Utils.showAlert("No Data", "warning");
		hideLoading();
	}
	$("#count").text(cnt+"/"+totalcnt);
	$("#totalqty").text(formatNumber(scanqty));
	hideLoading();
}




function addEntry() {			// 로컬스토리지 저장
	const barcodeInput = document.getElementById('barcodeInput');
	const barcode = barcodeInput.value.trim();
	let saveItemcode = $("#barcode").val().split(",")[1];
	if (!barcode) {
		Utils.showAlert(m("warning.barcode.required"));
		return;
	}
	// 🔹 저장 제한 체크
	if (!savelimitCheck("barcodeListpart", 500)) {
		return; // 500개 초과 시 여기서 중단 → showLoading() 실행 안 됨
	}
	
	if (inputMode === 'manual') {
		if (barcode) {
			$('#barcodeInput').val('');
			$('#barcodeInput').attr('readonly', true);
			inputMode = 'readonly';
		}
	} else {
		console.warn("현재 스캔 모드입니다.");
	}

	if ((barcode.split(",").length === 5 && (barcode.split(",")[4] === "SCMMEX" || barcode.split(",")[4] === "WMSMEX" || barcode.split(",")[4] === "WMSUSA"))) {
		let stored = [];

		if (window.localStorage && localStorage.getItem("barcodeListpart")) {
			stored = JSON.parse(localStorage.getItem("barcodeListpart"));
		} else {
			stored = [];
		}
		if(saveItemcode != barcode.split(",")[0]){
			Utils.showAlert(m("info.oneItemPerPallet"));
			$("#barcodeInput").val("");
			let audio = new Audio('/sounds/buzzer.wav');
			audio.play();
			hideLoading();
			return;
		}

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
			localStorage.setItem("barcodeListpart", JSON.stringify(stored));
			$("#barcodeInput").val("");
			renderTable();
			hideLoading();
			Utils.showAlert(`${barcode}<br>${m("info.barcode.saved")}`, "#008000", barcode)
		}
	} else {
		let audio = new Audio('/sounds/buzzer.wav');
		audio.play();
		$("#barcodeInput").val("");
		hideLoading();
		Utils.showAlert(`${m("warning.barcode.invalid")}<br>${m("warning.check")}`, "warning")
		return;
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

	const barcodeList = JSON.parse(localStorage.getItem("barcodeListpart") || "[]");

	if (barcodeList.length === 0) {
		Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
		focusWithoutKeyboard()
		isSaving = false;
		return;
	}
	let data = {
		barcode: barcodeList ,
		barcodeone:$("#barcode").val(),
		factory:localStorage.getItem('rememberedFactory'),
	}
	console.log("barcode : "+barcodeList)
	Utils.showConfirm(m("confirm.send.all"), () => {
		showLoading();
		$.ajax({
		       url: "/purchase/insPalletBarcode",
		       method: 'POST',
			   contentType: "application/json",
			   data: JSON.stringify(data),
		       success: function(result) {
				let response = result.response;
				if (response == "success") {
					localStorage.removeItem('barcodeListpart');
					$("#dataTableBody").empty();
					$("#count").text("0");
					$("#totalqty").text("0");
					Utils.showAlert(m("info.barcode.sent"), "info");
					playSound("complete");
					hideLoading();
				}else{
                    const barcodeHtml = makeBarcodesClickable(result.barcode);
                    showAlert("", barcodeHtml + `<br>${m(result.response)}`, "warning");
					
					highlightErrorRows(result.barcode);		// 에러바코드 배경 빨간색으로 바꿔주는 함수
					playSound('error')
					hideLoading();
				}
				   
		       },
		       error: function(xhr, status, error) {
				if (request.status === 401) {
						Utils.showAlert("Your session has expired. Please log in again.", 'warning');
			            window.location.href = "/login";
			        }else{
		           console.error("DB 전송 실패:", error);
		           Utils.showAlert("Failed to save scan data.", "error");
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
	   },
	   		() => {
	   			Utils.showAlert(m("success.cancel.sendAll"), "#008000");
				isSaving = false;
	   			hideLoading();
	   		})
}



function search(){
	const barcode = $("#barcode").val();		
	console.log(barcode);
	showLoading();
	$.ajax({
		url: "/purchase/unpack_barcode_list",		
        method: 'POST',
		data: {barcode:barcode},
		success: function(result) {
			console.log(result)
			console.log(result.list);
			if(result.list.list.length>0){
				console.log("최대 스캔수량"+result.list.list[0].TOTALQTY);
				$("#unpackqty").val(result.list.list[0].TOTALQTY);
				$("#barcodeInput").val("");
				console.log("서버 응답 전체:", result);
			}
			renderTable(result.list);
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