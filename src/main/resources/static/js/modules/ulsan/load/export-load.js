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
            focusWithoutKeyboard();
            if ($(window.event.target).hasClass('ui-datepicker-current')) {
                $(this).datepicker('setDate', new Date());

            }
        }
    });
    $("#datepicker").datepicker();
    $("#datepicker").datepicker("setDate", new Date());

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
    $('#barcodeInput').on('touchstart mousedown', function () {
        manualTouch = true;
    });

    $('#barcodeInput').on('blur', function () {
        $('#barcodeInput').attr('readonly', true);
        inputMode = 'readonly';
    });

    $(document).on('touchend', function (e) {
        if (window.focusTimeout) clearTimeout(window.focusTimeout);

        if ($(e.target).is('#barcodeInput')) {
            window.focusTimeout = setTimeout(function () {
                const $input = $('#barcodeInput');
                if ($input.length) {
                    $input.focus();
                    if (!manualTouch) {
                        focusWithoutKeyboard();
                    }
                    manualTouch = false;
                }
            }, 500);
        }
    });

})

function addEntry() {			// 로컬스토리지 저장
    const barcodeInput = document.getElementById('barcodeInput');
    const barcode = barcodeInput.value.trim();

    if (!barcode) {
        Utils.showAlert('바코드를 입력해주세요.');
        return;
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

    // 트랜시스 바코드인지 확인
    let stored = [];

    if (window.localStorage && localStorage.getItem("barcodeListTransysOut")) {
        stored = JSON.parse(localStorage.getItem("barcodeListTransysOut"));
    } else {
        stored = [];
    }

    if (stored.includes(barcode)) {
        console.log("barcode : " + barcode)
        let audio = new Audio('/sounds/buzzer2.wav');
        audio.play();
        $("#barcodeInput").val("");
        Utils.showAlert(`${barcode}<br>${m("warning.barcode.duplicate")}`);
        hideLoading();
        return;
    } else {
        let audio = new Audio('/sounds/complete.wav');
        audio.play();

        stored.push(barcode);
        localStorage.setItem("barcodeListTransysOut", JSON.stringify(stored));
        $("#barcodeInput").val("");
        renderTable();
        hideLoading();
        Utils.showAlert(`${barcode}<br>${m("info.barcode.saved")}`, "#008000", barcode)
    }
}

function saveBarcode() {					// 전체전송
    // 중복 방지: 이미 저장 중이면 바로 종료
    console.log("isaving : " + isSaving);
    if (isSaving) {
        console.log("⚠ 중복 클릭 방지됨");
        return;
    }
    isSaving = true;

    const barcodeList = JSON.parse(localStorage.getItem("barcodeListTransysOut") || "[]");

    if (barcodeList.length === 0) {
        Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
        focusWithoutKeyboard()
        isSaving = false;
        return;
    }

    let data = {
        date: $("#datepicker").val(),
        barcode: barcodeList,
        source: "LOAD",
        main: "OUT",
        kind: "LOAD",
        shipTo: $(".shipto-select").val(),
        factory: localStorage.getItem('rememberedFactory'),
        memo: ""
    }

    Utils.showConfirm(m("confirm.send.all"), () => {
            showLoading();
            $.ajax({
                url: `/ulsan/insOutput`,
                method: 'POST',
                contentType: "application/json",
                data: JSON.stringify(data),
                success: function (result) {
                    let response = result.response;
                    console.log("response : " + response)
                    if (response === "success") {
                        localStorage.removeItem('barcodeListTransysOut');
                        $("#dataTableBody").empty();
                        $("#count").text("0");
                        Utils.showAlert(m("info.barcode.sent"), "info");
                        playSound('complete');
                    } else {
                        const barcodeHtml = makeBarcodesClickable(result.barcode);
                        showAlert("", barcodeHtml + `<br>${m(result.response)}`, "warning");
                        highlightErrorRows(result.barcode);		// 에러바코드 배경 빨간색으로 바꿔주는 함수

                        playSound('error')
                    }
                    hideLoading();
                },
                error: function (request, status, error) {
                    console.log(error);
                    if (request.status == 200) {

                    } else if (request.status == 500) {
                        Utils.showAlert(m("error.generic"), 'warning');
                    } else if (request.status == 0) {
                        Utils.showAlert(m("error.offline"), 'warning');
                    }
                    if (request.status === 401) {
                        Utils.showAlert("Your session has expired. Please log in again.", 'warning');
                        window.location.href = "/login";
                    } else {
                        Utils.showAlert("code: " + request.status + "<br>message: " + request.responseText + "<br>error: " + error, "warning");
                    }
                    hideLoading();
                },
                complete: function () {
                    hideLoading();
                    // ❗ AJAX 끝나면 초기화
                    isSaving = false;
                    console.log("isaving false 1 : " + isSaving);
                }
            });
        },
        () => {
            Utils.showAlert(m("success.cancel.sendAll"), "#008000");
            isSaving = false;
            hideLoading();
        })
}

function renderTable() {		//테이블그리기
    console.log("테이블그리기")
    let table = $("#dataTableBody");
    let barcodeArray = [];

    if (typeof localStorage !== 'undefined' && localStorage.getItem("barcodeListTransysOut")) {
        barcodeArray = JSON.parse(localStorage.getItem("barcodeListTransysOut"));
    } else {
        barcodeArray = [];
    }
    table.empty();
    for (let i = 0; i < barcodeArray.length; i++) {
        const barcodeStr = barcodeArray[i];

        const tbody = `
            <tr class="bar_${barcodeStr} bar-row" data-barcode="${barcodeStr}">
                <td>${barcodeStr}</td>
                <td><button class="delete-btn" onclick="deleteEntry('${barcodeStr}')">${m("btn.delete")}</button></td>
            </tr>
        `;

        table.append(tbody);
    }
    $("#count").text(+barcodeArray.length)
}

function deleteEntry(bar) {		// localstorage에서 특정데이터 삭제
    let className = "bar_" + bar;
    console.log("삭제 바코드 : " + bar)
    Utils.showConfirm(m("confirm.delete.item"), () => {
        let barcodeArray = JSON.parse(localStorage.getItem("barcodeListTransysOut") || "[]");
        let newArray = barcodeArray.filter(item => item.toString().trim() !== bar.toString().trim());
        localStorage.setItem("barcodeListTransysOut", JSON.stringify(newArray));
        $("." + CSS.escape(className)).remove();
        $("#count").text(newArray.length)
        Utils.showAlert(m("success.deleted"), 'success');
    })
    focusWithoutKeyboard()

}

function clearAll() {			//localstorage 전체삭제
    Utils.showConfirm(m("confirm.delete.all"), () => {
        localStorage.removeItem("barcodeListTransysOut");
        $("#dataTableBody").empty();
        $("#count").text("0")
        Utils.showAlert("전체 삭제되었습니다.", "success");
    })
    focusWithoutKeyboard()
}
