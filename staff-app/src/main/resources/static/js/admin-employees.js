/**
 * admin-employees.js
 * Логика управления модальными окнами для страницы сотрудников.
 */

// === БЛОКИРОВКА ===
function openBlockModal(id, name) {
    // 1. Устанавливаем имя сотрудника в текст подтверждения
    const nameSpan = document.getElementById('blockName');
    if (nameSpan) nameSpan.textContent = name;

    // 2. Формируем правильную ссылку для формы: /admin/employees/{id}/block
    const form = document.getElementById('blockForm');
    if (form) form.action = '/admin/employees/' + id + '/block';

    // 3. Открываем окно
    openModal('blockModal');
}

// === РАЗБЛОКИРОВКА ===
function openUnblockModal(id, name) {
    const nameSpan = document.getElementById('unblockName');
    if (nameSpan) nameSpan.textContent = name;

    const form = document.getElementById('unblockForm');
    if (form) form.action = '/admin/employees/' + id + '/unblock';

    openModal('unblockModal');
}

// === УДАЛЕНИЕ ===
function openDeleteModal(id, name) {
    const nameSpan = document.getElementById('deleteName');
    if (nameSpan) nameSpan.textContent = name;

    const form = document.getElementById('deleteForm');
    if (form) form.action = '/admin/employees/' + id + '/delete';

    openModal('deleteModal');
}

// === ОБЩИЕ ФУНКЦИИ (Открытие/Закрытие) ===

function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) return;

    // Сначала делаем блок видимым (display: flex), чтобы браузер его отрендерил
    modal.style.display = 'flex';

    // Небольшая задержка перед добавлением класса show для срабатывания CSS-анимации (opacity)
    setTimeout(() => {
        modal.classList.add('show');
    }, 10);
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (!modal) return;

    // Убираем класс show (запускается анимация исчезновения)
    modal.classList.remove('show');

    // Ждем окончания анимации (300мс как в CSS transition) перед скрытием элемента
    setTimeout(() => {
        modal.style.display = 'none';
    }, 300);
}

// === ЗАКРЫТИЕ ПО КЛИКУ НА ФОН ===
// Если пользователь кликнул вне контента модального окна (на темный фон)
window.onclick = function(event) {
    if (event.target.classList.contains('modal-overlay')) {
        closeModal(event.target.id);
    }
}

// Закрытие по нажатию Esc
document.addEventListener('keydown', function(event) {
    if (event.key === "Escape") {
        document.querySelectorAll('.modal-overlay.show').forEach(modal => {
            closeModal(modal.id);
        });
    }
});