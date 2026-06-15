let manualTouch = false;
$(document).ready(function() {
	hideLoading();
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
//			focusWithoutKeyboard();
			if ($(window.event.target).hasClass('ui-datepicker-current')) {
				$(this).datepicker('setDate', new Date());

			}
		}
	});
	$("#datepicker").val(today);
	$("#datepicker").datepicker();

	// 페이지 로드 시 조회
	search();
	
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
	console.log("테이블그리기 : "+list.length)
	for(let i = 0; i < list.length; i++){
		let barcode = list?.[i]?.BARCODE.split(",").slice(0,-1)
		let tbody = `
			<tr>
				<td>${(list?.[i]?.BARCODE == null || list?.[i]?.BARCODE === 'null') ? '' : barcode}</td>
				<td>${(list?.[i]?.TIME == null || list?.[i]?.TIME === 'null') ? '' : list[i].TIME}</td>
				<td><button class="delete-btn" onclick="deleteEntry('${list?.[i]?.BARCODE}','${list?.[i]?.WMS_KEY}')">${m('btn.delete')}</button></td>
            </tr>
		`;
		totalqty = totalqty + Number(list?.[i]?.QTY);
		table.append(tbody);
	}
	$("#count").text(list.length);
	$("#totalqty").text(formatNumber(totalqty));
	hideLoading();
}

function deleteEntry(barcode,meskey){
	showLoading();
	// meskey 유효성 체크
    if (!meskey || (typeof meskey === "string" && meskey.trim() === "" || meskey =='undefined')) {
        Utils.showAlert("Please try again later.", "error");
		search();
        hideLoading();
        return;
    }
	Utils.showConfirm(m("confirm.delete.item"), () => {
		$.ajax({
			url: "/production/search-semi/detail/del",		
	        method: 'POST',
			data: {
				barcode:barcode,
				meskey:meskey,
				factory: localStorage.getItem('rememberedFactory'),
				date:$("#datepicker").val()
			},
			success: function(result) {
				let response = result.response;
				console.log("response : " + response)
				if (response == "success") {
					$("#barcodeInput").val("");
					playSound('complete');
					search();
					Utils.showAlert(m("success.deleted"), 'success');
				}else{
					const barcodeHtml = makeBarcodesClickable(result.barcode);
					showAlert("", barcodeHtml + `<br>${m(result.response)}`, "warning");
				}
				
				hideLoading();
			},
			error: function(xhr, status, error) {
				console.error("요청 실패");
				console.error("Status:", status);       // 예: "error"
				console.error("Error:", error);         // 예: 서버 응답 메시지
				console.error("Response:", xhr.responseText); // 서버 응답 본문
				alert("오류가 발생했습니다: " + error);
				playSound('error');
				hideLoading();
			}
		});
	})
	hideLoading();
}

function search(){
	const sdate = $("#datepicker").val();
	
	const barcode = $("#barcodeInput").val();		// barcode로 변수명 바꿔야함 250901
	console.log(sdate);
	let data = {
		date: sdate,
		factory:localStorage.getItem('rememberedFactory'),
		barcode: barcode
	}
	showLoading();
	$.ajax({
		url: "/production/search-semi/detail",		
        method: 'POST',
		contentType: "application/json",
		data: JSON.stringify(data),
		success: function(result) {
			
			console.log(result.list);
			$("#barcodeInput").val("");
			renderTable(result.list);
		},
		error: function(xhr, status, error) {
			playSound('error');
			console.error("요청 실패");
			console.error("Status:", status);       // 예: "error"
			console.error("Error:", error);         // 예: 서버 응답 메시지
			console.error("Response:", xhr.responseText); // 서버 응답 본문
			alert("오류가 발생했습니다: " + error);
		}
	});
}