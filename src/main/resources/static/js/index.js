$(document).ready(function() {
	const factory = localStorage.getItem('rememberedFactory');
	$('.status-title').text(factory);
	
	// 현재 날짜 초기화 (오늘 날짜)
    var currentDate = new Date();
    updateDateDisplay(currentDate);
    
    // 페이지 로드 시 현재 날짜로 상태 조회
    getStatusData(formatDate(currentDate));
	
    // 이전 날짜 버튼 클릭
    $('.date-nav').eq(0).on('click', function() {
        currentDate.setDate(currentDate.getDate() - 1);
        updateDateDisplay(currentDate);
        getStatusData(formatDate(currentDate));
    });
    
    // 다음 날짜 버튼 클릭
    $('.date-nav').eq(1).on('click', function() {
        currentDate.setDate(currentDate.getDate() + 1);
        updateDateDisplay(currentDate);
        getStatusData(formatDate(currentDate));
    });                    	
});

// 상태 데이터 조회 AJAX
function getStatusData(selectedDate) {
    // showLoading(); // 로딩 표시
    //
    // $.ajax({
    //     url: '/getStatus',
    //     method: 'POST',
    //     data: {
    //         date: selectedDate,
    //         factory: localStorage.getItem('rememberedFactory')
    //     },
    //     success: function(response) {
    //     	//console.log(response);
    //
    //         updateStatusDisplay(response);
    //         hideLoading();
    //     },
    //     error: function(xhr, status, error) {
    //         console.error('상태 조회 실패:', error);
    //         hideLoading();
    //         // 에러 처리 - 필요시 사용자에게 알림
    //         if (window.AndroidInterface) {
    //             AndroidInterface.showToast('데이터 조회에 실패했습니다.');
    //         } else {
    //             alert('데이터 조회에 실패했습니다.');
    //         }
    //     }
    // });
}

 // 상태 표시 업데이트 (서버 응답 데이터로 화면 갱신)
function updateStatusDisplay(data) {
    // 총 수량, 총 건수 업데이트
	$('.stat-card.blue .stat-value').text(Number((data.totalSum) || 0).toLocaleString());
    $('.stat-card.green .stat-value').text(Number((data.totalCount) || 0).toLocaleString());
    
    // 업무별 현황 동적 업데이트
    if (data.list) {
        // 기존 status-list 비우기
        $('.status-list').empty();
        // 새로운 데이터로 동적 생성
        Object.entries(data.list).forEach(([source, stat]) => {
			if(source ==  'INCOMINGCKD'){
				source = 'Incoming(CKD)'
			}else if(source ==  'INCOMINGLOCAL'){
				source = 'Incoming(Local)'
			}else if(source ==  'WIPINPUT'){
				source = 'WIP-Input'
			}else if(source ==  'INCOMINGEXCEPTION'){
				source = 'Exception-Incoming'
			}else if(source ==  'LOADEXCEPTION'){
				source = 'Exception-Load'
			}else if(source ==  'WIPRETURN'){
				source = 'WIP-Return'
			}else if(source ==  'SEMI-PRODUCT'){
				source = 'Semi-Production'
			}else if(source ==  'RECEIVING'){
				source = 'Factory-Receive'
			}else if(source ==  'SENDING'){
				source = 'Factory-Sending'
			}if(source ==  'UNPACK'){
				source = 'Unpack'
			}
			// WIPSENDING 항목은 표시하지 않음
			if(source !=  'WIPSENDING'){
            	const html = createStatusItem(source, stat);
				$('.status-list').append(html);
            }
			
        });
    }
}

//개별 상태 아이템 HTML 생성
function createStatusItem(name, stat) {
	const typeClass = name.toLowerCase().replace(/[^a-z]/g,'');
    
    return `
        <div class="status-item ${typeClass}">
            <div class="status-info">
                <div class="status-indicator ${typeClass}"></div>
                <span class="status-name">${name}</span>
            </div>
            <div class="status-numbers">
				<div class="count-section">
                    <span class="status-count"><span class="status-value-count">${Number(stat.count || 0).toLocaleString()}</span></span>
                </div>
                <div class="qty-section">
                    <span class="status-count"><span class="status-value-sum">${Number(stat.sum || 0).toLocaleString()}</span></span>
                </div>
                
            </div>
        </div>
    `;
}

// 날짜 표시 업데이트
function updateDateDisplay(date) {
    var formattedDate = formatDate(date);
    $('.status-date span').eq(1).text(formattedDate);
}

// 날짜 포맷팅 (YYYY-MM-DD)
function formatDate(date) {
    var year = date.getFullYear();
    var month = String(date.getMonth() + 1).padStart(2, '0');
    var day = String(date.getDate()).padStart(2, '0');
    return year + '-' + month + '-' + day;
}

// 정규식으로 쿠기 가져오기
function getCookie(cookieName) {
	const match = document.cookie.match(new RegExp('(^| )' + cookieName + '=([^;]+)'));
	return match ? decodeURIComponent(match[2]) : '';
}
        