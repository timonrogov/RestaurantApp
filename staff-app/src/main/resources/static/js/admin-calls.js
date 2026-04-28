/**
 * admin-calls.js
 * Логика для страницы активных вызовов.
 */

// CSRF токены для POST запросов
const csrfToken = $("meta[name='_csrf']").attr("content");
const csrfHeader = $("meta[name='_csrf_header']").attr("content");

function acceptCall(callId) {
    if (!confirm("Вы подошли к столику и готовы закрыть вызов?")) return;

    $.ajax({
        url: '/api/admin/calls/' + callId + '/close',
        method: 'POST',
        beforeSend: function(xhr) {
            if (csrfToken && csrfHeader) {
                xhr.setRequestHeader(csrfHeader, csrfToken);
            }
        },
        success: function(response) {
            // Успех: удаляем карточку с анимацией
            const $card = $('#call-' + callId);

            $card.fadeOut(300, function() {
                $(this).remove();

                // Если карточек не осталось, показываем сообщение "Нет вызовов"
                if ($('.call-card').length === 0) {
                    location.reload(); // Проще всего перезагрузить, чтобы показать блок .no-calls
                }
            });

            toastr.success('Вызов закрыт');
        },
        error: function(xhr) {
            toastr.error('Ошибка при закрытии вызова');
            console.error(xhr);
        }
    });
}

// === Функция "Время назад" ===
function updateTimeAgo() {
    $('.time-ago').each(function() {
        const timeString = $(this).data('time'); // Получаем ISO строку из data-time
        if (!timeString) return;

        const callTime = new Date(timeString);
        const now = new Date();
        const diffMs = now - callTime; // Разница в миллисекундах
        const diffMins = Math.floor(diffMs / 60000); // Разница в минутах

        let text;
        if (diffMins < 1) {
            text = "(только что)";
            $(this).css('color', 'var(--success)'); // Зеленый для свежих
        } else if (diffMins < 5) {
            text = `(${diffMins} мин. назад)`;
            $(this).css('color', 'var(--color5)'); // Обычный цвет
        } else {
            text = `(${diffMins} мин. назад)`;
            $(this).css('color', 'var(--error)'); // Красный для старых (просроченных)
            $(this).css('font-weight', 'bold');
        }

        $(this).text(text);
    });
}

// Запускаем обновление времени каждую минуту
setInterval(updateTimeAgo, 60000);

// И при загрузке страницы
$(document).ready(function() {
    updateTimeAgo();
});

// ============================================================
// Добавить в конец admin-calls.js
// ============================================================

/**
 * WebSocket-подписка для страницы активных вызовов.
 * Новые вызовы появляются в DOM без перезагрузки страницы.
 */
function connectCallsWebSocket() {
    // Переиспользуем глобальное соединение из header (staff-notifications.js)
    // или создаём новое
    function subscribe(client) {
        client.subscribe('/topic/calls', function (message) {
            const data = JSON.parse(message.body);

            if (data.type === 'NEW_CALL') {
                // Перезагружаем страницу для отображения новой карточки
                // (простое решение — карточка формируется Thymeleaf на сервере)
                location.reload();
            }
            // При CALL_RESOLVED карточка уже удалена через acceptCall() локально
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
                       function () { setTimeout(connectCallsWebSocket, 5000); });
}

$(document).ready(function () {
    connectCallsWebSocket();
});