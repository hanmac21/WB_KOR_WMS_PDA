let partScanned  = false;   // 부품(타 업체) 라벨 스캔 여부
let partItemcode = '';      // 대차와 비교할 품번 (부품 품번에서 앞 1글자 제거)
let manualTouch  = false;

// 전용 키
const PART_STATE_KEY   = 'partStateTransysOut';    // 부품 라벨 상태
const BARCODE_LIST_KEY = 'carrierListTransysOut';  // 스캔한 대차 바코드 목록(전송 대상)

$(document).ready(function () {
    hideLoading();

    function dateFormat(date) {
        let month = date.getMonth() + 1;
        let day   = date.getDate();
        month = month >= 10 ? month : '0' + month;
        day   = day   >= 10 ? day   : '0' + day;
        return date.getFullYear() + '-' + month + '-' + day;
    }

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
        onClose: function () {
            focusWithoutKeyboard();
            if ($(window.event.target).hasClass('ui-datepicker-current')) {
                $(this).datepicker('setDate', new Date());
            }
        }
    });
    $("#datepicker").datepicker();
    $("#datepicker").datepicker("setDate", new Date());

    $(document).on('click', '.ui-datepicker-current', function () {
        $("#datepicker").datepicker("setDate", new Date());
        if (typeof focusWithoutKeyboard === 'function') {
            $("#datepicker").datepicker("hide");
            focusWithoutKeyboard();
        }
    });

    // 스캐너 포커스 처리
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
                    if (!manualTouch) focusWithoutKeyboard();
                    manualTouch = false;
                }
            }, 500);
        }
    });

    // 이전 데이터 복원
    restoreState();
});

// ===== 파싱 =====

// 부품(타 업체) 라벨 : 공백 구분
// 예) AAIY264004  T89370J6000VNB  300SM2W  20260511 ...
function parsePartBarcode(barcode) {
    const parts = barcode.trim().split(/\s+/);
    if (parts.length < 3) return null;

    const itemcode = parts[1].substring(1);   // T89370J6000VNB → 89370J6000VNB (앞 1글자 제거)
    const qtyMatch = parts[2].match(/^\d+/);   // 300SM2W → 300
    if (!itemcode || !qtyMatch) return null;

    return { barcode, itemcode, qty: parseInt(qtyMatch[0], 10) };
}

// 대차 라벨 : 콤마 구분, 끝이 WBT
// 예) JK,89370J6000VNB,A301255720122,00100,P26070700001,WBT
function parseCarrierBarcode(barcode) {
    const parts = barcode.split(',');
    if (parts.length < 6) return null;
    const itemcode = parts[1];         // 89370J6000VNB
    const qty      = Number(parts[3]); // 00100 → 100
    return { barcode, itemcode, qty };
}

// 대차 목록의 수량 합계
function sumCarrierQty(list) {
    return list.reduce(function (sum, bc) {
        const info = parseCarrierBarcode(bc);
        return sum + (info ? (Number(info.qty) || 0) : 0);
    }, 0);
}

// ===== 상태 저장/복원 =====

function savePartState() {
    localStorage.setItem(PART_STATE_KEY, JSON.stringify({
        partScanned,
        partItemcode,
        barcode  : $('#barcode').text(),
        itemcode : $('#itemcode').text(),
        nowqty   : $('#nowqty').text(),
        targetQty: $('#targetQty').text()
    }));
}

function restoreState() {
    const raw = localStorage.getItem(PART_STATE_KEY);
    if (!raw) return;
    const state = JSON.parse(raw);
    if (!state.partScanned) return;

    partScanned  = true;
    partItemcode = state.partItemcode;

    showPartInfo({ barcode: state.barcode, itemcode: state.itemcode, qty: state.nowqty });
    $('#targetQty').text(state.targetQty);

    const list = JSON.parse(localStorage.getItem(BARCODE_LIST_KEY) || '[]');
    $('#count').text(sumCarrierQty(list));   // 대차 수량 합계
    $('#carrierCount').text(list.length);    // 대차 건수

    checkMatch(true);
}

// ===== 표시 =====

// 부품(타 업체) 라벨 정보 표시
function showPartInfo(info) {
    $('#barcode').text(info.barcode);
    $('#itemcode').text(info.itemcode);
    $('#nowqty').text(Number(info.qty));
    $('#targetQty').text(Number(info.qty));   // 목표수량 = 부품 수량

    $('#carrierEmpty').hide();
    $('#carrierInfoGrid').show();
    $('#carrierCard').addClass('filled');
    $('#carrierCardHeader').addClass('filled');

    $('#scanGuide').hide();
}

function checkMatch(silent = false) {
    const scanQty   = parseInt($('#count').text()) || 0;      // 대차 수량 합계
    const targetQty = parseInt($('#targetQty').text()) || 0;  // 부품 수량
    const matched   = targetQty > 0 && scanQty === targetQty;

    $('#targetBox, #scanBox').toggleClass('match', matched);
    if (matched) {
        if (!silent) playSound('ok');
        $('#barcodeInput').prop('disabled', true);
        $('#scanBtn').prop('disabled', true);
    }
}

// ===== 스캔 =====

function addEntry() {
    const barcode = $("#barcodeInput").val().trim();
    if (!barcode) return;

    if (!partScanned) {
        // STEP 1 : 부품(타 업체) 바코드
        const info = parsePartBarcode(barcode);
        if (!info || !info.itemcode || !info.qty) {
            playSound('error');
            Utils.showAlert(m("warning.barcode.invalid") + "<br>" + m("warning.check"), "warning", barcode);
            $("#barcodeInput").val('');
            return;
        }
        partItemcode = info.itemcode;
        partScanned  = true;
        showPartInfo(info);
        savePartState();
        $("#barcodeInput").val('');

    } else {
        // STEP 2 : 대차(WBT) 바코드
        const targetQty  = parseInt($('#targetQty').text()) || 0;
        const currentSum = parseInt($('#count').text()) || 0;

        // 이미 목표 수량 충족
        if (targetQty > 0 && currentSum >= targetQty) {
            playSound('error');
            Utils.showAlert(m("warning.qty.exceed"), "warning", barcode);
            $("#barcodeInput").val('');
            return;
        }

        // 대차 형식 확인
        if (!(barcode.includes(',') && barcode.endsWith('WBT'))) {
            playSound('error');
            Utils.showAlert(m("warning.barcode.invalid") + "<br>" + m("warning.check"), "warning", barcode);
            $("#barcodeInput").val('');
            return;
        }

        const cart = parseCarrierBarcode(barcode);
        if (!cart || !cart.itemcode) {
            playSound('error');
            Utils.showAlert(m("warning.barcode.invalid") + "<br>" + m("warning.check"), "warning", barcode);
            $("#barcodeInput").val('');
            return;
        }

        // 품번 일치 확인 (대차 품번 == 부품 품번)
        if (cart.itemcode !== partItemcode) {
            playSound('error');
            showAlert("", m("warning.barcode.itemcode.mismatch") + "<br><span style = 'color:red'> " + cart.itemcode + "</span>", "warning");
            $("#barcodeInput").val('');
            return;
        }

        // 중복 확인
        const stored = JSON.parse(localStorage.getItem(BARCODE_LIST_KEY) || '[]');
        if (stored.includes(barcode)) {
            playSound('error2');
            Utils.showAlert(m("warning.barcode.duplicate") + "<br>" + m("warning.check"), "warning", barcode);
            $("#barcodeInput").val('');
            return;
        }

        // 수량 초과 확인 (합계가 목표를 넘으면 거부)
        const cartQty = Number(cart.qty) || 0;
        if (currentSum + cartQty > targetQty) {
            playSound('error');
            Utils.showAlert(m("warning.qty.exceed"), "warning", barcode);
            $("#barcodeInput").val('');
            return;
        }

        // 저장 및 갱신
        stored.push(barcode);
        localStorage.setItem(BARCODE_LIST_KEY, JSON.stringify(stored));

        $('#count').text(currentSum + cartQty);   // 대차 수량 합계
        $('#carrierCount').text(stored.length);   // 대차 건수
        playSound('complete');
        $("#barcodeInput").val('');
        checkMatch();
    }
}

// ===== 전송 =====

function saveBarcode() {
    if (isSaving) return;

    const carrierBarcodes = JSON.parse(localStorage.getItem(BARCODE_LIST_KEY) || '[]');
    if (carrierBarcodes.length === 0) {
        Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
        focusWithoutKeyboard();
        return;
    }

    // DB 전송: 스캔한 "대차 바코드 리스트"
    let data = {
        date   : $("#datepicker").val(),
        barcode: carrierBarcodes,
        source : "LOAD",
        main   : "OUT",
        kind   : "LOAD",
        shipTo : $(".shipto-select").val(),
        factory: localStorage.getItem('rememberedFactory'),
        memo   : ""
    };

    const scanQty   = parseInt($('#count').text()) || 0;      // 대차 수량 합계
    const targetQty = parseInt($('#targetQty').text()) || 0;  // 부품 수량
    const matched   = targetQty > 0 && scanQty === targetQty;
    const confirmMsg = matched
        ? m("confirm.send.all")
        : mf("confirm.send.qty.mismatch", targetQty, scanQty).replace('\\n', '<br>');

    Utils.showConfirm(confirmMsg, () => {
            isSaving = true;
            showLoading();
            $.ajax({
                url        : '/ulsan/insOutput',
                method     : 'POST',
                contentType: 'application/json',
                data       : JSON.stringify(data),
                success: function (res) {
                    if (res.response === 'success') {
                        Utils.showAlert(m("info.barcode.sent"), "info");
                        playSound('complete');
                        clearAll();
                    } else {
                        playSound('error');
                        Utils.showAlert(res.message || m('error.generic'), 'warning', '');
                    }
                    hideLoading();
                },
                error: function (xhr) {
                    hideLoading();
                    playSound('error');
                    if (xhr.status === 401) {
                        Utils.showAlert("Your session has expired. Please log in again.", 'warning');
                        window.location.href = "/login";
                    } else if (xhr.status === 0) {
                        Utils.showAlert(m("error.offline"), 'warning');
                    } else {
                        Utils.showAlert(m('error.generic'), 'warning', '');
                    }
                },
                complete: function () {
                    hideLoading();
                    isSaving = false;
                }
            });
        },
        () => {
            Utils.showAlert(m("success.cancel.sendAll"), "#008000");
        });
}

function clearAll() {
    partScanned  = false;
    partItemcode = '';

    localStorage.removeItem(BARCODE_LIST_KEY);
    localStorage.removeItem(PART_STATE_KEY);

    $('#barcode, #itemcode, #nowqty').text('-');
    $('#carrierEmpty').show();
    $('#carrierInfoGrid').hide();
    $('#carrierCard').removeClass('filled');
    $('#carrierCardHeader').removeClass('filled');

    $('#scanGuide').show();

    $('#count').text(0);
    $('#carrierCount').text(0);
    $('#targetQty').text('-');
    $('#targetBox, #scanBox').removeClass('match');
    $('#barcodeInput').prop('disabled', false);
    $('#scanBtn').prop('disabled', false);
    $('#lastResult').removeClass('ok ng').hide();
    $("#barcodeInput").val('');
    focusWithoutKeyboard();
}