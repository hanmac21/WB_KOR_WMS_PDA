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
		monthNames: ['1월','2월','3월','4월','5월','6월','7월','8월','9월','10월','11월','12월'],
		monthNamesShort: ['1월','2월','3월','4월','5월','6월','7월','8월','9월','10월','11월','12월'],
		dayNames: ['일','월','화','수','목','금','토'],
		dayNamesShort: ['일','월','화','수','목','금','토'],
		dayNamesMin: ['일','월','화','수','목','금','토'],
		showMonthAfterYear: true,
		yearSuffix: '년',
		changeMonth: true,
		changeYear: true,
		showButtonPanel: true,
		currentText: '오늘 날짜',
		onClose: function(dateText, inst) {
			if ($(window.event.target).hasClass('ui-datepicker-current')) {
				$(this).datepicker('setDate', new Date());
			}
		}
	});
	$("#datepicker").val(today);
	$("#datepicker").datepicker();

	search();

	$(document).on('click', '.ui-datepicker-current', function() {
		const today = new Date();
		$("#datepicker").datepicker("setDate", today);
	});
});

function renderTable(list) {
	let table = $("#dataTableBody");
	let totalqty = 0;
	table.empty();
	for (let i = 0; i < list.length; i++) {
		const raw = list[i].JUDGMENT || '';
		const judgmentText = raw === 'SCRAP' ? '폐기' : raw === 'RETURN' ? '입고반품' : raw;
		let tbody = `
			<tr>
				<td class="dataInfo">${(list?.[i]?.BARCODE == null || list?.[i]?.BARCODE === 'null') ? '' : list[i].BARCODE}</td>
				<td class="dataInfo">${(list?.[i]?.QTY == null || list?.[i]?.QTY === 'null') ? '' : list[i].QTY}</td>
				<td class="dataInfo">${judgmentText}</td>
			</tr>
		`;
		totalqty = totalqty + Number(list?.[i]?.QTY || 0);
		table.append(tbody);
	}
	$("#count").text(list.length);
	$("#totalqty").text(formatNumber(totalqty));
	hideLoading();
}

function search() {
	const date = $("#datepicker").val();
	const source2 = $(".judgment-select").val();

	let data = {
		date: date,
		factory: localStorage.getItem('rememberedFactory'),
		source: 'STORAGE',
		source2: source2
	};

	showLoading();

	$.ajax({
		url: "/purchase/inspection/search-list",
		method: 'POST',
		contentType: "application/json",
		data: JSON.stringify(data),
		success: function(result) {
			renderTable(result.list);
		},
		error: function(xhr, status, error) {
			console.error("요청 실패", status, error);
			alert("오류가 발생했습니다: " + error);
			hideLoading();
		}
	});
}
