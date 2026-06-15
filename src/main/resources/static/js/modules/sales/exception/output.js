let manualTouch = false;
$(document).ready(function(){
	hideLoading();
	var today = new Date();
	sangho();	
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
	
})
	function sangho(){
		$.ajax({
			url: "/purchase/load_sangho",
			type: "POST",
			contentType: "application/json",
			success: function(data) {
				//console.log(data)
				let output = '<option value=" "></option>';
				console.log(data.length)
				for (i = 0; i < data.length; i++) {
					output += `<option value="${data[i]}">${data[i]}</option>`
				}
				$("select.customer-select").prepend(output);
				
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
	function  addEntry() {			// 로컬스토리지 저장
        const barcodeInput = document.getElementById('barcodeInput');
        const barcode = barcodeInput.value.trim();
        
        if (!barcode) {
            Utils.showAlert(m("warning.barcode.required"));
            return;
        }
		// 🔹 저장 제한 체크
	   if (!savelimitCheck("barcodeListexceptionOutput", 500)) {
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
			
		if((barcode.split(",").length == 5 && (barcode.split(",")[4] == "SCMMEX" || barcode.split(",")[4] == "WMSMEX"))  || (barcode[0][0] == "P" && barcode.endsWith("MEX") || barcode.split(",").length == 1 && barcode[0][0] == "P")){
			let stored = [];
		
			if (window.localStorage && localStorage.getItem("barcodeListexceptionOutput")) {
			    stored = JSON.parse(localStorage.getItem("barcodeListexceptionOutput"));
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
				localStorage.setItem("barcodeListexceptionOutput", JSON.stringify(stored));
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

		const barcodeList = JSON.parse(localStorage.getItem("barcodeListexceptionOutput") || "[]");

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
			date:$("#datepicker").val(),
			barcode: barcodeList,
			source:"LOADEXCEPTION",
			main:"OUT",
			kind:"LOADEXCEPTION",
			/*cust:custcode,
			custname : custname,*/
			storage: $(".storage-select").val(),
			factory:localStorage.getItem('rememberedFactory'),
			memo:$("#memo").val()
		}
		Utils.showConfirm(m("confirm.send.all"), () => {
		showLoading();
		        $.ajax({
		            url: `/purchase/insOutput`,
		            method: 'POST',
					contentType: "application/json",
					data: JSON.stringify(data),
					success:function(result){
						let response = result.response;
						console.log("response : "+response)
						if(response == "success"){
							localStorage.removeItem('barcodeListexceptionOutput');
							$("#dataTableBody").empty();
							$("#count").text("0");
							$("#totalqty").text("0");
							Utils.showAlert(m("info.barcode.sent"), "info");
							playSound('complete');
						}else{
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
						}if (request.status === 401) {
							Utils.showAlert("Your session has expired. Please log in again.", 'warning');
				            window.location.href = "/login";
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
			hideLoading();
	    },
		() =>{
			Utils.showAlert(m("success.cancel.sendAll"),"#008000");
			hideLoading();
			isSaving = false;
		})
	}
	function renderTable() {		//테이블그리기
		console.log("테이블그리기")
		let table = $("#dataTableBody");
		let totalqty = 0;
		let barcodeArray = [];

		if (typeof localStorage !== 'undefined' && localStorage.getItem("barcodeListexceptionOutput")) {
		    barcodeArray = JSON.parse(localStorage.getItem("barcodeListexceptionOutput"));
		} else {
		    barcodeArray = [];
		}
		table.empty();
		for(let i = 0; i<barcodeArray.length; i++){
			let barcodeStr = barcodeArray[i];	// 전체 문자열
			let barcodeOneArr = barcodeArray[i].split(",");	// 내부 필드 배열
			let tbody = "";
			if(barcodeOneArr.length == 5 && barcodeStr.endsWith("MEX")){	// 정상 바코드
				tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
							<td class = "dataInfo">${barcodeOneArr[0]}</td>
							<td class = "dataInfo">${barcodeOneArr[1]}</td>
							<td class = "dataInfo">${barcodeOneArr[2]}</td>
							<td class = "dataInfo">${Number(barcodeOneArr[3])}</td>
							<td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}','${Number(barcodeOneArr[3])}')">${m('btn.delete')}</button></td>
						</tr>`;
				totalqty = totalqty + Number(barcodeOneArr[3]);
			}else if(barcodeOneArr.length == 1 && barcodeOneArr[0][0] == "P"){
				tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
							<td>-</td>
							<td>${barcodeArray[i].substring(1,7)}</td>
							<td>${barcodeArray[i].substring(7)}</td>
							<td>-</td>
							<td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}')">${m('btn.delete')}</button></td>
						</tr>`;
					
			}else if(barcodeOneArr[0][0] == "P" &&barcodeStr.endsWith("MEX")){
				tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
							<td class = "dataInfo">(P)${barcodeOneArr[1]}</td>
							<td class = "dataInfo">${barcodeArray[i].substring(1,7)}</td>
							<td class = "dataInfo">${barcodeArray[i].substring(7,12)}</td>
							<td class = "dataInfo">${Number(barcodeOneArr[2])}</td>
							<td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}','${Number(barcodeOneArr[2])}')">${m('btn.delete')}</button></td>
						</tr>`;
				totalqty = totalqty + Number(barcodeOneArr[2]);
			}
			table.append(tbody);
		}
		$("#count").text(+barcodeArray.length);
		$("#totalqty").text(totalqty);
	}
	
	function deleteEntry(bar,qty){		// localstorage에서 특정데이터 삭제
		let className = "bar_" + bar;
		console.log("삭제 바코드 : "+bar)
		Utils.showConfirm(m("confirm.delete.item"), () => {
			let barcodeArray = JSON.parse(localStorage.getItem("barcodeListexceptionOutput") || "[]");
		    let newArray = barcodeArray.filter(item => item.toString().trim() !== bar.toString().trim());
		    localStorage.setItem("barcodeListexceptionOutput", JSON.stringify(newArray));
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
			
			localStorage.removeItem("barcodeListexceptionOutput");
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
		const itemcode = isPallet ? itemCode.replace(/^\s*\(\s*p\s*\)\s*/i, "") : itemCode;
		
		
		$.ajax({
			url: "/purchase/getItemInfo",
			type: "GET",
			data: { itemcode},
			dataType : "json",
			success: function(result){
				console.log(result);			
				showPopup(result.list);
			}
		})
	});