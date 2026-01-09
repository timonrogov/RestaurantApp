$(document).ready(function() {
    let selectedRating = 0; // Переменная для хранения выбранного рейтинга

    // Функция для обновления отображения звезд
    function updateStars(value) {
        $('.rating-input .stars i').each(function(i) {
            if (i < value) {
                $(this).addClass('fas').removeClass('far'); // Заполненные звезды
            } else {
                $(this).addClass('far').removeClass('fas'); // Пустые звезды
            }
        });
    }

    // Обработка наведения на звезды
    $('.rating-input .stars i').hover(
        function() {
            const value = $(this).data('value');
            updateStars(value);
        },
        function() {
            updateStars(selectedRating); // Возвращаем к выбранному рейтингу
        }
    );

    // Обработка клика по звезде
    $('.rating-input .stars i').click(function() {
        selectedRating = $(this).data('value'); // Сохраняем выбранный рейтинг
        $('#ratingValue').val(selectedRating);  // Обновляем скрытое поле
        updateStars(selectedRating);            // Фиксируем отображение звезд
        $('.error').hide();
    });

    // Валидация формы
    $('#reviewForm').submit(function(e) {
        if (!$('#ratingValue').val()) {
            e.preventDefault();
            $('.error').text('Пожалуйста, выберите оценку').show();
        } else {
            $('.error').hide(); // Скрываем ошибку, если рейтинг выбран
        }
    });

    $('.review-item .stars i').css({
      'pointer-events': 'none',
      'cursor': 'default'
    });
});