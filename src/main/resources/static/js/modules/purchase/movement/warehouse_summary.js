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
    // 영업으로 접속시 헤더 수정
    if (localStorage.getItem("rememberedFactory") === "Saltillo") {
        if (window.WMS.currentModule == 'sales') {
            const title = window.I18N?.['menu.aunde.move.summary'] || '';
            if (title) {
                $('#headerTitle').text(title);
            }
        }
    }
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
				<td>${(list?.[i]?.SDATE == null || list?.[i]?.SDATE === 'null') ? '' : list[i].SDATE}</td>
				<td>${(list?.[i]?.FACTORY == null || list?.[i]?.FACTORY === 'null') ? '' : list[i].FACTORY}</td>
				<td>${(list?.[i]?.STORAGE1 == null || list?.[i]?.STORAGE1 === 'null') ? '' : list[i].STORAGE1}</td>
				<td>${(list?.[i]?.ITEMCODE == null || list?.[i]?.ITEMCODE === 'null') ? '' : list[i].ITEMCODE}</td>
				<td>${(list?.[i]?.SUMQTY == null || list?.[i]?.SUMQTY === 'null') ? '' : list[i].SUMQTY}</td>	
            </tr>
		`;
        totalqty = totalqty + Number(list?.[i]?.SUMQTY);
        table.append(tbody);
    }
    $("#count").text(list.length);
    $("#totalqty").text(formatNumber(totalqty));
    hideLoading();
}


function search() {
    // 날짜 값 가져오기
    const date = $("#datepicker").val();

    const factory = $('#locationPart1').val();
    let storage = $("#locationPart2").val();
    if (storage == 'All') {
        storage = '';
    }

    let data = {
        date: date,
        factory: factory,
        storage: storage
    }
    // 로딩창
    showLoading();

    // 데이터 가져오기
    $.ajax({
        url: "/purchase/search-movement/warehouse-summary",
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