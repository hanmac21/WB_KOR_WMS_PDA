let manualTouch = false;
$(document).ready(function(){
	hideLoading();
	var today = new Date();
			
	function dateFormat(date){
		let month = date.getMonth() + 1;
		let day = date.getDate();
		
		month = month >= 10 ? month : '0' + month;
		day = day >= 10? day : '0' + day;
		
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
    
    $(document).on('click', '.ui-datepicker-current', function () {
    const today = new Date();
    $("#datepicker").datepicker("setDate", today);
	
    // 오늘 날짜 설정 후 키보드 안뜨게 포커스
    if (typeof focusWithoutKeyboard === 'function') {
		$("#datepicker").datepicker("hide");
        focusWithoutKeyboard();
    }
  });

	renderTable();

	focusWithoutKeyboard();
	// input창에서 포커스 없어질때 세팅
	$('#barcodeInput').on('blur', function () {
	  manualTouch = false;
	  inputMode = 'readonly'
	});

	// ILLINOIS 창고 선택 시 OUTSIDE 기본 적용
	const _storedWarehouse = (localStorage.getItem("rememberedWarehouse") || "").trim();
	if (_storedWarehouse === 'ILLINOIS') {
		$('.storage-in').val('OUTSIDE');
	}

})



function  addEntry() {			// 로컬스토리지 저장
    const barcodeInput = document.getElementById('barcodeInput');
    const barcode = barcodeInput.value.trim();
    
    if (!barcode) {
        Utils.showAlert(m("warning.barcode.required"));
        return;
    }
	// 🔹 저장 제한 체크
   if (!savelimitCheck("barcodeListWHMoveIn", 500)) {
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
		
	if(barcode.split("_").length === 6 || barcode.split(",")[3] === "WMSUSA" || barcode.split(",")[4] === "WMSUSA"){
		let stored = [];
	
		if (window.localStorage && localStorage.getItem("barcodeListWHMoveIn")) {
		    stored = JSON.parse(localStorage.getItem("barcodeListWHMoveIn"));
		} else {
		    stored = [];
		}
		
		if (stored.includes(barcode)) {
			console.log("barcode : "+barcode)
			let audio = new Audio('/sounds/buzzer2.wav');
			audio.play();
			$("#barcodeInput").val("");
			Utils.showAlert(`${barcode}<br>${m("warning.barcode.duplicate")}`);
			hideLoading();
			return;
		}else{
			let audio = new Audio('/sounds/complete.wav');
			audio.play();
			
			stored.push(barcode);
			localStorage.setItem("barcodeListWHMoveIn", JSON.stringify(stored));
			$("#barcodeInput").val("");
			renderTable();
			hideLoading();
			Utils.showAlert(`${barcode}<br>${m("info.barcode.saved")}`, "#008000", barcode)
		}
	}else{
		let audio = new Audio('/sounds/buzzer.wav');
		audio.play();
		$("#barcodeInput").val("");
		hideLoading();
		Utils.showAlert(`${m("warning.barcode.invalid")}<br>${m("warning.check")}`, "warning")
		return;
	}
}



function saveBarcode() {					// 전체전송
	// 중복 방지: 이미 저장 중이면 바로 종료
	console.log("isaving : "+isSaving);
    if (isSaving) {
        console.log("⚠ 중복 클릭 방지됨");
        return;
    }
	isSaving = true;

	const barcodeList = JSON.parse(localStorage.getItem("barcodeListWHMoveIn") || "[]");

	console.log("storage : "+$("#datepicker").val());
	if (barcodeList.length === 0) {
		  Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
		  focusWithoutKeyboard()
		  isSaving = false;
		  return;
	}
	let data = {
		date:$("#datepicker").val(),
		barcode: barcodeList,
		factory:localStorage.getItem('rememberedFactory'),
		storage2: $('.storage-in').val()		,
		mainkind: 'MOVE',
        kind: 'STORAGEMOVE',
        source: 'RECEIVING'
	}
	Utils.showConfirm("Do you want to change the warehouse?", () => {
	showLoading();
	        $.ajax({
	            url: `/purchase/transferWarehouse`,
	            method: 'POST',
				contentType: "application/json",
				data: JSON.stringify(data),
				success:function(result){
					let response = result.response;
					console.log("response : "+response)
					if(response == "success"){
						localStorage.removeItem('barcodeListWHMoveIn');
						$("#dataTableBody").empty();
						$("#count").text("0");
						$("#totalqty").text("0");
						Utils.showAlert("Factory Move Complete", "info");
					}else{
						hideLoading();
						//showAlert("", result.barcode.join("\n") + `<br>${m(result.response)}`, "warning");
						const barcodeHtml = makeBarcodesClickable(result.barcode);
						showAlert("", barcodeHtml + `<br>${m(result.response)}`, "warning");	
						highlightErrorRows(result.barcode);		// 에러바코드 배경 빨간색으로 바꿔주는 함수

						playSound('error')
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
            hideLoading();
            isSaving = false; // 전송 끝나면 다시 가능
        })
}

function renderTable() {		//테이블그리기
	console.log("테이블그리기")
	let table = $("#dataTableBody");
//	const storage = $('.storage-select').val();
	let totalqty = 0;
	let barcodeArray = [];

	if (typeof localStorage !== 'undefined' && localStorage.getItem("barcodeListWHMoveIn")) {
	    barcodeArray = JSON.parse(localStorage.getItem("barcodeListWHMoveIn"));
	} else {
	    barcodeArray = [];
	}
	table.empty();
	for(let i = 0; i<barcodeArray.length; i++){
		let barcodeStr = barcodeArray[i];	// 전체 문자열
		let barcodeOneArr = barcodeArray[i].split("_");	// 내부 필드 배열
		let tbody = "";
		if (barcodeOneArr.length === 6 ) {
            tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
                        <td class = "dataInfo">${barcodeOneArr[3]}</td>
                        <td class = "dataInfo">${barcodeOneArr[2].substring(2)}${barcodeOneArr[1]}${barcodeOneArr[0]}</td>
                        <td class = "dataInfo">${Number(barcodeOneArr[4])}</td>
                        <td class = "dataInfo">${Number(barcodeOneArr[5])}</td>
                        <td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}', '${Number(barcodeOneArr[4])}')">${m('btn.delete')}</button></td>
                    </tr>`
            totalqty = totalqty + Number(barcodeOneArr[4]);
        }else if (barcodeStr.split(",").length === 5  && barcodeStr.endsWith("USA")){       // USA파트라벨
            let parts = barcodeStr.split(",");
            tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
                            <td class = "dataInfo">${parts[0]}</td>
                            <td class = "dataInfo">${parts[1]}</td>
                            <td class = "dataInfo">${Number(parts[3])}</td>
                            <td class = "dataInfo">${Number(parts[2])}</td>
                            <td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}', '${Number(barcodeOneArr[4])}')">${m('btn.delete')}</button></td>
                        </tr>`
            totalqty = totalqty + Number(parts[3]);
        }else if(barcodeStr.split(",").length === 4  && barcodeStr.endsWith("USA")){        // USA파레트라벨
            let parts = barcodeStr.split(",");
            tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
                            <td class = "dataInfo">${parts[1]}</td>
                            <td class = "dataInfo">${parts[0].substring(1, 7)}</td>
                            <td class = "dataInfo">${Number(parts[2])}</td>
                            <td class = "dataInfo">${Number(parts[0].substring(7))}</td>
                            <td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}', '${Number(barcodeOneArr[4])}')">${m('btn.delete')}</button></td>
                        </tr>`
            totalqty = totalqty + Number(parts[2]);
        }
		table.prepend(tbody);
	}
	$("#count").text(+barcodeArray.length);
	$("#totalqty").text(" "+formatNumber(totalqty));
}

function deleteEntry(bar, qty){		// localstorage에서 특정데이터 삭제
	let className = "bar_" + bar;
	console.log("삭제 바코드 : "+bar)
	Utils.showConfirm(m("confirm.delete.item"), () => {
		let barcodeArray = JSON.parse(localStorage.getItem("barcodeListWHMoveIn") || "[]");
	    let newArray = barcodeArray.filter(item => item.toString().trim() !== bar.toString().trim());
	    localStorage.setItem("barcodeListWHMoveIn", JSON.stringify(newArray));
	    $("." + CSS.escape(className)).remove();
	    $("#count").text(newArray.length);
		let totalqty = $("#totalqty").text();
		totalqty = Number(totalqty) - Number(qty);
		$("#totalqty").text(formatNumber(totalqty));
	    Utils.showAlert(m("success.deleted"), 'success');
	})
    focusWithoutKeyboard()
	
}

function clearAll(){			//localstorage 전체삭제
	Utils.showConfirm(m("confirm.delete.all"), () => {
		const $tb = $('#dataTableBody');
		console.log($tb);
		console.log($tb.children('tr'));
		console.log($tb.children('tr').length);			
		if($tb.children('tr').length === 0){
			Utils.showAlert(m('warning.data.delete.all'), "warning");
			return;
		}
		
		localStorage.removeItem("barcodeListWHMoveIn");
		$("#dataTableBody").empty();
		$("#count").text("0")
		$("#totalqty").text("0")
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
	const pno = isPallet ? itemCode.replace(/^\s*\(\s*p\s*\)\s*/i, "") : itemCode;
	
	
	$.ajax({
		url: "/purchase/getItemInfo",
		type: "GET",
		data: { pno},
		dataType : "json",
		success: function(result){
			console.log(result);			
			showPopup(result.list);
		}
	})
});