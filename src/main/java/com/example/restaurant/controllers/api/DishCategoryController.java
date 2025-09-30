package com.example.restaurant.controllers.api;

import com.example.restaurant.models.DishCategory;
import com.example.restaurant.services.DishCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class DishCategoryController {
    private final DishCategoryService dishCategoryService;

    @Autowired
    public DishCategoryController(DishCategoryService dishCategoryService) {
        this.dishCategoryService = dishCategoryService;
    }

    @GetMapping
    public List<DishCategory> getAllCategories() {
        return dishCategoryService.getAllCategories();
    }

    @GetMapping("/{id}")
    public DishCategory getCategoryById(@PathVariable Long id) {
        return dishCategoryService.getCategoryById(id);
    }

    @PostMapping
    public DishCategory createCategory(@RequestBody DishCategory category) {
        return dishCategoryService.createCategory(category);
    }

    @DeleteMapping("/{id}")
    public void deleteCategory(@PathVariable Long id) {
        dishCategoryService.deleteCategory(id);
    }
}
