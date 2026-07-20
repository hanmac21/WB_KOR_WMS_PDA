let manualTouch = false;
let isScanning = false;

/* ---------- 상태 ---------- */
let seqList = [];   // 조회된 서열 목록
let curIdx  = 0;    // 현재 검수 대상 인덱스
let curScan = 0;    // 현재 대상에서 스캔한 개수
let prevLine = '';     // 이전에 선택한 라인

// 한 세트 구성. CTR 확정되면 'CTR' 추가하면 됨
const POS = ['LH', 'RH'];

$(document).ready(function () {
    // 1. 라인 변경 시 자동 조회
    $('#lineSelect').on('change', search);

    prevLine = $('#lineSelect').val();

    search();

    // input창에서 포커스 없어질때 세팅
    $('#barcodeInput').on('blur', function () {
        manualTouch = false;
        inputMode = 'readonly'
    });

    // 행 클릭 시 해당 위치부터 시작
    $('#dataTableBody').on('click', 'tr', function () {
        curIdx  = parseInt($(this).data('idx'), 10);
        curScan = 0;
        renderPanel();
    });
});

function search() {
    // 미전송 내역 있으면 확인
    if (seqList.some(function (r) { return r.SCANNED; })) {
        Utils.showConfirm('전송하지 않은 검수 내역이 있습니다.<br>라인을 변경하면 사라집니다. 계속할까요?', () => {
            runSearch();
        }, () => {
            $('#lineSelect').val(prevLine);   // 취소 → 라인 되돌리기
        });
        return;
    }
    doSearch();
}

function doSearch() {
    const line = $(".line-select").val();
    prevLine = line;

    // 라인 미선택 → 화면 비우기
    if (!line) {
        clearAll();
        return;
    }

    showLoading();

    $.ajax({
        url: "/ulsan/search-sequenceList",
        method: 'POST',
        contentType: "application/json",
        data: JSON.stringify({ line: line }),
        success: function (result) {
            seqList = result.list || [];
            curIdx  = 0;
            curScan = 0;
            renderTable(seqList);
            renderPanel();
        },
        error: function (xhr, status, error) {
            console.error('요청 실패');
            console.error('Status:', status);
            console.error('Error:', error);
            console.error('Response:', xhr.responseText);
            clearAll();
            Utils.showAlert('오류가 발생했습니다: ' + error, 'warning');
        },
        complete: function () {
            hideLoading();
        }
    });
}

function addEntry() {
    if (isScanning) return;

    isScanning = true;

    try {
        const barcode = $('#barcodeInput').val().trim();
        $('#barcodeInput').val('');

        if (!barcode) {
            Utils.showAlert(m("warning.barcode.required"));
            return;
        }

        if (!seqList.length || curIdx >= seqList.length) {
            playSound('error');
            Utils.showAlert('검수할 서열이 없습니다.', 'warning');
            return;
        }

        /* ---------- 바코드에서 품번 추출 ---------- */
        let scanCode = null;

        // [)>|06|VSLBJ|P88700-S8010WFD|S|E|T260707LX31A0000004|MN|CW0001|||
        if (barcode.includes('|')) {
            const part = barcode.split('|').find(function (v) {
                return v.startsWith('P') && v.length > 1;
            });
            if (part) scanCode = part.substring(1).replace(/-/g, '');
        }
        // 다른 형식 바코드는 여기에 else if 로 추가

        if (!scanCode) {
            playSound('error');
            Utils.showAlert(`${m("warning.barcode.invalid")}<br>${m("warning.check")}`, 'warning');
            return;
        }

        /* ---------------------------------------- */
        const cur = seqList[curIdx];
        const pos = POS[curIdx % POS.length];
        const code = (cur['ROW1_' + pos + '_CODE'] || '').replace(/-/g, '');
        const qty = toInt(cur.QTY);

        // 재스캔
        if (cur.COMPLETEYN === 'Y') {
            playSound('error');
            Utils.showAlert('이미 검수 완료된 서열입니다.', 'warning');
            return;
        }

        // 코드 불일치
        if (scanCode !== code) {
            playSound('error');
            Utils.showAlert(`${scanCode}<br>${pos} 코드가 일치하지 않습니다.`, 'warning');
            return;
        }

        curScan++;
        playSound('complete');

        // 수량 미달 → 계속 스캔
        if (curScan < qty) {
            renderPanel();
            return;
        }

        // 수량 충족 → 완료 표시 후 다음 SEQ
        cur.SCANNED = true;     // 전송 대상 (클라이언트 전용)
        cur.COMPLETEYN = 'Y';
        markRow(curIdx);

        curIdx++;
        curScan = 0;
        renderPanel();

        if (curIdx >= seqList.length) {
            Utils.showAlert('모든 서열 검수가 완료되었습니다.<br>전체 전송을 눌러주세요.', 'info');
        }
    } finally {
        isScanning = false;
    }
}

function saveEntry() {
    if (isSaving) return;

    // 이번에 스캔한 것만 전송
    const targets = seqList.filter(function (r) { return r.SCANNED; });

    if (!targets.length) {
        Utils.showAlert('전송할 검수 내역이 없습니다.', 'warning');
        return;
    }

    isSaving = true;
    showLoading();

    $.ajax({
        url: "/ulsan/update-sequenceComplete",
        method: 'POST',
        contentType: "application/json",
        data: JSON.stringify({
            line: $(".line-select").val(),
            list: targets.map(function (r) {
                return {
                    SEQ: r.SEQ,
                    TIME: r.TIME,
                    J: r.J
                };   // ★ 서버 PK에 맞춰 조정
            })
        }),
        success: function (result) {
            if (result.response === 'success') {
                // 전송 성공 → 대상에서 제외
                targets.forEach(function (r) { r.SCANNED = false; });
                playSound('complete');
                Utils.showAlert(m("info.barcode.sent"), 'info');
            } else {
                playSound('error');
                Utils.showAlert(result.response || m("error.generic"), 'warning');
            }
        },
        error: function (xhr, status, error) {
            playSound('error');
            if (xhr.status === 500)
                Utils.showAlert(m("error.generic"), 'warning');
            else if (xhr.status === 0)
                Utils.showAlert(m("error.offline"), 'warning');
            else
                Utils.showAlert('code: ' + xhr.status + '<br>' + error, 'warning');
        },
        complete: function () {
            hideLoading();
            isSaving = false;
        }
    });
}

/* ---------- 목록 그리기 ---------- */
function renderTable(list) {
    const $body = $('#dataTableBody');
    let completeQty = 0;

    $body.empty();

    for (let i = 0; i < list.length; i++) {
        const data = list[i];
        if (data.COMPLETEYN === 'Y') completeQty++;

        let pos;
        if (i === 0 || i % 2 === 0){
            pos = 'LH';
        }else {
            pos = 'RH';
        }

        const cls = data.COMPLETEYN === 'Y' ? 'is-complete' : '';

        $body.append(`
            <tr data-idx="${i}" class="${cls}">
                <td>${i + 1}</td>
                <td>${data.OP4 || '-'}</td>
                <td>${data.TIME || '-'}</td>
                <td>${data.SEQ || '-'}</td>
                <td>${data.QTY || '-'}</td>
                <td>${data.COLORCODE || '-'}</td>
                <td>${pos}</td>
                <td>${data.ROW1_LH_CODE || '-'}</td>
                <td>${data.ROW1_RH_CODE || '-'}</td>
                <td>${data.ROW1_CTR_CODE || '-'}</td>
                <td>${data.J || '-'}</td>
            </tr>
        `);
    }

    $('#completecount').text(completeQty);
    $('#totalcount').text(list.length);
}

/* ---------- 목록 행 완료 표시 ---------- */
function markRow(idx) {
    $('#dataTableBody tr[data-idx="' + idx + '"]').addClass('is-complete');
    $('#completecount').text(
        seqList.filter(function (r) { return r.COMPLETEYN === 'Y'; }).length
    );
}

/* ---------- 정보 패널 그리기 ---------- */
function renderPanel() {
    // 현재 인덱스가 속한 세트의 시작 위치
    const setStart = Math.floor(curIdx / POS.length) * POS.length;
    const cur = seqList[curIdx];
    const qty = toInt(cur?.QTY);

    // 좌측 LH / RH ( / CTR )
    POS.forEach(function (pos, i) {
        const idx  = setStart + i;
        const row  = seqList[idx];
        const $row = $('#row' + pos);

        if (!row) {
            setRow($row, 'is-empty', '—');
            return;
        }

        const code = row['ROW1_' + pos + '_CODE'] || '-';

        if (idx < curIdx)         setRow($row, 'is-done',   code);   // 검수 끝, 값 유지
        else if (idx === curIdx)  setRow($row, curScan > qty ? 'is-over' : 'is-target', code);
        else                      setRow($row, 'is-wait',   code);   // 순서 대기
    });

    // POS에 없는 위치는 비활성
    ['LH', 'RH', 'CTR'].forEach(function (pos) {
        if (POS.indexOf(pos) === -1) setRow($('#row' + pos), 'is-empty', '—');
    });

    // 우측 수량
    $('#scanQty').text(curScan);
    $('#targetQty').text(qty);
    $('#seqQty').attr('class', 'seq-qty ' + (
        !cur                        ? 'is-idle' :
        curScan > qty               ? 'is-over' :
        (qty > 0 && curScan === qty) ? 'is-done' : ''
    ));

    // 우측 참고값
    $('#infoTime').text(cur?.TIME || '-');
    $('#infoSeq').text(cur?.SEQ || '-');
    $('#infoJ').text(cur?.J || '-');

    // 목록에서 현재 행 강조
    highlightRow();
}


/* ---------- 목록 현재 행 강조 + 스크롤 ---------- */
function highlightRow() {
    const $body = $('#dataTableBody');
    $body.find('tr').removeClass('is-current');

    const $tr = $body.find('tr[data-idx="' + curIdx + '"]');
    if (!$tr.length) return;

    $tr.addClass('is-current');

    const $area = $('.table-scroll-area');
    const offset = $tr.offset().top - $area.offset().top;   // 영역 기준 상대 위치

    $area.scrollTop(
        $area.scrollTop() + offset - ($area.height() / 2) + ($tr.outerHeight() / 2)
    );
}


/* ---------- 화면 초기화 ---------- */
function clearAll() {
    seqList = [];
    curIdx  = 0;
    curScan = 0;

    $('#dataTableBody').empty();
    $('#completecount').text(0);
    $('#totalcount').text(0);
    $('.seq-part-value').text('-');
    $('#scanQty').text(0);
    $('#targetQty').text(0);
    $('#infoTime').text('-');
    $('#infoSeq').text('-');
    $('#infoJ').text('-');

    renderPanel();
}


/* ---------- 유틸 ---------- */
function setRow($row, state, code) {
    $row.attr('class', 'seq-part-row ' + state).find('.seq-part-value').text(code);
}

function toInt(v) {
    const n = parseInt(v, 10);
    return isNaN(n) ? 0 : n;
}







