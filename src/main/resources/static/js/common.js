// 인터넷 연결 확인 + 안 되면 안드로이드 알림창 띄우고 false 반환
function checkNetwork() {
	if (window.AndroidInterface && !AndroidInterface.isNetworkConnected()) {
		AndroidInterface.showNoInternetDialog();
		return false;
	}
	return true;
}
let inputMode = "readonly";
$(document).on("touchstart click", "#barcodeInput", function() {
	$(this).removeAttr("readonly");
	inputMode = 'manual';
});

$("#barcodeInput").on("keydown", function(e) {
	if (e.key === "Enter") {
		if (inputMode == 'manual') {
			let page = $('body').data('page');
			if (page == 'addEntry') {
				addEntry(); // 바코드 처리 로직
			} else if (page == 'findPallet') {
				findPallet();
			} else if (page == 'history') {
				selectList();
			}
			$(this).attr("readonly", true);
			inputMode = 'readonly';
		}

	}
});
// 안드로이드에서 바코드 스캔값 가져옴
function handleBarcode(barcode) {
	if ($(".popupLoading").is(":visible") || Swal.isVisible()) {	// 데이터 처리중이면 리스트에 추가 X
		$("#barcodeInput").val("");
	} else {
		//지웅
		if (inputMode == 'readonly') {
			$("#barcodeInput").val(barcode);
			let page = $('body').data('page');
			if (page == 'addEntry') {
				addEntry(); // 바코드 처리 로직
			} else {
				findPallet();
			}
		} else {
		}
	}
}



// alert창 대체
function showAlert(title, text, icon) {
	Swal.fire({
		title: title,
		html: text,
		confirmButtonText: 'Ok',
		confirmButtonColor: '#3085d6',
	});
}

// confirm창 대체
function showConfirm(title, text, confirmText, cancelText, onConfirm, onCancel) {
	Swal.fire({
		text: text,
		showCancelButton: true,
		confirmButtonText: confirmText,
		cancelButtonText: cancelText,
		reverseButtons: true
	}).then((result) => {
		if (result.isConfirmed && typeof onConfirm === 'function') {
			onConfirm(); // 확인 시 실행할 함수
		} else if (result.dismiss === Swal.DismissReason.cancel) {
			onCancel(); // 취소 시 실행할 함수
		}
	});
}

// 토스트 창
let toastTimer;
function showToast(message, color, barcode, duration = 2500) {
	const toast = document.getElementById("toast");
	let toastClass = document.querySelector(".toast");
	toast.innerHTML = message + "<br>" + "<br>" + barcode;
	toast.classList.add("show");
	if (color) {
		toastClass.style.backgroundColor = color;  // 예: "red", "#ffffff"
	}
	if (color == '#FF0000') {
		duration = 5000;
	}

	if (toastTimer) clearTimeout(toastTimer);
	toastTimer = setTimeout(() => {
		toast.classList.remove("show");
		toastClass.style.backgroundColor = "";
	}, duration);
}

// 키보드 내리기
function focusWithoutKeyboard() {
	const input = document.getElementById("barcodeInput");
	input.blur();
	input.setAttribute("readonly", true); // 항상 readonly 유지
	//input.focus();
	/*setTimeout(() => {
		input.removeAttribute("readonly");  // 150ms 후 readonly 해제 → 자판 안 뜸
	  }, 150);*/
}










