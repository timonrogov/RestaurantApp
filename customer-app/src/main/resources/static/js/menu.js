$(document).ready(function() {
    const csrfToken = $("meta[name='_csrf']").attr("content");
    const csrfHeader = $("meta[name='_csrf_header']").attr("content");
    const currentDate = "[[${currentDate}]]";
    const currentTime = "[[${currentTime}]]";

    const $searchInput = $('#dishSearch');
    const $noResults = $('<div class="no-results">Ничего не найдено</div>').insertAfter('.search-container');

    // --- УДАЛЕН ВЕСЬ БЛОК СЛАЙДЕРА ---
    // Так как мы теперь используем CSS Scroll Snap, JS для слайдера не нужен
    // ---------------------------------

    // Добавление в заказ
    $(".add-to-order-btn").on("click", function() {
        const form = $(this).closest(".add-to-order-form");
        const dishItem = form.closest(".dish-item");
        const isAvailable = dishItem.data("is-available");

        // Проверяем доступность блюда
        if (!isAvailable) {
            toastr.error("Это блюдо недоступно для заказа.");
            return;
        }

        // Если блюдо доступно, продолжаем с отправкой запроса
        const dishId = form.find("input[name='dishId']").val();
        const quantity = form.find("input[name='quantity']").val();
        const comment = form.find("textarea[name='comment']").val();

        $.ajax({
            url: "/orders/add-item",
            method: "POST",
            data: {
                dishId: dishId,
                quantity: quantity,
                comment: comment
            },
            beforeSend: xhr => {
                xhr.setRequestHeader(csrfHeader, csrfToken);
            },
            success: response => {
                if (response.success) {
                    toastr.success("Блюдо добавлено!");
                    updateOrderInfo(response);
                } else {
                    toastr.error(response.error);
                }
            },
            error: xhr => {
                toastr.error(xhr.responseText || "Ошибка добавления блюда");
            }
        });
    });

    function updateOrderInfo(data) {
        const priceHtml = data.hasDiscount
            ? `<div class="order-price">
                    <span class="original">${data.totalPrice.toFixed(2)} ₽</span>
                    <span class="discounted">${data.totalDiscounted.toFixed(2)} ₽</span>
               </div>`
            : `<div class="order-price">
                    <span class="discounted">${data.totalDiscounted.toFixed(2)} ₽</span>
               </div>`;

        $(".order-summary").html(`
            <div class="order-summary-content">
                ${priceHtml}
                <a href="/orders/view" class="btn btn-primary">Перейти к заказу</a>
            </div>
        `);

        $(".order-summary").toggle(data.hasItems);
    }


    // Обработчик клика по кнопкам категорий
    $('.category-link').click(function(e) {
        // Убираем выделение у всех кнопок
        $('.category-link').removeClass('active');

        // Выделяем текущую кнопку
        $(this).addClass('active');

        // Плавная прокрутка к разделу
        const targetId = $(this).attr('href');
        const targetElement = $(targetId);

        if (targetElement.length) {
            $('html, body').animate({
                scrollTop: targetElement.offset().top - 85 // Учитываем отступ
            }, 800);
        }
    });

    // Обработчик скролла для выделения активной категории
    $(window).scroll(function() {
        const currentScroll = $(this).scrollTop() + 100;

        $('.category-section').each(function() {
            const section = $(this);
            const sectionTop = section.offset().top - 150;
            const sectionBottom = sectionTop + section.outerHeight();
            const categoryId = section.attr('id');

            if (currentScroll >= sectionTop && currentScroll < sectionBottom) {
                $('.category-link').removeClass('active');
                $(`a[href="#${categoryId}"]`).addClass('active');
                return false;
            }
        });
    });

    // Инициализация активной категории при загрузке страницы
    $(window).trigger('scroll');

    // Функция фильтрации
    function filterDishes(searchText) {
        let hasResults = false;
        const searchTerms = searchText.toLowerCase().split(' ');

        $('.category-section').each(function() {
            const $category = $(this);
            let categoryHasVisibleItems = false;

            $category.find('.dish-item').each(function() {
                const $dish = $(this);
                const dishText = $dish.text().toLowerCase();
                const isVisible = searchTerms.every(term => dishText.includes(term));

                $dish.toggle(isVisible);
                if (isVisible) categoryHasVisibleItems = true;
            });

            // Скрываем/показываем категорию
            $category.toggle(categoryHasVisibleItems);
            if (categoryHasVisibleItems) hasResults = true;
        });

        // Показываем сообщение если нет результатов
        $noResults.toggle(!hasResults);

        // Обновляем навигацию категорий
        updateCategoryNavigation();
    }

    // Обновление навигационного меню
    function updateCategoryNavigation() {
        $('.category-link').each(function() {
            const $link = $(this);
            const targetId = $link.attr('href');
            const $category = $(targetId);
            $link.toggle($category.is(':visible'));
        });
    }

    // Обработчик ввода с задержкой
    let searchTimeout;
    $searchInput.on('input', function() {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            filterDishes($(this).val().trim());
        }, 300);
    });


    // 1. Устанавливаем начальный текст кнопок
        $('.read-more-btn').text('Показать описание');

        // 2. Обработчик клика
        $('.read-more-btn').on('click', function() {
            const $btn = $(this);
            const $text = $btn.siblings('.dish-description-text');

            // Переключаем класс (показываем/скрываем)
            $text.toggleClass('collapsed');

            // Меняем текст кнопки
            if ($text.hasClass('collapsed')) {
                $btn.text('Показать описание');
            } else {
                $btn.text('Скрыть описание');
            }
        });

        // 3. Проверка на пустые описания (если описания нет, кнопку прячем)
        $('.dish-description-text').each(function() {
            if ($(this).text().trim().length === 0) {
                $(this).siblings('.read-more-btn').hide();
            }
        });
});

document.querySelectorAll('.stars').forEach(element => {
    const rating = parseFloat(element.dataset.rating);
    if (!isNaN(rating)) {
        element.style.setProperty('--rating', rating);
    }
});

document.querySelectorAll('.clickable-image').forEach(img => {
    img.addEventListener('click', function(e) {
        e.stopPropagation(); // Блокируем всплытие события
        window.location.href = this.dataset.reviewsUrl;
    });
});

// Функция пересчета цен
function updateDishPriceDisplay(inputElement) {
    const $input = $(inputElement);
    const quantity = parseInt($input.val()) || 1;
    const $dishItem = $input.closest('.dish-item');

    // Считываем данные из data-атрибутов
    const hasPromo = $dishItem.data('has-promo'); // true/false

    // Если акции нет, ничего не делаем
    if (!hasPromo) return;

    //const basePrice = parseFloat($dishItem.data('base-price')); // Не используется, но можно оставить
    const minQty = parseInt($dishItem.data('min-qty'));
    //const discount = parseFloat($dishItem.data('discount')); // Не используется

    const $regularView = $dishItem.find('.regular-price-view');
    const $discountView = $dishItem.find('.discount-price-view');

    // Логика переключения
    if (quantity >= minQty) {
        // Условия акции выполнены
        $regularView.hide();
        $discountView.css('display', 'flex');
    } else {
        // Условия акции НЕ выполнены
        $discountView.hide();
        $regularView.show();
    }
}

// Навешиваем обработчики
$(document).on('input change', '.quantity-input', function() {
    let value = parseInt(this.value);

    // Валидация
    if (value > 10) this.value = 10;
    if (value < 1) this.value = 1;
    if (isNaN(value)) this.value = 1;

    // Вызываем обновление цен
    updateDishPriceDisplay(this);
});

// Инициализация при загрузке
$('.quantity-input').each(function() {
    updateDishPriceDisplay(this);
});