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

    $(".storage-select").val("PRODUCT").trigger("change").prop("disabled", true);

    renderTable();

    focusWithoutKeyboard();
    // input창에서 포커스 없어질때 세팅
    $('#barcodeInput').on('blur', function () {
        manualTouch = false;
        inputMode = 'readonly'
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
    if (!savelimitCheck("barcodeListInspectionStorage", 500)) {
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

    if (barcode.split("_").length === 6){
        let stored = [];

        if (window.localStorage && localStorage.getItem("barcodeListInspectionStorage")) {
            stored = JSON.parse(localStorage.getItem("barcodeListInspectionStorage"));
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
            localStorage.setItem("barcodeListInspectionStorage", JSON.stringify(stored));
            $("#barcodeInput").val("");
            renderTable();
            hideLoading();
            Utils.showAlert(`${barcode}<br>${m("info.barcode.saved")}`, "#008000", barcode)
        }
    } else {
        let audio = new Audio('/sounds/buzzer.wav');
        audio.play();
        $("#barcodeInput").val("");
        hideLoading();
        Utils.showAlert(`${m("warning.barcode.invalid")}<br>${m("warning.check")}`, "warning")
        return;
    }
}

function saveBarcode(kind) {				// 창고검사 전송 (kind: 'RETURN' | 'SCRAP')
    if (isSaving) return;
    isSaving = true;

    const barcodeList = JSON.parse(localStorage.getItem("barcodeListInspectionStorage") || "[]");

    if (barcodeList.length === 0) {
        Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
        focusWithoutKeyboard();
        isSaving = false;
        return;
    }

    const confirmMsg = kind === 'RETURN' ? m("confirm.inspection.return") : m("confirm.inspection.dispose");

    let data = {
        date: $("#datepicker").val(),
        barcode: barcodeList,
        source: "STORAGE",
        source2: kind,
        kind: kind,
        storage: $('.storage-select').val(),
        factory: localStorage.getItem('rememberedFactory'),
    };

    Utils.showConfirm(confirmMsg, () => {
            showLoading();
            $.ajax({
                url: "/purchase/inspectionStorage",
                type: "POST",
                data: JSON.stringify(data),
                contentType: "application/json",
                success: function (result) {
                    let response = result.response;
                    if (response === "success") {
                        localStorage.removeItem('barcodeListInspectionStorage');
                        $("#dataTableBody").empty();
                        $("#count").text("0");
                        $("#totalqty").text("0");
                        Utils.showAlert(m("info.barcode.sent"), "info");
                        playSound("complete");
                    } else {
                        const barcodeHtml = makeBarcodesClickable(result.barcode);
                        showAlert("", barcodeHtml + `<br>${m(result.response)}`, "warning");
                        highlightErrorRows(result.barcode);
                        playSound('error');
                    }
                },
                error: function (xhr, status, error) {
                    alert("오류가 발생했습니다: " + error);
                },
                complete: function () {
                    isSaving = false;
                    hideLoading();
                }
            });
        },
        () => {
            Utils.showAlert(m("success.cancel.sendAll"), "#008000");
            isSaving = false;
            hideLoading();
        });
}

function renderTable() {		//테이블그리기
    console.log("테이블그리기")
    let table = $("#dataTableBody");
    let totalqty = 0;
    let barcodeArray = [];

    if (typeof localStorage !== 'undefined' && localStorage.getItem("barcodeListInspectionStorage")) {
        barcodeArray = JSON.parse(localStorage.getItem("barcodeListInspectionStorage"));
    } else {
        barcodeArray = [];
    }
    table.empty();
    for (let i = 0; i < barcodeArray.length; i++) {
        let barcodeStr = barcodeArray[i];	// 전체 문자열
        let barcodeOneArr = barcodeArray[i].split(",");	// 내부 필드 배열
        let tbody = "";
        if (barcodeOneArr.length === 5 && (barcodeStr.endsWith("MEX") || barcodeStr.endsWith("USA"))) {	// 정상 바코드
            tbody = `<tr class = "bar_${barcodeArray[i]}">
							<td class = "dataInfo">${barcodeOneArr[0]}</td>
							<td class = "dataInfo">${Number(barcodeOneArr[3])}</td>
							<td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}','${Number(barcodeOneArr[3])}')">${m('btn.delete')}</button></td>
						</tr>`;
            totalqty = totalqty + Number(barcodeOneArr[3]);
        } else if (barcodeOneArr[0][0] === "P" && (barcodeStr.endsWith("MEX") || barcodeStr.endsWith("USA"))) {
            tbody = `<tr class = "bar_${barcodeArray[i]}">
							<td class = "dataInfo">(P)${barcodeOneArr[1]}</td>
							<td class = "dataInfo">${Number(barcodeOneArr[2])}</td>
							<td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}','${Number(barcodeOneArr[2])}')">${m('btn.delete')}</button></td>
						</tr>`;
            totalqty = totalqty + Number(barcodeOneArr[2]);
        } else if (barcodeStr.split("_").length === 6) {
            let underParts = barcodeStr.split("_");
            tbody = `<tr class = "bar_${barcodeStr} bar-row" data-barcode="${barcodeStr}">
                        <td class = "dataInfo">${underParts[3]}</td>
                        <td class = "dataInfo">${Number(underParts[4])}</td>
                        <td><button class="delete-btn" onclick="deleteEntry('${barcodeStr}', '${Number(underParts[4])}')">${m('btn.delete')}</button></td>
                    </tr>`;
            totalqty = totalqty + Number(underParts[4]);
        } else if (barcodeStr.startsWith("[)>")) {
            let barcodeParts = barcodeArray[i].split(":");
            let itemcode = barcodeParts[2].substring(1);
            let qty = barcodeParts[3].substring(2);
            tbody = `<tr class = "bar_` + barcodeArray[i] + ` bar-row" data-barcode="${barcodeArray[i]}">
							<td>` + itemcode + `</td>
							<td>` + Number(qty) + `</td>
							<td><button class="delete-btn" onclick="deleteEntry('` + barcodeArray[i] + `')">${m("btn.delete")}</button></td>
						</tr>`
            totalqty = totalqty + Number(qty);
        }
        table.prepend(tbody);
    }
    $("#count").text(+barcodeArray.length);
    $("#totalqty").text(formatNumber(totalqty));
}

function deleteEntry(bar, qty) {		// localstorage에서 특정데이터 삭제
    let className = "bar_" + bar;
    console.log("삭제 바코드 : " + bar)
    Utils.showConfirm(m("confirm.delete.item"), () => {
        let barcodeArray = JSON.parse(localStorage.getItem("barcodeListInspectionStorage") || "[]");
        let newArray = barcodeArray.filter(item => item.toString().trim() !== bar.toString().trim());
        localStorage.setItem("barcodeListInspectionStorage", JSON.stringify(newArray));
        $("." + CSS.escape(className)).remove();
        $("#count").text(newArray.length);
        let totalqty = $("#totalqty").text();
        totalqty = Number(totalqty) - Number(qty);
        $("#totalqty").text(formatNumber(totalqty));
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

        localStorage.removeItem("barcodeListInspectionStorage");
        $("#dataTableBody").empty();
        $("#count").text("0")
        $("#totalqty").text("0")
        Utils.showAlert(m("success.deleted.all"), "success");
    })
    focusWithoutKeyboard()
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
