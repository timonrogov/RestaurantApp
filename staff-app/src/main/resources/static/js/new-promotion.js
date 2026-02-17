/**
 * new-promotion.js
 */

// Функция пересчета и отображения цены
function calculateNewPrice() {
    const select = document.getElementById('dishSelect');
    const discountInput = document.getElementById('discountInput');
    const previewBox = document.getElementById('pricePreviewBox');

    // Получаем выбранную опцию
    const selectedOption = select.options[select.selectedIndex];
    const discountPercent = parseFloat(discountInput.value);

    // Проверяем, выбрано ли блюдо
    if (selectedOption.value && selectedOption.value !== "") {
        const originalPrice = parseFloat(selectedOption.getAttribute('data-price'));

        // Если скидка валидная, считаем
        if (!isNaN(discountPercent) && discountPercent > 0 && discountPercent < 100) {
            const newPrice = originalPrice * (1 - discountPercent / 100);

            // Обновляем текст
            document.getElementById('oldPriceVal').textContent = Math.round(originalPrice) + ' ₽';
            document.getElementById('newPriceVal').textContent = Math.round(newPrice) + ' ₽';

            // Показываем блок
            previewBox.style.display = 'block';
        } else {
            // Если скидки нет или она кривая, показываем просто цену
             document.getElementById('oldPriceVal').textContent = '';
             document.getElementById('newPriceVal').textContent = Math.round(originalPrice) + ' ₽';
             previewBox.style.display = 'block';
        }
    } else {
        // Если блюдо не выбрано - прячем блок
        previewBox.style.display = 'none';
    }
}

// Валидация дат при отправке
document.getElementById('promotionForm').addEventListener('submit', function(e) {
    const startDate = document.querySelector('input[name="startDate"]').value;
    const endDate = document.querySelector('input[name="endDate"]').value;

    if (new Date(startDate) > new Date(endDate)) {
        toastr.error('Дата окончания не может быть раньше даты начала!');
        e.preventDefault();
        return;
    }

    const startTime = document.querySelector('input[name="startTime"]').value;
    const endTime = document.querySelector('input[name="endTime"]').value;

    if (startTime >= endTime) {
        toastr.error('Время окончания должно быть позже времени начала!');
        e.preventDefault();
    }
});