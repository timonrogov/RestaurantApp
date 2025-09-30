$(document).ready(function() {
    const csrfToken = $("meta[name='_csrf']").attr("content");
    const csrfHeader = $("meta[name='_csrf_header']").attr("content");
    const currentDate = "[[${currentDate}]]"; // Передаем из модели
    const currentTime = "[[${currentTime}]]"; // Передаем из модели

    const $searchInput = $('#dishSearch');
    const $noResults = $('<div class="no-results">Ничего не найдено</div>').insertAfter('.search-container');

    const $sliderWrapper = $('.slider-wrapper');
    const $cards = $('.promo-card');
    const cardWidth = $sliderWrapper.width(); // Ширина одной карточки = ширина контейнера
    let currentIndex = 0;

    // Показываем первую карточку
    $sliderWrapper.css('transform', `translateX(0)`);

    // Функция для переключения на следующую карточку
    function slideNext() {
        currentIndex = (currentIndex + 1) % $cards.length; // Циклический переход
        $sliderWrapper.css('transform', `translateX(-${currentIndex * cardWidth}px)`);
    }

    // Автопрокрутка каждые 5 секунд
    setInterval(slideNext, 5000);


    // Добавление в заказ
    $(".add-to-order-btn").on("click", function() {
        const form = $(this).closest(".add-to-order-form");
        const dishItem = form.closest(".dish-item");
        const isAvailable = dishItem.data("is-available");

        // Проверяем доступность блюда
        if (!isAvailable) {
            toastr.error("Это блюдо недоступно для заказа.");
            return; // Прерываем выполнение, если блюдо недоступно
        }

        // Если блюдо доступно, продолжаем с отправкой запроса
        const dishId = form.find("input[name='dishId']").val();
        const quantity = form.find("input[name='quantity']").val();
        const comment = form.find("textarea[name='comment']").val();

        $.ajax({
            url: "/add-item",
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
                    scrollTop: targetElement.offset().top - 115 // Учитываем отступ
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

    // При прокрутке страницы
    window.addEventListener('scroll', () => {
        if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 500) {
            loadMoreReviews();
        }
    });
});

document.querySelectorAll('.stars').forEach(element => {
    const rating = parseFloat(element.dataset.rating);
    element.style.setProperty('--rating', rating);
});

document.querySelectorAll('.clickable-image').forEach(img => {
    img.addEventListener('click', function(e) {
        e.stopPropagation(); // Блокируем всплытие события
        window.location.href = this.dataset.reviewsUrl;
    });
});

document.querySelectorAll('.quantity-input').forEach(input => {
    input.addEventListener('change', function() {
        // Преобразуем значение в число
        let value = parseInt(this.value);

        // Если значение больше 10 - устанавливаем 10
        if (value > 10) this.value = 10;

        // Если значение меньше 1 - устанавливаем 1
        if (value < 1) this.value = 1;

        // Если поле пустое или не число - устанавливаем 1
        if (isNaN(value)) this.value = 1;
    });
});

document.querySelectorAll('.quantity-input').forEach(input => {
    input.addEventListener('input', function() {
        if (this.value > 10) this.value = 10;
        if (this.value < 1) this.value = 1;
    });
});