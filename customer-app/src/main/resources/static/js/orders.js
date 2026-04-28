/**
 * orders.js
 * WebSocket-логика для страницы заказов клиента.
 * Подписывается на топики активных заказов и обновляет статусные бейджи.
 */

$(document).ready(function () {

    // Собираем ID всех активных заказов со страницы
    // data-order-id и data-order-active проставляются в Thymeleaf-шаблоне
    const activeOrderIds = [];
    document.querySelectorAll('.order-card-item[data-order-active="true"]').forEach(card => {
        activeOrderIds.push(card.dataset.orderId);
    });

    if (activeOrderIds.length === 0) return; // Нет активных заказов — WebSocket не нужен

    // Подключаемся к WebSocket
    const socket = new SockJS('/ws');
    const client = Stomp.over(socket);
    client.debug = null;

    client.connect({}, function () {
        // Подписываемся на топик каждого активного заказа
        activeOrderIds.forEach(orderId => {
            client.subscribe('/topic/order.' + orderId, function (message) {
                const data = JSON.parse(message.body);
                updateOrderStatus(data.orderId, data.status, data.statusDisplay);
            });
        });

    }, function (error) {
        console.warn('WebSocket отключён, статусы заказов будут обновляться при перезагрузке');
    });

    /**
     * Обновить статусный бейдж карточки заказа.
     */
    function updateOrderStatus(orderId, status, statusDisplay) {
        const card = document.querySelector(`.order-card-item[data-order-id="${orderId}"]`);
        if (!card) return;

        const badge = card.querySelector('.status-badge');
        if (badge) {
            badge.className = badge.className.replace(/\bstatus-\S+/g, '').trim();
            badge.classList.add('status-badge', 'status-' + status.toLowerCase());
            badge.textContent = statusDisplay;
        }

        // Визуальное выделение обновлённой карточки
        card.classList.add('order-updated');
        setTimeout(() => card.classList.remove('order-updated'), 2000);

        // Если заказ завершён — убираем пометку "активный" чтобы не подписываться снова
        if (status === 'SERVED' || status === 'CANCELED') {
            card.dataset.orderActive = 'false';
        }
    }
});