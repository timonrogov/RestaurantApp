/**
 * admin-orders.js
 * Логика для страницы управления заказами.
 */

function handleOrderClick(event) {
    // 1. Проверяем, что клик не был по кнопке или форме (на всякий случай)
    if (event.target.closest('.card-actions') || event.target.closest('button')) {
        return;
    }

    // 2. Ищем карточку по НОВОМУ классу .admin-order-card
    // event.currentTarget ссылается на элемент, на котором висит обработчик (header или body)
    // closest ищет родителя
    const card = event.currentTarget.closest('.admin-order-card');
    
    if (card) {
        const orderId = card.dataset.orderId;
        if (orderId) {
            window.location.href = `/admin/orders/${orderId}`;
        }
    }
}

// Фильтрация по столику (поиск)
function filterOrdersByTable() {
    const input = document.getElementById('tableNumberFilter');
    const filterValue = input.value.trim().toLowerCase();
    
    // Используем НОВЫЙ класс карточек
    const orderCards = document.querySelectorAll('.admin-order-card');

    orderCards.forEach(card => {
        const tableElement = card.querySelector('[data-table]');
        
        if (tableElement) {
            const tableNumber = tableElement.getAttribute('data-table').toLowerCase();
            
            // Проверяем, содержит ли номер столика введенный текст
            const isVisible = tableNumber.includes(filterValue);
            
            // Показываем или скрываем (сбрасываем display в дефолтное значение css или ставим none)
            card.style.display = isVisible ? '' : 'none';
        }
    });
}

// ============================================================
// Добавить в конец admin-orders.js
// ============================================================

/**
 * WebSocket-подписка для страницы управления заказами.
 * При изменении статуса заказа — обновляем бейдж на карточке.
 */
function connectOrdersWebSocket() {
    function subscribe(client) {
        client.subscribe('/topic/orders', function (message) {
            const data = JSON.parse(message.body);
            updateOrderCard(data.orderId, data.status, data.statusDisplay);
        });
    }

    if (window.stompClient && window.stompClient.connected) {
        subscribe(window.stompClient);
        return;
    }

    const socket = new SockJS('/ws');
    const client = Stomp.over(socket);
    client.debug = null;
    client.connect({}, function () { subscribe(client); },
                       function () { setTimeout(connectOrdersWebSocket, 5000); });
}

/**
 * Обновить статусный бейдж на карточке заказа без перезагрузки страницы.
 */
function updateOrderCard(orderId, status, statusDisplay) {
    const card = document.querySelector(`.admin-order-card[data-order-id="${orderId}"]`);
    if (!card) return; // Карточка не на этой странице (другой фильтр активен)

    // Обновляем бейдж статуса
    const badge = card.querySelector('.status-badge');
    if (badge) {
        // Убираем все старые классы статуса
        badge.className = badge.className.replace(/\bstatus-\S+/g, '').trim();
        badge.classList.add('status-badge', 'status-' + status.toLowerCase());
        badge.textContent = statusDisplay;
    }

    // Визуальная вспышка для привлечения внимания
    card.classList.add('card-updated');
    setTimeout(() => card.classList.remove('card-updated'), 1500);
}

$(document).ready(function () {
    connectOrdersWebSocket();
});