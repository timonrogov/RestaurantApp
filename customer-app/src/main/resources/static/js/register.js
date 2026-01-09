// Находим форму и добавляем обработчик события
document.getElementById('registerForm').addEventListener('submit', function(e) {
    const gender = document.getElementById('gender').value;

    // Если выбран "Другой"
    if (gender === 'Другой') {
        e.preventDefault(); // Отменяем отправку формы
        document.getElementById('errorMessage').style.display = 'block'; // Показываем сообщение
    }
});