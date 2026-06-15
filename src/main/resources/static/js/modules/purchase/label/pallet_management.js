let manualTouch = false;
$(document).ready(function () {
    hideLoading();
    $(document).on('click', '.ui-datepicker-current', function () {
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
    let scanqty = 0;
    table.empty();
    let cnt = 0;
    let barcode = list.PBARCODE.split(",").slice(0, -2).join(",");
    let tbody = `
		<tr class = 'unpack-row' data-barcode="${list.PBARCODE}" style="cursor: pointer;">
			<td class="barcode-cell">${(list?.PBARCODE == null || list?.PBARCODE === 'null') ? '' : barcode}</td>
			<td>${(list?.QTY == null || list?.QTY === 'null') ? '' : list.QTY}</td>
        </tr>
	`;
    table.append(tbody)
    $("#count").text(cnt);
    $("#totalqty").text(formatNumber(scanqty) + "/" + formatNumber(totalqty));
    hideLoading();
}

// tr 클릭 이벤트
$("#dataTableBody").on("click", "tr", function () {
    let barcode = $(this).data("barcode");
    if (barcode) {
        // 새 페이지로 이동 (GET 방식)
        window.location.href = `/purchase/label/pallet-management-partscan?barcode=${encodeURIComponent(barcode)}`;

        // 또는 POST 방식으로 폼 전송하려면:
        // let form = $('<form method="POST" action="/barcode-detail">');
        // form.append($('<input type="hidden" name="barcode" value="' + barcode + '">'));
        // $('body').append(form);
        // form.submit();
    }
});

function addEntry() {
    const sdate = $("#datepicker").val();
    const barcode = $("#barcodeInput").val();		// barcode로 변수명 바꿔야함 250901
    console.log(barcode);
    let data = {
        date: sdate,
        factory: localStorage.getItem('rememberedFactory'),
        barcode: barcode
    }
    if ((barcode.split(",").length === 4 && barcode[0][0] === "P" && (barcode.endsWith("MEX") || barcode.endsWith("USA")))) {
        showLoading();
        $.ajax({
            url: "/purchase/pallet_management_search",
            method: 'POST',
            data: data,
            success: function (result) {
                console.log(result.list.list);
                if (result.list.list == null) {
                    playSound('error')
                    showAlert("", m("warning.pallet.infoNotFound"), "warning");
                    $("#barcodeInput").val("");
                    hideLoading();
                    return;
                }
                $("#barcodeInput").val("");
                renderTable(result.list.list);
            },
            error: function (xhr, status, error) {
                console.error("요청 실패");
                console.error("Status:", status);       // 예: "error"
                console.error("Error:", error);         // 예: 서버 응답 메시지
                console.error("Response:", xhr.responseText); // 서버 응답 본문
                alert("오류가 발생했습니다: " + error);
            }
        });
    } else {
        playSound('error')
        Utils.showAlert(m("warning.barcode.invalid") + "<br>" + m("warning.check"), "#FF0000", barcode)
        $("#barcodeInput").val("");
        $("#dataTableBody").empty();
        $("#count").text(0);
        $("#totalqty").text(0);
    }
}