-- Количество порций, готовящихся за один заход (этап шаблона)
ALTER TABLE cooking_task_template
    ADD COLUMN IF NOT EXISTS portions_per_slot INTEGER NOT NULL DEFAULT 1;

-- Количество порций в конкретной задаче-партии
ALTER TABLE cooking_task
    ADD COLUMN IF NOT EXISTS portion_count INTEGER NOT NULL DEFAULT 1;