let carrierScanned = false;
let carrierItemcode = '';

const CARRIER_STATE_KEY = 'carrierStateValidationTransys';

$(document).ready(function() {
	hideLoading();
	restoreState();
});

// 대차 정보 저장
function saveCarrierState() {
	localStorage.setItem(CARRIER_STATE_KEY, JSON.stringify({
		carrierScanned,
		carrierItemcode,
		barcode  : $('#barcode').text(),
		itemcode : $('#itemcode').text(),
		nowqty   : $('#nowqty').text(),
		targetQty: $('#targetQty').text()
	}));
}

// 기존데이터 복원
function restoreState() {
	const raw = localStorage.getItem(CARRIER_STATE_KEY);
	if (!raw) return;
	const state = JSON.parse(raw);
	if (!state.carrierScanned) return;

	carrierScanned  = true;
	carrierItemcode = state.carrierItemcode;

	showCarrierInfo({ barcode: state.barcode, itemcode: state.itemcode, qty: state.nowqty });
	$('#targetQty').text(state.targetQty);

	const list = JSON.parse(localStorage.getItem('barcodeListValidationTransys') || '[]');
	$('#count').text(list.length);

	checkMatch(true);
}

function parseCarrierBarcode(barcode) {
	const parts = barcode.split(',');
	if (parts.length < 6) return null;
	const itemcode = parts[1];   // P89900XG100WK → 89900XG100WK
	const qty      = parts[3];   // 5Q15 → 15
	return { barcode, itemcode, qty };
}

function parsePartBarcode(barcode) {
	const parts = barcode.split('|');
	if (parts.length < 4) return null;
	const itemcode = parts[3].substring(1);   // P89900R5000MDQ → 89900R5000MDQ
	return { itemcode };
}

function showCarrierInfo(info) {
	$('#barcode').text(info.barcode);
	$('#itemcode').text(info.itemcode);
	$('#nowqty').text(Number(info.qty));
	$('#targetQty').text(Number(info.qty));

	$('#carrierEmpty').hide();
	$('#carrierInfoGrid').show();
	$('#carrierCard').addClass('filled');
	$('#carrierCardHeader').addClass('filled');

	$('#step1Badge').removeClass('active').addClass('done');
	$('#step1Label').removeClass('active').addClass('done');
	$('#stepLine').addClass('done');
	$('#step2Badge').removeClass('inactive').addClass('active');
	$('#step2Label').removeClass('inactive').addClass('active');
}

function checkMatch(silent = false) {
	const scanQty   = parseInt($('#count').text()) || 0;
	const targetQty = parseInt($('#targetQty').text()) || 0;
	const matched   = targetQty > 0 && scanQty === targetQty;

	$('#targetBox, #scanBox').toggleClass('match', matched);
	if (matched) {
		if (!silent) playSound('ok');
		$('#barcodeInput').prop('disabled', true);
		$('#scanBtn').prop('disabled', true);
	}
	if (scanQty > 0) {
		$('#saveBtn').prop('disabled', false);
	}
}

function addEntry() {
	const barcode = $("#barcodeInput").val().trim();
	if (!barcode) return;

	if (!carrierScanned) {
		// STEP 1 : 대차 바코드
		if (barcode.includes(',') && barcode.endsWith('WBT')) {
			const info = parseCarrierBarcode(barcode);
			if (!info || !info.itemcode) {
				playSound('error');
				Utils.showAlert(m("warning.barcode.invalid") + "<br>" + m("warning.check"), "warning", barcode);
				$("#barcodeInput").val('');
				return;
			}
			carrierItemcode = info.itemcode;
			carrierScanned  = true;
			showCarrierInfo(info);
			saveCarrierState();
			$("#barcodeInput").val('');
		} else {
			playSound('error');
			Utils.showAlert(m("warning.barcode.invalid") + "<br>" + m("warning.check"), "warning", barcode);
			$("#barcodeInput").val('');
		}
	} else {
		// STEP 2 : 부품 바코드
		const targetQty = parseInt($('#targetQty').text()) || 0;
		const currentQty = parseInt($('#count').text()) || 0;
		if (targetQty > 0 && currentQty >= targetQty) {
			playSound('error');
			Utils.showAlert(m("warning.qty.exceed"), "warning", barcode);
			$("#barcodeInput").val('');
			return;
		}

		if (!barcode.includes('|')) {
			playSound('error');
			Utils.showAlert(m("warning.barcode.invalid") + "<br>" + m("warning.check"), "warning", barcode);
			$("#barcodeInput").val('');
			return;
		}

		const part = parsePartBarcode(barcode);
		// 바코드에서 품번을 못가져오면
		if (!part) {
			playSound('error');
			Utils.showAlert(m("warning.barcode.invalid") + "<br>" + m("warning.check"), "warning", barcode);
			$("#barcodeInput").val('');
			return;
		}

		// 품번 일치 확인
		if (part.itemcode !== carrierItemcode) {
			playSound('error');
			showAlert("",m("warning.barcode.itemcode.mismatch") + "<br><span style = 'color:red'> " +part.itemcode+"</span>", "warning");
			$("#barcodeInput").val('');
			return;
		}

		// 중복 확인
		const stored = JSON.parse(localStorage.getItem('barcodeListValidationTransys') || '[]');
		if (stored.includes(barcode)) {
			playSound('error2');
			Utils.showAlert(m("warning.barcode.duplicate") + "<br>" + m("warning.check"), "warning", barcode);
			$("#barcodeInput").val('');
			return;
		}

		// localStorage 저장 및 카운트 증가
		stored.push(barcode);
		localStorage.setItem('barcodeListValidationTransys', JSON.stringify(stored));

		const current = parseInt($('#count').text()) || 0;
		$('#count').text(current + 1);
		playSound('complete');
		$("#barcodeInput").val('');
		checkMatch();
	}
}

function clearAll() {
	carrierScanned  = false;
	carrierItemcode = '';

	localStorage.removeItem('barcodeListValidationTransys');
	localStorage.removeItem(CARRIER_STATE_KEY);

	$('#barcode, #itemcode, #itemname, #nowqty').text('-');
	$('#carrierEmpty').show();
	$('#carrierInfoGrid').hide();
	$('#carrierCard').removeClass('filled');
	$('#carrierCardHeader').removeClass('filled');

	$('#step1Badge').removeClass('done').addClass('active');
	$('#step1Label').removeClass('done').addClass('active');
	$('#stepLine').removeClass('done');
	$('#step2Badge').removeClass('active').addClass('inactive');
	$('#step2Label').removeClass('active').addClass('inactive');

	$('#count').text(0);
	$('#targetQty').text('-');
	$('#targetBox, #scanBox').removeClass('match');
	$('#barcodeInput').prop('disabled', false);
	$('#scanBtn').prop('disabled', false);
	$('#saveBtn').prop('disabled', true);
	$('#lastResult').removeClass('ok ng').hide();
	$("#barcodeInput").val('');
	focusWithoutKeyboard();
}

function validationSave() {
	focusWithoutKeyboard();
	const assyBarcodes = JSON.parse(localStorage.getItem('barcodeListValidationTransys') || '[]');
	if (assyBarcodes.length === 0) return;

	const data = {
		cartBarcode : $('#barcode').text(),
		cartQty     : $('#targetQty').text(),
		assyQty     : $('#count').text(),
		oitemcode    : carrierItemcode,
		factory     : localStorage.getItem('rememberedFactory') || '',
		assyBarcodes: assyBarcodes,
		source      : 'TRANSYSCART'
	};

	const scanQty   = parseInt($('#count').text()) || 0;
	const targetQty = parseInt($('#targetQty').text()) || 0;
	const matched   = targetQty > 0 && scanQty === targetQty;
	const confirmMsg = matched
		? m("confirm.send.all")
		: mf("confirm.send.qty.mismatch", targetQty, scanQty).replace('\\n', '<br>');

	Utils.showConfirm(confirmMsg, () => {
        showLoading();
        $.ajax({
            url        : '/ulsan/validation/save',
            method     : 'POST',
            contentType: 'application/json',
            data       : JSON.stringify(data),
            success: function(res) {
                hideLoading();
                if (res.response === 'success') {
                    Utils.showAlert(m("info.barcode.sent"), "info");
                    clearAll();
                } else {
                    playSound('error');
                    Utils.showAlert(res.message || m('error.generic'), 'warning', '');
                }
            },
            error: function(xhr) {
                hideLoading();
                playSound('error');
                Utils.showAlert(m('error.generic'), 'warning', '');
            }
        });
    },
    () => {
        Utils.showAlert(m("success.cancel.sendAll"), "#008000");
    })
}
