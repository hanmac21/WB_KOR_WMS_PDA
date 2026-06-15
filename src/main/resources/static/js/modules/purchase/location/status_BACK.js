/* --------------------------------------------------------------
 * 📌 구매 - 적재 - LOCATION 현황 (DB 연동 버전)
 * 비고: 수정된 완전 버전 - RACK W 스타일 적용 + 아코디언 기능 + DB 연동
 * -------------------------------------------------------------- */


$(document).ready(function () {
    const storedModule = sessionStorage.getItem('currentModule');
    if (storedModule) {
        window.WMS = window.WMS || {};
        window.WMS.currentModule = storedModule;
        console.log("복원된 모듈:", window.WMS.currentModule);
    } else {
        console.log('메인 화면 또는 모듈 미설정');
    }
    // 🆕 **DB 연동 1**: RACK 목록 데이터를 DB에서 불러오는 함수
    window.loadRackListData = function () {
        return new Promise((resolve, reject) => {
            $.ajax({
                url: '/purchase/rack/list', // 실제 API 엔드포인트로 수정 필요
                type: 'POST',
                dataType: 'json',
                data: {
                    // 필터 조건들
                    storage: $('#rack-filter-storageVal').val() || 'default',
                    factory: $('#rack-filter-factoryVal').val() || 'default',
                    searchType: $('#rack-filter-searchVal').val() || 'searchVal-product',
                    keyword: $('#searchVal-keyword').val() || ''
                },
                success: function (response) {
                    console.log('✅ RACK 목록 데이터 로드 성공:', response);
                    resolve(response.data || response);
                },
                error: function (xhr, status, error) {
                    console.error('❌ RACK 목록 데이터 로드 실패:',
                        {status: xhr.status, statusText: xhr.statusText, error, responseText: xhr.responseText});
                    reject(error);
                }
            });
        });
    }

    // 🆕 **DB 연동 2**: 특정 RACK의 상세 적재 정보를 DB에서 불러오는 함수
    window.loadRackDetailData = function (rackId) {

        //showloading("data")
        showCustomLoading();
        $(".opacityLoading").show();

        showLoading();
        $('#warehouse-display').empty();
        return new Promise((resolve, reject) => {
            $.ajax({
                url: '/purchase/rack/detail', // 실제 API 엔드포인트로 수정 필요
                type: 'POST',
                dataType: 'json',
                data: {
                    rackId: rackId,
                    // 추가 필터 조건이 있다면 포함
                    storage: $('#rack-filter-storageVal').val() || 'default',
                    factory: $('#rack-filter-factoryVal').val() || 'default'
                },
                success: function (response) {
                    hideLoading();
                    console.log('✅ RACK 상세 데이터 로드 성공:', response);
                    resolve(response.data || response);
                },
                error: function (xhr, status, error) {
                    hideLoading();
                    console.error('❌ RACK 상세 데이터 로드 실패:', error);
                    reject(error);
                }
            });
        });
    }

    // 🔥 **라인 58-62 함수 시그니처 변경**: DB 데이터를 받도록 수정
    window.generateStructure = function (rackId, rackData) {
        // 🔥 **라인 63-64 주석처리**: 임시 랜덤 상태 생성 제거
        // const getRandomStatus = () => Math.random() > 0.5 ? 'occupied' : 'empty';
        let html = ``
        if ($("#rack-filter-factoryVal").val() == 'WBTA') {
            html = `
	            <div class="rack-main">
	                <!-- 좌측 레벨 표시 -->
	                <div class="level-sidebar" style="display:none;">
	                    <div class="level-item level-d">Level D</div>
	                    <div class="level-item level-c">Level C</div>
	                    <div class="level-item level-b">Level B</div>
	                    <div class="level-item level-a">Level A</div>
	                </div>
	                
	                <!-- 모듈들 -->
	                <div class="modules-container" id="modules-container">
	        `;
        } else {
            html = `
	            <div class="rack-main">
	                <!-- 좌측 레벨 표시 -->
	                <div class="level-sidebar" style="display:none;">
	                    <div class="level-item level-d">Level 4</div>
	                    <div class="level-item level-c">Level 3</div>
	                    <div class="level-item level-b">Level 2</div>
	                    <div class="level-item level-a">Level 1</div>
	                </div>
	                
	                <!-- 모듈들 -->
	                <div class="modules-container" id="modules-container">
	        `;
        }


        // 🆕 데이터가 없어도 기본 모듈 수 설정
        const moduleCount = rackData?.MODULES ?? 1;  // 기본값 9

        // ⭐ 데이터가 아예 없으면 기본 rackData 생성
        if (!rackData || Object.keys(rackData).length === 0) {
            rackData = {
                factory: $('#rack-filter-factoryVal').val() || 'WBTA',
                STORAGE: $('#rack-filter-storageVal').val() || 'INBOUND',
                MODULES: 9,
                modules: []  // 빈 배열
            };
            console.warn('⚠️ RACK 데이터 없음 - 기본 구조 생성');
        }

        // Module 생성 - DB 데이터 기반으로 수정
        for (let i = 1; i <= moduleCount; i++) {
            const num = i;

            let moduleNum = String(num).padStart(2, '0');

            // 🆕 **DB 연동 5**: 해당 모듈의 DB 데이터 가져오기
            const moduleData = rackData?.modules?.[num - 1] || {};

            html += `
	                <div class="module">
	                    <div class="module-header">Module ${moduleNum}</div>
	                    <div class="position-headers">
	                        <div class="position-header">Position 1</div>
	                        <div class="position-header">Position 2</div>
	                    </div>
	                    
	                    <!-- Level D -->
	                    <div class="level-row level-d-bg">
	            `;

            // 각 레벨(D, C, B, A)과 포지션(1, 2) 처리
            ['4', '3', '2', '1'].forEach(level => {
                if (level !== '4') {
                    let levelClass = ""
                    if (level === '3') {
                        levelClass = 'c'
                    } else if (level === '2') {
                        levelClass = 'b'
                    } else if (level === '1') {
                        levelClass = 'a'
                    }
                    html += `
	                    </div>
	                    
	                    <!-- Level ${level} -->
	                    <div class="level-row level-${levelClass}-bg">
	                    `;
                }

                for (let pos = 1; pos <= 2; pos++) {
                    // isValid 에 따라 pos 방향 결정
                    let realPos = "";
                    if (pos === 1) {
                        realPos = 'L'
                    } else if (pos === 2) {
                        realPos = 'R'
                    }

                    const positionId = `${rackId}`;

                    // 🆕 **DB 연동 6**: DB에서 해당 포지션의 실제 데이터 가져오기 //개선
                    const isRackOnly = moduleData?.positions?.some(p => p.level === null && p.position === null);

                    let positionData = {};

                    if (isRackOnly) {
                        // rack-only 모드: 모듈1, 레벨1, 포지션L(첫번째)에만 데이터 표시
                        if (num === 1 && level === '1' && realPos === 'L') {
                            positionData = moduleData?.positions?.[0] || {};
                        }
                    } else {
                        positionData = moduleData?.positions?.find(p =>
                            p.positionId === positionId ||
                            (p.module == num && p.level == level && p.position == realPos)
                        ) || {};
                    }
                    console.log(" LOCA INFO - POS ")
                    console.log(positionData)
                    console.log(moduleData)
                    console.log(positionId)

                    const status = positionData.status || 'empty'; // 'occupied' or 'empty'
                    //const warehouseInfo = positionData.carInfo || {};

                    html += `
	                    <div class="position-box ${status}" data-position="${positionId}" onclick="clickPosition('${positionId}', '${status}', ${JSON.stringify(positionData).replace(/"/g, '&quot;')})">
	                `;

                    // 🔥 **라인 122, 136, 150, 164 임시 차량 정보 주석처리 및 실제 데이터 사용**
                    console.log(" LOCA INFO ")
                    if (status === 'occupied') {
                        // DB에서 받은 실제 차량 정보 사용
                        html += `<div class="car-info">QTY : ${positionData.carname || 'N/A'}<br>${positionData.itemcode || 'N/A'}<br></div>`;
                        html += `<div class="pallet-icon"><img src="/images/rbg_pallet.png"></div>`;
                    } else {
                        html += `<div class="car-info-empty">EMPTY</div>`;
                    }

                    html += `
	                        <div class="position-label">${positionId}<span></span></div>
	                    </div>
	                    `;
                }
            });


            html += `
                    </div>
                </div>
            `;
        }

        html += `
                </div>
            </div>
        `;

        $('#warehouse-display').html(html);

        // 🆕 가로 스크롤 기능 추가
        setupHorizontalScroll();

        inputSideBar();

        //hideloading();
        hideCustomLoading();
        $(".opacityLoading").hide();
    }

    window.clickPosition = function (location, status) {
        // 모달에 데이터 설정
        $('#modalLocation').text(location);
        location = $("#rack-filter-factoryVal").val() + "-" + $("#rack-filter-storageVal").val() + "-" + location;

        $.ajax({
            url: "/purchase/rack/locationDetail",
            method: 'POST',
            data: {location},
            success: function (result) {
                console.log(result);

                renderInfo(result.list);
            },
        });
    }
    let detailLocation = "";

    function renderInfo(list) {
        const infoListBody = $(".modal-body");

        infoListBody.find('.modal-data').remove();

        var infoHtml = "";
        list.forEach(function (data) {
            detailLocation = data.LOCATION
//			console.log("DEBUG -- DATA;")
//			console.log(data);
            infoHtml += `
					<div class="modal-data">							
						<div class="modal-info">
							<div class="info-label">Item Code</div>
							<div class="info-value" id="modalItemCode">${data.ITEMCODE}</div>
						</div>
						<div class="modal-info">
							<div class="info-label">Item Name</div>
							<div class="info-value" id="modalItemName">${data.ITEMNAME}</div>
						</div>
						<div class="modal-info">
							<div class="info-label">Qty</div>
							<div class="info-value" id="modalCarType">${data.QTY}</div>
						</div>
						<div class="modal-info">
							<div class="info-label">Incoming Date</div>
							<div class="info-value" id="modalInboundDate">${data.SDATE}</div>	
						</div>
						<div class="modal-info">
							<div class="info-label">Storage Date</div>
							<div class="info-value" id="modalStorageDate">${data.LOCDATE}</div>
						</div>
						<div class="modal-info">
							<div class="info-label">LOT</div>
							<div class="info-value" id="modalLot">${data.LOT}</div>
						</div>
						<div class="modal-info">
							<div class="info-label">Barcode</div>
							<div class="info-value info-barcode" >${data.BARCODE}</div>
						</div>
						<div class= "btn-area">
							<button class= "delete-btn" data-barcode=${data.BARCODE}>Delete</button>
							<!--<button class= "unload-btn" data-barcode=${data.BARCODE}>Unload</button>-->
						</div>
					</div>
			`;
        });
        infoListBody.append(infoHtml);

        // 모달 표시
        $('#positionModal').css("display", "flex");

        hideLoading();
    }

    // 🆕 아코디언 토글 함수
    window.toggleAccordion = function () {
        const accordionContent = $('#accordion-content');
        const accordionHeader = $('#accordion-toggle');

        accordionContent.toggleClass('active');
        accordionHeader.toggleClass('active');

        console.log('🔄 아코디언 토글');
    }

    // 🆕 필터 상태 업데이트 함수
    window.updateFilterStatus = function () {
        const storage = $('#rack-filter-storageVal').val();
        const factory = $('#rack-filter-factoryVal').val();
        const searchType = $('#rack-filter-searchVal').val();
        const keyword = $('#searchVal-keyword').val();

        let activeFilters = [];

        if (storage !== 'default') activeFilters.push('저장소');
        if (factory !== 'default') activeFilters.push('공장');
        if (searchType !== 'default') activeFilters.push('종류');
        if (keyword && keyword.trim() !== '') activeFilters.push('키워드');

        const filterStatusText = activeFilters.length === 0 ? m('filter.search.all') : mf('filter.search.info', activeFilters.length)/*${activeFilters.length}개 필터 적용*/;
        $('#filter-status').text(filterStatusText);

        console.log('📊 필터 상태 업데이트:', filterStatusText);
    }


    // 공장에 따른 창고 옵션 초기화
    function initFactoryStorageFilter($root) {
        const part1 = $root.find('#rack-filter-factoryVal')[0]; // 공장 select
        const part2 = $root.find('#rack-filter-storageVal')[0]; // 창고 select
        if (!part1 || !part2) return;

        // 현재 렌더된 옵션을 템플릿으로 스냅샷
        const template = Array.from(part2.options).map(o => ({value: o.value, label: o.text}));

        // 공장별 허용 규칙
        const rules = {
            'WBTA': ['INBOUND', 'OUTBOUND', 'ILLI'],
        };

        // 공장별 기본 창고
        const defaults = {
            'WBTA': 'INBOUND',
        };

        const norm = v => String(v || '').trim();

        function updatePart2(siteRaw) {
            const site = norm(siteRaw);
            const allowed = rules[site] || [];
            const prefer = defaults[site];
            let initialVal = prefer;

            if (window.WMS?.currentModule === "sales") {
                initialVal = "Fabric";
            }

            // 🔑 템플릿 쓰지 말고 '허용값'으로 새로 구성 → 케이스를 rules에 맞춰 강제
            part2.innerHTML = '';

            if (allowed.length) {
                // 중복 방지(허용값 안에 중복이 없더라도 안전하게)
                const seen = new Set();
                allowed.forEach(v => {
                    const key = String(v).trim();
                    if (!seen.has(key.toLowerCase())) {
                        // 라벨도 값과 동일하게(푸에블라는 대문자 유지)
                        part2.add(new Option(key, key));
                        seen.add(key.toLowerCase());
                    }
                });
            } else {
                // 허용값이 없으면 원본 유지(필요 시 제거 가능)
                template.forEach(opt => part2.add(new Option(opt.label, opt.value)));
            }

            // 기본값 선택 (없으면 첫 번째)
            if (prefer && Array.from(part2.options).some(o => o.value === initialVal)) {
                part2.value = initialVal;
            } else if (part2.options.length) {
                part2.selectedIndex = 0;
            }

            part2.dispatchEvent(new Event('change'));
        }

        // 공장 선택 변경 시 창고 갱신
        part1.addEventListener('change', () => updatePart2(part1.value));

        // ✅ 로컬스토리지 값 우선 반영
        const storedFactory = norm(localStorage.getItem('rememberedFactory'));
        if (storedFactory) {
            // part1에 해당 값이 존재하면 선택
            const hasOption = Array.from(part1.options).some(
                o => norm(o.value).toLowerCase() === storedFactory.toLowerCase()
            );
            if (hasOption) {
                // 실제 옵션의 원래 value로 맞춰줌 (대소문자 유지)
                const realVal = Array.from(part1.options).find(
                    o => norm(o.value).toLowerCase() === storedFactory.toLowerCase()
                ).value;
                part1.value = realVal;
            }
        }

        // 초기 1회 적용
        updatePart2(part1.value);
    }


    // 🔥 **라인 235-242 함수 수정**: DB 연동으로 검색 기능 개선
    window.performSearch = function () {
        console.log('🔍 검색 실행');
        updateFilterStatus();

        // 🆕 **DB 연동 8**: 필터 조건에 따른 RACK 목록 다시 로드
        loadRackListData().then(function (rackListData) {
            updateRackList(rackListData);
        }).catch(function (error) {
            console.error('검색 실패:', error);
        });

        // 검색 후 아코디언 닫기
        //$('#accordion-content').removeClass('active');
        //$('#accordion-toggle').removeClass('active');
    }

    // 🆕 **DB 연동 9**: RACK 목록을 업데이트하는 함수
    window.updateRackList = function (rackListData) {
        const rackListContainer = $('.rack-list-area');
        const headerAndFilter = rackListContainer.find('.rack-list-header, .accordion-container');

        // 기존 RACK 아이템들 제거
        rackListContainer.find('.rack-item').remove();
        rackListContainer.find('.btn_unstorageItems').remove();

        // 새로운 RACK 아이템들 추가
        var rackHtml = "";
        rackListData.forEach(function (rack) {
            //console.log("DEBUG __ rack")
            //console.log(rack)
            const cap = Number(rack.TOTAL_STORAGE_CAPACITY) || 0;
            const cur = Number(rack.currentCount) || 0;
            const percent = cap ? Math.round((cur / cap) * 100) : 0;
            rackHtml += `
				<div class="rack-item" data-rack="${rack.rackId}" onclick="selectRack('${rack.rackId}')">
					<div class="rack-name">${rack.rackId}</div>
					<div class="rack-usage">${percent}%</div>
					<div class="rack-details">${rack.currentCount} / ${rack.TOTAL_STORAGE_CAPACITY}</div>
				</div>
			`;
            /*					<div class="rack-details">${rack.currentCount} / ${rack.totalCapacity}</div>*/
        });

        rackHtml += `
			<div class="unstorageItemsArea">
				<input type="button" class="btn_unstorageItems" value="Unregistered storage items">
			</div>
		`;

        rackListContainer.innerHTML = '';
        rackListContainer.append(rackHtml);

        // 전체 적재율 업데이트
        const totalCurrent = rackListData.reduce((sum, rack) => sum + rack.currentCount, 0);
        const totalCapacity = rackListData.reduce((sum, rack) => sum + rack.TOTAL_STORAGE_CAPACITY, 0);
        const overallRate = totalCapacity > 0 ? Math.round((totalCurrent / totalCapacity) * 100) : 0;

        $('.rack-filter-resultArea').html(`
			${m('filter.title')}<br>
			<span style="font-size: 13pt; opacity: 0.8;">${overallRate}% | ${totalCurrent} / ${totalCapacity}</span>
		`);
    }

    // 전역 함수로 등록
    window.call_m2_5_2 = function () {
        console.log('🚀 call_m2_5_2 호출됨');

        let content_output = `
	        <div class="divBlockControl" id="view_m2_5_2">
	            <!-- 좌측 RACK 리스트 영역 -->
	            <div class="rack-list-area">
	                <div class="rack-list-header">
	                	<div class="rack-filter-resultArea">
		                    ${m('filter.title')}<br>
		                    <span style="font-size: 11pt; opacity: 0.8;">${m('info.loading')}</span>
	                	</div>
	                	
	                	<!-- 🆕 아코디언 필터 영역 -->
	                	<div class="accordion-container">
	                		<div class="accordion-header active" id="accordion-toggle" style = 'display:none'onclick="toggleAccordion()">
	                			<div class="accordion-title">
	                				<span>🔍</span>
	                				<span>${m('filter.search')}</span>
	                				<span class="filter-status" id="filter-status">${m('filter.search.all')}</span>
	                			</div>
	                			<div class="accordion-icon">▼</div>
	                		</div>
	                		
	                		<div class="accordion-content active" id="accordion-content">
			                	<div class="rack-filter-area">
					                <div class="filter-group">
					                    <span class="filter-label">${m('table.factory')}</span>
					                    <select id="rack-filter-factoryVal" onchange="updateFilterStatus()">
					                        <option value="WBTA" selected>WBTA</option>
					                    </select>
					                </div>
					                
					                <div class="filter-group">
					                    <label class="filter-label">${m('table.storage')}</label>
					                    <select id="rack-filter-storageVal" onchange="updateFilterStatus()">
					                        <option value="INBOUND" selected>INBOUND</option>
					                        <option value="OUTBOUND">OUTBOUND</option>
					                        <option value="ILLI">ILLI</option>
					                    </select>
					                </div>
					                
					                <div class="search-section">
					                    <div class="search-input-group">
					                        <button class="search-btn" id="rack-btnSearch" onclick="performSearch()">${m('btn.search')}</button>
					                    </div>
					                </div>
					            </div>
	                		</div>
	                	</div>
	                </div>
	            </div>
	
	            <!-- 우측 구조도 영역 -->
	            <div class="structure-area" style="display: none;" id="structure-area">
				    <div class="structure-header">
				        <div class="structure-title" id="current-rack-title">RACK을 선택해주세요</div>
				        <div class="structure-info" id="current-rack-info">적재율 · 적재량 / 총 적재량</div>
				    </div>
				    <div class="warehouse-structure" id="warehouse-display">
				        <!-- 구조도 내용이 들어갈 곳 -->
				    </div>
				</div>
				
				<!-- 초기 안내 영역 추가 -->
				<div class="initial-guide" id="initial-guide">
				    <div class="guide-content">
				        <h3>📦</h3>
				        <h3>RACK 선택</h3>
				        <p>좌측에서 RACK을 선택하면 상세 구조도가 표시됩니다</p>
				    </div>
				</div>
	        </div>
        `;

        // 기존 내용 제거 후 새 내용 추가
        $("#view_m2_5_2").remove();
        $(".w_contentArea").prepend(content_output);

        // ✅ 필터 초기화 (여기서 즉시 실행해야 동작)
        initFactoryStorageFilter($('#view_m2_5_2'));

        // 🆕 **DB 연동 10**: 페이지 로드 시 초기 RACK 목록 데이터 불러오기
        loadRackListData().then(function (rackListData) {
            updateRackList(rackListData);
            updateFilterStatus();
        }).catch(function (error) {
            console.error('초기 데이터 로드 실패:', error);
            // 에러 시 기본 메시지 표시
            $('.rack-filter-resultArea').html(`
				${m('filter.title')}<br>
				<span style="font-size: 11pt; opacity: 0.8;">${m('error.failed.data.load')}</span>
			`);
        });

        // 모달 이벤트 바인딩
        $(document).on('click', '.close', function () {
            $('#positionModal').hide();
        });

        $(document).on('click', function (event) {
            if (event.target.id === 'positionModal') {
                $('#positionModal').hide();
            }
        });

        console.log('✅ HTML 삽입 완료');
    }
    $(document).on("click", ".unload-btn", function () {
        const barcode = $(this).data("barcode"); // 자동으로 원래 문자열 그대로 가져옴
        console.log("Unload barcode:", barcode);
        unloadItem(barcode); // 원하는 함수 호출
    });
    console.log('📚 스크립트 로드 완료');
    window.unloadItem = function (barcode) {
        console.log("detailLocation : " + detailLocation);
        let modalLocation = $("#modalLocation").text();
        showLoading();
        Utils.showConfirm("Do you want to unload?", () => {
                $.ajax({
                    url: '/purchase/location-unload', //
                    type: 'POST',
                    data: {
                        location: detailLocation,
                        barcode: barcode,
                        factory: localStorage.getItem('rememberedFactory'),
                    },
                    success: function (result) {
                        let response = result.response;
                        if (response == 'ok') {
                            Utils.showAlert("It has been unloaded.");
                            $("#positionModal").css("display", "none");
                            $('.module-main').trigger('click');
                        } else {
                            Utils.showAlert("Unload failed. Please try again.");
                        }
                        hideLoading();
                    },
                    error: function (xhr, status, error) {
                        console.error('❌ RACK 목록 데이터 로드 실패:',
                            {status: xhr.status, statusText: xhr.statusText, error, responseText: xhr.responseText});
                        reject(error);
                        hideLoading();
                    }
                });
            },
            () => {
                Utils.showAlert(m("success.cancel.sendAll"), "#008000");
                hideLoading();
            });
    }

    $(document).on("click", ".delete-btn", function () {
        const barcode = $(this).data("barcode"); // 자동으로 원래 문자열 그대로 가져옴
        console.log("Delete barcode:", barcode);
        deleteItem(barcode); // 원하는 함수 호출
    });
    window.deleteItem = function (barcode) {
        console.log("detailLocation : " + detailLocation);
        let modalLocation = $("#modalLocation").text();
        showLoading();
        Utils.showConfirm("Do you want to delete?", () => {
                $.ajax({
                    url: '/purchase/location-delete-exload', //
                    type: 'POST',
                    data: {
                        location: detailLocation,
                        barcode: barcode,
                        factory: localStorage.getItem('rememberedFactory'),
                    },
                    success: function (result) {
                        let response = result.response;
                        if (response == 'ok') {
                            Utils.showAlert("It has been deleted.");
                            $("#positionModal").css("display", "none");
                            $('.module-main').trigger('click');
                        } else {
                            Utils.showAlert("Unload failed. Please try again.");
                        }
                        hideLoading();
                    },
                    error: function (xhr, status, error) {
                        console.error('❌ RACK 목록 데이터 로드 실패:',
                            {status: xhr.status, statusText: xhr.statusText, error, responseText: xhr.responseText});
                        reject(error);
                        hideLoading();
                    }
                });
            },
            () => {
                Utils.showAlert(m("success.cancel.sendAll"), "#008000");
                hideLoading();
            });
    }
    //1. 스크롤 위치 저장하는 함수
    window.saveScrollPosition = function () {
        const modulesContainer = document.getElementById('modules-container');
        if (modulesContainer) {
            const scrollLeft = modulesContainer.scrollLeft;
            const rackId = localStorage.getItem('lastSelectedRack');

            if (rackId) {
                localStorage.setItem(`rackScrollPosition_${rackId}`, scrollLeft);
                console.log(`💾 스크롤 위치 저장: RACK ${rackId}, 위치: ${scrollLeft}px`);
            }
        }
    }

    // 2. 스크롤 위치 복원하는 함수
    window.restoreScrollPosition = function (rackId) {
        const savedScrollPosition = localStorage.getItem(`rackScrollPosition_${rackId}`);

        if (savedScrollPosition !== null) {
            const scrollLeft = parseInt(savedScrollPosition);

            // DOM이 완전히 렌더링된 후에 스크롤 위치 복원
            setTimeout(() => {
                const modulesContainer = document.getElementById('modules-container');
                if (modulesContainer) {
                    modulesContainer.scrollLeft = scrollLeft;
                    console.log(`🔄 스크롤 위치 복원: RACK ${rackId}, 위치: ${scrollLeft}px`);

                    // 스크롤 이동 애니메이션을 위한 부드러운 이동 (선택사항)
                    modulesContainer.scrollTo({
                        left: scrollLeft,
                        behavior: 'smooth'
                    });
                }
            }, 500); // 구조도 생성 후 0.5초 후 스크롤 복원
        } else {
            console.log(`📍 저장된 스크롤 위치 없음: RACK ${rackId}`);
        }
    }

    // 3. 수정된 RACK 선택 함수 (스크롤 위치 저장 포함)
    window.selectRack = function (rackId) {
        console.log('RACK 선택됨:', rackId);

        // 🆕 이전 RACK의 스크롤 위치 저장 (다른 RACK으로 이동할 때)
        /* const currentRack = localStorage.getItem('lastSelectedRack');
         if (currentRack && currentRack !== rackId) {
             saveScrollPosition();
         }*/

        // localStorage에 선택된 RACK 저장
        localStorage.setItem('lastSelectedRack', rackId);
        localStorage.setItem('lastSelectedRackTime', new Date().getTime());

        // 초기 안내 영역 숨기고 구조도 영역 표시
        $('#initial-guide').hide();
        $('#structure-area').show();

        // Active 클래스 토글
        $('.rack-item').removeClass('active');
        $(`[data-rack="${rackId}"]`).addClass('active');

        // 헤더 업데이트
        $('#current-rack-title').text(`RACK ${rackId}`);

        // DB 연동으로 실제 RACK 정보 불러오기
        loadRackDetailData(rackId).then(function (rackData) {
            console.log("DEB 1 - rackData");

            // DB에서 받은 데이터로 헤더 정보 업데이트
            const utilizationRate = rackData.utilizationRate || rackData.UTILIZATIONRATE || 0;
            const currentCount = rackData.currentCount || rackData.CURRENTCOUNT || 0;
            const totalCapacity = rackData.totalCapacity || rackData.TOTALCAPACITY || 0;

            $('#current-rack-info').text(`${utilizationRate}% ${currentCount} / ${totalCapacity}`);

            // 디버깅을 위한 전역 데이터 저장
            window.currentRackData = rackData;

            // 구조도 생성 (DB 데이터 전달)
            generateStructure(rackId, rackData);

            // 🆕 구조도 생성 후 스크롤 위치 복원
            //restoreScrollPosition(rackId);

        }).catch(function (error) {
            console.error('RACK 데이터 로드 실패:', error);
            $('#current-rack-info').text('데이터 로드 실패');

            // 실패 시 저장된 정보 제거
            localStorage.removeItem('lastSelectedRack');
            localStorage.removeItem('lastSelectedRackTime');
            localStorage.removeItem(`rackScrollPosition_${rackId}`);
        });
    }

    // 4. 수정된 가로 스크롤 설정 함수 (스크롤 이벤트 리스너 추가)
    window.setupHorizontalScroll = function () {
        const modulesContainer = document.getElementById('modules-container');

        if (modulesContainer) {
            // 기존 휠 이벤트
            modulesContainer.addEventListener('wheel', function (e) {
                e.preventDefault();
                const scrollAmount = e.deltaY * 2;
                modulesContainer.scrollLeft += scrollAmount;
            });

            // 🆕 스크롤 이벤트 리스너 추가 (스크롤 위치 자동 저장)
            let scrollTimeout;
            modulesContainer.addEventListener('scroll', function (e) {
                // 스크롤이 멈춘 후 300ms 후에 위치 저장 (성능 최적화)
                clearTimeout(scrollTimeout);
                scrollTimeout = setTimeout(() => {
                    saveScrollPosition();
                }, 300);
            });

            console.log('✅ 가로 스크롤 및 위치 저장 설정 완료');
        }
    }

    // 6. 특정 모듈로 스크롤하는 헬퍼 함수 (추가 기능)
    window.scrollToModule = function (moduleNumber, smooth = true) {
        const modulesContainer = document.getElementById('modules-container');
        if (!modulesContainer) return;

        // 각 모듈의 예상 너비 (실제 CSS에 따라 조정 필요)
        const moduleWidth = 300; // 모듈 하나당 대략적인 너비 (px)
        const targetScrollLeft = (moduleNumber - 1) * moduleWidth;

        if (smooth) {
            modulesContainer.scrollTo({
                left: targetScrollLeft,
                behavior: 'smooth'
            });
        } else {
            modulesContainer.scrollLeft = targetScrollLeft;
        }

        console.log(`📍 모듈 ${moduleNumber}로 스크롤 이동: ${targetScrollLeft}px`);
    }

    $(document).on('click', '.module-main', function () {
        const currentRackId = localStorage.getItem('lastSelectedRack');

        if (!currentRackId) {
            console.log('선택된 RACK이 없습니다.');
            return;
        }

        console.log('🔄 RACK 데이터 새로고침:', currentRackId);

        // 로딩 표시
        showCustomLoading();
        $(".opacityLoading").show();

        // 현재 스크롤 위치 저장
        saveScrollPosition();

        // DB에서 최신 데이터 다시 불러오기
        loadRackDetailData(currentRackId).then(function (rackData) {
            console.log('✅ RACK 데이터 새로고침 완료:', rackData);

            // 헤더 정보 업데이트
            const utilizationRate = rackData.utilizationRate || rackData.UTILIZATIONRATE || 0;
            const currentCount = rackData.currentCount || rackData.CURRENTCOUNT || 0;
            const totalCapacity = rackData.totalCapacity || rackData.TOTALCAPACITY || 0;

            $('#current-rack-info').text(`${utilizationRate}% ${currentCount} / ${totalCapacity}`);

            // 전역 데이터 업데이트
            window.currentRackData = rackData;

            // 구조도 다시 생성
            generateStructure(currentRackId, rackData);

            // 저장된 스크롤 위치로 복원
            restoreScrollPosition(currentRackId);

        }).catch(function (error) {
            console.error('❌ RACK 데이터 새로고침 실패:', error);
            alert('데이터를 불러오는데 실패했습니다.');

            hideCustomLoading();
            $(".opacityLoading").hide();
        });
    });

    call_m2_5_2();
});

//미적재 리스트 버튼 클릭 이벤트
$(document).on("click", ".btn_unstorageItems", function () {
    loadUnloadedPage();
});

function inputSideBar() {
    let level_low, level_up;
    if ($('#rack-filter-factoryVal').val() == 'WBTA') {
        level_low = ["a", "b", "c", "d"];
        level_up = ["A", "B", "C", "D"];
    } else {
        level_low = ["1", "2", "3", "4"];
        level_up = ["1", "2", "3", "4"];
    }
    for (i = 0; i < 4; i++) {
        if (level_up[i] == 1) {
            level_low[i] = 'a'
        } else if (level_up[i] == 2) {
            level_low[i] = 'b'
        } else if (level_up[i] == 3) {
            level_low[i] = 'c'
        } else if (level_up[i] == 4) {
            level_low[i] = 'd'
        }
        let output = `
			<div class="level-item level-${level_low[i]}">Level ${level_up[i]}</div> 
		`;
        $(".level-" + level_low[i] + "-bg").first().prepend(output);
    }
    $(".module-header").first().prepend(`
		<div class="modal-undo"><img alt="" src="/images/arrowBack.png"></div>
	`)


    $(".module").first().css({
        flex: '0 0 325px'
    })
    $(".module-header").first().css({
        width: '267px',
        'margin-left': 'auto',
        display: 'flex',
        'justify-content': 'center',
        'align-items': 'center'
    })
    $(".position-headers").first().css({
        width: '267px',
        'margin-left': 'auto'
    })
    $(".module .position-box:first-child").first().css({
        width: '133px !important',
    })
    $(".module .position-box").first().css({
        width: '133px',
        'margin-left': 'auto'

    })

    $(".position-box").css({
        height: '100%'
    })

}


let totalCount = 0;


//✅ 미적재 리스트 모달 렌더링 (페이징 포함)
function renderUnLoadedModal(data) {
    const {list = [], total = 0} = data;

    // 전역 변수 업데이트
    totalCount = total;

    const rows = list.map((row, i) => {
        const barcode = row.barcode ?? row.BARCODE ?? '';
        const sdate = row.sdate ?? row.SDATE ?? '';
        const car = row.car ?? row.CAR ?? '';
        const itemcode = row.itemcode ?? row.ITEMCODE ?? '';
        const itemname = row.itemname ?? row.ITEMNAME ?? '';
        const qty = row.qty ?? row.QTY ?? '';
        const idx = i + 1;

        return `
		<tr>
			<td class="idx">${idx}</td>	
			<td class="sdate">${sdate}</td>
			<td class="barcode">${barcode}</td>
		</tr>`;

    }).join('');

    const tableHtml = list.length > 0
        ? `
     <div class="table-wrap">
         <table class="unloaded-table">
             <thead>
                 <tr>
                     <th>${m('table.no')}</th>
					 <th>${m('table.date')}</th>
                     <th>${m('table.barcode')}</th>
                 </tr>
             </thead>
             <tbody>${rows}</tbody>
         </table>
     </div>`
        : `<div class="empty">No Data.</div>`;


    const modalHtml = `
 		<div class="m2_5_2_mainDiv">
 			<div class="modal-header" style="margin-bottom:0px !important;">
            	<h3 class="modal-title">${m('title.modal.unloaded.list')}(${mhtml('table.total', totalCount)})</h3>
             	<button class="modal-close" onclick="closeModal()">&times;</button>
         	</div>
 			<div class="modal-body m2_5_2">
 				${tableHtml}
 			</div>
 		</div>`;

    // 기존 컨테이너에 삽입 + 표시
    $("#editModal").empty().prepend(modalHtml).css('display', 'flex');
    hideLoading();
}


//전역 함수로 선언
window.loadUnloadedPage = function () {
    showLoading("data");
    let data = {
        factory: $("#rack-filter-factoryVal").val(),
        storage: $("#rack-filter-storageVal").val()
    }
    $.ajax({
        url: '/purchase/rack/unloaded',
        type: 'POST',
        contentType: 'application/json; charset=UTF-8',
        dataType: 'json',
        data: JSON.stringify(data),
        success: function (result) {
            renderUnLoadedModal(result);
        },
        error: function (xhr, status, error) {
            console.error('❌ 미적재리스트 로드 실패:', error);
            alert('미적재리스트 로드에 실패했습니다.');
        },
        complete: function () {
            hideLoading();
        }
    });
};


function closeModal() {
    $("#editModal").empty().css('display', 'none');
}


$(document).on("click", ".modal-undo img", function () {
    $(".structure-area").hide();
});


// 로딩 표시
function showCustomLoading() {
    $('.opacityLoading').show();
    $('body').addClass('loading-active');
}

// 로딩 숨기기
function hideCustomLoading() {
    $('.opacityLoading').hide();
    $('body').removeClass('loading-active');
}
