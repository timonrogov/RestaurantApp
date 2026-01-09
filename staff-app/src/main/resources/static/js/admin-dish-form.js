/**
 * admin-dish-form.js
 * Логика для формы добавления/редактирования блюда.
 */

// 1. Функция для клика по скрытому инпуту
// Вызывается при клике на красивую область с картинкой
function triggerFileInput() {
    document.getElementById('imageInput').click();
}

// 2. Функция предпросмотра изображения
// Вызывается событием onchange у инпута файла
function previewImage(input) {
    // Проверяем, что файл действительно выбран
    if (input.files && input.files[0]) {
        const reader = new FileReader();

        // Когда файл будет прочитан...
        reader.onload = function(e) {
            // ...устанавливаем его содержимое (base64) как источник для картинки
            const preview = document.getElementById('imagePreview');
            preview.src = e.target.result;

            // Можно добавить класс, чтобы показать, что фото изменено (для CSS)
            preview.parentElement.classList.add('image-selected');
        }

        // Читаем файл как Data URL (строка base64)
        reader.readAsDataURL(input.files[0]);
    }
}