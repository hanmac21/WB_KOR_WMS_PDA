let inputMode = "readonly";
let isConfirmVisible = false;
let isSaving = false;					// 버튼 빠르게 두번 눌렀을때 두번째 데이터 전송 막기위함

window.WMS = {							// 어떤 대메뉴로 들어갔는지 저장
    currentModule: null,
    /*moduleData: {						// 추후 필요하면 사용
        purchase: {},
        sales: {},
        production: {},
        quality: {}
    }*/
};

$(document).ready(function () {
    // 로그인 후 원래 페이지로 돌아오게 현재 URL을 redirect 파라미터로 보냄
    var fallbackLogin = '/login?expired=true&redirect=' + encodeURIComponent(location.pathname + location.search);

    // 모든 AJAX 요청에 Ajax 헤더 부착 + 401 전역 처리
    $.ajaxSetup({
        headers: {'X-Requested-With': 'XMLHttpRequest'},
        statusCode: {
            401: function (xhr) {
                // 401이 중복 발생할 때 무한호출 방지
                if (window.__redirecting401) return;
                window.__redirecting401 = true;

                var to = xhr.getResponseHeader('X-Login-Redirect') || fallbackLogin;
                window.location.replace(to);
            }
        }
    });

    // (백업) 혹시 다른 경로로 401이 떨어질 때
    $(document).ajaxError(function (event, jqXHR) {
        if (jqXHR.status === 401) {
            if (window.__redirecting401) return;
            window.__redirecting401 = true;

            var to = jqXHR.getResponseHeader('X-Login-Redirect') || fallbackLogin;
            window.location.replace(to);
        }
    });

    hideLoading();
});


window.showLoading = showLoading;
window.hideLoading = hideLoading;

// utils/common.js - 공통 유틸리티
class Utils {
    // 알림 메시지 표시
    static showAlert(message, type = 'info') {
        // 기존 알림이 있다면 제거
        const existingAlert = document.querySelector('.custom-alert');
        if (existingAlert) {
            existingAlert.remove();
        }

        const alert = document.createElement('div');
        alert.className = `custom-alert alert-${type}`;
        alert.innerHTML = `
            <div class="alert-content">
                <span class="alert-message">${message}</span>
                <button class="alert-close">&times;</button>
            </div>
        `;

        // 스타일 적용
        alert.style.cssText = `
            position: fixed;
            bottom: 80px;
            left: 15px;
            right: 15px;
            background: ${this.getAlertColor(type)};
            color: white;
            padding: 15px;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            z-index: 2000;
            animation: slideDown 0.3s ease;
			word-wrap: break-word; /* 긴 단어 줄바꿈 */
	        word-break: break-all; /* 바코드같은 긴 문자열 강제 줄바꿈 */
	        max-width: calc(100vw - 30px); /* 화면 너비 초과 방지 */
        `;

        const alertContent = alert.querySelector('.alert-content');
        alertContent.style.cssText = `
            display: flex;
            justify-content: space-between;
            align-items: center;
        `;

        const closeBtn = alert.querySelector('.alert-close');
        closeBtn.style.cssText = `
            background: none;
            border: none;
            color: white;
            font-size: 20px;
            cursor: pointer;
            padding: 0;
            margin-left: 10px;
        `;

        // 스타일시트에 애니메이션 추가
        if (!document.querySelector('#alertStyles')) {
            const style = document.createElement('style');
            style.id = 'alertStyles';
            style.textContent = `
                @keyframes slideDown {
                    from {
                        opacity: 0;
                        transform: translateY(-30px);
                    }
                    to {
                        opacity: 1;
                        transform: translateY(0);
                    }
                }
                
                @keyframes slideUp {
                    from {
                        opacity: 1;
                        transform: translateY(0);
                    }
                    to {
                        opacity: 0;
                        transform: translateY(-30px);
                    }
                }
            `;
            document.head.appendChild(style);
        }

        document.body.appendChild(alert);

        // 닫기 버튼 이벤트
        closeBtn.addEventListener('click', () => {
            this.hideAlert(alert);
        });

        // 3초 후 자동 닫기
        setTimeout(() => {
            if (document.body.contains(alert)) {
                this.hideAlert(alert);
            }
        }, 3000);
    }

    static hideAlert(alert) {
        alert.style.animation = 'slideUp 0.3s ease';
        setTimeout(() => {
            if (document.body.contains(alert)) {
                alert.remove();
            }
        }, 300);
    }

    static getAlertColor(type) {
        const colors = {
            'info': '#3b82f6',
            'success': '#059669',
            'warning': '#ea580c',
            'error': '#dc2626'
        };
        return colors[type] || colors.info;
    }

    static showConfirmWithImage(src, message, onConfirm, onCancel = null, options = {}) {
        const {
            width = 420,                // 모달 최대 가로 폭
            imageMaxHeight = 240,       // 이미지 최대 높이
            objectFit = 'contain',      // cover | contain | fill
            overlayClose = false,       // 오버레이 클릭으로 닫기 허용
            alt = 'preview',            // 이미지 대체 텍스트
        } = options;

        const modal = document.createElement('div');
        isConfirmVisible = true;
        modal.className = 'custom-modal';
        modal.style.cssText = `
	    position: fixed; inset: 0; z-index: 3000;
	  `;

        modal.innerHTML = `
	    <div class="modal-overlay">
	      <div class="modal-content">
	        <div class="modal-image-wrap">
	          <img class="modal-image" alt="${alt}">
	        </div>
	        <div class="modal-message"></div>
	        <div class="modal-buttons">
	          <button class="modal-btn btn-cancel">${m("btn.cancel")}</button>
	          <button class="modal-btn btn-confirm">${m("btn.confirm")}</button>
	        </div>
	      </div>
	    </div>
	  `;

        // 공통 스타일
        const overlay = modal.querySelector('.modal-overlay');
        overlay.style.cssText = `
	    width:100%; height:100%;
	    background: rgba(0,0,0,0.5);
	    display:flex; justify-content:center; align-items:center;
	    padding:20px;
	  `;

        const content = modal.querySelector('.modal-content');
        content.style.cssText = `
	    background:#fff; border-radius:8px; padding:20px;
	    width:100%; max-width:${width}px;
	    box-shadow:0 10px 25px rgba(0,0,0,0.3);
		max-height: -webkit-fill-available;
		overflow: auto;
		max-height: 550px;
		background-color: red;
	  `;

        const imgWrap = modal.querySelector('.modal-image-wrap');
        imgWrap.style.cssText = `
	    width:100%; margin:0 0 14px 0;
	    background:#f3f4f6; border-radius:6px; overflow:hidden;
		justify-items: center;
	  `;

        const imgEl = modal.querySelector('.modal-image');
        imgEl.style.cssText = `
	    display:block; width:60%;
	    max-height:${imageMaxHeight}px;
	    object-fit:${objectFit};
	    background:#f9fafb;
	  `;

        const messageEl = modal.querySelector('.modal-message');
        messageEl.style.cssText = `
	    margin: 8px 0 18px 0; color:#374151; font-size:16px; line-height:1.5; color: white;
		overflow-wrap: anywhere;
	  `;

        const buttons = modal.querySelector('.modal-buttons');
        buttons.style.cssText = `
	    display:flex; gap:10px; justify-content:flex-end;
	  `;

        const btnCancel = modal.querySelector('.btn-cancel');
        const btnConfirm = modal.querySelector('.btn-confirm');
        [btnCancel, btnConfirm].forEach(btn => {
            btn.style.cssText = `
	      padding:10px 20px; border:none; border-radius:6px;
	      cursor:pointer; font-size:14px;
	    `;
        });
        btnCancel.style.background = '#6b7280';
        btnCancel.style.color = '#fff';
        btnConfirm.style.background = '#3b82f6';
        btnConfirm.style.color = '#fff';

        // 메시지 주입 (기존 showConfirm과 동일하게 HTML 허용)
        messageEl.innerHTML = message || '';

        // 이미지 로드
        if (src) {
            imgEl.src = src;
            imgEl.addEventListener('error', () => {
                // 이미지 로드 실패 시 영역 정리
                imgWrap.style.display = 'none';
            });
        } else {
            imgWrap.style.display = 'none';
        }

        // DOM 추가
        document.body.appendChild(modal);

        // 닫기 로직
        const close = (cb) => {
            isConfirmVisible = false;
            modal.remove();
            if (cb) cb();
        };

        // 버튼 이벤트
        btnCancel.addEventListener('click', () => close(onCancel));
        btnConfirm.addEventListener('click', () => close(onConfirm));

        // 오버레이 클릭으로 닫기 (옵션)
        if (overlayClose) {
            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) close(onCancel);
            });
        }

        // 키보드 접근성: ESC=취소, Enter=확인
        const keyHandler = (e) => {
            if (!isConfirmVisible) return;
            if (e.key === 'Escape') {
                e.preventDefault();
                close(onCancel);
            }
            if (e.key === 'Enter') {
                e.preventDefault();
                close(onConfirm);
            }
        };
        document.addEventListener('keydown', keyHandler, {once: true});
    }

    // 확인 대화상자
    static showConfirm(message, onConfirm, onCancel = null) {
        const modal = document.createElement('div');
        isConfirmVisible = true;
        modal.className = 'custom-modal';
        modal.innerHTML = `
            <div class="modal-overlay">
                <div class="modal-content">
                    <div class="modal-message">${message}</div>
                    <div class="modal-buttons">
                        <button class="modal-btn btn-cancel">${m("btn.cancel")}</button>
                        <button class="modal-btn btn-confirm">${m('btn.confirm')}</button>
                    </div>
                </div>
            </div>
        `;

        // 스타일 적용
        modal.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            z-index: 3000;
        `;

        const overlay = modal.querySelector('.modal-overlay');
        overlay.style.cssText = `
            width: 100%;
            height: 100%;
            background: rgba(0,0,0,0.5);
            display: flex;
            justify-content: center;
            align-items: center;
            padding: 20px;
        `;

        const content = modal.querySelector('.modal-content');
        content.style.cssText = `
            background: white;
            border-radius: 8px;
            padding: 24px;
            max-width: 400px;
            width: 100%;
            box-shadow: 0 10px 25px rgba(0,0,0,0.3);
        `;

        const messageEl = modal.querySelector('.modal-message');
        messageEl.style.cssText = `
            margin-bottom: 20px;
            color: #374151;
            font-size: 16px;
            line-height: 1.5;
			overflow-wrap: anywhere;
        `;

        const buttons = modal.querySelector('.modal-buttons');
        buttons.style.cssText = `
            display: flex;
            gap: 10px;
            justify-content: flex-end;
        `;

        const btnCancel = modal.querySelector('.btn-cancel');
        const btnConfirm = modal.querySelector('.btn-confirm');

        [btnCancel, btnConfirm].forEach(btn => {
            btn.style.cssText = `
                padding: 10px 20px;
                border: none;
                border-radius: 6px;
                cursor: pointer;
                font-size: 14px;
            `;
        });

        btnCancel.style.background = '#6b7280';
        btnCancel.style.color = 'white';

        btnConfirm.style.background = '#3b82f6';
        btnConfirm.style.color = 'white';

        document.body.appendChild(modal);

        // 이벤트 리스너
        btnCancel.addEventListener('click', () => {
            isConfirmVisible = false; // 컨펌 종료
            modal.remove();
            if (onCancel) onCancel();
        });

        btnConfirm.addEventListener('click', () => {
            isConfirmVisible = false; // 컨펌 종료
            modal.remove();
            if (onConfirm) onConfirm();
        });

        /* overlay.addEventListener('click', (e) => {
             if (e.target === overlay) {
                 isConfirmVisible
                 modal.remove();
                 if (onCancel) onCancel();
             }
         });*/
    }

    // 날짜 포맷팅
    static formatDate(date, format = 'YYYY-MM-DD') {
        const d = new Date(date);
        const year = d.getFullYear();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        const seconds = String(d.getSeconds()).padStart(2, '0');

        return format
            .replace('YYYY', year)
            .replace('MM', month)
            .replace('DD', day)
            .replace('HH', hours)
            .replace('mm', minutes)
            .replace('ss', seconds);
    }

    // 숫자 포맷팅 (천단위 콤마)
    static formatNumber(num) {
        return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    }

    // 로컬 스토리지 헬퍼
    static getLocalData(key, defaultValue = null) {
        try {
            const data = localStorage.getItem(key);
            return data ? JSON.parse(data) : defaultValue;
        } catch (error) {
            console.error('로컬 데이터 읽기 오류:', error);
            return defaultValue;
        }
    }

    static setLocalData(key, value) {
        try {
            localStorage.setItem(key, JSON.stringify(value));
        } catch (error) {
            console.error('로컬 데이터 저장 오류:', error);
        }
    }

    // 바코드 유효성 검사
    static isValidBarcode(barcode) {
        if (!barcode || typeof barcode !== 'string') return false;

        // 일반적인 바코드 패턴 (숫자와 대문자 영문, 하이픈 허용)
        const pattern = /^[A-Z0-9\-]{6,20}$/;
        return pattern.test(barcode.trim());
    }

    // 디바이스 타입 체크
    static isMobile() {
        return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
    }

    // 네트워크 상태 체크
    static isOnline() {
        return navigator.onLine;
    }

    // 네트워크 연결 확인 (안드로이드 인터페이스 포함)
    static checkNetwork() {
        if (window.AndroidInterface && !AndroidInterface.isNetworkConnected()) {
            AndroidInterface.showNoInternetDialog();
            return false;
        }
        return true;
    }

    // 디바운스 함수
    static debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }
}

function showLoading() {						// 로딩보여주기
    document.getElementById('loading').classList.remove('hidden');
    loading.style.display = 'flex';
}

function hideLoading() {						// 로딩숨기기
    document.getElementById('loading').classList.add('hidden');
    loading.style.display = 'none';
}

function checkNetworkAndMove(url) {				// 네트워크 확인
    console.log(window.AndroidInterface);
    /*console.log(AndroidInterface.isNetworkConnected());
    if (window.AndroidInterface && !AndroidInterface.isNetworkConnected()) {
        AndroidInterface.showNoInternetDialog();
        return;
    }*/
    console.log("url : " + url);
    if (url == '/menu-purchase' || url == '/menu-sales') {
        location.href = url;
    } else {
        Utils.showAlert(m("info.developing"));
    }
}

// 인터넷 연결 확인 + 안 되면 안드로이드 알림창 띄우고 false 반환
function checkNetwork() {
    if (window.AndroidInterface && !AndroidInterface.isNetworkConnected()) {
        AndroidInterface.showNoInternetDialog();
        return false;
    }
    return true;
}

// 모든 ajax 요청 전에 네트워크 체크
$.ajaxSetup({
    beforeSend: function () {
        if (!checkNetwork()) {
            return false; // 요청 중단
        }
    }
});
/*$(document).on("touchstart click", "#barcodeInput", function () {
    $(this).removeAttr("readonly");
    inputMode = 'manual';
});*/


// 지웅 - 바코드 직접입력 테스트용 배포시 주석 해제
/*$("#barcodeInput").on("keydown", function(e) {
  if (e.key === "Enter") {
      if(inputMode == 'manual'){
          let page = $('body').data('page');
          if(page == 'addEntry' || page =='addEntryLocation'){
              addEntry(); // 바코드 처리 로직
          }else if(page == 'findPallet'){
              findPallet();
          }else if(page == 'history'){
            selectList();
          }
          $(this).attr("readonly", true);
          inputMode = 'readonly';
      }

  }
});*/

// 안드로이드에서 바코드 스캔값 가져옴
function handleBarcode(barcode) {
    console.log("스캔값 : " + barcode);
    barcode = barcode.replace(/(\r\n|\n|\r)/g, "");
    if ($(".popupLoading").is(":visible") || isConfirmVisible) {	// 데이터 처리중이면 리스트에 추가 X
        $("#barcodeInput").val("");
        console.log("잘못들어옴");
        return;
    } else {
        if (inputMode == 'readonly') {
            console.log(" readonly" + barcode);
            /*if ($("#qty").length && $("#qty").val() !== '') {
                $("#location").val(barcode);
            }else{
                $("#barcodeInput").val(barcode);
                let page = $('body').data('page');
                if(page == 'addEntry'){
                    addEntry(); // 바코드 처리 로직
                }else{
                    findPallet();
                }
            }*/

            let value = barcode.split("-");
            let page = $('body').data('page');

            console.log("250903" + page)

            if (barcode.charAt(13) === '_' && page.includes('stock-info')) {
                console.log("14번째 자리가 '-' 입니다.");
                let itemcode = barcode.split("_")[0];
                $("#barcodeInput").val(itemcode);
                playSound("complete")
                addEntry(); // 바코드 처리 로직
            } else if (barcode.split("-").length == 6 || barcode.split("-").length == 5) {
                if (page.includes('stock-info')) {
                    $("#barcodeInput").val(barcode);
                    addEntry(); // 바코드 처리 로직
                }
                console.log("251224")
                const location1 = $("#locationPart1").val();
                const location2 = $("#locationPart2").val();
                const location3 = $("#locationPart3").val();
                const location4 = $("#locationPart4").val();
                const location5 = $("#locationPart5").val();
                const location6 = $("#locationPart6").val();
                // 6자리 기준으로 반복해서 채우기
                for (let i = 0; i < 6; i++) {
                    $("#locationPart" + (i + 1)).val(value[i] !== undefined ? value[i] : "");
                    console.log("251224 : " + value[i])
                }

                for (let i = 0; i < 5; i++) {
                    $("#workLocationPart" + (i + 1)).val(value[i] !== undefined ? value[i] : "");
                }
                playSound('complete')

                const currentColor = $('.location-select').css("background-color");
                // 색상 변경
                if (currentColor === "rgb(255, 255, 255)" || currentColor === "rgba(0, 0, 0, 0)") {			// 배경이 흰색이면 푸른색으로 변경
                    $(".location-select").css({
                        "background-color": "#53abd9",
                        "color": "black"
                    });
                } else if (currentColor === "rgb(83, 171, 217)") {											// 배경이 푸른색이면 붉은색으로 변경
                    if (location1 != $("#locationPart1").val()) {
                        $("#locationPart1").css({
                            "color": "red"
                        });
                    } else {
                        $("#locationPart1").css({
                            "color": "black"
                        });
                    }
                    if (location2 != $("#locationPart2").val()) {
                        $("#locationPart2").css({
                            "color": "red"
                        });
                    } else {
                        $("#locationPart2").css({
                            "color": "black"
                        });
                    }
                    if (location3 != $("#locationPart3").val()) {
                        $("#locationPart3").css({
                            "color": "red"
                        });
                    } else {
                        $("#locationPart3").css({
                            "color": "black"
                        });
                    }
                    if (location4 != $("#locationPart4").val()) {
                        $("#locationPart4").css({
                            "color": "red"
                        });
                    } else {
                        $("#locationPart4").css({
                            "color": "black"
                        });
                    }
                    if (location5 != $("#locationPart5").val()) {
                        $("#locationPart5").css({
                            "color": "red"
                        });
                    } else {
                        $("#locationPart5").css({
                            "color": "black"
                        });
                    }
                    if (location6 != $("#locationPart6").val()) {
                        $("#locationPart6").css({
                            "color": "red"
                        });
                    } else {
                        $("#locationPart6").css({
                            "color": "black"
                        });
                    }

                }

            } else if (barcode.split("_").length == 6) { // 박스바코드 형식 일_월_연_고객사품번_수량_박스번호
                console.log("box barcode")
                $("#barcodeInput").val(barcode);
                addEntry();
            } else {
                $("#barcodeInput").val(barcode);
                addEntry(); // 바코드 처리 로직
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
        confirmButtonText: '확인',
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

// 팝업 표시
function showPopup(list) {
    const overlay = document.getElementById('popupOverlay');
    updateProductInfo(list);
    overlay.classList.add('show');
}

// 팝업 창 닫기
function closePopup() {
    const overlay = document.getElementById('popupOverlay');
    const popup = overlay.querySelector('.popup');
    popup.style.animation = 'popupFadeOut 0.2s ease-out forwards';

    setTimeout(() => {
        overlay.classList.remove('show');
        popup.style.animation = 'popupFadeIn 0.3s ease-out'; // 리셋
    }, 200);
}

// 팝업 외부 클릭시 닫기
/*document.getElementById('popupOverlay').addEventListener('click', function(e) {
    if (e.target === this) {
        closePopup();
    }
});*/

// 팝업 fade out 애니메이션
const style = document.createElement('style');
style.textContent = `
        @keyframes popupFadeOut {
            from {
                opacity: 1;
                transform: scale(1) translateY(0);
            }
            to {
                opacity: 0;
                transform: scale(0.9) translateY(-10px);
            }
        }
    `;
document.head.appendChild(style);

// 데이터 업데이트 함수 (외부에서 호출 가능)
function updateProductInfo(list) {
    console.log(list)
    document.getElementById('productCode').textContent = list[0].ITEMCODE;
    document.getElementById('productName').textContent = list[0].ITEMNAME;

    let table = $("#detailDataBody");
    table.empty();
    for (let i = 0; i < list.length; i++) {
        let tbody = `
    			<tr>
    				<td class="date-cell">${list[i].SDATE}</td>
                    <td class="qty-cell">${list[i].QTY}</td>
                    <td class="location-cell">${list[i].LOCATION}</td>	
                </tr>
    		`;
        //table.append(tbody);
    }
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

function logout() {
    showConfirm('',
        '로그아웃하시겠습니까?',
        '확인',
        '취소',
        () => {
            location.href = "/logout";
        }, () => {

        }
    )
}

/*function playSound(type) {
    let soundMap = {
        complete: '/sounds/complete.wav',
        error: '/sounds/buzzer.wav',
        error2: '/sounds/buzzer2.wav',
    };

    let src = soundMap[type];
    if (src) {
        let audio = new Audio(src);
        audio.stop();
        audio.play();
    } else {
        console.warn("등록되지 않은 사운드 타입:", type);
    }
}*/

function playSound(type) {
    console.log(type)
    const soundMap = {
        complete: '/sounds/complete.wav',
        error: '/sounds/buzzer.wav',
        error2: '/sounds/buzzer2.wav',
        ok: '/sounds/ok.wav',
    };
    const src = soundMap[type];
    if (!src) {
        console.warn("등록되지 않은 사운드 타입:", type);
        return;
    }

    // 새 오디오 객체 생성
    const audio = new Audio(src);

    // 재생 중이던 거 초기화 (stop 대신)
    audio.pause();
    audio.currentTime = 0;

    // play()가 실패할 경우 대비
    audio.play().catch(err => {
        console.warn("오디오 재생 실패:", err);
    });
}

// 새로운 3버튼 confirm
function threeButtonConfirm(message = "3개 버튼 중 하나를 선택하세요.", button1Text = "버튼1", button2Text = "버튼2", button3Text = "버튼3", onButton1, onButton2, onButton3) {
    const modal = document.createElement('div');
    isConfirmVisible = true;
    modal.className = 'custom-modal';
    modal.innerHTML = `
                <div class="modal-overlay">
                    <div class="modal-content">
                        <div class="modal-message">${message}</div>
                        <div class="modal-buttons">
                            <button class="modal-btn btn-first">${button1Text}</button>
                            <button class="modal-btn btn-second">${button2Text}</button>
                            <button class="modal-btn btn-third">${button3Text}</button>
                        </div>
                    </div>
                </div>
            `;

    // 스타일 적용 (기존과 동일)
    modal.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                z-index: 3000;
            `;

    const overlay = modal.querySelector('.modal-overlay');
    overlay.style.cssText = `
                width: 100%;
                height: 100%;
                background: rgba(0,0,0,0.5);
                display: flex;
                justify-content: center;
                align-items: center;
                padding: 20px;
            `;

    const content = modal.querySelector('.modal-content');
    content.style.cssText = `
                background: white;
                border-radius: 8px;
                padding: 24px;
                max-width: 500px;
                width: 100%;
                box-shadow: 0 10px 25px rgba(0,0,0,0.3);
            `;

    const messageEl = modal.querySelector('.modal-message');
    messageEl.style.cssText = `
                margin-bottom: 20px;
                color: #374151;
                font-size: 16px;
                line-height: 1.5;
            `;

    const buttons = modal.querySelector('.modal-buttons');
    buttons.style.cssText = `
                display: flex;
                gap: 10px;
                justify-content: flex-end;
                flex-wrap: wrap;
            `;

    const btn1 = modal.querySelector('.btn-first');
    const btn2 = modal.querySelector('.btn-second');
    const btn3 = modal.querySelector('.btn-third');

    [btn1, btn2, btn3].forEach(btn => {
        btn.style.cssText = `
                    padding: 10px 20px;
                    border: none;
                    border-radius: 6px;
                    cursor: pointer;
                    font-size: 14px;
                    flex: 1;
                    min-width: 80px;
                `;
    });

    // 각 버튼 색상
    btn1.style.background = '#6b7280'; // 회색
    btn1.style.color = 'white';

    btn2.style.background = '#f59e0b'; // 주황색
    btn2.style.color = 'white';

    btn3.style.background = '#3b82f6'; // 파란색
    btn3.style.color = 'white';

    document.body.appendChild(modal);

    // 이벤트 리스너
    btn1.addEventListener('click', () => {
        isConfirmVisible = false;
        modal.remove();
        console.log('첫 번째 버튼 클릭됨');
        if (onButton1) onButton1();
    });

    btn2.addEventListener('click', () => {
        isConfirmVisible = false;
        modal.remove();
        console.log('두 번째 버튼 클릭됨');
        if (onButton2) onButton2();
    });

    btn3.addEventListener('click', () => {
        isConfirmVisible = false;
        modal.remove();
        console.log('세 번째 버튼 클릭됨');
        if (onButton3) onButton3();
    });

    /*overlay.addEventListener('click', (e) => {
        if (e.target === overlay) {
            isConfirmVisible = false;
            modal.remove();
            console.log('오버레이 클릭으로 닫힘');
        }
    });*/
}

// ① 무조건 blur → keep-focus는 건너뛰기
$(document).on("click change", "button, input, textarea", function () {
    setTimeout(() => {
        const a = document.activeElement;
        if (a && a.classList && a.classList.contains('keep-focus')) return;
        if (a) a.blur();
    }, 100);
});
// select는 별도로
let lastBlurTimer = null;

// ② 셀렉터에서 keep-focus 제외
$(document).on("click change", "button, input:not(.keep-focus), textarea", function () {
    const el = this;
    if (lastBlurTimer) clearTimeout(lastBlurTimer);
    lastBlurTimer = setTimeout(() => {
        if (document.activeElement === el) el.blur();
    }, 100);
});

// select는 값 변경이 끝난 뒤에만 살짝 blur (원하면 생략 가능)
$(document).on("change", "select", function () {
    const el = this;
    setTimeout(() => el.blur(), 0);
});

// 사용자가 select를 열려는 순간, 대기 중인 blur 타이머 취소
$(document).on("pointerdown mousedown", "select", function () {
    if (lastBlurTimer) clearTimeout(lastBlurTimer);
});

// 제품 바코드 체크
function barcodeCheck(barcode) {
    if ((barcode.split(",").length === 5 && barcode.split(",")[4] === "WMSUSA")
        || (barcode.split(",").length === 4 && barcode.split(",")[3] === "WMSUSA")
        || barcode.split("_").length == 6) {
        playSound('complete');
        return true;
    } else {
        playSound('error');
        Utils.showAlert(`${m("warning.barcode.invalid")}<br>${m("warning.check")}`, "warning")
        return false;
    }
}

function savelimitCheck(key, limit) {
    let list = JSON.parse(localStorage.getItem(key) || "[]");
    if (list.length >= limit) {
        Utils.showAlert(`${m("warning.localstorage.limit")}`, "warning")
        return false;
    } else {
        return true;
    }
}

// 반환된 바코드 배경색 변경
function highlightErrorRows(badBarcodes) {
    console.log("데이터 확인전")
    if (!Array.isArray(badBarcodes) || badBarcodes.length === 0) return;
    console.log("데이터 있음")
    const set = new Set(badBarcodes.map(String)); // 안전하게 문자열화
    $("#dataTableBody tr.bar-row").each(function () {
        const bc = this.dataset.barcode || "";
        $(this).toggleClass("error-row", set.has(bc));
    });

    // 선택: 첫 에러행으로 스크롤
    const first = $("#dataTableBody tr.error-row").get(0);
    if (first) first.scrollIntoView({behavior: "smooth", block: "center"});
}

// 숫자 형식 둘째자리고정 + 3자리마다 ,추가
function formatNumber(num) {
    // 소수점 둘째자리까지 고정
    let str = num.toFixed(2);

    // .00이면 소수점 제거
    if (str.endsWith(".00")) {
        str = str.replace(".00", "");
    }

    // 3자리마다 , 추가
    return str.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}


/*
 *  바코드 모달 창 관련
 */

// 초기 설정 (한 번만)
document.addEventListener('click', function (event) {
    if (event.target.classList.contains('clickable-barcode')) {
        showBarcodeModal(event.target.textContent);
    }
});

// 바코드 연결해주는 함수
function makeBarcodesClickable(barcodes) {
    if (!Array.isArray(barcodes) || barcodes.length === 0) {
        return ''; // barcodes가 없거나 비어있으면 빈 문자열 반환
    }
    return barcodes
        .map(barcode => `<span class="clickable-barcode">${barcode}</span>`)
        .join("<br>");
}

//바코드 모달 표시
function showBarcodeModal(barcode) {

    const swal = document.querySelector('.swal2-container');

    // 알림 창 재사용을 위해 안보이게 설정
    if (swal) {
        swal.style.display = 'none';
    }

    showLoading();

    $.ajax({
        url: '/purchase/stock-history-barcode',
        method: 'POST',
        contentType: 'application/json',
        data: barcode,
        success: function (result) {
            console.log(result);
            if (result.error) {
                hideLoading();
                playSound('error');
                Utils.showAlert(m(result.error), "warning");
                return;
            }
            renderModal(result.list, barcode, result.main);
        },
        error: function (xhr, status, error) {
            console.error('바코드 데이터 로드 실패:', error);
        }
    });
}

// 히스토리 모달 렌더링
async function renderModal(list, barcode, main) {
    // 모달이 없다면 생성
    if (!document.getElementById('historyModal')) {
        createHistoryModalStructure();
    }

    const modalHistoryDiv = document.getElementById('modalHistoryDiv');
    modalHistoryDiv.innerHTML = '';
    $("#infoBarcode").text(barcode);
    if (!list || list.length === 0) {
        // 빈 상태 표시
        modalHistoryDiv.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">📋</div>
                <div class="empty-state-text">There is no history</div>
            </div>
        `;
    } else {
        let historyInfo = `	<div class="info-grid">
						      	<div class="info-item">
									<div class="info-label" th:text = "#{table.barcode}">Barcode</div>
						            <div class="info-value"><span id = "infoBarcode">${barcode}</span></div>
						        </div>
						      	<div class="info-item">
									<div class="info-label" th:text = "#{table.oitemcode}">Spec</div>
						            <div class="info-value"><span id = "infoItemcode">${main.spec}</span></div>
						        </div>
						      	<div class="info-item">
									<div class="info-label" th:text = "#{table.itemName}">Itemname</div>
						            <div class="info-value"><span id = "infoItemname">${main.itemname}</span></div>
						        </div>
						        <div class="info-item" id="location-wrap" >
									<div class="info-label" th:text="#{table.location}">Location</div>
									<div class="info-value"><span id = "location">${main.location || ''}</span></div>
								</div>
							</div>`

        $("#modalBarcodeInfo").html(historyInfo);
        // 히스토리 카드들 생성
        let historyHtml = '';
        for (let i = 0; i < list.length; i++) {
            const item = list[i];

            let checkBarcode = item.barcode.charAt(0);  // 첫 번째 글자
            let checkBarcodeKor = item.barcode.split(",")[3];  // 콤마 맨 뒤 값 (WMSMEX)
            let checkBarcodeMex = item.barcode.split(",")[4];  // 콤마 맨 뒤 값 (WMSMEX)

            const sequenceNumber = list.length - i;

            // 수량 포맷팅
            let qtyFormatted = '-';
            if (item.qty) {
                const num = Number(item.qty);
                qtyFormatted = Number.isInteger(num)
                    ? num.toLocaleString()
                    : num.toLocaleString(undefined, {minimumFractionDigits: 0, maximumFractionDigits: 2});
            }

            // 공통 필드: 날짜/시간, 수량
            historyHtml += `<div class="history-card">`
            if (item.kind == 'PALLET') {

                console.log(checkBarcode + " -- " + checkBarcodeKor);
                if (checkBarcode != "P") {
                    return;
                } else {
                    if (checkBarcodeKor == "SCMMEX") {
                        historyHtml += `
    			                <div class="history-header korBackground">
    								<span class = "quantity-badge">${sequenceNumber} </span>
    			                    <span>${item.kind}</span>
    			                </div>
    		               `
                    } else if (checkBarcodeKor == "MEXUSA") {
                        historyHtml += `
    			                <div class="history-header usaBackground">
    								<span class = "quantity-badge">${sequenceNumber} </span>
    			                    <span>${item.kind}</span>
    			                </div>
    		               `
                    } else {
                        historyHtml += `
    			                <div class="history-header mexBackground">
    								<span class = "quantity-badge">${sequenceNumber} </span>
    			                    <span>${item.kind}</span>
    			                </div>
    		               `
                    }
                }
            } else if (item.kind == 'BARCODE' || item.kind == 'PALLET_BARCODE' || item.kind == 'PALLET_BARCODE_INCLUDE') {
                console.log(checkBarcode + " -- " + checkBarcodeKor + "--" + checkBarcodeMex);
                if (checkBarcodeMex == "WMSMEX") {
                    historyHtml += `
    	                <div class="history-header mexBackground">
    						<span class = "quantity-badge">${sequenceNumber} </span>
    	                    <span>${item.kind}</span>
    	                </div>`
                } else if (checkBarcodeMex == "WMSUSA") {
                    historyHtml += `
    	                <div class="history-header usaBackground">
    						<span class = "quantity-badge">${sequenceNumber} </span>
    	                    <span>${item.kind}</span>
    	                </div>`
                } else {
                    historyHtml += `
    	                <div class="history-header korBackground">
    						<span class = "quantity-badge">${sequenceNumber} </span>
    	                    <span>${item.kind}</span>
    	                </div>`
                }
            } else {
                historyHtml += `
    	                <div class="history-header">
		                    <span class="quantity-badge">${sequenceNumber}</span>
		                    <span>${item.kind}</span>
		                </div>
                   `
            }

            // 카드 HTML 생성
            historyHtml += `
                    <div class="history-items">
	                    <div class="history-item">
							<!-- <div class="item-detail">
	                            <div class="detail-label">YN</div>
	                            <div class="detail-value">${item.useyn || '-'}</div>
	                        </div> -->`
            if (item.loginid && item.loginid != ' ') {
                historyHtml += `
                    <div class="item-detail">
                        <div class="detail-label">${m('table.user')}</div>
                        <div class="detail-value">${item.loginid || '-'}</div>
                    </div>`;
            }

            historyHtml += `
					<!--<div class="item-detail">
                        <div class="detail-label">${m('table.barcode')}</div>
                        <div class="detail-value">${item.barcode || '-'}</div>
                    </div>-->
                    <div class="item-detail">
                        <div class="detail-label">${m('table.date')}</div>
                        <div class="detail-value">${item.sdate || '-'}</div>
                    </div>
                    <div class="item-detail">
                        <div class="detail-label">${m('table.worktime')}</div>
                        <div class="detail-value">${item.time || '-'}</div>
                    </div>
                    <div class="item-detail">
                        <div class="detail-label">${m('table.qty')}</div>
                        <div class="detail-value">
                            <span class="detail-value">${qtyFormatted ? qtyFormatted : '-'}</span>
                        </div>
                    </div>`;

            if (item.kind == 'PALLET' && checkBarcode == "P" && checkBarcodeKor == "SCMMEX") {
                try {
                    const data = await $.ajax({
                        url: "/purchase/show_stockHistory_sangho",
                        type: "POST",
                        data: {
                            custCode: item.custcode
                        }
                    });

                    historyHtml += `
    						<div class="item-detail">
    				            <div class="detail-label">${m('table.factory')}</div>
    				                <div class="detail-value">
    				                <span class="detail-value">KOREA - ${data.CU_SANGHO}</span>
    				            </div>
    				        </div>
    			    `;
                } catch (error) {
                    console.error("CUSTNAME NOT FOUND:", error);
                }

            } else {
                historyHtml += `
    						<div class="item-detail">
                                <div class="detail-label">${m('table.factory')}</div>
                                <div class="detail-value">
                                    <span class="detail-value">${item.factory || '-'}</span>
                                </div>
                            </div>
                        `;
            }
            // kind 값에 따라 추가 필드 표시
            if (item.kind && (item.kind.includes('WIP') || item.kind.includes('LOCATION'))) {
                // location 필드 추가
                if (item.location) {
                    historyHtml += `
    	                    <div class="item-detail">
    	                        <div class="detail-label">${m('table.location')}</div>
    	                        <div class="detail-value">${item.location}</div>
    	                    </div>`;
                }
            }

            if (item.kind && (item.kind.includes('BARCODE') || item.kind.includes('PALLET'))) {
                // location 필드 추가
                if (item.laststatus) {
                    historyHtml += `
    	                    <div class="item-detail">
    	                        <div class="detail-label">Laststatus</div>
    	                        <div class="detail-value">${item.laststatus}</div>
    	                    </div>
    						`;
                }
                if (item.labeltype) {
                    historyHtml += `
    	                   
    						<div class="item-detail">
    	                        <div class="detail-label">Label Type</div>
    	                        <div class="detail-value">${item.labeltype}</div>
    	                    </div>`;
                }
            }
            if (item.kind && item.kind.includes('PALLET')) {
                if ((item.partbarcode).split(",").length === 4) {
                    historyHtml += `
    					<div class="item-detail">
    	                    <div class="detail-label">Pallet Barcode</div>
    	                    <div class="detail-value clickable-barcode">${item.partbarcode}</div>
    	                </div>`;
                } else {
                    const parts = item.partbarcode.match(/.*?USA/g) || [];

                    historyHtml += `
					<div class="item-detail">
	                    <div class="detail-label">Part Barcode</div>
	                    <div class="detail-barcodes">`
                    for (let j = 0; j < parts.length; j++) {
                        historyHtml += `
						<div class="detail-value clickable-barcode">${parts[j]}</div>
						`
                    }

                    historyHtml += `
	                    </div>
	                </div>`;
                }
            }

            if (item.kind && item.kind.includes('CHANGE ITEMCODE')) {
                const parts = item.partbarcode.split('_');

                historyHtml += `
				<div class="item-detail">
					<div class="detail-label">OLD ITEMCODE</div>
					<div class="detail-value">${parts[0] || ''}</div>
				</div>				
				<div class="item-detail">
					<div class="detail-label">NEW ITEMCODE</div>
					<div class="detail-value">${parts[1] || ''}</div>
				</div>
				`;
            }

            if (item.kind && (item.kind.includes('WORKMOVE'))) {
                // work 필드 추가
                if (item.work) {
                    historyHtml += `
    	                    <div class="item-detail">
    	                        <div class="detail-label">Work</div>
    	                        <div class="detail-value">${item.work}</div>
    	                    </div>`;
                }
            }

            if (item.kind == 'STOCK MOVE' || item.kind == 'FACTORY MOVE' || item.kind == 'FACTORY SENDING' || item.kind == 'FACTORY RECEIVE') {
                const factory = item.factory || '';
                const storage = item.storage || '';
                const custcode = item.custcode || '';
                const custname = item.custname || '';

                historyHtml += `
                   	   	<div class="item-detail">
            	            <div class="detail-label">${m('table.location')}</div>
        	        		<div class="detail-value">${factory} ${storage} <br>-> ${custcode} ${custname}</div>
    	            	</div>
    	            `
            } else if (item.kind == 'LOAD') {

                const custcode = item.custcode || '';
                const storage = item.storage || '';
                try {

                    const data = await $.ajax({
                        url: "/purchase/show_stockHistory_sangho",
                        type: "POST",
                        data: {
                            custCode: custcode
                        }
                    });

                    let lastStatus = $("#laststatus").text();
                    lastStatus += "\n[" + storage + " -> " + data.CU_SANGHO + "]";
                    $("#laststatus").text(lastStatus);
                } catch (error) {
                    console.error("CUSTNAME NOT FOUND:", error);
                }

            }
            historyHtml += `
	       	   	<div class="item-detail">
		            <div class="detail-label">${m('table.memo')}</div>
	        		<div class="detail-value">${item.memo}</div>
	        	</div>
	        `;
            // 카드 닫기
            historyHtml += `
    	                </div>
    	            </div>
    	        </div>`;
        }
        modalHistoryDiv.innerHTML = historyHtml;
    }

    // 모달 표시
    document.getElementById('historyModal').style.display = 'flex';
    document.body.style.overflow = 'hidden';
    hideLoading();
}

// 모달 구조 생성
function createHistoryModalStructure() {
    const modalHtml = `
        <div class="modal-overlay" id="historyModal">
            <div class="modal-container">
                <div class="modal-header">
                    <div class="modal-title">Barcode Infomation</div>
                    <button class="modal-close" onclick="closeHistoryModal()">&times;</button>
                </div>
				<div class="form-container itemInfo-div" id = "modalBarcodeInfo">
					<div class="info-grid">
						<div class="info-item">
							<div class="info-label" th:text = "#{table.barcode}">Barcode</div>
							<div class="info-value"><span id = "infoBarcode"></span></div>
						</div>
						<div class="info-item" id="location-wrap" style="display: none;">
							<div class="info-label" th:text="#{table.location}">Location</div>
							<div class="info-value"><span id = "location"></span></div>
						</div>
					</div>
				</div>
                <div class="modal-body">
                    <div class="history-div" id="modalHistoryDiv"></div>
                </div>
            </div>
        </div>
    `;

    document.body.insertAdjacentHTML('beforeend', modalHtml);

    // 이벤트 리스너 추가
    const modal = document.getElementById('historyModal');

    // 오버레이 클릭으로 모달 닫기
    modal.addEventListener('click', function (event) {
        if (event.target === modal) {
            closeHistoryModal();
        }
    });
}

// 모달 닫기
function closeHistoryModal() {
    const modal = document.getElementById('historyModal');
    if (modal) {
        modal.style.display = 'none';
        document.body.style.overflow = 'auto';
    }

    const swal = document.querySelector('.swal2-container');

    // 알림 창 재사용
    if (swal) {
        swal.style.display = 'flex';
    }
}

function getStroage(type, onDone) {
    const data = {};
    if (type) {
        data.type = type;
    }

    $.ajax({
        url: "/ulsan/getStroage",
        type: "POST",
        data: data,
        dataType: "json",
        success: function (res) {
            const $sel = $('.storage-select');
            $sel.empty();
            res.forEach(function (s) {
                $sel.append($('<option>').val(s).text(s));
            });
            if (typeof onDone === 'function') onDone();   // 채운 뒤 콜백
        },
        error: function (res) {
            console.error('getStroage failed:', res);
        }
    });
}