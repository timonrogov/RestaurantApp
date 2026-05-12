/**
 * order-details.js
 * Подписка на обновления статуса заказа через WebSocket.
 *
 * Топик: /topic/order.{orderId}
 * Публикует: CustomerWebSocketService.pushOrderStatusUpdate() каждые 5 сек при изменении.
 */
$(document).ready(function () {

    const orderId = $('body').data('order-id');
    if (!orderId) return;

    const STATUS_CLASSES = [
        'status-assembly', 'status-cooking',
        'status-ready',    'status-served', 'status-canceled'
    ];

    function onStatusUpdate(data) {
        // 1. Обновить бейдж
        const $badge = $('.status-badge');
        $badge.removeClass(STATUS_CLASSES.join(' '));
        $badge.addClass('status-' + data.status.toLowerCase());
        $badge.text(data.statusDisplay);

        // 2. Уведомление
        if (data.status === 'READY') {
            toastr.success('Ваш заказ готов! Ожидайте подачи.', '🍽️ Заказ готов', { timeOut: 6000 });
        } else if (data.status === 'SERVED') {
            toastr.success('Приятного аппетита!', '✅ Заказ подан', { timeOut: 5000 });
        } else if (data.status === 'CANCELED') {
            toastr.error('Ваш заказ был отменён.', 'Заказ отменён', { timeOut: 5000 });
        }

        // 3. При финальном статусе — редирект через 3 секунды
        if (data.status === 'SERVED' || data.status === 'CANCELED') {
            setTimeout(() => { window.location.href = '/orders'; }, 3000);
        }
    }

    function connect() {
        const socket = new SockJS('/ws');
        const client = Stomp.over(socket);
        client.debug = null;

        client.connect({},
            function () {
                client.subscribe('/topic/order.' + orderId, function (message) {
                    const data = JSON.parse(message.body);
                    if (String(data.orderId) === String(orderId)) {
                        onStatusUpdate(data);
                    }
                });
            },
            function () {
                setTimeout(connect, 5000);
            }
        );
    }

    connect();
});