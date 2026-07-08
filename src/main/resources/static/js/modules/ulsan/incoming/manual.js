let itemInfo = [];      // [itemcode, itemname]
const ENTRY_KEY = "entryListInManual";
const MASTER_KEY = "itemInfoInManual";

$(document).ready(function () {
    let today = new Date();

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

    renderTable();
    loadItemInfo();
    // 창고 리스트 동적으로 사용
    getStroage('사내');

    $(document).on("input", ".qty-input", function () {
        updateTotalQty();
    });

    // 키보드 감지로 푸터 표시/숨김 (focus/blur 대신 이걸로 교체)
    if (window.visualViewport) {
        let baseHeight = window.visualViewport.height;

        window.visualViewport.addEventListener('resize', function () {
            // 화면 높이가 원래보다 많이 줄었으면 키보드가 올라온 상태로 판단
            const shrink = baseHeight - window.visualViewport.height;

            if (shrink > 150) {
                $('.footer').hide();   // 키보드 올라옴
            } else {
                $('.footer').show();   // 키보드 내려감 (뒤로가기 포함)
                baseHeight = window.visualViewport.height; // 기준 높이 갱신
            }
        });
    }

    // 수량칸: 마이너스 차단 + 최대 5자리 제한
    $(document).on('keydown', '.qty-input', function (e) {
        // 마이너스, e, +, . 입력 차단
        if (['-', 'e', 'E', '+', '.'].includes(e.key)) {
            e.preventDefault();
        }
    });

    $(document).on('input', '.qty-input', function () {
        // 숫자 이외 문자 제거 + 5자리 초과 자르기
        let v = this.value.replace(/[^0-9]/g, '');
        if (v.length > 5) v = v.slice(0, 5);
        this.value = v;
    });
})

// 품번 전체 검색
function loadItemInfo() {
    let $input = $("#itemInput");
    let $spin  = $("#itemLoading");

    let cached = localStorage.getItem(MASTER_KEY);
    if (cached) {
        try {
            itemInfo = JSON.parse(cached);
            initAutocomplete();
        } catch (e) {
            itemInfo = [];
        }
    }

    // 로딩 표시 시작
    $spin.removeClass("hidden");
    $input.attr("placeholder", "품번 불러오는 중...");

    $.ajax({
        url: `/ulsan/getItemList`,
        type: 'GET',
        dataType: 'json',
        success: function (data) {
            let listData = Array.isArray(data) ? data : (data.list || []);
            itemInfo = listData.map(it => ({
                itemcode: it.ITEMCODE,
                itemname: it.ITEMNAME
            }));
            localStorage.setItem(MASTER_KEY, JSON.stringify(itemInfo));
            initAutocomplete();
        },
        error: function () {
            if (!itemInfo.length) Utils.showAlert(m("error.offline"), "warning");
        },
        complete: function () {
            // 로딩 표시 종료
            $spin.addClass("hidden");
            $input.attr("placeholder", "품번을 입력하세요");
        }
    });
}

function initAutocomplete() {
    $("#itemInput").autocomplete({
        minLength: 1,
        delay: 0,
        source: function (req, res){
            let term = req.term.toUpperCase();
            let matched = itemInfo.filter(it =>
                it.itemcode.toUpperCase().indexOf(term) > -1 ||
                (it.itemname && it.itemname.toUpperCase().indexOf(term) > -1)
            ).slice(0, 20).map(it => ({
                label: it.itemcode + " · " + it.itemname,
                value: it.itemcode,
                code: it.itemcode
            }));
            res(matched);
        },
        select: function (event, ui) {
            addEntry(ui.item.code);
            $(this).val("");        // 다음 검색을 위해 비움
            return false;
        }
    });
}

function getEntryList() {
    try {
        return JSON.parse(localStorage.getItem(ENTRY_KEY) || "[]");
    } catch(e) {
        return [];
    }
}

// ── 행 추가 (같은 품번도 항상 새 행) ──
function addEntry(code) {
    if (!code) return;
    if (!savelimitCheck(ENTRY_KEY, 500)) return;

    syncQtyFromInputs();          // 화면 수량 먼저 저장 (중복 알림 후 재그리기 대비)

    let list = getEntryList();
    if (list.some(e => e.itemcode === code)) {
        Utils.showAlert(code + "<br>이미 추가된 품번입니다", "warning");
        if (typeof playSound === 'function') playSound("error");
        return;
    }

    let entry = {
        id: Date.now() + "_" + Math.random().toString(36).slice(2, 7),
        itemcode: code,
        qty: 0
    };
    list.push(entry);
    localStorage.setItem(ENTRY_KEY, JSON.stringify(list));
    renderTable();

    $("#qty_" + entry.id).focus();   // 방금 추가한 행 수량칸 포커스
    if (typeof playSound === 'function')
        playSound("complete");
}

function renderTable() {       //테이블그리기
    let table = $("#dataTableBody");
    let list = getEntryList();
    table.empty();

    list.forEach(function (entry) {
        let row = `
            <tr data-id="${entry.id}">
                <td class="dataInfo">${entry.itemcode}</td>
                <td><input type="number" min="1" inputmode="numeric" class="qty-input keep-focus" style="height: 25px"
                           id="qty_${entry.id}" value="${entry.qty || ''}">
                </td>
                <td><button class="delete-btn" onclick="deleteEntry('${entry.id}')">${m('btn.delete')}</button></td>
            </tr>`;
        table.prepend(row);
    });
    $("#count").text(list.length);
    updateTotalQty();          // 그린 뒤 총 수량 반영
}

function syncQtyFromInputs() {
    let list = getEntryList();
    $(".qty-input").each(function () {
        let id = this.id.replace("qty_", "");
        let v = parseInt(this.value, 10);
        let idx = list.findIndex(e => e.id === id);
        if (idx > -1) list[idx].qty = (isNaN(v) || v < 0) ? 0 : v;
    });
    localStorage.setItem(ENTRY_KEY, JSON.stringify(list));
}

function updateTotalQty() {
    let total = 0;
    $(".qty-input").each(function () {
        let v = parseInt(this.value, 10);
        if (!isNaN(v) && v > 0) total += v;
    });
    $("#totalQty").text(total);
}

function deleteEntry(id) {
    Utils.showConfirm(m("confirm.delete.item"), () => {
        syncQtyFromInputs();
        let list = getEntryList().filter(e => e.id !== id);
        localStorage.setItem(ENTRY_KEY, JSON.stringify(list));
        renderTable();
        Utils.showAlert(m("success.deleted"), 'success');
    });
}

function clearAll() {
    Utils.showConfirm(m("confirm.delete.all"), () => {
        if (getEntryList().length === 0) {
            Utils.showAlert(m('warning.data.delete.all'), "warning");
            return;
        }
        localStorage.removeItem(ENTRY_KEY);
        renderTable();
        Utils.showAlert(m("success.deleted.all"), "success");
    });
}


// ── 전체 전송 (수량 0/미입력 검증) ──
function saveEntry() {
    if (isSaving) return;

    syncQtyFromInputs();          // 화면 수량을 저장소에 먼저 반영

    let list = getEntryList();
    if (list.length === 0) {
        Utils.showAlert(m("warning.barcode.noneToSave"), 'warning');
        return;
    }

    let invalid = list.some(item => !item.qty || Number(item.qty) <= 0);
    if (invalid) {
        $(".qty-input").each(function () {
            let v = Number(this.value);
            $(this).closest('tr').css('background', (!v || v <= 0) ? '#ffe0e0' : '');
        });
        Utils.showAlert("수량을 입력해 주세요", 'warning');
        return;
    }

    isSaving = true;

    let data = {
        date: $("#datepicker").val(),
        itemcodes: list.map(item => item.itemcode),
        qtys: list.map(item => String(item.qty)),
        source: "INCOMING",
        storage: $('.storage-select').val(),
        factory: localStorage.getItem('rememberedFactory'),
        main: 'IN'
    };

    showLoading();
    $.ajax({
        url: `/ulsan/insInboundManual`,
        method: 'POST',
        contentType: "application/json",
        data: JSON.stringify(data),
        success: function (result) {
            if (result.response === "success") {
                localStorage.removeItem(ENTRY_KEY);
                renderTable();
                Utils.showAlert(m("info.barcode.sent"), "info");
                if (typeof playSound === 'function')
                    playSound("complete");
            } else {
                Utils.showAlert(m(result.response), "warning");
                if (typeof playSound === 'function')
                    playSound("error");
            }
            hideLoading();
        },
        error: function (request, status, error) {
            if (request.status === 500) Utils.showAlert(m("error.generic"), 'warning');
            else if (request.status === 0) Utils.showAlert(m("error.offline"), 'warning');
            else Utils.showAlert("code: " + request.status + "<br>" + request.responseText, "warning");
            hideLoading();
        },
        complete: function () {
            hideLoading();
            isSaving = false;
        }
    });
}

// $(document).on("click", ".dataInfo", function () {
//     // 클릭한 tr중 dataInfo클래스를 찾아 첫번째 텍스트 반환
//     const itemCode = $(this).closest('tr').find('td.dataInfo').first().text().trim();
//
//     // 파레트라벨인지
//     const isPallet = itemCode.startsWith("(P)");
//
//     // 정규식으로 pno만 추출
//     const itemcode = isPallet ? itemCode.replace(/^\s*\(\s*p\s*\)\s*/i, "") : itemCode;
//
//
//     $.ajax({
//         url: "/purchase/getItemInfo",
//         type: "GET",
//         data: {itemcode},
//         dataType: "json",
//         success: function (result) {
//             console.log(result);
//             showPopup(result.list);
//         }
//     })
// });

$(document).on("change blur", ".customer-select", function () {
    hideLoading();
});










