$(document).ready(function() {
    const $starInput = $('.stars-input i');
    const $ratingValue = $('#ratingValue');

    // 1. Подсветка при наведении
    $starInput.on('mouseenter', function() {
        const val = $(this).data('value');
        $starInput.each(function() {
            $(this).toggleClass('hover', $(this).data('value') <= val);
        });
    });

    $starInput.on('mouseleave', function() {
        $starInput.removeClass('hover');
    });

    // 2. Выбор рейтинга
    $starInput.on('click', function() {
        const val = $(this).data('value');
        $ratingValue.val(val);

        $starInput.each(function() {
            const isFilled = $(this).data('value') <= val;
            $(this).toggleClass('active', isFilled);
            $(this).removeClass('fas far').addClass(isFilled ? 'fas' : 'far');
        });
    });

    // 3. Валидация
    $('#reviewForm').on('submit', function(e) {
        if (!$ratingValue.val()) {
            e.preventDefault();
            toastr.warning('Пожалуйста, выберите оценку ресторана');
        }
    });
});