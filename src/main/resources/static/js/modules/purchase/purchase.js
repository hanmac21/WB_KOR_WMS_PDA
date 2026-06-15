// modules/purchase.js - 구매 메뉴 모듈
class PurchaseModule {
    static menuItems = [
        { id: 'incoming-local', name: '입고등록(가상수입, 로컬)' },
        { id: 'incoming-ckd', name: '입고등록(미착품, CKD)' },
        { id: 'location-load', name: 'Location 적재' },
        { id: 'wip/input', name: '공정불출' },
        { id: 'material-return', name: '공정불출반납' },
        { id: 'receiving-return', name: '입고반품' },
        { id: 'stock-count/barcode', name: '재고실사' },
        { id: 'factory-transfer', name: '공장간이송처리' },
        { id: 'transfer-process', name: '이송처리' },
        { id: 'material-info', name: '자재정보조회' }
    ];

    static getMenuHTML() {
        return MenuTemplate.getSubMenuTemplate('구매', this.menuItems);
    }

    static setupEventListeners(app) {
        // 각 서브메뉴 아이템에 이벤트 리스너 등록
        document.querySelectorAll('.sub-menu-item').forEach(item => {
            item.addEventListener('click', (e) => {
                const action = e.target.getAttribute('data-action');
                this.handleMenuClick(action, app);
            });
        });
    }

    static handleMenuClick(action, app) {
        switch(action) {
            case 'incoming-local':
                app.navigateToMenu('incoming-local');
                break;
            case 'incoming-ckd':
				app.navigateToMenu('incoming-ckd');
                //Utils.showAlert('입고등록(미착품, CKD) 기능은 준비중입니다.');
                break;
            case 'location/load':
				app.navigateToMenu('location-load');
				//Utils.showAlert('Location 적재 기능은 준비중입니다.');
                break;
            case 'wip/input':
                app.navigateToMenu('wip/input');
                break;
            case 'material-return':
                Utils.showAlert('공정불출반납 기능은 준비중입니다.');
                break;
            case 'receiving-return':
                Utils.showAlert('입고반품 기능은 준비중입니다.');
                break;
            case 'stock-count/barcode':
                app.navigateToMenu('stock-count/barcode');
                break;
            case 'factory-transfer':
                Utils.showAlert('공장간이송처리 기능은 준비중입니다.');
                break;
            case 'transfer-process':
                Utils.showAlert('이송처리 기능은 준비중입니다.');
                break;
            case 'material-info':
                Utils.showAlert('자재정보조회 기능은 준비중입니다.');
                break;
            default:
                Utils.showAlert('알 수 없는 메뉴입니다.');
        }
    }
}