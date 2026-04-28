/**
 * staff-notifications.js
 * Глобальный скрипт для уведомлений персонала.
 * Использует WebSocket вместо HTTP-поллинга.
 *
 * Стратегия соединения:
 * - Если kds.js уже создал window.stompClient — переиспользуем его.
 * - Иначе создаём собственное соединение.
 */

$(document).ready(function () {

    const STORAGE_KEY = 'restaurant_last_call_count';

    function subscribeToCallUpdates(client) {
        client.subscribe('/topic/calls', function (message) {
            const data = JSON.parse(message.body);

            // Обновляем бейдж всегда
            updateBadge(data.activeCallsCount);

            // Уведомление показываем только при новом вызове
            if (data.type === 'NEW_CALL') {
                const lastKnown = parseInt(localStorage.getItem(STORAGE_KEY) || '0');
                if (data.activeCallsCount > lastKnown) {
                    toastr.info(
                        'Столик ' + data.tableNumber + ' вызывает официанта!',
                        'Новый вызов'
                    );
                    playNotificationSound();
                }
            }

            localStorage.setItem(STORAGE_KEY, data.activeCallsCount);
        });
    }

    function initWebSocket() {
        // Переиспользуем соединение kds.js если оно есть и активно
        if (window.stompClient && window.stompClient.connected) {
            subscribeToCallUpdates(window.stompClient);
            return;
        }

        // Иначе создаём собственное соединение
        const socket = new SockJS('/ws');
        const client = Stomp.over(socket);
        client.debug = null;

        client.connect({}, function () {
            subscribeToCallUpdates(client);
            // Запрашиваем текущее количество при подключении
            $.get('/api/admin/calls/count', function (count) {
                updateBadge(count);
                localStorage.setItem(STORAGE_KEY, count);
            });
        }, function (error) {
            // При ошибке — повтор через 10 секунд
            setTimeout(initWebSocket, 10000);
        });
    }

    function updateBadge(count) {
        const $badge = $('#callBadge');
        if (count > 0) {
            const lastKnown = parseInt(localStorage.getItem(STORAGE_KEY) || '0');
            $badge.text(count).show();
            if (count !== lastKnown) {
                $badge.removeClass('animate__bounceIn');
                void $badge[0].offsetWidth;
                $badge.addClass('animate__bounceIn');
            }
        } else {
            $badge.hide();
        }
    }

    function playNotificationSound() {
        // const audio = new Audio('/sounds/bell.mp3');
        // audio.play().catch(e => console.log('Autoplay blocked'));
    }

    // Небольшая задержка, чтобы дать kds.js время создать соединение первым (если он есть)
    setTimeout(initWebSocket, 500);
});