let manualTouch = false;
$(document).ready(function() {
	hideLoading();
//	var today = new Date();
//
//	function dateFormat(date) {
//		let month = date.getMonth() + 1;
//		let day = date.getDate();
//
//		month = month >= 10 ? month : '0' + month;
//		day = day >= 10 ? day : '0' + day;
//
//		return date.getFullYear() + '-' + month + '-' + day;
//	}
//
//	today = dateFormat(today);
//
//	$.datepicker.setDefaults({
//		dateFormat: 'yy-mm-dd',
//		prevText: '이전 달',
//		nextText: '다음 달',
//		monthNames: ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'],
//		monthNamesShort: ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'],
//		dayNames: ['일', '월', '화', '수', '목', '금', '토'],
//		dayNamesShort: ['일', '월', '화', '수', '목', '금', '토'],
//		dayNamesMin: ['일', '월', '화', '수', '목', '금', '토'],
//		showMonthAfterYear: true,
//		yearSuffix: '년',
//		changeMonth: true,
//		changeYear: true,
//		showButtonPanel: true,
//		currentText: '오늘 날짜',
//		onClose: function(dateText, inst) {
//			/*focusWithoutKeyboard();*/
//			if ($(window.event.target).hasClass('ui-datepicker-current')) {
//				$(this).datepicker('setDate', new Date());
//
//			}
//		}
//	});
//	$("#datepicker").val(today);
//	$("#datepicker").datepicker();

	search();
	
	$(document).on('click', '.ui-datepicker-current', function() {
		const today = new Date();
		$("#datepicker").datepicker("setDate", today);
	});
})



function renderTable(list) {		//테이블그리기
	console.log("테이블그리기")
	let table = $("#dataTableBody");
	let totalqty = 0;
	table.empty();
	for(let i = 0; i < list.length; i++){
		if (Number(list[i].QTY) === list[i].SCANQTY) {
	        scanCell = `<td style="color: green;">${(list?.[i]?.SCANQTY == null) ? '' : list[i].SCANQTY.toLocaleString()}</td>`;
	    } else if (Number(list[i].QTY) < list[i].SCANQTY) {
	    	scanCell = `<td style="color: orange;">${(list?.[i]?.SCANQTY == null) ? '' : list[i].SCANQTY.toLocaleString()}</td>`;
	    } else {
	        scanCell = `<td style="color: red;">${(list?.[i]?.SCANQTY == null) ? '' : list[i].SCANQTY.toLocaleString()}</td>`;
	    }
		
		let tbody = `
			<tr data-invoice = ${list[i].INVOICE_NO}  style = "height:50px;">
				<td class = "dataInfo">${(list?.[i]?.ARRIVAL_DATE == null || list?.[i]?.ARRIVAL_DATE === 'null') ? '' : list[i].ARRIVAL_DATE}</td>
				<td class = "dataInfo">${(list?.[i]?.INVOICE_NO == null || list?.[i]?.INVOICE_NO === 'null') ? '' : list[i].INVOICE_NO}</td>
				<td class = "dataInfo">${(list?.[i]?.QTY == null || list?.[i]?.QTY === 'null') ? '' : list[i].QTY.toLocaleString()}</td>		
				${scanCell}
            </tr>
		`;
		totalqty = totalqty + Number(list?.[i]?.QTY);
		table.append(tbody);
	}
	$("#count").text(list.length);
	$("#totalqty").text(formatNumber(totalqty));
	hideLoading();
}


function search(){
	// 날짜 값 가져오기
//	const date = $("#datepicker").val();
	
	const type = $('.type-select').val();
	
	let data = {
//		date: date,
		type: type
	}
		
	// 로딩창 
	showLoading();
	
	// 데이터 가져오기
	$.ajax({
		url: "/purchase/search-incoming/invoiceList",		
        method: 'POST',
		contentType: "application/json",
		data: JSON.stringify(data),
		success: function(result) {
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


//tr 클릭 이벤트 
$("#dataTableBody").on("click", "tr", function() {
 let invoice = $(this).data("invoice");
 if (invoice) {
     // 새 페이지로 이동 (GET 방식)
     window.location.href = `/purchase/incoming/invoiceScan?invoice=${encodeURIComponent(invoice)}`;
     
     // 또는 POST 방식으로 폼 전송하려면:
     // let form = $('<form method="POST" action="/barcode-detail">');
     // form.append($('<input type="hidden" name="barcode" value="' + barcode + '">'));
     // $('body').append(form);
     // form.submit();
 }
});