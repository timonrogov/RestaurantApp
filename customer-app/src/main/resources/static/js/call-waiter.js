/**
 * call-waiter.js
 * Логика вызова официанта для клиентского приложения.
 */

// === УПРАВЛЕНИЕ МОДАЛЬНЫМ ОКНОМ ===

function openCallWaiterModal() {
    const modal = document.getElementById('callWaiterModal');
    if (!modal) return;

    // Очищаем старые сообщения и инпут при открытии
    document.getElementById('waiterCallMessage').textContent = '';
    document.getElementById('waiterCallMessage').className = 'waiter-message';
    document.getElementById('waiterTableNumber').value = '';

    modal.style.display = 'flex';
    // Небольшая задержка для плавного появления
    setTimeout(() => {
        modal.classList.add('show');
        // Фокус на поле ввода для удобства
        document.getElementById('waiterTableNumber').focus();
    }, 10);
}

function closeCallWaiterModal() {
    const modal = document.getElementById('callWaiterModal');
    if (!modal) return;

    modal.classList.remove('show');
    setTimeout(() => {
        modal.style.display = 'none';
    }, 300); // Время должно совпадать с CSS transition
}

// Закрытие по клику на затемненный фон
window.addEventListener('click', function(event) {
    const modal = document.getElementById('callWaiterModal');
    if (event.target === modal) {
        closeCallWaiterModal();
    }
});

// Закрытие по Escape
document.addEventListener('keydown', function(event) {
    if (event.key === "Escape") {
        const modal = document.getElementById('callWaiterModal');
        if (modal && modal.style.display === 'flex') {
            closeCallWaiterModal();
        }
    }
});

// Отправка по нажатию Enter в поле ввода
document.getElementById('waiterTableNumber')?.addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
        submitWaiterCall();
    }
});


// === ОТПРАВКА ЗАПРОСА НА СЕРВЕР ===

function submitWaiterCall() {
    const tableInput = document.getElementById('waiterTableNumber');
    const messageBox = document.getElementById('waiterCallMessage');
    const tableNumber = tableInput.value.trim();

    // 1. Простая валидация
    if (!tableNumber) {
        showMessage('Пожалуйста, укажите номер столика', 'error');
        tableInput.focus();
        return;
    }

    // 2. Получение CSRF-токена (так как это POST запрос)
    // Эти мета-теги должны быть в <head> ваших страниц (layout)
    const csrfToken = document.querySelector("meta[name='_csrf']")?.getAttribute("content");
    const csrfHeader = document.querySelector("meta[name='_csrf_header']")?.getAttribute("content");

    // 3. AJAX запрос (используем jQuery, так как он у вас уже подключен)
    $.ajax({
        url: '/api/calls/create',
        method: 'POST',
        data: { tableNumber: tableNumber },
        beforeSend: function(xhr) {
            if (csrfToken && csrfHeader) {
                xhr.setRequestHeader(csrfHeader, csrfToken);
            }
        },
        success: function(response) {
            // Успех
            showMessage('Официант уведомлен! Скоро подойдем.', 'success');

            // Закрываем окно через 1.5 секунды
            setTimeout(() => {
                closeCallWaiterModal();
            }, 1500);
        },
        error: function(xhr) {
            // Ошибка
            console.error(xhr);
            showMessage('Ошибка соединения. Попробуйте еще раз.', 'error');
        }
    });
}

// Вспомогательная функция для отображения текста
function showMessage(text, type) {
    const msgBox = document.getElementById('waiterCallMessage');
    msgBox.textContent = text;
    // Сбрасываем классы и добавляем нужный
    msgBox.className = 'waiter-message ' + type;
}