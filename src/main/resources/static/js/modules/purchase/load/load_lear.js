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

    // 창고 기본값 변경 (ILLINOIS 창고 선택 시 OUTSIDE 우선)
    const _storedWarehouse = (localStorage.getItem("rememberedWarehouse") || "").trim();
    const _defaultStorage = _storedWarehouse === 'ILLINOIS' ? "OUTSIDE" : "PRODUCT";
    $(".storage-select").val(_defaultStorage).trigger("change").prop("disabled", true);

    renderTable();
    const savedInvoice = localStorage.getItem("invoiceDataLearOut");
    if (savedInvoice) {
        const info = JSON.parse(savedInvoice);
        $('#invoiceManifestNo').text(info.manifestNo);
        updateInvoiceStats(info);
    }

    $('#barcodeInput').attr('readonly', true);

    $(document).on('touchend', function (e) {
        if (window.focusTimeout) clearTimeout(window.focusTimeout);

        if ($(e.target).is('#barcodeInput')) {
            window.focusTimeout = setTimeout(function () {
                focusWithoutKeyboard();
            }, 100);
        }
    });

})

function addEntry() {			// 로컬스토리지 저장
    if ($("#barcodeListModal").css("display") !== "none") {
        closeBarcodeModal();
    }

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

    // 인보이스 바코드: [)>$$LEAR$$...
    if (barcode.startsWith("[)>") && barcode.includes("$$") && barcode.includes("LEAR")) {
        if (localStorage.getItem("invoiceDataLearOut")) {
            playSound('error');
            $("#barcodeInput").val("");
            hideLoading();
            Utils.showAlert(m("warning.invoice.alreadyScanned"), "warning");
            return;
        }
        const info = parseInvoiceBarcode(barcode);
        if (info) {
            $('#invoiceManifestNo').text(info.manifestNo);
            localStorage.setItem("invoiceDataLearOut", JSON.stringify(info));
            renderTable();
            playSound('complete');
        } else {
            playSound('error');
            Utils.showAlert(`${m("warning.barcode.invalid")}`, "warning");
        }
        $("#barcodeInput").val("");
        hideLoading();
        return;
    }

    // 파트 바코드: L003243835TVNAA,260424,00001,00144.00,WMSUSA
    const bParts = barcode.split(",");
    if (bParts.length === 5 && (barcode.endsWith("USA")) ) {
        const invoiceRaw = localStorage.getItem("invoiceDataLearOut");
        if (!invoiceRaw) {
            playSound('error');
            Utils.showAlert(m("warning.invoice.required"), "warning");
            $("#barcodeInput").val("");
            hideLoading();
            return;
        }

        const invoice = JSON.parse(invoiceRaw);
        const parsed = parsePartBarcode(barcode);
        if (!parsed) {
            playSound('error');
            Utils.showAlert(m("warning.barcode.invalid"), "warning");
            $("#barcodeInput").val("");
            hideLoading();
            return;
        }

        const matchIdx = invoice.items.findIndex(item => item.itemCode === parsed.itemCode);
        const scannedList = JSON.parse(localStorage.getItem("partBarcodeListLearOut") || "[]");
        if (scannedList.includes(barcode)) {
            playSound('error2');
            Utils.showAlert(m("warning.barcode.duplicate"), "warning");
        } else if (matchIdx !== -1) {
            const item = invoice.items[matchIdx];
            if (item.scannedQty >= item.qty) {
                playSound('error');
                Utils.showAlert(`${parsed.itemCode}<br>${m("warning.invoice.alreadyComplete")}`, "warning");
            } else if (item.scannedQty + parsed.qty > item.qty) {
                playSound('error');
                Utils.showAlert(`${parsed.itemCode}<br>${item.scannedQty}/${item.qty}<br>${m("warning.invoice.overQty")}`, "warning");
            } else {
                item.scannedQty += parsed.qty;
                scannedList.push(barcode);
                localStorage.setItem("partBarcodeListLearOut", JSON.stringify(scannedList));
                localStorage.setItem("invoiceDataLearOut", JSON.stringify(invoice));
                renderTable();
                const allComplete = invoice.items.every(it => (it.scannedQty || 0) >= it.qty);
                playSound(allComplete ? 'ok' : 'complete');
                Utils.showAlert(`${parsed.itemCode}<br>${item.scannedQty}/${item.qty}`, "#008000");
            }
        } else {
            playSound('error');
            Utils.showAlert(m("warning.invoice.notMatched"), "warning");
        }
        $("#barcodeInput").val("");
        hideLoading();
        return;
    }

    // 박스 바코드: _구분 6파트 형식
    if (barcode.split("_").length === 6) {
        const invoiceRaw = localStorage.getItem("invoiceDataLearOut");
        if (!invoiceRaw) {
            playSound('error');
            Utils.showAlert(m("warning.invoice.required"), "warning");
            $("#barcodeInput").val("");
            hideLoading();
            return;
        }

        const invoice = JSON.parse(invoiceRaw);
        const parsed = parseBoxBarcode(barcode);
        if (!parsed) {
            playSound('error');
            Utils.showAlert(m("warning.barcode.invalid"), "warning");
            $("#barcodeInput").val("");
            hideLoading();
            return;
        }

        const matchIdx = invoice.items.findIndex(item => item.itemCode === parsed.itemCode);
        const scannedList = JSON.parse(localStorage.getItem("partBarcodeListLearOut") || "[]");
        if (scannedList.includes(barcode)) {
            playSound('error2');
            Utils.showAlert(m("warning.barcode.duplicate"), "warning");
        } else if (matchIdx !== -1) {
            const item = invoice.items[matchIdx];
            if (item.scannedQty >= item.qty) {
                playSound('error');
                Utils.showAlert(`${parsed.itemCode}<br>${m("warning.invoice.alreadyComplete")}`, "warning");
            } else if (item.scannedQty + parsed.qty > item.qty) {
                playSound('error');
                Utils.showAlert(`${parsed.itemCode}<br>${item.scannedQty}/${item.qty}<br>${m("warning.invoice.overQty")}`, "warning");
            } else {
                item.scannedQty += parsed.qty;
                scannedList.push(barcode);
                localStorage.setItem("partBarcodeListLearOut", JSON.stringify(scannedList));
                localStorage.setItem("invoiceDataLearOut", JSON.stringify(invoice));
                renderTable();
                const allComplete = invoice.items.every(it => (it.scannedQty || 0) >= it.qty);
                playSound(allComplete ? 'ok' : 'complete');
                Utils.showAlert(`${parsed.itemCode}<br>${item.scannedQty}/${item.qty}`, "#008000");
            }
        } else {
            playSound('error');
            Utils.showAlert(m("warning.invoice.notMatched"), "warning");
        }
        $("#barcodeInput").val("");
        hideLoading();
        return;
    }

    // 그 외 잘못된 바코드
    playSound('error');
    $("#barcodeInput").val("");
    hideLoading();
    Utils.showAlert(`${m("warning.barcode.invalid")}<br>${m("warning.check")}`, "warning");
}

function saveBarcode() {					// 전체전송
    // 중복 방지: 이미 저장 중이면 바로 종료
    console.log("isaving : " + isSaving);
    if (isSaving) {
        console.log("⚠ 중복 클릭 방지됨");
        return;
    }
    isSaving = true;

    const barcodeList = JSON.parse(localStorage.getItem("partBarcodeListLearOut") || "[]");

    if (barcodeList.length === 0) {
        Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
        focusWithoutKeyboard();
        isSaving = false;
        return;
    }

    const invoiceRaw = localStorage.getItem("invoiceDataLearOut");
    if (invoiceRaw) {
        const invoice = JSON.parse(invoiceRaw);
        const incomplete = invoice.items.filter(item => item.scannedQty !== item.qty);
        if (incomplete.length > 0) {
            Utils.showAlert(m("warning.invoice.notComplete"), 'warning');
            focusWithoutKeyboard();
            isSaving = false;
            return;
        }
    }

    let data = {
        date: $("#datepicker").val(),
        barcode: barcodeList,
        source: "LOAD",
        main: "OUT",
        kind: "LOAD",
        storage: $(".storage-select").val(),
        shipTo: $(".shipto-select").val(),
        factory: localStorage.getItem('rememberedFactory'),
        memo: invoiceRaw ? JSON.parse(invoiceRaw).manifestNo : "",
    }

    Utils.showConfirm(m("confirm.send.all"), () => {
            showLoading();
            $.ajax({
                url: `/purchase/insOutput`,
                method: 'POST',
                contentType: "application/json",
                data: JSON.stringify(data),
                success: function (result) {
                    let response = result.response;
                    console.log("response : " + response)
                    if (response === "success") {
                        localStorage.removeItem('partBarcodeListLearOut');
                        localStorage.removeItem('invoiceDataLearOut');
                        $("#dataTableBody").empty();
                        $("#count").text("0");
                        $('#invoiceManifestNo').text('-');
                        $('#invoiceTotalBox').text('-');
                        $('#invoiceTotalQty').text('-');
                        Utils.showAlert(m("info.barcode.sent"), "info");
                        playSound('complete');
                    } else {
                        const barcodeHtml = makeBarcodesClickable(result.barcode);
                        showAlert("", barcodeHtml + `<br>${m(result.response)}`, "warning");
                        highlightErrorRowsByItemCode(result.barcode);

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

    const savedInvoice = localStorage.getItem("invoiceDataLearOut");
    if (savedInvoice) {
        const invoiceInfo = JSON.parse(savedInvoice);
        if (invoiceInfo.items && invoiceInfo.items.length > 0) {
            table.empty();
            invoiceInfo.items.forEach((item, idx) => {
                const scanned = item.scannedQty ?? 0;
                const bgColor = scanned === item.qty ? 'background-color:#c8f5c8;'
                              : scanned > 0           ? 'background-color:#fff3cd;'
                              :                         '';
                table.append(`<tr data-index="${idx}" data-itemcode="${item.itemCode}" style="${bgColor}cursor:pointer;">
                    <td>${item.itemCode}</td>
                    <td>${item.scannedQty ?? 0}/${item.qty}</td>
                </tr>`);
            });
            $("#dataTableBody").off("click", "tr[data-itemcode]").on("click", "tr[data-itemcode]", function () {
                openBarcodeModal(this.dataset.itemcode);
            });
            $("#count").text(invoiceInfo.items.length);
            updateInvoiceStats(invoiceInfo);
            return;
        }
    }

    table.empty();
    $("#count").text("0");
}


function clearAll() {			//localstorage 전체삭제
    Utils.showConfirm(m("confirm.delete.all"), () => {
        localStorage.removeItem("invoiceDataLearOut");
        localStorage.removeItem("partBarcodeListLearOut");
        $("#dataTableBody").empty();
        $("#count").text("0");
        $('#invoiceManifestNo').text('-');
        $('#invoiceTotalBox').text('-');
        $('#invoiceTotalQty').text('-');
        Utils.showAlert(m("success.deleted.all"), "success");
    })
    focusWithoutKeyboard()
}

function updateInvoiceStats(invoiceInfo) {
    const completedItems = invoiceInfo.items.filter(item => item.scannedQty === item.qty).length;
    const scannedTotalQty = invoiceInfo.items.reduce((sum, item) => sum + (item.scannedQty || 0), 0);
    $('#invoiceTotalBox').text(`${completedItems}/${invoiceInfo.totalBox}`);
    $('#invoiceTotalQty').text(`${scannedTotalQty}/${invoiceInfo.totalQty}`);
}

// LEAR 파트바코드 파싱
// Format: L003243835TVNAA,260424,00001,00144.00,WMSUSA
function parsePartBarcode(barcode) {
    const parts = barcode.split(",");
    if (parts.length !== 5) return null;
    const itemCode = parts[0];
    const qty = parseFloat(parts[3]);
    if (!itemCode || isNaN(qty)) return null;
    return { itemCode, qty, serial: parts[1] + "-" + parts[2] };
}

// 박스바코드 파싱 (_구분 6파트 형식)
// Format: xxx_xxx_xxx_itemCode_qty_xxx
function parseBoxBarcode(barcode) {
    const parts = barcode.split("_");
    if (parts.length !== 6) return null;
    const itemCode = parts[3];
    const qty = parseFloat(parts[4]);
    if (!itemCode || isNaN(qty)) return null;
    return { itemCode, qty, serial: parts[0] + "-" + parts[2] };
}

function parseAnyPartBarcode(barcode) {
    return parsePartBarcode(barcode) || parseBoxBarcode(barcode);
}

// 리어 인보이스 바코드 파싱
// Format: [)>$$LEAR$$WBTA20260420_#1$$L002231880NCPAA:1$$L002556779NCPAB:48$$...
function parseInvoiceBarcode(barcode) {
    const parts = barcode.split("$$");
    // parts[0]='[)>', parts[1]='ADNT', parts[2]=manifestNo, parts[3..]=items
    if (parts.length < 4) return null;
    const manifestNo = parts[2];

    let totalQty = 0;
    const items = [];
    for (let i = 3; i < parts.length; i++) {
        const part = parts[i];
        const colonIdx = part.indexOf(":");
        if (colonIdx === -1) continue;
        // L{품번}:{수량} - L포함 전체가 품번
        const itemCode = part.substring(0, colonIdx);
        const qty = parseInt(part.substring(colonIdx + 1), 10) || 0;
        if (!itemCode) continue;
        totalQty += qty;
        items.push({ itemCode, qty, scannedQty: 0 });
    }

    if (items.length === 0) return null;
    return { manifestNo, totalBox: items.length, totalQty, items };
}

function openBarcodeModal(itemCode) {
    const allBarcodes = JSON.parse(localStorage.getItem("partBarcodeListLearOut") || "[]");
    const matched = allBarcodes.filter(bc => {
        const parsed = parseAnyPartBarcode(bc);
        return parsed && parsed.itemCode === itemCode;
    });

    $("#barcodeModalTitle").text(itemCode);
    const tbody = $("#barcodeModalBody").empty();

    if (matched.length === 0) {
        tbody.append(`<tr><td colspan="3" style="text-align:center;color:#9ca3af;">스캔된 바코드 없음</td></tr>`);
    } else {
        matched.forEach(bc => {
            const parsed = parseAnyPartBarcode(bc);
            const serial = (parsed && parsed.serial) ? parsed.serial : bc;
            const encodedBc = encodeURIComponent(bc);
            tbody.append(`<tr>
                <td style="font-size:12px;word-break:break-all;">${serial}</td>
                <td style="text-align:center;">${parsed ? parsed.qty : '-'}</td>
                <td><button class="delete-btn" onclick="deleteBarcodeFromItem(decodeURIComponent('${encodedBc}'), '${itemCode}')">삭제</button></td>
            </tr>`);
        });
    }
    $("#barcodeListModal").css("display", "flex");
}

function closeBarcodeModal() {
    $("#barcodeListModal").css("display", "none");
}

function deleteBarcodeFromItem(barcode, itemCode) {
    Utils.showConfirm(m("confirm.delete.item"), () => {
        const parsed = parseAnyPartBarcode(barcode);

        let allBarcodes = JSON.parse(localStorage.getItem("partBarcodeListLearOut") || "[]");
        allBarcodes = allBarcodes.filter(bc => bc !== barcode);
        localStorage.setItem("partBarcodeListLearOut", JSON.stringify(allBarcodes));

        const invoiceRaw = localStorage.getItem("invoiceDataLearOut");
        if (invoiceRaw && parsed) {
            const invoice = JSON.parse(invoiceRaw);
            const item = invoice.items.find(it => it.itemCode === itemCode);
            if (item) {
                item.scannedQty = Math.max(0, (item.scannedQty || 0) - parsed.qty);
                localStorage.setItem("invoiceDataLearOut", JSON.stringify(invoice));
            }
        }

        renderTable();
        openBarcodeModal(itemCode);
    });
}

function highlightErrorRowsByItemCode(errorBarcodes) {
    if (!Array.isArray(errorBarcodes) || errorBarcodes.length === 0) return;
    const itemCodes = new Set(
        errorBarcodes.map(bc => {
            const parsed = parseAnyPartBarcode(bc);
            return parsed ? parsed.itemCode : null;
        }).filter(Boolean)
    );
    if (itemCodes.size === 0) return;
    $("#dataTableBody tr[data-itemcode]").each(function () {
        if (itemCodes.has(this.dataset.itemcode)) {
            $(this).css("background-color", "#ffcccc");
            this.scrollIntoView({ behavior: "smooth", block: "center" });
        }
    });
}
