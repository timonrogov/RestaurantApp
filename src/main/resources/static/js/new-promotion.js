function updatePrice() {
    const select = document.getElementById('dishSelect');
    const priceDisplay = document.getElementById('priceDisplay');
    const selectedOption = select.options[select.selectedIndex];

    if (selectedOption.value) {
        const price = selectedOption.getAttribute('data-price');
        priceDisplay.textContent = `Текущая цена: ${price} ₽`;
    } else {
        priceDisplay.textContent = '';
    }
}

document.getElementById('promotionForm').addEventListener('submit', function(e) {
    const startDate = document.querySelector('input[name="startDate"]').value;
    const endDate = document.querySelector('input[name="endDate"]').value;

    if (new Date(startDate) > new Date(endDate)) {
        alert('Дата окончания не может быть раньше даты начала!');
        e.preventDefault();
    }
});