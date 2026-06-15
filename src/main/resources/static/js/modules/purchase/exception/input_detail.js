let manualTouch = false;
$(document).ready(function () {
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
        onClose: function (dateText, inst) {
            /*focusWithoutKeyboard();*/
            if ($(window.event.target).hasClass('ui-datepicker-current')) {
                $(this).datepicker('setDate', new Date());

            }
        }
    });
    $("#datepicker").val(today);
    $("#datepicker").datepicker();

    search();

    $(document).on('click', '.ui-datepicker-current', function () {
        const today = new Date();
        $("#datepicker").datepicker("setDate", today);
    });
})


function renderTable(list) {		//테이블그리기
    console.log("테이블그리기")
    let table = $("#dataTableBody");
    let totalqty = 0;
    table.empty();
    for (let i = 0; i < list.length; i++) {
        let tbody = `
			<tr>
				<td class = "dataInfo">${(list?.[i]?.ITEMCODE == null || list?.[i]?.ITEMCODE === 'null') ? '' : list[i].ITEMCODE}</td>
				<td class = "dataInfo">${(list?.[i]?.QTY == null || list?.[i]?.QTY === 'null') ? '' : list[i].QTY}</td>
				<td class = "dataInfo">${(list?.[i]?.LOCATION == null || list?.[i]?.LOCATION === 'null') ? '' : list[i].LOCATION}</td>	
				<td class = "dataInfo">${(list?.[i]?.STORAGE == null || list?.[i]?.STORAGE === 'null') ? '' : list[i].STORAGE}</td>	
				<td class = "dataInfo">${(list?.[i]?.TIME == null || list?.[i]?.TIME === 'null') ? '' : list[i].TIME}</td>		
				<td class = "dataInfo">${(list?.[i]?.INVOICENO == null || list?.[i]?.INVOICENO === 'null') ? '' : list[i].INVOICENO}</td>		
            </tr>
		`;
        totalqty = totalqty + Number(list?.[i]?.QTY);
        table.append(tbody);
    }
    $("#count").text(list.length);
    $("#totalqty").text(formatNumber(totalqty));
    hideLoading();
}


function search() {
    // 날짜 값 가져오기
    const date = $("#datepicker").val();

    let data = {
        factory: localStorage.getItem('rememberedFactory'),
        storage: $(".storage-select").val(),
        date: date
    }
    // 로딩창
    showLoading();

    // 데이터 가져오기
    $.ajax({
        url: "/purchase/search-exception/input-detail",
        method: 'POST',
        contentType: "application/json",
        data: JSON.stringify(data),
        success: function (result) {
            renderTable(result.list);
        },
        error: function (xhr, status, error) {
            console.error("요청 실패");
            console.error("Status:", status);       // 예: "error"
            console.error("Error:", error);         // 예: 서버 응답 메시지
            console.error("Response:", xhr.responseText); // 서버 응답 본문
            alert("오류가 발생했습니다: " + error);
        }
    });
}

$(document).on("click", ".dataInfo", function () {
    // 클릭한 tr중 dataInfo클래스를 찾아 첫번째 텍스트 반환
    const itemCode = $(this).closest('tr').find('td.dataInfo').first().text().trim();

    // 파레트라벨인지
    const isPallet = itemCode.startsWith("(P)");

    // 정규식으로 pno만 추출
    const itemcode = isPallet ? itemCode.replace(/^\s*\(\s*p\s*\)\s*/i, "") : itemCode;

    $.ajax({
        url: "/purchase/getItemInfo",
        type: "GET",
        data: {itemcode},
        dataType: "json",
        success: function (result) {
            console.log(result);
            showPopup(result.list);
        }
    })
});