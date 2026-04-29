/**
 * admin-scheduler.js
 * WebSocket-логика для страницы планировщика.
 */

function connectSchedulerWebSocket() {
    function subscribe(client) {
        client.subscribe('/topic/scheduler', function (message) {
            // 1. Обновляем плитки и вид "Карточки" (старая логика)
            fetchSchedulerStats();

            // 2. БЕСШОВНО обновляем Ганта, если он сейчас инициализирован
            // Передаем true, чтобы обновление прошло "тихо", без лоадера
            if (typeof ganttState !== 'undefined' && ganttState.initialized) {
                fetchGanttData(ganttState.date, true);
            }
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
                       function () { setTimeout(connectSchedulerWebSocket, 5000); });
}

/**
 * Запросить актуальные данные и бесшовно обновить DOM (для плиток и карточек).
 */
function fetchSchedulerStats() {
    $.ajax({
        url: location.href,
        cache: false,
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