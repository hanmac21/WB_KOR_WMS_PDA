// 인터넷 연결 확인 + 안 되면 안드로이드 알림창 띄우고 false 반환
function checkNetwork() {
  if (window.AndroidInterface && !AndroidInterface.isNetworkConnected()) {
    AndroidInterface.showNoInternetDialog();
    return false;
  }
  return true;
}
// wms-main.js - 메인 앱 로직
class WMSApp {
    constructor() {		// 생성자
        this.currentMenu = 'main';		// 현재메뉴
        this.menuHistory = [];			// 이전메뉴기록(뒤로가기)
        this.init();					// 초기화 실행
    }

    init() {
        this.setupEventListeners();		// 버튼 및 브라우저 이벤트 리스너 등록
        this.setCurrentDate();			// 현재날짜 표시
        this.showMainMenu();			// 메인 메뉴 화면 보여줌
    }

    setupEventListeners() {				//뒤로가기
        // 뒤로가기 버튼
        document.getElementById('backBtn').addEventListener('click', () => {
            this.goBack();
        });

        // 브라우저 뒤로가기
        window.addEventListener('popstate', (e) => {
            if (e.state && e.state.menu) {
                this.navigateToMenu(e.state.menu, false);
            } else {
                this.showMainMenu();
            }
        });
    }

    setCurrentDate() {					// 현재날짜
        const now = new Date();
        const dateStr = now.toISOString().split('T')[0];
        document.getElementById('currentDate').textContent = dateStr;
    }

    showLoading() {						// 로딩보여주기
        document.getElementById('loading').classList.remove('hidden');
		loading.style.display = 'flex';
    }

    hideLoading() {						// 로딩숨기기
        document.getElementById('loading').classList.add('hidden');
		loading.style.display = 'none';
    }

    updateHeader(title, showBackBtn = false) {	//
        document.getElementById('headerTitle').textContent = title;
        const backBtn = document.getElementById('backBtn');
        
        if (showBackBtn) {
            backBtn.classList.remove('hidden');
        } else {
            backBtn.classList.add('hidden');
        }
    }

    showMainMenu() {						// 메인 메뉴 화면 보여줌
        this.showLoading();
        
        setTimeout(() => {
            const content = document.getElementById('content');
            content.innerHTML = MenuTemplate.getMainMenu();
            
            // 메인 메뉴 이벤트 리스너 등록
            this.setupMainMenuListeners();
            
            this.updateHeader('WMS 시스템', false);
            this.currentMenu = 'main';
            this.hideLoading();
        }, 100);
    }

    setupMainMenuListeners() {				// 메인 메뉴 이벤트 리스너 
        // 구매 메뉴
        document.getElementById('purchaseMenu').addEventListener('click', () => {
            this.navigateToMenu('purchase');
        });

        document.getElementById('productionSalesMenu').addEventListener('click', () => {
            this.showComingSoon('구매,생산,영업(푸에블라)');
        });

        document.getElementById('productionMenu').addEventListener('click', () => {
            this.showComingSoon('생산(살티오)');
        });

        document.getElementById('salesMenu').addEventListener('click', () => {
            this.navigateToMenu('영업');
        });

        document.getElementById('qualityMenu').addEventListener('click', () => {
            this.showComingSoon('품질');
        });
    }

    navigateToMenu(menuType, addToHistory = true) {
        if (addToHistory) {
            this.menuHistory.push(this.currentMenu);
            // 브라우저 히스토리에 추가
            history.pushState({menu: menuType}, '', `#${menuType}`);
        }

        this.showLoading();

        setTimeout(() => {
            switch(menuType) {
                case 'purchase':
                    this.showPurchaseMenu();
                    break;
                case 'incoming-local':
                    this.showReceivingLocalMenu();
                    break;
				case 'incoming-ckd':
                    this.showReceivingCkdMenu();
                    break;
				case 'location/load':
                    this.showLocationMenu();
                    break;
				case 'wip/input':
                    this.showProcessIssueMenu();
                    break;
				case 'stock-count/barcode':
                    this.showStockCheckMenu();
                    break;
                default:
                    this.showMainMenu();
            }
            this.hideLoading();
        }, 200);
    }

    showPurchaseMenu() {
        const content = document.getElementById('content');
        content.innerHTML = PurchaseModule.getMenuHTML();
        
        // 구매 메뉴 이벤트 리스너 등록
        PurchaseModule.setupEventListeners(this);
        
        this.updateHeader('구매', true);
        this.currentMenu = 'purchase';
    }

    showReceivingLocalMenu() {
        const content = document.getElementById('content');
        content.innerHTML = showReceivingLocalMenu.getMenuHTML();
        
        // 입고등록 이벤트 리스너 등록
        showReceivingLocalMenu.setupEventListeners(this);
        
        this.updateHeader('입고등록(가상수입, 로컬)', true);
        this.currentMenu = 'incoming-local';
    }
	
	showProcessIssueMenu() {
        const content = document.getElementById('content');
        content.innerHTML = ReceivingCkdModule.getMenuHTML();
        
        // 입고등록 이벤트 리스너 등록
        ReceivingCkdModule.setupEventListeners(this);
        
        this.updateHeader('공정불출', true);
        this.currentMenu = 'process-issue';
    }
	
	showLocationMenu() {
        const content = document.getElementById('content');
        content.innerHTML = LocationStorageModule.getMenuHTML();
        
        // 로케이션 적재
        LocationStorageModule.setupEventListeners(this);
        
        this.updateHeader('Location 적재', true);
        this.currentMenu = 'location/load';
    }

	showStockCheckMenu() {
		console.log("재고실사 클릭")
        const content = document.getElementById('content');
        content.innerHTML = StockCheckModule.getMenuHTML();
        
        // 입고등록 이벤트 리스너 등록
        StockCheckModule.setupEventListeners(this);
        
        this.updateHeader('재고실사', true);
        this.currentMenu = 'stock-count/barcode';
    }
	
    showComingSoon(menuName) {
        Utils.showAlert(`${menuName} 메뉴는 준비중입니다.`);
    }

    goBack() {
        if (this.menuHistory.length > 0) {
            const previousMenu = this.menuHistory.pop();
            this.navigateToMenu(previousMenu, false);
            // 브라우저 히스토리 뒤로가기
            history.back();
        } else {
            this.showMainMenu();
        }
    }
}

// 앱 초기화
document.addEventListener('DOMContentLoaded', () => {
    window.wmsApp = new WMSApp();
});