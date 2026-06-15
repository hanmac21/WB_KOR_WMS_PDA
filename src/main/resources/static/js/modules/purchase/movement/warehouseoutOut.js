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

    focusWithoutKeyboard();

    renderTable();

    const $outSelect = $('.storage-select1');
    const $inSelect  = $('.storage-select2');

    // OUT 선택값에 따라 IN 옵션을 갱신
    function updateInOptions(outValue) {
        $inSelect.empty();

        if (outValue === 'INBOUND' || outValue === 'PRODUCT') {
            // OUT이 INBOUND/PRODUCT면 IN은 OUTSIDE 고정
            $inSelect.append(new Option('OUTSIDE', 'OUTSIDE'));
            $inSelect.val('OUTSIDE');
            $inSelect.prop('disabled', true);   // 고정이므로 비활성화
        } else if (outValue === 'OUTSIDE') {
            // OUT이 OUTSIDE면 IN은 INBOUND/PRODUCT 중 선택
            $inSelect.append(new Option('INBOUND',  'INBOUND'));
            $inSelect.append(new Option('PRODUCT', 'PRODUCT'));
            $inSelect.val('INBOUND');           // 기본값
            $inSelect.prop('disabled', false);
        }
    }

    // OUT 옵션 초기 세팅 (INBOUND, PRODUCT, OUTSIDE)
    function initStorageSelectors() {
        $outSelect.empty();
        ['INBOUND', 'PRODUCT', 'OUTSIDE'].forEach(opt => {
            $outSelect.append(new Option(opt, opt));
        });

        // ILLINOIS 창고 선택 시 OUTSIDE 기본 적용, 아니면 INBOUND
        const _storedWarehouse = (localStorage.getItem("rememberedWarehouse") || "").trim();
        const defaultOut = _storedWarehouse === 'ILLINOIS' ? 'OUTSIDE' : 'INBOUND';
        $outSelect.val(defaultOut);
        updateInOptions(defaultOut);
    }

    // OUT 변경 시 IN 옵션 재구성
    $outSelect.on('change', function () {
        updateInOptions($(this).val());
    });

    // 실행
    initStorageSelectors();
})

function addEntry() {
    const barcodeInput = document.getElementById('barcodeInput');
    const barcode = barcodeInput.value.trim();

    if (!barcode) {
        Utils.showAlert(m("warning.barcode.required"));
        return;
    }
    // 🔹 저장 제한 체크
    if (!savelimitCheck("barcodeListWHMoveOut", 500)) {
        return; // 500개 초과 시 여기서 중단 → showLoading() 실행 안 됨
    }
    // 로딩 표시
    showLoading();

    //if ((barcode.split(",").length === 5 && (barcode.split(",")[4] === "SCMMEX" || barcode.split(",")[4] === "WMSMEX" || barcode.split(",")[4] === "WMSUSA"))
    //  || barcode[0][0] === "P" && (barcode.endsWith("MEX") || barcode.endsWith("USA"))) {
    if(barcode.split("_").length === 6 || barcode.split(",")[3] === "WMSUSA" || barcode.split(",")[4] === "WMSUSA"){
        let stored = [];
        if (window.localStorage && localStorage.getItem("barcodeListWHMoveOut")) {
            stored = JSON.parse(localStorage.getItem("barcodeListWHMoveOut"));
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
            playSound("complete");

            stored.push(barcode);
            localStorage.setItem("barcodeListWHMoveOut", JSON.stringify(stored));
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
        Utils.showAlert(barcode + "<br>" + m("warning.barcode.invalid") + "<br>" + m("warning.check"), "#FF0000", barcode)
        return;
    }
}

function saveBarcode() {
    // 중복 방지: 이미 저장 중이면 바로 종료
    console.log("isaving : " + isSaving);
    if (isSaving) {
        console.log("⚠ 중복 클릭 방지됨");
        return;
    }

    const barcodeList = JSON.parse(localStorage.getItem("barcodeListWHMoveOut") || "[]");
    let data = {
        date: $("#datepicker").val(),
        storage1: $('.storage-select1').val(),
        storage2: $('.storage-select2').val(),
        factory: localStorage.getItem('rememberedFactory'),
        mainkind: 'MOVE',
        kind: 'STORAGEMOVE',
        barcode: barcodeList,
        source: 'SENDING'
    }

    if ($('.storage-select1').val() == $('.storage-select2').val()) {
        showAlert("", m("warning.storage.same"), "warning");
        hideLoading();
        isSaving = false;
        return;
    }
    Utils.showConfirm("Do you want to change the warehouse?", () => {
            isSaving = true;
            showLoading();
            $.ajax({
                url: `/purchase/transferWarehouse`,
                method: 'POST',
                contentType: "application/json",
                data: JSON.stringify(data),
                success: function (result) {
                    let response = result.response;
                    console.log("response : " + response)
                    if (response === "success") {
                        localStorage.removeItem('barcodeListWHMoveOut');
                        $("#dataTableBody").empty();
                        $("#count").text("0");
                        $("#totalqty").text("0");
                        Utils.showAlert(m("info.barcode.sent"), "info");
                        playSound('complete')
                    } else {
                        //showAlert("",`${m(result.response)}`, "warning");
                        //showAlert("", result.barcode.join("\n") + `<br>${m(result.response)}`, "warning");
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
                    } else {
                        Utils.showAlert("code: " + request.status + "<br>message: " + request.responseText + "<br>error: " + error, "warning");
                    }
                    hideLoading();
                },
                complete: function () {
                    hideLoading();
                    isSaving = false; // 전송 끝나면 다시 가능
                }
            });
        },
        () => {
            Utils.showAlert(m("success.cancel.sendAll"), "#008000");
            hideLoading();
            isSaving = false; // 전송 끝나면 다시 가능
        })
}

function renderTable() {		//테이블그리기
    console.log("테이블그리기")
    let table = $("#dataTableBody");
    let barcodeArray = [];

    if (typeof localStorage !== 'undefined' && localStorage.getItem("barcodeListWHMoveOut")) {
        barcodeArray = JSON.parse(localStorage.getItem("barcodeListWHMoveOut"));
    } else {
        barcodeArray = [];
    }
    console.log("250907 barcodeArray.length : " + barcodeArray.length)
    let totalqty = 0;
    table.empty();
    for (let i = 0; i < barcodeArray.length; i++) {
        let barcodeStr = barcodeArray[i];	// 전체 문자열
        let barcodeOneArr = barcodeArray[i].split("_");	// 내부 필드 배열
        let tbody = "";
        if (barcodeOneArr.length === 6 ) {
            tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
                        <td class = "dataInfo">${barcodeOneArr[3]}</td>
                        <td class = "dataInfo">${barcodeOneArr[2].substring(2)}${barcodeOneArr[1]}${barcodeOneArr[0]}</td>
                        <td class = "dataInfo">${Number(barcodeOneArr[4])}</td>
                        <td class = "dataInfo">${Number(barcodeOneArr[5])}</td>
                        <td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}', '${Number(barcodeOneArr[4])}')">${m('btn.delete')}</button></td>
                    </tr>`
            totalqty = totalqty + Number(barcodeOneArr[4]);
        }else if (barcodeStr.split(",").length === 5  && barcodeStr.endsWith("USA")){       // USA파트라벨
            let parts = barcodeStr.split(",");
            tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
                            <td class = "dataInfo">${parts[0]}</td>
                            <td class = "dataInfo">${parts[1]}</td>
                            <td class = "dataInfo">${Number(parts[3])}</td>
                            <td class = "dataInfo">${Number(parts[2])}</td>
                            <td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}', '${Number(barcodeOneArr[4])}')">${m('btn.delete')}</button></td>
                        </tr>`
            totalqty = totalqty + Number(parts[3]);
        }else if(barcodeStr.split(",").length === 4  && barcodeStr.endsWith("USA")){        // USA파레트라벨
            let parts = barcodeStr.split(",");
            tbody = `<tr class = "bar_${barcodeArray[i]} bar-row" data-barcode="${barcodeArray[i]}">
                            <td class = "dataInfo">${parts[1]}</td>
                            <td class = "dataInfo">${parts[0].substring(1, 7)}</td>
                            <td class = "dataInfo">${Number(parts[2])}</td>
                            <td class = "dataInfo">${Number(parts[0].substring(7))}</td>
                            <td><button class="delete-btn" onclick="deleteEntry('${barcodeArray[i]}', '${Number(barcodeOneArr[4])}')">${m('btn.delete')}</button></td>
                        </tr>`
            totalqty = totalqty + Number(parts[2]);
        }
        table.prepend(tbody);
    }
    $("#count").text(barcodeArray.length);
    $("#totalqty").text(formatNumber(totalqty));
    hideLoading();
}

function deleteEntry(elOrBar) { // localStorage에서 특정데이터 삭제
    // elOrBar가 버튼 요소면 tr에서 data-barcode 추출
    let bar = "";
    let $row = null;

    if (elOrBar && elOrBar.nodeType === 1) {
        $row = $(elOrBar).closest("tr");
        bar = String($row.data("barcode") || "").trim();
    } else {
        bar = String(elOrBar || "").trim();
        $row = $('#dataTableBody tr').filter(function () {
            return String($(this).data('barcode') || '').trim() === bar;
        }).first();
    }

    if (!bar) {
        focusWithoutKeyboard();
        return;
    }

    Utils.showConfirm(m("confirm.delete.item"), () => {
        const key = "barcodeListWHMoveOut";
        const arr = JSON.parse(localStorage.getItem(key) || "[]");
        const next = arr.filter(item => String(item).trim() !== bar);
        localStorage.setItem(key, JSON.stringify(next));

        if ($row && $row.length) $row.remove();

        // 개수 갱신(테이블 기준)
        $("#count").text($("#dataTableBody tr").length);

        Utils.showAlert(m("success.deleted"), 'success');
        renderTable();
    });

    focusWithoutKeyboard();
}


function clearAll() {
    Utils.showConfirm(m("confirm.delete.all"), () => {
        const $tb = $('#dataTableBody');
        console.log($tb);
        console.log($tb.children('tr'));
        console.log($tb.children('tr').length);
        if ($tb.children('tr').length === 0) {
            Utils.showAlert(m('warning.data.delete.all'), "warning");
            return;
        }
        localStorage.removeItem('barcodeListWHMoveOut');
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