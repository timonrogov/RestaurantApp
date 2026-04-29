/**
 * admin-gantt.js
 * Диаграмма Ганта для планировщика кухни.
 */

// ─────────────────────────────────────────────
// Состояние (Используем var для защиты от Hot Reloading)
// ─────────────────────────────────────────────
var ganttState = {
    date:         todayStr(),      // "YYYY-MM-DD"
    ppm:          2.5,             // pixels per minute
    data:         null,            // GanttDataDto
    initialized:  false,
    nowTimer:     null,
    scrolledToNow: false           // Флаг автоскролла
};

var ROW_HEIGHT  = 76;
var OFFSET_X    = 40;

var EQUIP_ICONS = {
    GRILL:  'fa-fire',
    OVEN:   'fa-temperature-high',
    FRYER:  'fa-oil-can',
    STOVE:  'fa-burn',
};

var RU_MONTHS   =['января','февраля','марта','апреля','мая','июня',
                  'июля','августа','сентября','октября','ноября','декабря'];
var RU_WEEKDAYS =['Воскресенье','Понедельник','Вторник','Среда',
                  'Четверг','Пятница','Суббота'];


function switchView(view, silent) {
    const isGantt = (view === 'gantt');
    document.getElementById('viewCards').style.display = isGantt ? 'none' : '';
    document.getElementById('viewGantt').style.display = isGantt ? ''     : 'none';
    document.getElementById('btnViewCards').classList.toggle('view-toggle-btn--active', !isGantt);
    document.getElementById('btnViewGantt').classList.toggle('view-toggle-btn--active',  isGantt);

    if (!silent) localStorage.setItem('schedulerView', view);
    if (isGantt && !ganttState.initialized) {
        ganttState.initialized = true;
        initGantt();
    }
}

function initGantt() {
    const timelineCol = document.getElementById('ganttTimelineCol');
    const labelsCol   = document.getElementById('ganttLabelsCol');

    timelineCol.addEventListener('scroll', () => {
        labelsCol.scrollTop = timelineCol.scrollTop;
    });

    // --- ЛОГИКА PAN/DRAG ---
    let isDown = false;
    let startX;
    let scrollLeft;

    timelineCol.addEventListener('mousedown', (e) => {
        if (e.target.closest('.gantt-bar')) return;
        isDown = true;
        timelineCol.classList.add('is-dragging');
        startX = e.pageX - timelineCol.offsetLeft;
        scrollLeft = timelineCol.scrollLeft;
    });
    timelineCol.addEventListener('mouseleave', () => { isDown = false; timelineCol.classList.remove('is-dragging'); });
    timelineCol.addEventListener('mouseup', () => { isDown = false; timelineCol.classList.remove('is-dragging'); });
    timelineCol.addEventListener('mousemove', (e) => {
        if (!isDown) return;
        e.preventDefault();
        const x = e.pageX - timelineCol.offsetLeft;
        timelineCol.scrollLeft = scrollLeft - (x - startX) * 1.5;
    });

    document.addEventListener('click', (e) => {
        const popup = document.getElementById('ganttPopup');
        if (popup.style.display !== 'none' && !popup.contains(e.target) && !e.target.closest('.gantt-bar')) {
            closePopup();
        }
    });

    fetchGanttData(ganttState.date);
}

// ─────────────────────────────────────────────
// Загрузка данных
// ─────────────────────────────────────────────

function fetchGanttData(date, silent = false) {
    const loader = document.getElementById('ganttLoader');
    const rows   = document.getElementById('ganttTaskRows');
    const axis   = document.getElementById('ganttAxis');
    const labels = document.getElementById('ganttLabelRows');
    const timelineCol = document.getElementById('ganttTimelineCol');

    // Запоминаем текущую позицию скролла, чтобы при живом обновлении экран не прыгал
    const currentScroll = timelineCol ? timelineCol.scrollLeft : 0;

    // Если обновление НЕ тихое (например, при смене дня) — показываем лоадер и чистим экран
    if (!silent) {
        loader.style.display = 'flex';
        rows.innerHTML   = '';
        axis.innerHTML   = '';
        labels.innerHTML = '';
        closePopup();
    }

    $.get('/admin/scheduler/api/gantt', { date: date })
        .done(data => {
            ganttState.data = data;
            updateDateLabel(data.date);

            // Рендерим заново (перезапишет innerHTML без мерцания)
            renderGantt(data);

            // Восстанавливаем позицию скролла после рендера
            if (timelineCol) {
                timelineCol.scrollLeft = currentScroll;
            }
        })
        .fail(() => {
            if (!silent) {
                rows.innerHTML = '<div class="gantt-empty"><i class="fas fa-exclamation-circle"></i><p>Ошибка загрузки данных</p></div>';
            }
        })
        .always(() => {
            if (!silent) {
                loader.style.display = 'none';
            }
        });
}

function renderGantt(data) {
    const ppm         = ganttState.ppm;
    const dayStartMin = timeToMinutes(data.dayStart);
    const dayEndMin   = timeToMinutes(data.dayEnd);
    const totalMin    = dayEndMin - dayStartMin;
    const totalWidth  = Math.round(totalMin * ppm) + (OFFSET_X * 2);

    ganttState.dayStartMin = dayStartMin;
    ganttState.dayEndMin   = dayEndMin;

    const axisEl   = document.getElementById('ganttAxis');
    const rowsEl   = document.getElementById('ganttTaskRows');
    const labelsEl = document.getElementById('ganttLabelRows');

    axisEl.innerHTML = ''; rowsEl.innerHTML = ''; labelsEl.innerHTML = '';
    rowsEl.style.minWidth = totalWidth + 'px';
    axisEl.style.minWidth = totalWidth + 'px';

    renderAxis(axisEl, dayStartMin, totalMin, ppm, totalWidth);

    if (!data.cooks || data.cooks.length === 0) {
        rowsEl.innerHTML = '<div class="gantt-empty"><i class="fas fa-user-slash"></i><p>Нет активных поваров</p></div>';
        return;
    }

    data.cooks.forEach(cook => {
        const labelRow = document.createElement('div');
        labelRow.className = 'gantt-label-row';
        labelRow.style.height = ROW_HEIGHT + 'px';
        labelRow.innerHTML = `<div class="gantt-label-name" title="${esc(cook.name)}">${esc(cook.name)}</div>
                              <div class="gantt-label-spec">${esc(cook.specialization)}</div>`;
        labelsEl.appendChild(labelRow);

        const taskRow = document.createElement('div');
        taskRow.className = 'gantt-task-row';
        taskRow.style.height = ROW_HEIGHT + 'px';
        taskRow.style.minWidth = totalWidth + 'px';

        for (let m = 0; m <= totalMin; m += 15) {
            if (m % 60 === 0 || ppm >= 4) {
                const line = document.createElement('div');
                line.className = (m % 60 === 0) ? 'gantt-hour-line' : 'gantt-hour-line gantt-hour-line--minor';
                line.style.left = Math.round(m * ppm) + OFFSET_X + 'px';
                taskRow.appendChild(line);
            }
        }

        cook.tasks.forEach(task => {
            const bar = renderBar(task, dayStartMin, ppm);
            if (bar) taskRow.appendChild(bar);
        });
        rowsEl.appendChild(taskRow);
    });

    // ── НЕЗАВИСИМАЯ ЛОГИКА ТЕКУЩЕГО ВРЕМЕНИ ──
    const isViewedToday = (ganttState.date === getLocalTodayStr());

    if (isViewedToday) {
        const localNowStr = currentTimeStr();
        renderNowLine(rowsEl, axisEl, dayStartMin, dayEndMin, ppm, localNowStr);

        if (!ganttState.scrolledToNow) {
            scrollToTime(localNowStr, dayStartMin, ppm);
            ganttState.scrolledToNow = true;
        }

        clearInterval(ganttState.nowTimer);
        ganttState.nowTimer = setInterval(updateNowLinePosition, 10000);
    }
}

function updateNowLinePosition() {
    const nowStr = currentTimeStr();
    const nowMin = timeToMinutes(nowStr);

    const line = document.getElementById('ganttNowLine');
    const label = document.getElementById('ganttNowLabel');

    if (nowMin < ganttState.dayStartMin || nowMin > ganttState.dayEndMin) {
        if (line) line.style.display = 'none';
        if (label) label.style.display = 'none';
        return;
    }

    const left = Math.round((nowMin - ganttState.dayStartMin) * ganttState.ppm) + OFFSET_X;

    if (line) {
        line.style.display = 'block';
        line.style.left = left + 'px';
    }
    if (label) {
        label.style.display = 'flex';
        label.style.left = left + 'px';
        label.innerHTML = nowStr;
    }
}

function renderAxis(axisEl, dayStartMin, totalMin, ppm, totalWidth) {
    const inner = document.createElement('div');
    inner.className = 'gantt-axis-inner';
    inner.style.width = totalWidth + 'px';

    for (let m = 0; m <= totalMin; m += 5) {
        const totalAbsMin = dayStartMin + m;
        const h = Math.floor(totalAbsMin / 60);
        const mins = totalAbsMin % 60;
        const timeStr = String(h).padStart(2,'0') + ':' + String(mins).padStart(2,'0');
        const left = Math.round(m * ppm) + OFFSET_X;

        if (m % 60 === 0) {
            const tick = document.createElement('div');
            tick.className = 'gantt-hour-tick';
            tick.style.left = left + 'px';
            tick.innerHTML = `<div class="gantt-hour-label">${timeStr}</div>`;
            inner.appendChild(tick);
        } else if (m % 15 === 0) {
            const tick = document.createElement('div');
            tick.className = 'gantt-minute-tick';
            tick.style.left = left + 'px';
            if (ppm >= 4) {
                tick.classList.add('gantt-minute-tick--labeled');
                tick.innerHTML = `<div class="gantt-sub-label">${timeStr}</div>`;
            }
            inner.appendChild(tick);
        } else if (m % 5 === 0 && ppm >= 3) {
            const tick = document.createElement('div');
            tick.className = 'gantt-minute-tick gantt-minute-tick--minor';
            tick.style.left = left + 'px';
            if (ppm >= 8) {
                tick.classList.add('gantt-minute-tick--labeled');
                tick.innerHTML = `<div class="gantt-sub-label">${timeStr}</div>`;
            }
            inner.appendChild(tick);
        }
    }
    axisEl.appendChild(inner);
}

function renderNowLine(rowsEl, axisEl, dayStartMin, dayEndMin, ppm, nowStr) {
    const nowMin = timeToMinutes(nowStr);
    if (nowMin < dayStartMin || nowMin > dayEndMin) return;

    const left = Math.round((nowMin - dayStartMin) * ppm) + OFFSET_X;

    const line = document.createElement('div');
    line.className = 'gantt-now-line';
    line.id = 'ganttNowLine';
    line.style.left = left + 'px';
    rowsEl.appendChild(line);

    const label = document.createElement('div');
    label.className = 'gantt-now-label';
    label.id = 'ganttNowLabel';
    label.innerHTML = nowStr;
    label.style.left = left + 'px';
    axisEl.querySelector('.gantt-axis-inner').appendChild(label);
}

function scrollToTime(timeStr, dayStartMin, ppm) {
    const tMin = timeToMinutes(timeStr);
    const left = Math.round((tMin - dayStartMin) * ppm) + OFFSET_X;
    const timelineCol = document.getElementById('ganttTimelineCol');

    timelineCol.scrollTo({
        left: left - (timelineCol.clientWidth / 2),
        behavior: 'smooth'
    });
}

// ─────────────────────────────────────────────
// Полоска задачи
// ─────────────────────────────────────────────

function renderBar(task, dayStartMin, ppm) {
    // 1. УМНАЯ ЛОГИКА ВРЕМЕНИ: Берем ФАКТ, если его нет - берем ПЛАН
    const startStr = task.actualStart || task.plannedStart;
    const endStr   = task.actualEnd || task.plannedEnd;

    if (!startStr || !endStr) return null;

    const startMin = timeToMinutes(startStr);
    let endMin     = timeToMinutes(endStr);

    // Защита: если повар нажал "Начать" и "Готово" за 1 секунду,
    // даем полоске минимальную ширину (2 минуты), чтобы ее вообще было видно на графике
    if (endMin <= startMin) {
        endMin = startMin + 2;
    }

    // + СДВИГ
    const left  = Math.round((startMin - dayStartMin) * ppm) + OFFSET_X;
    const width = Math.max(Math.round((endMin - startMin) * ppm), 4);
    const barHeight = ROW_HEIGHT - 20;

    const statusClass = 'gantt-bar--' + task.status.toLowerCase();
    const overdueClass = task.isOverdue ? ' gantt-bar--overdue' : '';

    const bar = document.createElement('div');
    bar.className = `gantt-bar ${statusClass}${overdueClass}`;
    bar.style.cssText = `left:${left}px; width:${width}px; height:${barHeight}px;`;
    bar.dataset.taskId = task.id;

    bar.innerHTML = buildBarContent(task, width);

    bar.addEventListener('click', (e) => {
        e.stopPropagation();
        openPopup(task, bar);
    });

    return bar;
}

function buildBarContent(task, width) {
    const equipIcon = task.equipmentType
        ? `<span class="gantt-bar-equip" title="${esc(task.equipmentType)}"><i class="fas ${EQUIP_ICONS[task.equipmentType] || 'fa-cog'}"></i></span>` : '';
    if (width < 60) return `<span class="gantt-bar-order">#${task.orderId}</span>`;
    if (width < 110) return `<span class="gantt-bar-order">#${task.orderId}</span>${equipIcon}`;

    const imgHtml = `<img class="gantt-bar-img" src="${esc(task.dishImagePath)}" alt="" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                     <span class="gantt-bar-img-fallback" style="display:none"><i class="fas fa-utensils"></i></span>`;

    return `<span class="gantt-bar-order">#${task.orderId}</span> ${imgHtml}
            <span class="gantt-bar-text"><span class="gantt-bar-dish">${esc(task.dishName)}</span>
            ${width > 180 ? `<span class="gantt-bar-step"> (${esc(task.stepName)})</span>` : ''}</span>${equipIcon}`;
}

function openPopup(task, barEl) {
    const popup  = document.getElementById('ganttPopup');
    document.getElementById('ganttPopupContent').innerHTML = buildPopupHtml(task);
    popup.style.display = 'block';

    const barRect   = barEl.getBoundingClientRect();
    const popW = popup.offsetWidth, popH = popup.offsetHeight;
    const MARGIN = 10;

    let left = barRect.left + barRect.width / 2 - popW / 2;
    left = Math.max(MARGIN, Math.min(left, window.innerWidth - popW - MARGIN));

    let top = barRect.top - popH - 12;
    const placeBelow = top < MARGIN;
    if (placeBelow) top = barRect.bottom + 12;

    popup.style.left = left + 'px'; popup.style.top  = top  + 'px';
    popup.classList.toggle('gantt-popup--below', placeBelow);
}

function buildPopupHtml(task) {
    const statusClass = { PLANNED: 'sched-status-planned', IN_PROGRESS: 'sched-status-in_progress', DONE: 'sched-status-done', FAILED: 'sched-status-failed', CANCELLED: 'sched-status-failed', PENDING: 'sched-status-planned'}[task.status] || '';
    const equipRow = task.equipmentType ? `<div class="gantt-popup-row"><i class="fas ${EQUIP_ICONS[task.equipmentType] || 'fa-cog'}"></i><span>Оборудование: <strong>${esc(task.equipmentType)}</strong></span></div>` : '';
    // Красиво форматируем фактическое время
        let actualText = '';
        if (task.actualStart && task.actualEnd) {
            actualText = `${esc(task.actualStart)} → ${esc(task.actualEnd)}`;
        } else if (task.actualStart) {
            actualText = `с ${esc(task.actualStart)}`;
        }

        const actualRow = actualText ? `
            <div class="gantt-popup-row"><i class="fas fa-play-circle" style="color: var(--success)"></i>
            <span>Фактически: <strong>${actualText}</strong></span></div>` : '';
    const delayRow = task.delayReason ? `<div class="gantt-popup-row" style="color: var(--error)"><i class="fas fa-exclamation-circle" style="color:var(--error)"></i><span>Задержка: ${esc(task.delayReason)}</span></div>` : '';

    return `
        <div class="gantt-popup-header">
            <img class="gantt-popup-img" src="${esc(task.dishImagePath)}" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
            <span class="gantt-popup-img-fallback" style="display:none"><i class="fas fa-utensils"></i></span>
            <div><div class="gantt-popup-title">${esc(task.dishName)}</div><div class="gantt-popup-subtitle">Заказ #${task.orderId} &middot; Стол ${esc(task.tableNumber)}</div></div>
        </div>
        <div class="gantt-popup-body">
            <div class="gantt-popup-row"><i class="fas fa-list-ol"></i><span>Шаг ${task.stepNumber} — <strong>${esc(task.stepName)}</strong></span></div>
            <div class="gantt-popup-row"><i class="fas fa-tag"></i><span class="sched-task-status ${statusClass}">${esc(task.statusDisplay)}</span></div>
            <div class="gantt-popup-row"><i class="fas fa-clock"></i><span>Плановое: <strong>${esc(task.plannedStart)} → ${esc(task.plannedEnd)}</strong> <span style="color:var(--color3)">(${task.durationMinutes} мин)</span></span></div>
            ${actualRow} ${equipRow} ${delayRow}
        </div>
    `;
}

function closePopup() { document.getElementById('ganttPopup').style.display = 'none'; }


// ─────────────────────────────────────────────
// Навигация по датам
// ─────────────────────────────────────────────

function changeDate(delta) {
    // 1. Разбиваем дату "2026-04-29" на компоненты (защита от сдвига часовых поясов)
    const parts = ganttState.date.split('-');
    const year = parseInt(parts[0], 10);
    const month = parseInt(parts[1], 10) - 1; // В JS месяцы начинаются с 0
    const day = parseInt(parts[2], 10);

    // 2. Создаем локальную дату ровно на полдень, чтобы избежать проблем с переходом на летнее/зимнее время
    const d = new Date(year, month, day, 12, 0, 0);

    // 3. Прибавляем или отнимаем день
    d.setDate(d.getDate() + delta);

    // 4. Формируем строку YYYY-MM-DD вручную из ЛОКАЛЬНЫХ данных
    const newYear = d.getFullYear();
    const newMonth = String(d.getMonth() + 1).padStart(2, '0');
    const newDay = String(d.getDate()).padStart(2, '0');

    ganttState.date = `${newYear}-${newMonth}-${newDay}`;
    ganttState.scrolledToNow = false; // Сбрасываем флаг для автоскролла

    // 5. Запрашиваем новые данные
    fetchGanttData(ganttState.date);
}

function goToToday() {
    ganttState.date = getLocalTodayStr();
    ganttState.scrolledToNow = false;
    fetchGanttData(ganttState.date);
}

function updateDateLabel(dateStr) {
    // Аналогичная защита для метки
    const parts = dateStr.split('-');
    const year = parseInt(parts[0], 10);
    const month = parseInt(parts[1], 10) - 1;
    const day = parseInt(parts[2], 10);

    const d = new Date(year, month, day);
    const label = `${RU_WEEKDAYS[d.getDay()]}, ${d.getDate()} ${RU_MONTHS[d.getMonth()]} ${d.getFullYear()}`;
    document.getElementById('ganttDateLabel').textContent = label;
}


function applyZoom(ppm) { ganttState.ppm = ppm; if (ganttState.data) renderGantt(ganttState.data); }

// --- ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ВРЕМЕНИ (ТЕПЕРЬ ТОЛЬКО ЛОКАЛЬНЫЕ) ---
function getLocalTodayStr() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function todayStr() { return getLocalTodayStr(); }
function currentTimeStr() {
    const n = new Date();
    return String(n.getHours()).padStart(2,'0') + ':' + String(n.getMinutes()).padStart(2,'0');
}
function timeToMinutes(timeStr) {
    if (!timeStr) return 0;
    const [h, m] = timeStr.split(':').map(Number);
    return h * 60 + m;
}
function esc(str) { return str == null ? '' : String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }