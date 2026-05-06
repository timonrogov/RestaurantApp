package com.example.restaurant.scheduler.messages;

/**
 * Перечень всех типов сообщений, которыми обмениваются агенты планировщика.
 *
 * Каждое сообщение имеет строго определённое направление (кто кому пишет)
 * и ожидаемое тело (body). Тела описаны в dto-классах рядом с этим файлом.
 *
 * Группы сообщений:
 *   1. Инициализация
 *   2. Переговоры TaskAgent ↔ CookAgent
 *   3. Переговоры TaskAgent ↔ EquipmentTypeAgent
 *   4. Резервирование (общее для поваров и оборудования)
 *   5. Вытеснение
 *   6. Координация заказа (TaskAgent ↔ OrderAgent ↔ DispatcherAgent)
 *   7. Запросы к сцене (SceneAgent)
 *   8. Адаптивные события (внешние команды от SchedulerService)
 */
public enum MessageType {

    // -----------------------------------------------------------------------
    // 1. Инициализация
    // -----------------------------------------------------------------------

    /**
     * Диспетчер → любой агент.
     * Тело: зависит от типа агента (см. каждый агент отдельно).
     * Агент должен инициализировать своё состояние и быть готов к работе.
     */
    INIT,

    // -----------------------------------------------------------------------
    // 2. Переговоры TaskAgent ↔ CookAgent
    // -----------------------------------------------------------------------

    /**
     * TaskAgent → CookAgent.
     * Тело: ParamsRequestBody.
     * Задача спрашивает повара: «Когда ты можешь меня взять?
     * Дай список вариантов размещения в своём расписании».
     */
    PARAMS_REQUEST,

    /**
     * CookAgent → TaskAgent.
     * Тело: ParamsResponseBody.
     * Повар отвечает списком вариантов (asap, jit, conflict).
     * Если вариантов нет — список пустой.
     */
    PARAMS_RESPONSE,

    // -----------------------------------------------------------------------
    // 3. Переговоры TaskAgent ↔ EquipmentTypeAgent
    // -----------------------------------------------------------------------

    /**
     * TaskAgent → EquipmentTypeAgent.
     * Тело: EquipmentRequestBody.
     * Задача проверяет: «Есть ли у тебя свободная ёмкость в нужный интервал?»
     * Вызывается только если template.requiredEquipmentType != null.
     */
    EQUIPMENT_REQUEST,

    /**
     * EquipmentTypeAgent → TaskAgent.
     * Тело: EquipmentResponseBody.
     * Агент отвечает, доступно ли оборудование и в какое время.
     */
    EQUIPMENT_RESPONSE,

    // -----------------------------------------------------------------------
    // 4. Резервирование (общее для обоих типов ресурсов)
    // -----------------------------------------------------------------------

    /**
     * TaskAgent → CookAgent или EquipmentTypeAgent.
     * Тело: PlanningRequestBody.
     * Задача выбрала лучший вариант и просит зарезервировать временной слот.
     */
    PLANNING_REQUEST,

    /**
     * CookAgent или EquipmentTypeAgent → TaskAgent.
     * Тело: PlanningResponseBody.
     * Ресурс подтверждает резервирование или отказывает.
     * Отказ возможен если слот занял кто-то другой пока шли переговоры.
     */
    PLANNING_RESPONSE,

    // -----------------------------------------------------------------------
    // 5. Вытеснение
    // -----------------------------------------------------------------------

    /**
     * CookAgent → TaskAgent (вытесненному).
     * Тело: Long taskId (ID вытесненной задачи).
     * Повар уведомляет задачу, что та удалена из его расписания —
     * она должна заново начать переговоры с другими поварами.
     */
    REMOVE_TASK,

    /**
     * OrderAgent → TaskAgent.
     * Тело: CancelAndReplanBody.
     * OrderAgent сообщает задаче: "Условия изменились (например, предыдущий курс завершился раньше/позже).
     * Отмени текущие брони у повара и оборудования и начни торги заново с новыми временными рамками."
     */
    CANCEL_AND_REPLAN,

    /**
     * TaskAgent → CookAgent / EquipmentTypeAgent.
     * Тело: Long taskId.
     * TaskAgent добровольно отказывается от своего слота (например, получив CANCEL_AND_REPLAN).
     * Ресурс-агент должен просто удалить эту задачу из своего in-memory расписания.
     */
    FREE_SLOT,

    // -----------------------------------------------------------------------
    // 6. Координация заказа
    // -----------------------------------------------------------------------

    /**
     * TaskAgent → OrderAgent.
     * Тело: TaskPlannedBody (taskId + confirmedStart + confirmedEnd).
     * Задача успешно запланирована, слоты зарезервированы, CookingTask сохранён в БД.
     */
    TASK_PLANNED,

    /**
     * TaskAgent → OrderAgent.
     * Тело: Long taskId.
     * Задача вытеснена и начинает перепланирование.
     * OrderAgent должен учесть, что latestPlannedEnd может измениться.
     */
    TASK_REPLANNING,

    /**
     * TaskAgent → OrderAgent.
     * Тело: Long taskId.
     * Задача исчерпала все варианты и не смогла запланироваться.
     * OrderAgent логирует предупреждение и завершает курс без этой задачи.
     */
    TASK_FAILED,

    /**
     * OrderAgent → DispatcherAgent.
     * Тело: Long orderId.
     * Все задачи всех курсов заказа запланированы (или провалились).
     * DispatcherAgent может выполнить финальные действия (например, уведомить официанта).
     */
    ALL_TASKS_PLANNED,

    // -----------------------------------------------------------------------
    // 7. Запросы к сцене
    // -----------------------------------------------------------------------

    /**
     * TaskAgent → SceneAgent.
     * Тело: CookSpecialization.
     * Задача запрашивает список ID агентов поваров с нужной специализацией.
     */
    GET_AVAILABLE_COOKS,

    /**
     * SceneAgent → TaskAgent.
     * Тело: List<String> (список agentId активных CookAgent-ов).
     */
    AVAILABLE_COOKS_RESPONSE,

    /**
     * TaskAgent → SceneAgent.
     * Тело: String equipmentType.
     * Задача запрашивает agentId агента оборудования нужного типа.
     * (Один агент на тип — поэтому ответ содержит не список, а один ID.)
     */
    GET_AVAILABLE_EQUIPMENT,

    /**
     * SceneAgent → TaskAgent.
     * Тело: String equipmentTypeAgentId (или null если такого типа нет/недоступен).
     */
    AVAILABLE_EQUIPMENT_RESPONSE,

    // -----------------------------------------------------------------------
    // 8. Адаптивные события (внешние команды от SchedulerService)
    // -----------------------------------------------------------------------

    /**
     * SchedulerService → DispatcherAgent.
     * Тело: Order (JPA-сущность).
     * Заказ перешёл в статус COOKING — начать планирование.
     */
    NEW_ORDER,

    /**
     * SchedulerService → DispatcherAgent.
     * Тело: Long orderId.
     * Заказ отменён — освободить все слоты его задач.
     */
    ORDER_CANCELLED,

    /**
     * SchedulerService → DispatcherAgent.
     * Тело: Long cookProfileId.
     * Повар недоступен — перераспределить все его задачи.
     */
    COOK_UNAVAILABLE,

    /**
     * SchedulerService → DispatcherAgent.
     * Тело: Long cookProfileId.
     * Повар снова доступен — зарегистрировать его в сцене.
     */
    COOK_AVAILABLE,

    /**
     * SchedulerService → DispatcherAgent.
     * Тело: Long equipmentId (ID конкретной единицы Equipment).
     * Единица оборудования сломана — уменьшить суммарную ёмкость типа,
     * перераспределить задачи если ёмкость стала меньше числа активных задач.
     */
    EQUIPMENT_BROKEN,

    /**
     * SchedulerService → DispatcherAgent.
     * Тело: Long equipmentId.
     * Единица оборудования починена — восстановить ёмкость типа.
     */
    EQUIPMENT_FIXED,

    /**
     * SchedulerService → DispatcherAgent.
     * Тело: Long taskId.
     * Повар нажал «Готово» на KDS — зафиксировать actualEndTime,
     * уведомить OrderAgent о фактическом окончании (для пересчёта следующего курса).
     */
    TASK_DONE_EVENT,

    /**
     * SchedulerService → DispatcherAgent.
     * Тело: TaskDelayBody (taskId + delayMinutes + reason).
     * Повар сообщил о задержке — пересчитать notBefore зависимых задач.
     */
    TASK_DELAY_EVENT,

    /**
     * SchedulerService → DispatcherAgent.
     * Зарегистрирован новый повар или обновлена его специализация.
     */
    COOK_CREATED,

    /**
     * SchedulerService → DispatcherAgent.
     * Куплено новое оборудование.
     */
    EQUIPMENT_CREATED
}