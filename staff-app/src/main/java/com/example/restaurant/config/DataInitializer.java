package com.example.restaurant.config;

import com.example.restaurant.enums.CookSpecialization;
import com.example.restaurant.enums.Role;
import com.example.restaurant.models.*;
import com.example.restaurant.repositories.*;
import com.example.restaurant.services.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final EmployeeService employeeService;
    private final AccountRepository accountRepository;
    private final DishCategoryRepository dishCategoryRepository;
    private final DayRepository dayRepository;
    private final DishRepository dishRepository;

    // ДОБАВЛЕННЫЕ РЕПОЗИТОРИИ ДЛЯ ПЛАНИРОВЩИКА
    private final EquipmentRepository equipmentRepository;
    private final CookProfileRepository cookProfileRepository;
    private final CookingTaskTemplateRepository templateRepository;

    @Autowired
    public DataInitializer(EmployeeService employeeService,
                           AccountRepository accountRepository,
                           DishCategoryRepository dishCategoryRepository,
                           DayRepository dayRepository,
                           DishRepository dishRepository, EquipmentRepository equipmentRepository, CookProfileRepository cookProfileRepository, CookingTaskTemplateRepository templateRepository) {
        this.employeeService = employeeService;
        this.accountRepository = accountRepository;
        this.dishCategoryRepository = dishCategoryRepository;
        this.dayRepository = dayRepository;
        this.dishRepository = dishRepository;
        this.equipmentRepository = equipmentRepository;
        this.cookProfileRepository = cookProfileRepository;
        this.templateRepository = templateRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // Этот метод выполняется автоматически при запуске приложения

        initCategories();
        initEmployees();
        initCookProfiles(); // <-- Создаем профили поварам
        initDays();
        initEquipment();    // <-- Создаем оборудование
        initDishes();       // (Блюда)
        initTemplates();    // <-- Задаем тех. карты (шаблоны) блюдам
    }

    private void initCategories() {
        if (dishCategoryRepository.count() == 0) {
            List<String> categories =
                    List.of("Основные блюда", "Супы", "Морепродукты", "Салаты", "Десерты", "Напитки");
            for (String name : categories) {
                DishCategory category = new DishCategory();
                category.setName(name);
                dishCategoryRepository.save(category);
            }
            System.out.println("Категории успешно созданы.");
        }
    }

    private void initEmployees() {
        // Проверяем, есть ли админ, чтобы не создавать дубликаты
        if (!accountRepository.existsByUsername("admin")) {
            employeeService.registerEmployee(
                    "admin",        // username
                    "1",            // password (будет захеширован внутри сервиса!)
                    "admin@rest.com",
                    "Иванов Иван Иванович",
                    "+70000000001",
                    "Мужской",
                    LocalDate.of(1990, 1, 1),
                    Role.ADMIN      // Роль
            );
            System.out.println("Администратор создан: login=admin, pass=1");
        }

        if (!accountRepository.existsByUsername("cook")) {
            employeeService.registerEmployee(
                    "cook",
                    "1",
                    "cook@rest.com",
                    "Петров Петр Петрович",
                    "+70000000002",
                    "Мужской",
                    LocalDate.of(1995, 5, 5),
                    Role.COOK
            );
            System.out.println("Повар создан: login=cook, pass=1");
        }

        if (!accountRepository.existsByUsername("waiter")) {
            employeeService.registerEmployee(
                    "waiter",
                    "1",
                    "waiter@rest.com",
                    "Сидорова Анна Павловна",
                    "+70000000003",
                    "Женский",
                    LocalDate.of(2000, 10, 10),
                    Role.WAITER
            );
            System.out.println("Официант создан: login=waiter, pass=1");
        }
    }

    private void initDays() {
        LocalDate startDate = LocalDate.now();

        // Проверяем, есть ли уже запись на сегодня. Если есть, считаем, что календарь заполнен.
        if (dayRepository.existsById(startDate)) {
            return;
        }

        System.out.println("Начинаем заполнение календаря...");

        List<Day> daysToSave = new ArrayList<>();
        LocalDate endDate = startDate.plusYears(2); // Генерируем на 2 года вперед

        // Перебираем даты от сегодня до endDate
        startDate.datesUntil(endDate).forEach(date -> {
            Day day = new Day();
            day.setWorkDate(date);

            // Определяем, выходной ли это (Суббота или Воскресенье)
            boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY ||
                    date.getDayOfWeek() == DayOfWeek.SUNDAY;

            // ЛОГИКА ЗАПОЛНЕНИЯ:

            // Ресторан работает каждый день, но в выходные дольше
            if (isWeekend) {
                day.setWorkingDay(true);
                day.setStartTime(LocalTime.of(10, 0)); // В выходные с 10:00
                day.setEndTime(LocalTime.of(23, 0));   // До 23:00
            } else {
                day.setWorkingDay(true);
                day.setStartTime(LocalTime.of(9, 0));  // В будни с 09:00
                day.setEndTime(LocalTime.of(22, 0));   // До 22:00
            }

            daysToSave.add(day);
        });

        // Сохраняем все дни одним пакетным запросом (это очень быстро)
        dayRepository.saveAll(daysToSave);
        System.out.println("Календарь успешно заполнен на 2 года вперед (" + daysToSave.size() + " дней).");
    }



    private void initDishes() {
        if (dishRepository.count() > 0) {
            return; // Если блюда уже есть, не дублируем
        }

        System.out.println("Начинаем заполнение меню...");

        // --- Супы ---
        createDish("Борщ",
                "Традиционный украинский борщ с говядиной. Приготовлен на насыщенном говяжьем бульоне.",
                "Говядина, Свекла, Капуста, Картофель, Морковь, Лук, Томатная паста, Сметана, Зелень",
                500, 350.00, "Супы", "борщ.jpg");

        createDish("Грибной крем-суп",
                "Ароматный суп-пюре из лесных грибов с трюфельным маслом и гренками из чиабатты",
                "Шампиньоны, Белые грибы, Лук, Сливки, Чеснок, Тимьян, Трюфельное масло, Чиабатта",
                400, 320.00, "Супы", "грибнойКремСуп.jpg");

        createDish("Том Ям с креветками",
                "Острый тайский суп на кокосовом молоке с лемонграссом и королевскими креветками",
                "Креветки, Кокосовое молоко, Лемонграсс, Имбирь, Шампиньоны, Лайм, Чили, Кинза",
                450, 450.00, "Супы", "томЯмСКреветками.jpg");

        createDish("Суп Рамен",
                "Японский острый бульон с свиной грудинкой, яйцом пашот и ростками бамбука",
                "Свиная грудинка, Лапша удон, Яйцо, Ростки бамбука, Нори, Кунжут, Соус тамари, Перец чили",
                600, 390.00, "Супы", "супРамен.jpg");


        // --- Основные блюда ---
        createDish("Овощи гриль с розмарином",
                "Сезонные овощи, запеченные на углях с оливковым маслом и прованскими травами",
                "Кабачок, Баклажан, Перец, Помидор, Лук, Розмарин, Оливковое масло",
                300, 220.00, "Основные блюда", "овощиГриль.jpg");

        createDish("Картофель по-деревенски",
                "Ароматные дольки картофеля с хрустящей корочкой и чесночным соусом",
                "Картофель, Чеснок, Паприка, Укроп, Сметана, Оливковое масло",
                250, 160.00, "Основные блюда", "картофельПоДеревенски.jpg");

        createDish("Жареные баклажаны",
                "Ломтики баклажанов в хрустящей панировке",
                "Баклажаны, Чеснок, Йогурт, Мука, Яйцо, Паприка, Кинза",
                280, 200.00, "Основные блюда", "жареныеБаклажаныССоусом.jpg");

        createDish("Стейк Рибай Black Angus",
                "Мраморный стейк сухой выдержки с трюфельным пюре и томлёными шалотом",
                "Говядина Black Angus, Трюфельное масло, Картофель, Шалот, Чеснок, Розмарин, Морская соль, Перец горошком",
                350, 1890.00, "Основные блюда", "стейкРибайBlackAngus.jpg");

        createDish("Картофельные драники",
                "Хрустящие оладьи из тертого картофеля с соусом из сметаны и зеленого лука",
                "Картофель, Лук, Мука, Яйцо, Сметана, Зеленый лук, Укроп",
                220, 190.00, "Основные блюда", "картофельныеДраники.jpg");


        // --- Морепродукты ---
        createDish("Паста с тигровыми креветками",
                "Спагетти в сливочно-чесночном соусе с креветками гриль и пармезаном",
                "Тигровые креветки, Спагетти, Сливки, Чеснок, Белое вино, Пармезан, Петрушка, Лимонная цедра",
                400, 590.00, "Морепродукты", "пастаСТигровымиКреветками.jpg");

        createDish("Равиоли с крабом",
                "Домашние равиоли с крабовой начинкой и икрой летучей рыбы",
                "Крабовое мясо, Икра тобико, Мука для пасты, Шнитт-лук, Лимон, Укроп, Перец аллашотто",
                280, 1340.00, "Морепродукты", "равиолиСКрабомИШампанским.jpg");

        createDish("Лосось под фисташками",
                "Филе норвежского лосося с кунжутом и цитрусовым эмульсионом",
                "Лосось, Фисташки, Апельсин, Лимон, Кунжут, Оливковое масло, Укроп, Мёд",
                300, 790.00, "Морепродукты", "лососьПодФисташковойКорочкой.jpg");

        createDish("Кальмары в хрустящем кляре",
                "Кольца кальмаров в пивном кляре с соусом тар-тар и лаймовым айоли",
                "Кальмары, Пиво светлое, Мука, Яйцо, Чеснок, Майонез, Каперсы, Лайм",
                350, 420.00, "Морепродукты", "кальмарыВХрустящемКляре.jpg");

        createDish("Устрицы Рокфеллер",
                "Запеченные устрицы под соусом из шпината с пармезаном и беконом",
                "Устрицы, Шпинат, Пармезан, Бекон, Лук-шалот, Чеснок, Сливочное масло, Белое вино",
                180, 990.00, "Морепродукты", "устрицыРокфеллер.jpg");


        // --- Салаты ---
        createDish("Цезарь с тигровыми креветками",
                "Классический цезарь с креветками гриль, пармезаном и авторским чесночным крутоном",
                "Тигровые креветки, Романо, Пармезан, Черешня, Домашние крутоны, Соус цезарь, Анчоусы, Лимонный сок",
                300, 620.00, "Салаты", "цезарьСТигровымиКреветками.jpg");

        createDish("Греческий с кедровыми орехами",
                "Традиционная комбинация с добавлением авокадо и кедровых орешков",
                "Огурец, Помидор, Красный лук, Маслины, Фета, Оливковое масло, Лимонный сок, Кедровые орехи",
                250, 370.00, "Салаты", "греческийСКедровымиОрехами.jpg");

        createDish("Салат «Вальдорф»",
                "Американская классика 1896 года с сельдереем и грецкими орехами",
                "Куриная грудка, Сельдерей, Яблоко, Грецкие орехи, Виноград, Майонез, Лимонный сок, Салат айсберг",
                280, 390.00, "Салаты", "салатВальдорф.jpg");

        createDish("Салат «Мимоза»",
                "Советская классика с консервированной горбушей и слоёной структурой",
                "Горбуша консервированная, Картофель, Морковь, Яйцо, Лук, Майонез, Укроп, Лимон",
                200, 290.00, "Салаты", "салатМимоза.jpg");

        createDish("Капрезе с бальзамиком",
                "Итальянская классика из моцареллы и томатов с базиликовым песто",
                "Моцарелла ди Буфала, Томаты черри, Свежий базилик, Оливковое масло, Бальзамический крем, Кедровые орехи, Морская соль",
                220, 430.00, "Салаты", "капрезеСБальзамиком.jpg");


        // --- Десерты ---
        createDish("Чизкейк",
                "Нежный чизкейк с ванильной начинкой",
                "Творожный сыр, Песочное печенье, Сливки, Сахар, Ваниль, Яйца, Лимонный сок",
                150, 750.00, "Десерты", "чизкейк.jpg");

        createDish("Круассан с миндальным кремом",
                "Слоёное тесто с нежным франжипаном и хрустящей крошкой из обжаренного миндаля",
                "Мука высшего сорта, Сливочное масло, Миндальная паста, Сахарная пудра, Молоко, Дрожжи, Ванильный экстракт, Миндальные лепестки",
                120, 180.00, "Десерты", "круассанСМиндальнымКремом.jpg");

        createDish("Пирог с ревенём и меренгой",
                "Рассыпчатая песочная основа с кисло-сладкой начинкой и воздушной безе",
                "Ревень, Яичный белок, Сахар, Мука, Сливочный сыр, Лимонная цедра, Корица, Ванильный сахар",
                180, 210.00, "Десерты", "пирогСРевенемИМеренгой.jpg");

        createDish("Панна-котта с карамелью",
                "Нежный десерт в песочном корже с карамельным соусом и золотой фольгой",
                "Сливки 35%, Желатин, Ванильный стручок, Песочное тесто, Карамель, Малина, Мята, Съедобное золото",
                200, 390.00, "Десерты", "паннаКоттаВКарамельномКорже.jpg");

        createDish("Эклеры с заварным кремом",
                "Классическая французская выпечка с ванильным кремом и шоколадной глазурью",
                "Заварное тесто, Ванильный крем, Темный шоколад, Сливки 33%, Яйца, Сахар, Мука, Соль",
                100, 150.00, "Десерты", "эклерыСЗаварнымКремом.jpg");

        createDish("Парфе из белого шоколада",
                "Слоеный десерт с манговым желе, кремом-брюле и хрустящим пралине",
                "Белый шоколад, Манго, Желатин, Фундук, Сахар, Сливки, Ваниль, Съедобные цветы",
                180, 430.00, "Десерты", "парфеИзБелогоШоколада.jpg");


        // --- Напитки ---
        createDish("Имбирный лимонад",
                "Оригинальный лимонад с имбирным сиропом, мятой и ломтиками лайма",
                "Имбирный сироп, Сок лайма, Газированная вода, Лед, Мята, Ломтики лайма, Корица",
                1000, 400.00, "Напитки", "имбирныйЛимонад.jpg");

        createDish("Апельсиновый сок",
                "Фреш из валенсийских апельсинов с мякотью и кубиками льда",
                "Апельсины, Лед, Ломтик апельсина",
                600, 290.00, "Напитки", "свежевыжатыйАпельсиновыйСок.jpg");

        createDish("Глинтвейн безалкогольный",
                "Ароматный напиток с апельсином, гвоздикой и корицей в карамельном сиропе",
                "Яблочный сок, Апельсин, Корица, Гвоздика, Кардамон, Мед, Имбирь, Звездчатый анис",
                500, 350.00, "Напитки", "глинтвейнБезалкогольный.jpg");

        createDish("Чай \"Цитрусовый рай\"",
                "Ферментированный улун с цедрой апельсина, лимона и лепестками календулы",
                "Чай улун, Апельсиновая цедра, Лимонная цедра, Мед, Лепестки календулы, Вода 85°C",
                500, 240.00, "Напитки", "чайЦитрусовыйРай.jpg");

        createDish("Чай «Царский сбор»",
                "Ферментированный иван-чай с цветами липы и лесными ягодами",
                "Иван-чай, Липа, Малина, Черника, Медовые соты, Гвоздика",
                500, 250.00, "Напитки", "чайЦарскийСбор.jpg");

        createDish("Матча-латте",
                "Японский зеленый чай с ванильным сиропом и овсяным молоком в слоистой подаче",
                "Порошок матча, Овсяное молоко, Ванильный сироп, Взбитые сливки, Фисташковая крошка",
                500, 490.00, "Напитки", "матчаЛатте.jpg");

        createDish("Латте с карамелью",
                "Ароматный эспрессо с миндальным молоком, карамельным сиропом и воздушной молочной пенкой",
                "Эспрессо, Миндальное молоко, Карамельный сироп, Взбитые сливки, Карамельная крошка",
                300, 380.00, "Напитки", "латтеСКарамелью.jpg");

        createDish("Эспрессо Мароккано",
                "Арабский кофе с кардамоном и апельсиновой цедрой в медной джезве",
                "Арабика, Кардамон, Апельсиновая цедра, Тростниковый сахар, Розовая вода",
                200, 220.00, "Напитки", "эспрессоМароккано.jpg");

        createDish("Гляссе с лавандой",
                "Холодный кофе с лавандовым сиропом и миндальным молоком",
                "Эспрессо, Миндальное молоко, Лавандовый сироп, Дробленый лед, Темный шоколад",
                350, 410.00, "Напитки", "гляссеСЛавандой.jpg");

        System.out.println("Меню успешно заполнено.");
    }

    // Вспомогательный метод для создания блюда
    private void createDish(String name, String desc, String comp,
                            Integer weight, Double price,
                            String categoryName, String imagePath) {

        // Находим категорию по имени
        // ВАЖНО: Мы ищем только среди существующих (созданных в initCategories)
        // Чтобы избежать NullPointerException, используем stream filter
        DishCategory category = dishCategoryRepository.findAll().stream()
                .filter(c -> c.getName().equalsIgnoreCase(categoryName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Категория не найдена: " + categoryName));

        Dish dish = new Dish();
        dish.setName(name);
        dish.setDescription(desc);
        dish.setComposition(comp);
        dish.setWeight(weight);
        dish.setPrice(price); // Здесь можно использовать BigDecimal, если вы его внедрили
        dish.setCategory(category);
        dish.setImagePath(imagePath); // Сохраняем имя файла
        dish.setAvailable(true); // По умолчанию доступно

        dishRepository.save(dish);
    }



    private void initCookProfiles() {
        if (cookProfileRepository.count() == 0) {
            Employee cook = employeeService.getEmployeeByUsername("cook");
            if (cook != null) {
                CookProfile profile = new CookProfile();
                profile.setEmployee(cook);
                profile.setSpecialization(CookSpecialization.UNIVERSAL);
                profile.setActive(true);
                cookProfileRepository.save(profile);
                System.out.println("Профиль повара (CookProfile) успешно создан.");
            }
        }
    }

    private void initEquipment() {
        if (equipmentRepository.count() == 0) {
            equipmentRepository.save(new Equipment(null, "Гриль Josper", "GRILL", 2, true));
            equipmentRepository.save(new Equipment(null, "Духовой шкаф Unox", "OVEN", 4, true));
            equipmentRepository.save(new Equipment(null, "Фритюрница", "FRYER", 2, true));
            equipmentRepository.save(new Equipment(null, "Плита индукционная", "STOVE", 6, true));
            System.out.println("Кухонное оборудование успешно создано.");
        }
    }

    private void initTemplates() {
        if (templateRepository.count() == 0) {
            System.out.println("Начинаем заполнение шаблонов приготовления (техкарт)...");

            // 1. Шаблоны для Стейка Рибай (многоэтапное приготовление с оборудованием)
            dishRepository.findAll().stream()
                    .filter(d -> d.getName().equals("Стейк Рибай Black Angus"))
                    .findFirst()
                    .ifPresent(steak -> {
                        templateRepository.save(new CookingTaskTemplate(null, steak, 1, "Подготовка мяса", 3, CookSpecialization.HOT_SHOP, null, false));
                        templateRepository.save(new CookingTaskTemplate(null, steak, 2, "Жарка на гриле", 12, CookSpecialization.GRILL, "GRILL", false));
                        templateRepository.save(new CookingTaskTemplate(null, steak, 3, "Отдых мяса и подача", 5, CookSpecialization.HOT_SHOP, null, false));
                    });

            // 2. Шаблон для Цезаря (быстро, без оборудования)
            dishRepository.findAll().stream()
                    .filter(d -> d.getName().equals("Цезарь с тигровыми креветками"))
                    .findFirst()
                    .ifPresent(caesar -> {
                        templateRepository.save(new CookingTaskTemplate(null, caesar, 1, "Нарезка и сборка", 8, CookSpecialization.COLD_SHOP, null, false));
                    });

            // 3. Шаблон для Картофеля по-деревенски
            dishRepository.findAll().stream()
                    .filter(d -> d.getName().equals("Картофель по-деревенски"))
                    .findFirst()
                    .ifPresent(potato -> {
                        templateRepository.save(new CookingTaskTemplate(null, potato, 1, "Запекание", 15, CookSpecialization.HOT_SHOP, "OVEN", false));
                    });

            // Для простоты, всем остальным блюдам дадим базовый шаблон на 10 минут:
            dishRepository.findAll().forEach(dish -> {
                if (!templateRepository.existsByDishId(dish.getId())) {
                    templateRepository.save(new CookingTaskTemplate(null, dish, 1, "Приготовление: " + dish.getName(), 10, CookSpecialization.UNIVERSAL, null, false));
                }
            });

            System.out.println("Шаблоны приготовления успешно созданы.");
        }
    }
}