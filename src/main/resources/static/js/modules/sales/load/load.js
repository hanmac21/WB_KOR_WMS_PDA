let manualTouch = false;
$(document).ready(function () {
    //hideLoading();
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
                $(this).datepicker('setDate', new Date()); // ✅ 보정된 날짜 사용

            }
        }
    });
    $("#datepicker").val(today);
    $("#datepicker").datepicker();

    $(document).on('click', '.ui-datepicker-current', function () {
        const adjustedToday = new Date(); // ✅ 보정된 날짜 사용
        $("#datepicker").datepicker("setDate", adjustedToday);

        // 오늘 날짜 설정 후 키보드 안뜨게 포커스
        if (typeof focusWithoutKeyboard === 'function') {
            $("#datepicker").datepicker("hide");
            focusWithoutKeyboard();
        }
    });


    renderTable();

    focusWithoutKeyboard();
    // input창에서 포커스 없어질때 세팅
    $('#barcodeInput').on('blur', function () {
        manualTouch = false;
        inputMode = 'readonly'
    });
})

let barcodeType = "";

function addEntry() {		// 영업이송에서 스캔됐을때 로컬스토리지에 안넣고 그냥 화면에 보여줌
    const barcodeInput = document.getElementById('barcodeInput');
    const barcode = barcodeInput.value.trim();
    if ((barcode.split(",").length === 5 && (barcode.split(",")[4] === "SCMMEX" || barcode.split(",")[4] === "WMSMEX") || barcode.split(",")[4] === "WMSUSA")
        	|| (barcode.split(",").length === 4 && (barcode.split(",")[3] === "SCMMEX" || barcode.split(",")[3] === "WMSMEX" || barcode.split(",")[3] === "WMSUSA"))) {
        $("#scanBarcode").val(barcode)
        if ($("#scanBarcode2").val().trim() === '') {
            playSound('complete')
        }

    } else {
        $("#scanBarcode2").val(barcode)
        if ($("#scanBarcode").val().trim() === '') {
            playSound('complete')
        }
    }
    console.log("barcode1 : " + $("#scanBarcode").val())
    console.log("barcode2 : " + $("#scanBarcode2").val())
    if ($("#scanBarcode").val().trim() !== "" && $("#scanBarcode2").val().trim() !== "") {
        console.log(" save barcode")
        // 둘 다 값이 있을 때 실행됨
        saveBarcode();
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

    if ($("#scanBarcode").val().trim() == "" || $("#scanBarcode2").val().trim() == "") {
        Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
    }
    let data = {
        date: $("#datepicker").val(),
        barcode1: $("#scanBarcode").val(),
        barcode2: $("#scanBarcode2").val(),
        storage1: $('.storage-select1').val(),
        source: "LOAD",
        main: "OUT",
        kind: "LOAD",
        factory: localStorage.getItem('rememberedFactory'),
        factoryno: $("#factoryno").val(),
        cust: $("#cust").val(),
        dock: $("#dock").val()
    }
    //Utils.showConfirm(m("confirm.send.all"), () => {
    showLoading();
    $.ajax({
        url: `/sales/insSaleOutput`,
        method: 'POST',
        contentType: "application/json",
        data: JSON.stringify(data),
        success: function (result) {
            let response = result.response;
            console.log("response : " + response)
            if (response == "success") {
                Utils.showAlert(m("info.barcode.sent"), "info");
                playSound('ok')
                $("#scanBarcode").val("");
                $("#scanBarcode2").val("");
            } else {
                const barcodeHtml = makeBarcodesClickable(result.barcode);
                showAlert("", barcodeHtml + `<br>${m(result.response)}`, "warning");
                //highlightErrorRows(result.barcode);		// 에러바코드 배경 빨간색으로 바꿔주는 함수
                playSound('error')
            }
        },
        error: function (request, status, error) {
            console.log(error);
            if (request.status == 200) {

            } else if (request.status == 500) {
                Utils.showAlert(m("error.generic"), 'warning');
            } else if (request.status == 0) {
                Utils.showAlert(m("error.offline"), 'warning');
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
    /*},
        () => {
            Utils.showAlert(m("success.cancel.sendAll"), "#008000");
            isSaving = false;
            hideLoading();
        })*/
}

function playFinishSound() {
    const AudioContext = window.AudioContext || window.webkitAudioContext;
    const ctx = new AudioContext();

    const osc1 = ctx.createOscillator();
    const osc2 = ctx.createOscillator();
    const gain = ctx.createGain();

    osc1.type = 'triangle'; // 부드러운 중음
    osc2.type = 'sine';     // 또렷한 고음

    osc1.connect(gain);
    osc2.connect(gain);
    gain.connect(ctx.destination);

    const now = ctx.currentTime;

    // 👉 스캔음(293Hz)보다 확실히 위로
    osc1.frequency.setValueAtTime(520, now);      // 첫 음
    osc2.frequency.setValueAtTime(1040, now + 0.12); // 완료 강조

    gain.gain.setValueAtTime(0.0001, now);
    gain.gain.exponentialRampToValueAtTime(0.5, now + 0.04);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.35);

    osc1.start(now);
    osc2.start(now);

    osc1.stop(now + 0.36);
    osc2.stop(now + 0.36);
}

function renderTable() {		//테이블그리기
    console.log("테이블그리기")
    let table = $("#dataTableBody");
    let totalqty = 0;
    let barcodeArray = [];

    if (typeof localStorage !== 'undefined' && localStorage.getItem("barcodeListSalesTransfer")) {
        barcodeArray = JSON.parse(localStorage.getItem("barcodeListSalesTransfer"));
    } else {
        barcodeArray = [];
    }
    table.empty();
    for (let i = 0; i < barcodeArray.length; i++) {
        let barcodeStr = barcodeArray[i];	// 전체 문자열
        let barcodeOneArr = barcodeArray[i].split(",");	// 내부 필드 배열
        let tbody = "";
        if (barcodeOneArr.length === 5 && (barcodeStr.endsWith("MEX") || barcodeStr.endsWith("USA"))) {	// 정상 바코드
            tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
							<td class = "dataInfo">${barcodeOneArr[0]}</td>
							<td class = "dataInfo">${barcodeOneArr[1]}</td>
							<td class = "dataInfo">${barcodeOneArr[2]}</td>
							<td class = "dataInfo">${Number(barcodeOneArr[3])}</td>
							<td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}', '${Number(barcodeOneArr[3])}')">${m('btn.delete')}</button></td>
						</tr>`
            totalqty = totalqty + Number(barcodeOneArr[3]);
        } else if (barcodeOneArr[0][0] === "P" && (barcodeStr.endsWith("MEX") || barcodeStr.endsWith("USA"))) {
            tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
							<td class = "dataInfo">(P)${barcodeOneArr[1]}</td>
							<td class = "dataInfo">${barcodeArray[i].substring(1, 7)}</td>
							<td class = "dataInfo">${barcodeArray[i].substring(7, 12)}</td>
							<td class = "dataInfo">${Number(barcodeOneArr[2])}</td>
							<td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}', '${Number(barcodeOneArr[2])}')">${m('btn.delete')}</button></td>
						</tr>`
            totalqty = totalqty + Number(barcodeOneArr[2]);
        }
        table.append(tbody);
    }
    $("#count").text(+barcodeArray.length);
    $("#totalqty").text(formatNumber(totalqty));
}

function deleteEntry(bar, qty) {		// localstorage에서 특정데이터 삭제
    let className = "bar_" + bar;
    console.log("삭제 바코드 : " + bar)
    Utils.showConfirm(m("confirm.delete.item"), () => {
        let barcodeArray = JSON.parse(localStorage.getItem("barcodeListSalesTransfer") || "[]");
        let newArray = barcodeArray.filter(item => item.toString().trim() !== bar.toString().trim());
        localStorage.setItem("barcodeListSalesTransfer", JSON.stringify(newArray));
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
        $("#scanBarcode").val("");
        $("#scanBarcode2").val("");
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

