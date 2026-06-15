// templates/menu-template.js - 공통 템플릿
class MenuTemplate {
    static getMainMenu() {
        return `
            <div class="section-title">메인 메뉴</div>
            <div class="menu-grid">
                <button id="purchaseMenu" class="menu-item main-purchase">
                    <span>구매</span>
                    <span class="menu-arrow">›</span>
                </button>
                <button id="productionSalesMenu" class="menu-item main-production-sales">
                    <span>구매,생산,영업(푸에블라)</span>
                    <span class="menu-arrow">›</span>
                </button>
                <button id="productionMenu" class="menu-item main-production">
                    <span>생산(살티오)</span>
                    <span class="menu-arrow">›</span>
                </button>
                <button id="salesMenu" class="menu-item main-sales">
                    <span>영업</span>
                    <span class="menu-arrow">›</span>
                </button>
                <button id="qualityMenu" class="menu-item main-quality">
                    <span>품질</span>
                    <span class="menu-arrow">›</span>
                </button>
            </div>
        `;
    }

    static getSubMenuTemplate(title, menuItems) {
        const itemsHTML = menuItems.map(item => `
            <button class="sub-menu-item" data-action="${item.id}">
                ${item.name}
            </button>
        `).join('');

        return `
            <div class="section-title">${title} 메뉴</div>
            <div class="menu-grid">
                ${itemsHTML}
            </div>
        `;
    }

    static getFormContainer(title, formHTML) {		// 화면내 타이틀, 내용
        return `
            <div class="form-container">
                ${formHTML}
            </div>
        `;
    }
	
	static getTableContainer(title,tableHTML) {		// 테이블 템플릿
	    return `
	        <div class="table-only-container">
	            ${tableHTML}
	        </div>
	    `;
	}

    /*static getResultList(items) {
        if (!items || items.length === 0) {
            return '<div style="text-align: center; color: #6b7280; padding: 20px;">등록된 항목이 없습니다.</div>';
        }

        const itemsHTML = items.map(item => `
            <div class="result-item">
                <div class="item-code">${item.code}</div>
                <div class="item-name">${item.name}</div>
                <div style="font-size: 12px; color: #6b7280; margin-top: 5px;">
                    수량: ${item.quantity} | 위치: ${item.location || '미지정'}
                </div>
            </div>
        `).join('');

        return `
            <div class="section-title">등록 현황</div>
            <div style="max-height: 300px; overflow-y: auto;">
                ${itemsHTML}
            </div>
        `;
    }*/
}