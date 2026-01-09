$(document).ready(function() {
    const csrfToken = $("meta[name='_csrf']").attr("content");
    const csrfHeader = $("meta[name='_csrf_header']").attr("content");

    $("#confirm-order-btn").on("click", function() {
        const tableNumber = $("#tableNumber").val().trim();

        if (!tableNumber || tableNumber === "0") {
            toastr.error("Пожалуйста, укажите номер столика");
            return;
        }

        $.ajax({
            url: "/orders/confirm",
            method: "POST",
            data: {
                tableNumber: tableNumber
            },
            beforeSend: xhr => {
                xhr.setRequestHeader(csrfHeader, csrfToken);
            },
            success: function(response) {
                toastr.success("Заказ подтвержден!");
                setTimeout(() => window.location.href = "/orders", 1000);
            },
            error: xhr => {
                toastr.error(xhr.responseText || "Ошибка подтверждения заказа");
            }
        });
    });

    $("#cancel-order-btn").on("click", function() {
        // 1. Считываем ID заказа из атрибута кнопки
        const orderId = $(this).data("order-id");

        if (!confirm("Вы уверены, что хотите отменить заказ?")) return;

        $.ajax({
            url: "/orders/" + orderId + "/cancel",
            method: "POST",
            beforeSend: xhr => {
                xhr.setRequestHeader(csrfHeader, csrfToken);
            },
            success: function(response) {
                toastr.success("Заказ отменен");
                // Очищаем корзину визуально или редиректим
                setTimeout(() => window.location.href = "/menu", 1000);
            },
            error: xhr => {
                toastr.error(xhr.responseText || "Ошибка отмены заказа");
            }
        });
    });

    $(".remove-item-btn").on("click", function() {
        const itemId = $(this).data("item-id");

        if (!confirm("Удалить позицию из заказа?")) return;

        $.ajax({
            url: "/orders/remove-item/" + itemId,
            method: "POST",
            beforeSend: xhr => {
                xhr.setRequestHeader(csrfHeader, csrfToken);
            },
            success: () => {
                $(this).closest("tr").remove(); // Удаляем строку из таблицы
                toastr.success("Позиция удалена");
                // Обновляем итоговые суммы
                location.reload(); // Или динамический пересчет
            },
            error: xhr => {
                toastr.error(xhr.responseText || "Ошибка удаления");
            }
        });
    });
});