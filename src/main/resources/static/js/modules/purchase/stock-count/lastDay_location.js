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
    });

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
    focusWithoutKeyboard();


    $(".realStockCommon").click(function () {
        $(".datepicker_btnRealStock").click();
    })

    renderTable();
})


function addEntry() {
    const barcodeInput = document.getElementById('barcodeInput');
    const barcode = barcodeInput.value.trim();

    if (!barcode) {
        Utils.showAlert(m("warning.barcode.required"));
        playSound('error')
        return;
    }
    // 🔹 저장 제한 체크
    if (!savelimitCheck("barcodeListRealStockLastDayLocation", 500)) {
        console.log("250905 500건 이")
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

    if ((barcode.split(",").length === 5 && (barcode.split(",")[4] === "SCMMEX" || barcode.split(",")[4] === "WMSMEX" || barcode.split(",")[4] === "WMSUSA"))
        || (barcode[0][0] === "P" && (barcode.endsWith("MEX") || barcode.endsWith("USA")))) {
        let stored = [];
        let saveItemcode = "";
        if (window.localStorage && localStorage.getItem("barcodeListRealStockLastDayLocation")) {
            stored = JSON.parse(localStorage.getItem("barcodeListRealStockLastDayLocation"));
            if (stored.length > 0 && typeof stored[0] === 'string') {
                if (stored[0].split(",").length != barcode.split(",").length) {
                    $("#barcodeInput").val("");
                    Utils.showAlert(m("warning.labeltype.different"), 'warning')
                    playSound("error");
                    hideLoading();
                    return;
                }

            }
        } else {
            stored = [];
        }

        if (stored.includes(barcode)) {
            console.log("barcode : " + barcode)
            playSound('error2');
            $("#barcodeInput").val("");
            Utils.showAlert(`${barcode}<br>${m("warning.barcode.duplicate")}`);
            hideLoading();
            return;
        } else {
            playSound('complete');

            stored.push(barcode);
            localStorage.setItem("barcodeListRealStockLastDayLocation", JSON.stringify(stored));
            $("#barcodeInput").val("");
            renderTable();
            hideLoading();
            Utils.showAlert(barcode + "<br>" + m("info.barcode.saved"), "#008000", barcode)
        }
    } else {
        playSound('error');
        $("#barcodeInput").val("");
        hideLoading();
        Utils.showAlert(m("warning.barcode.invalid") + "<br>" + m("warning.check"), "#FF0000", barcode)
        return;
    }
}

function saveLocation0() {
    // 중복 방지: 이미 저장 중이면 바로 종료
    console.log("isaving : " + isSaving);
    if (isSaving) {
        console.log("⚠ 중복 클릭 방지됨");
        return;
    }
    isSaving = true;
    const barcodeList = JSON.parse(localStorage.getItem("barcodeListRealStockLastDayLocation") || "[]");

    console.log("바코드 리스트 로컬스토리지 형식 확인.");
    console.log(barcodeList);

    if ($('#locationPart2').val() === null) {
        Utils.showAlert(m('warning.storage.select'), 'warning');
        isSaving = false;
        return;
    }

    if (barcodeList.length === 0) {
        Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
        focusWithoutKeyboard()
        isSaving = false;
        return;
    }
    let data = {
        date: $("#datepicker").val().replaceAll("-", ""),
        barcode: barcodeList,
    }
    showLoading();
    $.ajax({
        url: `/purchase/barcodeTypeCheck`,
        method: 'POST',
        contentType: "application/json",
        data: JSON.stringify(data),
        success: function (result) {
            let response = result.response;
            console.log("response : " + response)
            if (response == "ok") {		// 팔레트로 묶인 파트라벨이 없을때
                Utils.showConfirm(m("confirm.send.all"), () => {
                    saveLocation('ok', result.list);
                    $("#storageBtn").blur();
                });
            } else if (response == "confirm") {
                Utils.showConfirmWithImage('/images/palletunbound.png', result.list.join("<br>") + "<br><br>" + m("warning.pallet.part.barcode"), () => {
                    saveLocation('confirm', result.list);
                });
                $("#storageBtn").blur();
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
    hideLoading();
}

function saveLocation(status0, list) {

    const barcodeList = JSON.parse(localStorage.getItem("barcodeListRealStockLastDayLocation") || "[]");

    if (barcodeList.length === 0) {
        Utils.showAlert(m("warning.barcode.required"), "warning");
        playSound('error')
        isSaving = false;
        return;
    }
    const date = $("#datepicker").val();
    const memo = $("#remarks").val();
    var itemcode = $("#itemcode").val();
    const qty = $("#qty").val();
    const type = '';
    let location = $("#locationPart1").val() + "-" + $("#locationPart2").val()
    for (let i = 1; i <= 6; i++) {
        if (i == 1 || i == 2) {
            if ($("#locationPart" + i).val() === "") {
                playSound('error')
                Utils.showAlert(m("warning.location.required"), "warning");
                isSaving = false;
                return; // 함수 종료
            }
        } else {
            if ($("#locationPart" + i).val()) {
                location = location + "-" + $("#locationPart" + i).val()
            }

        }


    }
    if (location.split("-").length < 3) {
        Utils.showConfirm(m("confirm.noLocation"), () => {
            existBarcode('add', status0, list)
        })
    } else {
        $.ajax({
            url: "/purchase/exist-location",		// 로케이션이 비어있는지 확인
            type: "POST",
            data: {location: location},
            success: function (count) {
                if (count == 0) {
                    existBarcode('ok', status0, list);
                } else {
                    threeButtonConfirm(
                        "There is a product at that location. Would you like to add it?<br><br>" +
                        "Select your action:<br><br>" +
                        "• <strong>Add</strong>: Add to existing items<br>" +
                        "• <strong>Change</strong>: Clear and add new items<br>" +
                        "• <strong>Cancel</strong>: Go back to previous screen",
                        "Add",
                        "Change",
                        "Cancel",
                        () => existBarcode('add', status0, list),        // 한 줄이면 중괄호 생략 가능
                        () => existBarcode('replace', status0, list),
                        () => {
                        }                          // 빈 함수 또는 null
                    );
                }
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


    function existBarcode(status1, status0, list) {			// 이미 적재된 바코드인지 확인
        const barcodeList = JSON.parse(localStorage.getItem("barcodeListRealStockLastDayLocation") || "[]");

        if (barcodeList.length === 0) {
            Utils.showAlert(m("warning.barcode.required"), "warning");
            playSound('error');
            isSaving = false;
            return;
        }
        let data = {
            barcode: barcodeList
        }
        $.ajax({
            url: `/purchase/exist-location-barcode`,
            method: 'POST',
            contentType: "application/json",
            data: JSON.stringify(data),
            success: function (result) {
                if (result == 0) {
                    saveLocation2(status1, 'ok', status0, list)
                } else {
                    // 250908 DH 위치 바꿀건지 물어보는 것 주석 해제
                    Utils.showConfirm('The barcode is already loaded. Would you like to change the position?',
                        () => {		// 확인 콜백
                            saveLocation2(status1, 'replace', status0, list)
                        },
                        () => {		// 취소 콜백
                            //saveLocation2(status,'replace')
                        })

                }
                hideLoading();
            },
            error: function (request, status, error) {
                console.log(error);
                if (request.status == 200) {

                } else if (request.status == 500) {
                    Utils.showAlert(m("error.generic"), 'warning');
                    playSound('error');
                } else if (request.status == 0) {
                    Utils.showAlert(m("error.offline"), 'warning');
                    playSound('error');
                } else {
                    Utils.showAlert("code: " + request.status + "<br>message: " + request.responseText + "<br>error: " + error, "warning");
                    playSound('error');
                }
                hideLoading();
            },
            complete: function () {
                hideLoading();
                isSaving = false; // 전송 끝나면 다시 가능
            }
        });
    }

    let isSaving2 = false;

    function saveLocation2(status1, status2, status0, list) {
        console.log("isSaving2 :", isSaving2);
        if (isSaving2) {
            console.log("⚠ saveLocation2 중복 실행 방지됨");
            return;
        }
        isSaving2 = true;
        const barcodeList = JSON.parse(localStorage.getItem("barcodeListRealStockLastDayLocation") || "[]");

        if (barcodeList.length === 0) {
            Utils.showAlert(m("warning.barcode.required"), "warning");
            playSound('error');
            isSaving2 = false;  // 체크 후 즉시 해제
            return;
        }

        let data = {
            date: date,
            barcode: barcodeList,
            location: location,
            itemcode: itemcode,
            memo: '-',
            qty: qty,
            status1: status1,
            status2: status2,
            realstock: 'Y',
            source: 'LOCATIONCOUNT',
            kind: 'LOCATIONCOUNT',
            main: 'MOVE',
            factory: $("#locationPart1").val(),
            storage: $("#locationPart2").val(),
            status0: status0,
            barcode2: list
        }
        $.ajax({
            url: `/purchase/insertRealStockLastDayLocation`,
            method: 'POST',
            contentType: "application/json",
            data: JSON.stringify(data),
            success: function (result) {
                let response = result.response;
                console.log("response : " + response)
                if (response == "success") {
                    playSound('complete')
                    Utils.showAlert(m("info.location.stroage"));
                    localStorage.removeItem("barcodeListRealStockLastDayLocation");
                    renderTable();
                    $("#locationPart3").val("no")
                    $("#locationPart4").val("no")
                    $("#locationPart5").val("no")
                    $("#locationPart6").val("no")
                    $("#storageBtn").blur();
                    $(".location-select").css({
                        "background-color": "white",
                        "color": "black"
                    });
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
                    playSound('error');
                } else if (request.status == 0) {
                    Utils.showAlert(m("error.offline"), 'warning');
                    playSound('error');
                } else {
                    Utils.showAlert("code: " + request.status + "<br>message: " + request.responseText + "<br>error: " + error, "warning");
                    playSound('error');
                }
                hideLoading();
            },
            complete: function () {
                hideLoading();
                // ❗ AJAX 끝나면 초기화
                isSaving2 = false;
                console.log("isaving2 false 1 : " + isSaving2);
            }
        });
    }
}

function renderTable() {		//테이블그리기
    console.log("테이블그리기")
    let table = $("#dataTableBody");
    const storage = $('.storage-select').val();
    let barcodeArray = [];

    if (typeof localStorage !== 'undefined' && localStorage.getItem("barcodeListRealStockLastDayLocation")) {
        barcodeArray = JSON.parse(localStorage.getItem("barcodeListRealStockLastDayLocation"));
    } else {
        barcodeArray = [];
    }
    table.empty();
    let totalqty = 0;
    for (let i = 0; i < barcodeArray.length; i++) {
        let barcodeStr = barcodeArray[i];	// 전체 문자열
        let barcodeOneArr = barcodeArray[i].split(",");	// 내부 필드 배열
        let tbody = "";
        if (barcodeOneArr.length === 5 && (barcodeStr.endsWith("MEX") || barcodeStr.endsWith("USA"))) {	// 정상 바코드
            tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}" style = 'background-color:#fdd793'>
						<td class = "dataInfo">${Number(barcodeOneArr[2])}</td>
						<td class = "dataInfo">${barcodeOneArr[0]}</td>
						<td class = "dataInfo">${Number(barcodeOneArr[3])}</td>
						<td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}','${Number(barcodeOneArr[3])}')">${m('btn.delete')}</button></td>
					</tr>`;
            totalqty = totalqty + Number(barcodeOneArr[3]);
        } else if (barcodeOneArr[0][0] === "P" && (barcodeStr.endsWith("MEX") || barcodeStr.endsWith("USA"))) {
            tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}" style = 'background-color:#aae2ca' >
						<td class = "dataInfo">${Number(barcodeArray[i].substring(7, 12))}</td>
						<td class = "dataInfo">${barcodeOneArr[1]}</td>
						<td class = "dataInfo">${Number(barcodeOneArr[2])}</td>
						<td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}', '${Number(barcodeOneArr[2])}')">${m('btn.delete')}</button></td>
					</tr>`;
            totalqty = totalqty + Number(barcodeOneArr[2]);
        }
        table.prepend(tbody);
    }

    $("#count").text(barcodeArray.length);
    $("#totalqty").text(formatNumber(totalqty));
}

function clearForm() {
    console.log("clear클릭");
    Utils.showConfirm(m("confirm.delete.clear"), () => {
        $("#barcode").val("");
        $("#itemcode").val("");
        $("#qty").val("");
        $("#locationPart3").val("");
        $("#locationPart4").val("");
        $("#locationPart5").val("");
        $("#locationPart6").val("");

        localStorage.removeItem("barcodeListRealStockLastDayLocation");
        $("#dataTableBody").empty();
        $("#count").text("0");
        renderTable();
    })

}

function deleteEntry(bar, qty) {		// localstorage에서 특정데이터 삭제
    let className = "bar_" + bar;
    console.log("삭제 바코드 : " + bar)
    Utils.showConfirm(m("confirm.delete.clear"), () => {
        let barcodeArray = JSON.parse(localStorage.getItem("barcodeListRealStockLastDayLocation") || "[]");
        let newArray = barcodeArray.filter(item => item.toString().trim() !== bar.toString().trim());
        localStorage.setItem("barcodeListRealStockLastDayLocation", JSON.stringify(newArray));
        $("." + CSS.escape(className)).remove();
        $("#count").text(newArray.length);
        let totalqty = $("#totalqty").text();
        totalqty = Number(totalqty) - Number(qty);
        $("#totalqty").text(formatNumber(totalqty));
        Utils.showAlert(m("success.deleted"), 'success');
    })
    focusWithoutKeyboard()

}

//팝업창
$(document).on("click", ".dataInfo", function () {
    // 클릭한 tr중 dataInfo클래스를 찾아 첫번째 텍스트 반환
    const itemcode = $(this).closest('tr').find('td.dataInfo').eq(1).text().trim();

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



