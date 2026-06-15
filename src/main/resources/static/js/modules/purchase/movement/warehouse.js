let manualTouch = false;
let pendingTransferData = null;
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

    let barcodeArray = [];

    if (typeof localStorage !== 'undefined' && localStorage.getItem("barcodeListWHMove")) {
        barcodeArray = JSON.parse(localStorage.getItem("barcodeListWHMove"));
    } else {
        barcodeArray = [];
    }
    renderTable();


    // input창에서 포커스 없어질때 세팅
    $('#barcodeInput').on('blur', function () {
        manualTouch = false;
        inputMode = 'readonly'
    });

    const _storedWarehouse = (localStorage.getItem("rememberedWarehouse") || "").trim();
    const _isIllinois = _storedWarehouse === 'ILLINOIS';

    const STORAGE_OPTIONS = _isIllinois
        ? ['OUTSIDE', 'INBOUND', 'PRODUCT']
        : ['INBOUND', 'PRODUCT'];
    const STORAGE_OPPOSITE = _isIllinois
        ? { 'OUTSIDE': 'INBOUND', 'INBOUND': 'OUTSIDE', 'PRODUCT': 'OUTSIDE' }
        : { 'INBOUND': 'PRODUCT', 'PRODUCT': 'INBOUND' };

    function updateStorageByFactory() {
        const $select1 = $('.storage-select1');
        const $select2 = $('.storage-select2');

        $select1.empty();
        STORAGE_OPTIONS.forEach(opt => $select1.append(new Option(opt, opt)));

        const defaultSel1 = _isIllinois ? 'OUTSIDE' : 'INBOUND';
        const defaultSel2 = STORAGE_OPPOSITE[defaultSel1] || '';

        $select1.val(defaultSel1);
        $select2.empty();
        $select2.append(new Option(defaultSel2, defaultSel2));
        $select2.val(defaultSel2);
        $select2.prop('disabled', true);
    }

    updateStorageByFactory();

    $(document).on('change', '.storage-select1', function () {
        const selected = $(this).val();
        const $select2 = $('.storage-select2');
        const opposite = STORAGE_OPPOSITE[selected] || '';
        $select2.empty();
        $select2.append(new Option(opposite, opposite));
        $select2.val(opposite);
    });
})

function addEntry() {
    const barcodeInput = document.getElementById('barcodeInput');
    const barcode = barcodeInput.value.trim();

    if (!barcode) {
        Utils.showAlert(m("warning.barcode.required"));
        return;
    }
    // 🔹 저장 제한 체크
    if (!savelimitCheck("barcodeListWHMove", 500)) {
        return; // 500개 초과 시 여기서 중단 → showLoading() 실행 안 됨
    }
    // 로딩 표시
    showLoading();

    //if ((barcode.split(",").length === 5 && (barcode.split(",")[4] === "SCMMEX" || barcode.split(",")[4] === "WMSMEX" || barcode.split(",")[4] === "WMSUSA"))
    //|| barcode[0][0] === "P" && (barcode.endsWith("MEX") || barcode.endsWith("USA"))) {
    if(barcode.split("_").length === 6 || barcode.split(",")[3] === "WMSUSA" || barcode.split(",")[4] === "WMSUSA"){
        let stored = [];
        if (window.localStorage && localStorage.getItem("barcodeListWHMove")) {
            stored = JSON.parse(localStorage.getItem("barcodeListWHMove"));
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
            localStorage.setItem("barcodeListWHMove", JSON.stringify(stored));
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

    const barcodeList = JSON.parse(localStorage.getItem("barcodeListWHMove") || "[]");
    let data = {
        date: $("#datepicker").val(),
        storage1: $('.storage-select1').val(),
        storage2: $('.storage-select2').val(),
        factory: localStorage.getItem('rememberedFactory'),
        mainkind: 'MOVE',
        kind: 'STORAGEMOVE',
        barcode: barcodeList,
    }

    if ($('.storage-select1').val() == $('.storage-select2').val()) {
        showAlert("", m("warning.storage.same"), "warning");
        hideLoading();
        isSaving = false;
        return;
    }
    showLoading();
    $.ajax({
        url: `/purchase/checkFifo`,
        method: 'POST',
        contentType: "application/json",
        data: JSON.stringify(data),
        success: function (fifoResult) {
            hideLoading();
            if (fifoResult && Object.keys(fifoResult).length > 0) {
                renderWipFifo(fifoResult, data);
            } else {
                Utils.showConfirm("Do you want to change the warehouse?", () => {
                    doTransferWarehouse(data);
                }, () => {
                    Utils.showAlert(m("success.cancel.sendAll"), "#008000");
                    isSaving = false;
                });
            }
        },
        error: function () {
            Utils.showAlert(m("error.generic"), 'warning');
            hideLoading();
            isSaving = false;
        }
    })
}

function doTransferWarehouse(data) {
    const transferData = data || pendingTransferData;
    document.getElementById('storageMoveModal').style.display = 'none';
    document.body.style.overflow = '';
    pendingTransferData = null;
    isSaving = true;
    showLoading();
    $.ajax({
        url: `/purchase/transferWarehouse`,
        method: 'POST',
        contentType: "application/json",
        data: JSON.stringify(transferData),
        success: function (result) {
            let response = result.response;
            console.log("response : " + response)
            if (response === "success") {
                localStorage.removeItem('barcodeListWHMove');
                $("#dataTableBody").empty();
                $("#count").text("0");
                $("#totalqty").text("0");
                Utils.showAlert(m("info.barcode.sent"), "info");
                playSound('complete')
            } else {
                const barcodeHtml = makeBarcodesClickable(result.barcode);
                showAlert("", barcodeHtml + `<br>${m(result.response)}`, "warning");
                highlightErrorRows(result.barcode);
                playSound('error')
            }
            hideLoading();
        },
        error: function (request, status, error) {
            console.log(error);
            if (request.status == 500) {
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
            isSaving = false;
        }
    });
}

function closestorageMoveModal() {
    document.getElementById('storageMoveModal').style.display = 'none';
    document.body.style.overflow = '';
    isSaving = false;
    pendingTransferData = null;
}

function renderWipFifo(result, transferData) {
    pendingTransferData = transferData;
    document.getElementById('storageMoveModal').style.display = 'flex';
    document.body.style.overflow = 'hidden';
    const container = document.getElementById('fifoContainer');
    container.innerHTML = '';

    const entries = Object.entries(result || {});
    if (entries.length === 0) {
        container.innerHTML = '<div class="empty">No data</div>';
        return;
    }
    entries.forEach(([itemcode, rows]) => {
        const oitemcode = (rows[0] && (rows[0].OITEMCODE || rows[0].oitemcode)) || itemcode;
        const section = document.createElement('div');
        section.className = 'location-section';
        section.innerHTML = `
          <div class="section-title">
            <div class="info-row">
              <span class="info-label">Item Code</span>
              <span class="info-value">${oitemcode}</span>
            </div>
          </div>
          <div class="location-table-wrapper">
            <table class="location-table">
              <thead>
                <tr>
                  <th>Barcode</th>
                  <th>Date</th>
                  <th>Qty</th>
                </tr>
              </thead>
              <tbody></tbody>
            </table>
          </div>
        `;
        const tbody = section.querySelector('tbody');
        (rows || []).forEach(r => {
            const barcode = r.BARCODE || r.barcode || '';
            const sdateRaw = r.SDATE || r.sdate || '';
            const qty = r.QTY || r.qty || 0;
            const sdateShort = sdateRaw.replace(/^\d{2}(\d{2})-/, '$1-'); // 2026-04-15 → 26-04-15
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td class="td-barcode">${barcode}</td>
                <td class="td-date">${sdateShort}</td>
                <td class="ta-right">${Number(qty).toLocaleString()}</td>
            `;
            tbody.appendChild(tr);
        });
        container.appendChild(section);
    });
}

function renderTable() {		//테이블그리기
    console.log("테이블그리기")
    let table = $("#dataTableBody");
    let barcodeArray = [];

    if (typeof localStorage !== 'undefined' && localStorage.getItem("barcodeListWHMove")) {
        barcodeArray = JSON.parse(localStorage.getItem("barcodeListWHMove"));
        console.log("250907")
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
        const key = "barcodeListWHMove";
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
        localStorage.removeItem('barcodeListWHMove');
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