$(document).ready(function() {
    const $starInput = $('.stars-input i');
    const $ratingValue = $('#ratingValue');

    // 1. Логика наведения (Hover)
    $starInput.on('mouseenter', function() {
        const value = $(this).data('value');

        // Подсвечиваем текущую и все предыдущие звезды
        $starInput.each(function() {
            if ($(this).data('value') <= value) {
                $(this).addClass('hover');
            } else {
                $(this).removeClass('hover');
            }
        });
    });

    // 2. Убираем подсветку при уходе мыши
    $('.stars-input').on('mouseleave', function() {
        $starInput.removeClass('hover');
    });

    // 3. Логика клика (Выбор оценки)
    $starInput.on('click', function() {
        const value = $(this).data('value');

        // Записываем значение в скрытое поле
        $ratingValue.val(value);

        // Фиксируем активное состояние звезд
        $starInput.each(function() {
            if ($(this).data('value') <= value) {
                $(this).addClass('active');
                $(this).removeClass('far').addClass('fas'); // Делаем иконку закрашенной
            } else {
                $(this).removeClass('active');
                $(this).removeClass('fas').addClass('far'); // Делаем иконку контурной
            }
        });
    });

    // 4. Валидация перед отправкой
    $('#reviewForm').on('submit', function(e) {
        if (!$ratingValue.val()) {
            e.preventDefault();
            toastr.warning('Пожалуйста, выберите оценку блюда (поставьте звезды)');
        }
    });
});