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
    })
    console.log("sdss")
    renderTable();

    getStroage("사내");

    // 지웅 - 배포시 주석제거
    $('#barcodeInput').on('touchstart mousedown', function () {
        manualTouch = true;
    });
    // input창에서 포커스 없어질때 세팅
    $('#barcodeInput').on('blur', function () {
        $('#barcodeInput').attr('readonly', true);
        inputMode = 'readonly';
    });

    $(document).on('touchend', function (e) {
        if (window.focusTimeout) clearTimeout(window.focusTimeout);

        // 터치한 곳이 barcodeInput일 때만 실행
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

    // 키보드 감지로 푸터 표시/숨김 (focus/blur 대신 이걸로 교체)
    if (window.visualViewport) {
        let baseHeight = window.visualViewport.height;

        window.visualViewport.addEventListener('resize', function () {
            // 화면 높이가 원래보다 많이 줄었으면 키보드가 올라온 상태로 판단
            const shrink = baseHeight - window.visualViewport.height;

            if (shrink > 150) {
                $('.footer').hide();   // 키보드 올라옴
            } else {
                $('.footer').show();   // 키보드 내려감 (뒤로가기 포함)
                baseHeight = window.visualViewport.height; // 기준 높이 갱신
            }
        });
    }

    // 수량칸: 마이너스 차단 + 최대 5자리 제한
    $(document).on('keydown', '.input-qty', function (e) {
        // 마이너스, e, +, . 입력 차단
        if (['-', 'e', 'E', '+', '.'].includes(e.key)) {
            e.preventDefault();
        }
    });

    $(document).on('input', '.input-qty', function () {
        // 숫자 이외 문자 제거 + 5자리 초과 자르기
        let v = this.value.replace(/[^0-9]/g, '');
        if (v.length > 5) v = v.slice(0, 5);
        this.value = v;

        // 변경된 수량 저장 (렌더 다시 그려도 유지됨)
        saveQty($(this).attr('data-barcode'), v);
    });
})

function addEntry() {			// 로컬스토리지 저장
    const barcodeInput = document.getElementById('barcodeInput');
    const barcode = barcodeInput.value.trim();

    if (!barcode) {
        Utils.showAlert(m("warning.barcode.required"));
        return;
    }
    // 🔹 저장 제한 체크
    if (!savelimitCheck("barcodeListRealStockIn", 500)) {
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

    // 울산 scm 바코드 : M으로 시작
    // 대차 : ,로 스플릿하면 6개
    // 부품 : [)>가 포함되면]
    // 협력사용 부품식별표 : ,로 스플릿하면 5개
    if (barcode.startsWith("M") || barcode.split(",").length === 6 || barcode.split(",").length === 5 || barcode.startsWith("[)>")) {
        let stored = [];

        if (window.localStorage && localStorage.getItem("barcodeListRealStockIn")) {
            stored = JSON.parse(localStorage.getItem("barcodeListRealStockIn"));
        } else {
            stored = [];
        }

        if (stored.includes(barcode)) {
            console.log("barcode : " + barcode)
            let audio = new Audio('/sounds/buzzer2.wav');
            audio.play();
            $("#barcodeInput").val("");
            Utils.showAlert(`${barcode}<br>${m("warning.barcode.duplicate")}`, "warning");
            hideLoading();
            return;
        } else {
            let audio = new Audio('/sounds/complete.wav');
            audio.play();
            stored.push(barcode);
            localStorage.setItem("barcodeListRealStockIn", JSON.stringify(stored));
            $("#barcodeInput").val("");
            renderTable();
            hideLoading();
            Utils.showAlert(`${barcode}<br>${m("info.barcode.saved")}`, "#008000", barcode)
        }
    } else {
        playSound('error');
        $("#barcodeInput").val("");
        hideLoading();
        Utils.showAlert(`${barcode}<br>${m("warning.barcode.invalid")}<br>${m("warning.check")}`, "warning")
        return;
    }
}

function saveBarcode2(status = 'ok', list = []) {				// 전체전송
    // 중복 방지: 이미 저장 중이면 바로 종료
    console.log("isaving : " + isSaving);
    if (isSaving) {
        console.log("⚠ 중복 클릭 방지됨");
        return;
    }
    isSaving = true;

    const barcodeList = JSON.parse(localStorage.getItem("barcodeListRealStockIn") || "[]");

    if (barcodeList.length === 0) {
        Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
        focusWithoutKeyboard()
        isSaving = false;
        return;
    }

    // barcodeList 순서대로 수량 구성
    let qtyList = barcodeList.map(function (barcode) {
        const parts = barcode.split(",");
        if (parts.length !== 6 && parts.length !== 5) return "";

        // 대차라벨 : 화면 input을 순회로 찾기
        let found = null;
        $('#dataTableBody .input-qty').each(function() {
            if ($(this).attr('data-barcode') === barcode){
                found = $(this);
                return false;
            }
        });

        if (found) {
            let qty = parseInt(found.val(), 10);
            if (isNaN(qty) || qty < 0) qty = 0;
            return String(qty);
        }
        return String(parseInt(parts[3], 10));
    });

    Utils.showConfirm(m("confirm.send.all"), () => {
        let data = {
            date: $("#datepicker").val(),
            barcode: barcodeList,
            qtys: qtyList,
            storage: $('.storage-select').val(),
            factory: localStorage.getItem('rememberedFactory'),
            scantype: 'BARCODE',
            status1: status,
            barcode2: list,
        }
        showLoading();
        $.ajax({
            url: `/ulsan/insertRealStock`,
            method: 'POST',
            contentType: "application/json",
            data: JSON.stringify(data),
            success: function (result) {
                let response = result.response;
                console.log("response : " + response)
                if (response === "success") {
                    localStorage.removeItem('barcodeListRealStockIn');
                    localStorage.removeItem('barcodeQtyRealStockIn');
                    $("#dataTableBody").empty();
                    $("#count").text("0");
                    playSound("complete");
                    Utils.showAlert(m("info.barcode.sent"), "info");
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
    }, () => {
        Utils.showAlert(m("success.cancel.sendAll"), "#008000");
        isSaving = false;
        hideLoading();
    })
}

function renderTable() {		//테이블그리기
    console.log("테이블그리기")
    let table = $("#dataTableBody");
    let barcodeArray = [];

    if (typeof localStorage !== 'undefined' && localStorage.getItem("barcodeListRealStockIn")) {
        barcodeArray = JSON.parse(localStorage.getItem("barcodeListRealStockIn"));
    } else {
        barcodeArray = [];
    }
    table.empty();
    for (let i = 0; i <barcodeArray.length ; i++) {
        let barcodeStr = barcodeArray[i];
        let parts = barcodeStr.split(',');
        let tbody = "";
        if (parts.length === 6) {
            let qty = getQty(barcodeStr, parseInt(parts[3], 10));

            tbody = `
                <tr class = "bar-row" data-barcode="${barcodeStr}">
                    <td class="dataInfo">${barcodeStr}</td>
                    <td>
                        <input type="number" class="input-qty keep-focus" min="0" value="${qty}" data-barcode="${barcodeStr}">
                    </td>
                    <td><button class="delete-btn" onclick="deleteEntry(this)">${m("btn.delete")}</button></td>
                </tr>`;
        } else if (parts.length === 5) {
            let qty = getQty(barcodeStr, parseInt(parts[3], 10));

            tbody = `
                <tr class = "bar-row" data-barcode="${barcodeStr}">
                    <td class="dataInfo">${barcodeStr}</td>
                    <td>
                        <input type="number" class="input-qty keep-focus" min="0" value="${qty}" data-barcode="${barcodeStr}">
                    </td>
                    <td><button class="delete-btn" onclick="deleteEntry(this)">${m("btn.delete")}</button></td>
                </tr>`;
        } else {
            tbody = `
                <tr class="bar-row" data-barcode="${barcodeStr}">
                    <td class="dataInfo" colspan="2">${barcodeStr}</td>
                    <td><button class="delete-btn" onclick="deleteEntry(this)">${m("btn.delete")}</button></td>
                </tr>`;
        }
        table.prepend(tbody);
    }
    $("#count").text(+barcodeArray.length);
}

function deleteEntry(btn) {         // localstorage에서 특정데이터 삭제
    const $row = $(btn).closest('tr');
    const bar = $row.attr('data-barcode');
    console.log("삭제 바코드 : " + bar)
    Utils.showConfirm(m("confirm.delete.item"), () => {
        let barcodeArray = JSON.parse(localStorage.getItem("barcodeListRealStockIn") || "[]");
        let newArray = barcodeArray.filter(item => item !== bar);
        localStorage.setItem("barcodeListRealStockIn", JSON.stringify(newArray));
        removeQty(bar);
        renderTable();
        Utils.showAlert(m("success.deleted"), 'success');
    })
    focusWithoutKeyboard()
}

function clearAll() {			//localstorage 전체삭제
    Utils.showConfirm(m("confirm.delete.all"), () => {
        const $tb = $('#dataTableBody');
        console.log($tb);
        console.log($tb.children('tr'));
        console.log($tb.children('tr').length);
        if ($tb.children('tr').length === 0) {
            Utils.showAlert(m('warning.data.delete.all'), "warning");
            return;
        }

        localStorage.removeItem("barcodeListRealStockIn");
        localStorage.removeItem('barcodeQtyRealStockIn');
        $("#dataTableBody").empty();
        $("#count").text("0");
        Utils.showAlert(m("success.deleted.all"), "success");
    })
    focusWithoutKeyboard()
}

// $(document).on("click", ".dataInfo", function () {
//     // 클릭한 tr중 dataInfo클래스를 찾아 첫번째 텍스트 반환
//     const barcode = $(this).closest('tr').find('td.dataInfo').first().text().trim();
//
//     $.ajax({
//         url: "/purchase/getItemInfo_barcode",
//         type: "GET",
//         data: {barcode},
//         dataType: "json",
//         success: function (result) {
//             console.log(result);
//             showPopup(result.list);
//         }
//     })
// });


// 수량을 별도 저장 (바코드 문자열은 그대로 유지)
function saveQty(barcode, qty) {
    let qtyMap = JSON.parse(localStorage.getItem("barcodeQtyRealStockIn") || "{}");
    qtyMap[barcode] = qty;
    localStorage.setItem("barcodeQtyRealStockIn", JSON.stringify(qtyMap));
}

function getQty(barcode, defaultQty) {
    let qtyMap = JSON.parse(localStorage.getItem("barcodeQtyRealStockIn") || "{}");
    return qtyMap.hasOwnProperty(barcode) ? parseInt(qtyMap[barcode], 10) : defaultQty;
}

function removeQty(barcode) {
    let qtyMap = JSON.parse(localStorage.getItem("barcodeQtyRealStockIn") || "{}");
    delete qtyMap[barcode];
    localStorage.setItem("barcodeQtyRealStockIn", JSON.stringify(qtyMap));
}