package com.example.restaurant.controllers;

import com.example.restaurant.models.*;
import com.example.restaurant.services.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.security.Principal;
import java.util.*;

@Controller
public class MenuController {

    private final MenuPageService menuPageService;


    @Autowired
    public MenuController(MenuPageService menuPageService) {
        this.menuPageService = menuPageService;
    }


    @GetMapping("/menu")
    public String getMenu(Model model,
                          Principal principal,
                          HttpServletRequest request,
                          HttpServletResponse response) {
        // 1. Получаем ВСЕ данные от сервиса одной строкой
        Map<String, Object> pageData = menuPageService.getMenuPageData(principal, request, response);

        // 2. Перекладываем данные из Map в Model
        model.addAllAttributes(pageData);

        return "menu";
    }
}
