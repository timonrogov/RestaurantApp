function handleOrderClick(event) {
    // Проверяем, что клик не на кнопках действий
    if (!event.target.closest('.action-buttons')) {
        const card = event.currentTarget.closest('.order-card');
        const orderId = card.dataset.orderId;
        window.location.href = `/admin/orders/${orderId}`;
    }
}

// Добавить обработчик для всей карточки (опционально)
document.querySelectorAll('.order-card').forEach(card => {
    card.addEventListener('click', function(event) {
        if (!event.target.closest('.action-buttons')) {
            const orderId = card.dataset.orderId;
            window.location.href = `/admin/orders/${orderId}`;
        }
    });
});

function filterOrdersByTable() {
    const input = document.getElementById('tableNumberFilter');
    const filterValue = input.value.trim().toLowerCase();
    console.log('Введенное значение:', filterValue);
    const orderCards = document.querySelectorAll('.order-card');

    orderCards.forEach(card => {
        const tableElement = card.querySelector('[data-table]');
        if (tableElement) {
            const tableNumber = tableElement.getAttribute('data-table').toLowerCase();
            console.log('Номер столика:', tableNumber);
            const isVisible = tableNumber.includes(filterValue);
            console.log('Видимость:', isVisible);
            card.style.display = isVisible ? 'block' : 'none';
        } else {
            console.log('Элемент с data-table не найден в карточке:', card);
            card.style.display = 'none';
        }
    });
}
