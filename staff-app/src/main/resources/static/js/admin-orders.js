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