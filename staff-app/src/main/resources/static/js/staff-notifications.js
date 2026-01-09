/**
 * staff-notifications.js
 * Глобальный скрипт для проверки новых вызовов официанта.
 * Использует localStorage для предотвращения повторных уведомлений при перезагрузке.
 */

$(document).ready(function() {

    const POLLING_INTERVAL = 15000;
    const STORAGE_KEY = 'restaurant_last_call_count'; // Ключ для хранения в браузере

    function checkActiveCalls() {
        $.ajax({
            url: '/api/admin/calls/count',
            method: 'GET',
            success: function(count) {
                // 1. Всегда обновляем бейдж
                updateBadge(count);

                // 2. Получаем предыдущее сохраненное значение (или 0, если его нет)
                let lastKnownCount = parseInt(localStorage.getItem(STORAGE_KEY) || '0');

                // 3. Показываем уведомление ТОЛЬКО если количество выросло
                // (Это значит, что поступил РЕАЛЬНО новый вызов)
                if (count > lastKnownCount) {
                    toastr.info('Поступил новый вызов официанта!', 'Внимание');
                    playNotificationSound(); // Опционально: звук
                }

                // 4. Сохраняем текущее количество в память браузера
                // Чтобы при следующей проверке или перезагрузке страницы знать актуальное число
                localStorage.setItem(STORAGE_KEY, count);
            },
            error: function(err) {
                console.warn('Ошибка при проверке вызовов:', err);
            }
        });
    }

    function updateBadge(count) {
        const $badge = $('#callBadge');

        if (count > 0) {
            $badge.text(count);
            $badge.show();

            // Анимацию добавляем только если число изменилось (чтобы не дергалось постоянно)
            let lastKnownCount = parseInt(localStorage.getItem(STORAGE_KEY) || '0');
            if (count !== lastKnownCount) {
                $badge.removeClass('animate__bounceIn'); // Сброс анимации
                void $badge[0].offsetWidth; // Трюк для перезапуска CSS анимации
                $badge.addClass('animate__bounceIn');
            }
        } else {
            $badge.hide();
        }
    }

    // Простая функция звука (короткий "дзынь")
    function playNotificationSound() {
        // Можно добавить файл звука в /static/sounds/bell.mp3
        // const audio = new Audio('/sounds/bell.mp3');
        // audio.play().catch(e => console.log('Autoplay blocked'));
    }

    // Первый запуск
    checkActiveCalls();

    // Периодический опрос
    setInterval(checkActiveCalls, POLLING_INTERVAL);
});