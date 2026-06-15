let manualTouch = false;
$(document).ready(function() {
	hideLoading();

	// 조정 품번 수정 시 품명 제거
	$('#adjustItemcode').on('input', function(){
		$('#adjustItemname').val('');
	});

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

function searchItemname(){
    const itemcode = $('#adjustItemcode').val().replace(/\s/g, '').replace(/\./g, '');

    if (!itemcode) return;

    showLoading();
    $.ajax({
        url: `/purchase/part-itemname-search`,
        method: 'GET',
        data: { itemcode: itemcode },
        success: function(res){
            hideLoading();

			// 결과가 없을 경우
			if (!res.items || res.items.length === 0) {
				$('#adjustItemname').val('');
				showAlert("", "Search results not found", "warning");
				return;
			}

			// 결과가 1건이면 바로 세팅
			if (res.items.length === 1) {
				$('#adjustItemcode').val(res.items[0].ITEMCODE);
				$('#adjustItemname').val(res.items[0].ITEMNAME);
				return;
			}

            // 결과 여러 건이면 모달 띄우기
            showItemSelectModal(res.items);
        },
        error: function(xhr, status, error) {
            hideLoading();
            console.error("Response:", xhr.responseText);
            alert("오류가 발생했습니다: " + error);
        }
    });
}

function showItemSelectModal(items){
    let rows = items.map(item => `
        <tr class="item-modal-row" data-itemcode="${item.ITEMCODE}" data-itemname="${item.ITEMNAME}">
            <td>${item.ITEMCODE}</td>
            <td>${item.ITEMNAME}</td>
        </tr>
    `).join('');

    $('#itemSelecttbody').html(rows);
    $('#itemSelectModal').css('display', 'flex');

    // 행 선택
    $(document).off('click', '.item-modal-row').on('click', '.item-modal-row', function(){
        $('#adjustItemcode').val($(this).data('itemcode'));
        $('#adjustItemname').val($(this).data('itemname'));
        $('#itemSelectModal').hide();
    });

    // 닫기
    $('#closeItemModal').off('click').on('click', function(){
        $('#itemSelectModal').hide();
    });
}

function addEntry(){
	const barcode = $("#barcodeInput").val();		// barcode로 변수명 바꿔야함 250901
	console.log(barcode);
	let data = {
		factory:localStorage.getItem('rememberedFactory'),
		barcode: barcode
	}
	// 파트라벨만 가능
	if((barcode.split(",").length == 5)){
		showLoading();
		$.ajax({
			url: "/purchase/part_info_search",
	        method: 'GET',
			data: data,
			success: function(data) {
				console.log(data);

				if (data.result.return) {
					const barcodeHtml = makeBarcodesClickable(barcode.split(" "));
					showAlert("", barcodeHtml + `<br>${m("warning.pallet.part.unbound")}`, "warning");
					hideLoading();
					$("#barcodeInput").val("");
				} else if (data.result.result == null) {
					const barcodeHtml = makeBarcodesClickable(barcode.split(" "));
					showAlert("", barcodeHtml + `<br>${m("warning.barcode.storagecheck")}`, "warning");
					hideLoading();
					$("#barcodeInput").val("");
				} else {
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
		Utils.showAlert(m("warning.barcode.invalid")+"<br>"+m("warning.check") , "#FF0000", barcode)
		$("#barcodeInput").val("");
		$("#dataTableBody").empty();
		$("#count").text(0);
		$("#totalqty").text(0);
	}
}

function adjustment(){
	const barcode = $("#barcode").text();
	const itemcode = $("#itemcode").text();
	const itemname = $("#itemname").text();
	const nowqty = $("#nowqty").text();
	const adjustItemcode = $("#adjustItemcode").val().trim();
	const adjustItemname = $("#adjustItemname").val().trim();
	const memo = $("#memo").val().trim();

	// 바코드 스캔 안했을 때
	if (barcode === ""){
		showAlert("", "바코드 스캔 해 주세요", "warning");
		return;
	}

	// 메모 공백 시
	if(memo === ""){
		showAlert("", m("warning.memo.empty"), "warning");
        return;
	}

	// 품번 공백 시
	if(adjustItemcode === ""){
		showAlert("", m("warning.input.adjustqty"), "warning");
	    return;
	}

	// 품명 공백 시
	if (adjustItemname === ""){
		// 검색 버튼을 눌러 품명을 선택해 주세요.
		showAlert("", "검색 버튼을 눌러 품명을 선택해 주세요.", "warning");
		return;
	}

	// 바꾼 품번과 현재 품번이 동일한 경우
	if (adjustItemcode === itemcode){
		showAlert("", "바코드의 품번과 조정 품번이 동일합니다", "warning");
		return;
	}

	let data = {
		factory:localStorage.getItem('rememberedFactory'),
		barcode: barcode,
		nowItemcode : itemcode,
		nowItemname : itemname,
		nowqty : nowqty,
		adjustItemcode : adjustItemcode,
		adjustItemname : adjustItemname,
		memo : memo
	}

	showLoading();

	$.ajax({
		url: "/purchase/part_adjustment_itemcode_update",
        method: 'POST',
		contentType: "application/json",
		data: JSON.stringify(data),
		success: function(result) {
			$("#barcodeInput").val("");
			$("#barcode").text("");
			$("#itemcode").text("");
			$("#itemname").text("");
			$("#nowqty").text("");
			$("#adjustItemcode").val("");
			$("#adjustItemname").val("");
			$("#memo").val("");
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
}