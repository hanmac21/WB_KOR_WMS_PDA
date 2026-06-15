let manualTouch = false;
$(document).ready(function() {
	hideLoading();
	$(".location-select").on("change", function() {
        search(); // 값이 바뀔 때마다 search() 실행
    });

	search();
})

function search(){
	let data = {
		factory: $("#locationPart1").val(),
		storage: $("#locationPart2").val()
	}
	showLoading();
	$.ajax({
		url: '/purchase/rack/unloaded',
	    type: 'POST',
		contentType: 'application/json; charset=UTF-8',
	    dataType: 'json',
		data:JSON.stringify(data),
	    success: function(result) {
	        renderUnLoadedModal(result);
	    },
	    error: function(xhr, status, error) {
	        console.error('❌ 미적재리스트 로드 실패:', error);
	        alert('미적재리스트 로드에 실패했습니다.');
	    },
	    complete: function () {
	        hideLoading();
	    }
	});
}

function renderUnLoadedModal(data) {
	const { list = [], total = 0 } = data;
	let table = $("#dataTableBody");
	table.empty();
	// 전역 변수 업데이트
	let totalqty = 0;
	for(let i = 0; i<list.length; i++){
		let tbody = "";
		tbody = `
			<tr>
				<td>${i+1}</td>
				<td>${list[i].SDATE}</td>
				<td>${list[i].ITEMCODE}</td>
				<td>${list[i].QTY}</td>
				<td>${list[i].BARCODE}</td>
			</tr>
		`
		table.append(tbody);
		totalqty += Number(list[i].QTY);
	}
	$("#totalqty").text(formatNumber(totalqty));
	$("#count").text(list.length);
 	hideLoading();
}


