/**
 * admin-scheduler.js
 * WebSocket-логика для страницы планировщика.
 */

function connectSchedulerWebSocket() {
    function subscribe(client) {
        client.subscribe('/topic/scheduler', function (message) {
            fetchSchedulerStats();
        });
    }

    if (window.stompClient && window.stompClient.connected) {
        subscribe(window.stompClient);
        return;
    }

    const socket = new SockJS('/ws');
    const client = Stomp.over(socket); // Используем правильный Stomp.over()
    client.debug = null;
    client.connect({}, function () { subscribe(client); },
                       function () { setTimeout(connectSchedulerWebSocket, 5000); });
}

/**
 * Запросить актуальные данные и бесшовно обновить DOM.
 */
function fetchSchedulerStats() {
    $.ajax({
        url: location.href,
        cache: false, // <-- ЖЕЛЕЗОБЕТОННО ОТКЛЮЧАЕМ КЭШИРОВАНИЕ
        success: function (html) {
            const newDoc = new DOMParser().parseFromString(html, 'text/html');
            $('.sched-stat-row').html($(newDoc).find('.sched-stat-row').html());
            $('.sched-dashboard-grid').html($(newDoc).find('.sched-dashboard-grid').html());
        }
    });
}

$(document).ready(function () {
    connectSchedulerWebSocket();
});