package com.example.restaurant.scheduler.messages.dto;

import lombok.ToString;

import java.util.List;

/**
 * Тело сообщения PARAMS_RESPONSE.
 * Отправляется CookAgent → TaskAgent.
 *
 * Содержит список вариантов размещения, которые повар готов предложить.
 * Может содержать 0–3 варианта: asap, jit, conflict (в любой комбинации).
 * Пустой список означает, что у повара нет ни одного подходящего варианта.
 */
@ToString
public class ParamsResponseBody {

    /** ID задачи, на которую отвечает повар (зеркало из запроса). */
    private final long taskId;

    /** ID повара — для удобства логирования и отладки. */
    private final long cookId;

    /**
     * Список вариантов размещения.
     * TaskAgent накапливает варианты от всех поваров, потом оценивает все вместе.
     */
    private final List<PlacementVariant> variants;

    public ParamsResponseBody(long taskId, long cookId, List<PlacementVariant> variants) {
        this.taskId = taskId;
        this.cookId = cookId;
        this.variants = variants;
    }

    public long getTaskId() { return taskId; }
    public long getCookId() { return cookId; }
    public List<PlacementVariant> getVariants() { return variants; }
}