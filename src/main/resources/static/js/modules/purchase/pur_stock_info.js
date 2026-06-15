let manualTouch = false;
$(document).ready(function () {
    hideLoading();

    // input창에서 포커스 없어질때 세팅
    $('#barcodeInput').on('blur', function () {
        manualTouch = false;
        inputMode = 'readonly'
    });

})

function addEntry() {
    // 바코드 값 가져오기
    const barcodeInput = document.getElementById('barcodeInput');
    const barcode = barcodeInput.value.trim();

    // 바코드가 없으면 알림
    if (!barcode) {
        Utils.showAlert(m("warning.barcode.required"));
        return;
    }

    if(barcode.split("_").length === 6 || barcode.split(",")[3] === "WMSUSA" || barcode.split(",")[4] === "WMSUSA"){

        // 로딩 표시
        showLoading();
        let data = {
            barcode: barcode,
            factory: localStorage.getItem('rememberedFactory'),
        }
        // 에이작스로 바코드 전송 및 데이터 가져오기
        $.ajax({
            url: "/purchase/stock-info-barcode",
            method: 'POST',
            contentType: "application/json",
            data: JSON.stringify(data),
            success: function (result) {
                console.log(result);

                // 테이블 항상 초기화
                $("#dataTableHead").empty();
                $("#dataTableBody").empty();
                $("#detailDataBody").empty();
                $("#locationMatin").text("");
                $(".detail-section-info").css("display", "none");

                if (!result.list || result.list.length === 0) {
                    hideLoading();
                    playSound('error');
                    Utils.showAlert(m("warning.item.notFound"), "warning");
                    $("#barcodeInput").val("");
                    return;
                }

                renderTable(result.list, result.type);

                if (result.type != "location") {
                    let value = "";
                    if (result.type == "itemcode") {
                        value = $("#barcodeInput").val();
                    } else {
                        value = result.list[0].ITEMCODE;
                    }
                    renderTable2(value);
                }

                if ($("#barcodeInput").val().length != 13) {
                    $("#barcodeInput").val("");
                }
            }
        });
    } else {
        console.log("잘못된 바코드")
        let audio = new Audio('/sounds/buzzer.wav');
        audio.play();
        $("#barcodeInput").val("");
        hideLoading();
        Utils.showAlert(`${m("warning.barcode.invalid")}<br>${m("warning.check")}`, "warning")
    }

}

// 전역 변수로 renderTable에서 사용된 데이터를 저장
let mainTableData = [];
let locationMain = "";

function renderTable(list, type) {		//테이블그리기
    playSound("complete")
    console.log("테이블그리기1")
    let head = $("#dataTableHead");
    let table = $("#dataTableBody");
    head.empty();
    table.empty();
    // 메인 테이블 데이터 저장
    mainTableData = list;
    let thead = ``;
    if (type === "part") {
        thead = `
				<tr>
					<th>YN</th>
                    <th>${m('table.itemName')}</th>
                    <th>${m('table.itemCode')}</th>
					<th>${m('table.qty')}</th>
                    <th>${m('table.lot')}</th>
					<th>${m('table.locationDate')}</th>
                </tr>
		`;
    } else if (type == "pallet") {
        thead = `
			<tr>
				<th>YN</th>
                <th>${m('table.itemName')}</th>
                <th>${m('table.itemCode')}</th>
				<th>${m('table.qty')}</th>
                <th>${m('table.date')}</th>
                <th>${m('table.labalQty')}</th>
				<th>${m('table.locationDate')}</th>
            </tr>
		`;
    } else if (type === "location") {
        thead = `
			<tr>
                <th>${m('table.itemCode')}</th>
                <th>${m('table.lot')}</th>
                <th>${m('table.no')}</th>
                <th>${m('table.qty')}</th>
            </tr>
        `;
    } else if (type == "itemcode") {
        thead = `
			<tr>
                <th>${m('table.itemName')}</th>
                <th>${m('table.itemCode')}</th>
            </tr>
        `;
    } else if (type == "box") {
        thead = `
			<tr>
				<th>YN</th>
                <th>${m('table.itemName')}</th>
                <th>${m('table.itemCode')}</th>
				<th>${m('table.qty')}</th>
                <th>${m('table.lot')}</th>
				<th>${m('table.locationDate')}</th>
            </tr>
		`;
    }  else {
        showAlert("", "Barcode Check", "warning");
    }
    head.append(thead);

    if (type === "part") {
        for (let i = 0; i < list.length; i++) {
            madate = list[i].BARCODE.split(",")[1];
            console.log(madate)
            let tbody = `
				<tr class = 'highlight-row table1' data-barcode = "${list[i].BARCODE}" data-type = "${type}">
					<td>${(list?.[i]?.YN == null || list?.[i]?.YN === 'null') ? '' : list[i].YN}</td>		
					<td style = "white-space: nowrap;">${(list?.[i]?.ITEMNAME == null || list?.[i]?.ITEMNAME === 'null') ? '' : list[i].ITEMNAME}</td>			
					<td class ="dataInfo">${(list?.[i]?.ITEMCODE == null || list?.[i]?.ITEMCODE === 'null') ? '' : list[i].ITEMCODE}</td>			
					<td>${(list?.[i]?.QTY == null || list?.[i]?.QTY === 'null') ? '' : list[i].QTY}</td>		
					<td>${(madate == null || madate === 'null') ? '' : madate}</td>			
					<!--<td>${(list?.[i]?.LOCATION == null || list?.[i]?.LOCATION === 'null') ? '' : list[i].LOCATION}</td>-->  <!--적재위치 -->	
					<td>${(list?.[i]?.SDATE == null || list?.[i]?.SDATE === 'null') ? '' : list[i].SDATE}</td> <!-- 적재일 -->	
	            </tr>
			`;
            table.append(tbody);
        }
        locationMain = (list?.[0]?.LOCATION == null || list?.[0]?.LOCATION === 'null') ? m('table.location') + ': ' : m('table.location') + ': ' + list[0].LOCATION;
        $("#locationMatin").text(locationMain);
    } else if (type == "pallet") {
        for (let i = 0; i < list.length; i++) {
            let tbody = `
				<tr class = 'highlight-row table1' data-barcode = "${list[i].PBARCODE}" data-type = "${type}">
					<td>${(list?.[i]?.YN == null || list?.[i]?.YN === 'null') ? '' : list[i].YN}</td>	
					<td style = "white-space: nowrap;">${(list?.[i]?.ITEMNAME == null || list?.[i]?.ITEMNAME === 'null') ? '' : list[i].ITEMNAME}</td> <!-- 품명 -->			
					<td>${(list?.[i]?.ITEMCODE == null || list?.[i]?.ITEMCODE === 'null') ? '' : list[i].ITEMCODE}</td>			
					<td>${(list?.[i]?.QTY == null || list?.[i]?.QTY === 'null') ? '' : list[i].QTY}</td>			
					<td>${(list?.[i]?.LABELQTY == null || list?.[i]?.LABELQTY === 'null') ? '' : list[i].LABELQTY}</td> <!-- 파트라벨 수량 -->
					<td>${(list?.[i]?.BDATE == null || list?.[i]?.BDATE === 'null') ? '' : list[i].BDATE}</td> <!-- 입고일 -->
					<!--<td>${(list?.[i]?.LOCATION == null || list?.[i]?.LOCATION === 'null') ? '' : list[i].LOCATION}</td> 적재위치 -->
					<td>${(list?.[i]?.SDATE == null || list?.[i]?.SDATE === 'null') ? '' : list[i].SDATE}</td> <!-- 적재일 -->
	            </tr>
			`;
            table.append(tbody);
        }
        locationMain = (list?.[0]?.LOCATION == null || list?.[0]?.LOCATION === 'null') ? m('table.location') + ': ' : m('table.location') + ': ' + list[0].LOCATION;
        $("#locationMatin").text(locationMain);
    } else if (type === "location") {

        for (let i = 0; i < list.length; i++) {
            let madate = (list?.[i]?.MADATE == null || list?.[i]?.MADATE === 'null') ? '' : list[i].MADATE;
            if (madate.split("-").length == 1) {
                madate = madate.substring(0, 4) + "-" + madate.substring(4, 6) + "-" + madate.substring(6, 8)
            }
            let no = "";
            let barcode = (list?.[i]?.BARCODE == null || list?.[i]?.BARCODE === 'null') ? '' : list[i].BARCODE;
            let parts = barcode.split(",");
            if (parts.length == 5) {
                no = parts[2];
            } else {
                no = parts[0].slice(7)
            }
            let tbody = `
				<tr class = 'highlight-row table1'>
					<td>${(list?.[i]?.ITEMCODE == null || list?.[i]?.ITEMCODE === 'null') ? '' : list[i].ITEMCODE}</td>			
					<td>${madate}</td> <!-- LOT -->	
					<td>${Number(no)}</td>			
					<td>${(list?.[i]?.QTY == null || list?.[i]?.QTY === 'null') ? '' : list[i].QTY}</td>		
	            </tr>
			`;
            table.append(tbody);
        }
        $(".detail-section-info").css("display", "none")

    } else if (type === "itemcode") {
        let tbody = `
			<tr class = 'highlight-row'>
				<td>${(list?.[0]?.ITEMNAME == null || list?.[0]?.ITEMNAME === 'null') ? '' : list[0].ITEMNAME}</td>			
				<td>${(list?.[0]?.ITEMCODE == null || list?.[0]?.ITEMCODE === 'null') ? '' : list[0].ITEMCODE}</td>			
            </tr>
		`;
        table.append(tbody);
    } else if (type === "box") {
        for (let i = 0; i < list.length; i++) {
            let tbody = `
				<tr class = 'table1' data-barcode = "${list[i].BARCODE}" data-type = "${type}">
					<td>${(list?.[i]?.YN == null || list?.[i]?.YN === 'null') ? '' : list[i].YN}</td>		
					<td style = "white-space: nowrap;">${(list?.[i]?.ITEMNAME == null || list?.[i]?.ITEMNAME === 'null') ? '' : list[i].ITEMNAME}</td>			
					<td class ="dataInfo">${(list?.[i]?.ITEMCODE == null || list?.[i]?.ITEMCODE === 'null') ? '' : list[i].ITEMCODE}</td>			
					<td>${(list?.[i]?.QTY == null || list?.[i]?.QTY === 'null') ? '' : list[i].QTY}</td>		
					<td>${(list?.[i]?.MADATE == null || list?.[i]?.MADATE === 'null') ? '' : list[i].MADATE}</td>	
					<!--<td>${(list?.[i]?.LOCATION == null || list?.[i]?.LOCATION === 'null') ? '' : list[i].LOCATION}</td>-->  <!--적재위치 -->	
					<td>${(list?.[i]?.SDATE == null || list?.[i]?.SDATE === 'null') ? '' : list[i].SDATE}</td> <!-- 적재일 -->
	            </tr>
			`;
            table.append(tbody);
        }
        locationMain = (list?.[0]?.LOCATION == null || list?.[0]?.LOCATION === 'null') ? m('table.location') + ': ' : m('table.location') + ': ' + list[0].LOCATION;
        $("#locationMatin").text(locationMain);
    } else {
        alert("바코드 오류 -" + type);
    }
    hideLoading();
}

// 메인 테이블 데이터에 해당 행이 있는지 확인하는 함수 로케이션으로 비교
function checkIfDataExistsInMainTable(detailItem) {
    if (locationMain && locationMain !== 'null' && locationMain.trim() !== '') {
        return locationMain.includes(detailItem.LOCATION);
    }
}

function renderTable2(itemcode) {
    let totalqty = 0;
    let data = {
        itemcode: itemcode,
        factory: localStorage.getItem('rememberedFactory'),
    }
    $.ajax({
        url: "/purchase/getItemInfo2",
        type: "GET",
        data: data,
        dataType: "json",
        success: function (result) {
            let detailTable = $("#detailDataBody");
            console.log(result);
            detailTable.empty();
            list = result.list;
            for (let i = 0; i < list.length; i++) {
                // mainTableData에 현재 행과 일치하는 데이터가 있는지 확인
                let isHighlight = checkIfDataExistsInMainTable(list[i]);

                // 색상 클래스 결정
                let colorClass = isHighlight ? 'highlight-row' : '';

                let tbody = `
	    			<tr class="${colorClass}">
	    				<td class="date-cell">${list[i].SDATE}</td>
	                    <td class="qty-cell">${list[i].QTY}</td>
	                    <td class="location-cell">${list[i].LOCATION}</td>	
	    				<!--<td class="lotdate-cell">${String(list?.[i]?.MADATE || '').replace(/-/g, '').slice(2)}</td>-->
	                </tr>
	    		`;
                detailTable.append(tbody);
                totalqty = totalqty + Number(list?.[i]?.QTY);
            }
            $("#count").text(list.length);
            $("#totalqty").text(formatNumber(totalqty));
            $(".detail-section-info").css("display", "block")
        }
    })
    hideLoading();
}

$(document).on("click", ".highlight-row", function () {
    const barcode = this.dataset.barcode || $(this).attr('data-barcode') || '';
    const type = this.dataset.type || $(this).attr('data-type') || '';

    if(!barcode) return;
    if(!type) return;

    console.log(barcode);
    console.log(type);

    showLoading();

    $.ajax({
        url: "/purchase/getPalletInfo",
        type: "GET",
        data: {barcode, type},
        dataType: "json",
        success: function (result) {
            console.log(result);
            showModal(result.list);
        }
    })
});


function showModal(list) {
    const modal = document.getElementById('modalOverlay');
    modal.style.display = 'flex';

    let table = $(".partsTableBody");
    table.empty();

    const header = Array.isArray(list) ? (list[0] || {}) : (list || {});
    $('.pallet-barcode').text(header.PBARCODE || '');
    $('.date').text(header.SDATE || '');


    const count = Array.isArray(list) ? list.length : 0;
    $('.count').text(count);

    for (let i = 0; i < list.length; i++) {
        let tbody = `
			<tr>
	            <td class="part-barcode">${list?.[i]?.BBARCODE}</td>
	        </tr>
		`;
        table.append(tbody);
    }
    hideLoading();
}

function closeModal(event) {
    if (event && event.target !== event.currentTarget) {
        return;
    }

    const modal = document.getElementById('modalOverlay');
    modal.style.display = 'none';
}
