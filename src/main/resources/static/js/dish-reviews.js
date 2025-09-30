$(document).ready(function() {
    // Обработка звезд для оценки
    $('.stars i').hover(
        function() {
            const value = $(this).data('value');
            $(this).parent().find('i').each(function(i) {
                $(this).removeClass('fas far')
                       .addClass(i < value ? 'fas' : 'far');
            });
        },
        function() {
            const selectedValue = $('#ratingValue').val();
            $(this).parent().find('i').each(function(i) {
                $(this).removeClass('fas far')
                       .addClass(i < selectedValue ? 'fas' : 'far');
            });
        }
    );

    $('.stars i').click(function() {
        const value = $(this).data('value');
        $('#ratingValue').val(value);
    });
});