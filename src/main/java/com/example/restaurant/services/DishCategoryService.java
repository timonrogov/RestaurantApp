package com.example.restaurant.services;

import com.example.restaurant.models.DishCategory;
import com.example.restaurant.repositories.DishCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DishCategoryService {
    private final DishCategoryRepository dishCategoryRepository;

    @Autowired
    public DishCategoryService(DishCategoryRepository dishCategoryRepository) {
        this.dishCategoryRepository = dishCategoryRepository;
    }

    public List<DishCategory> getAllCategories() {
        return dishCategoryRepository.findAll();
    }

    public DishCategory createCategory(DishCategory category) {
        /*if (category.getName() == null || category.getName().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be null or empty");
        }*/
        return dishCategoryRepository.save(category);
    }

    public DishCategory getCategoryById(Long id) {
        return dishCategoryRepository.findById(id).orElse(null);
    }

    public void deleteCategory(Long id) {
        dishCategoryRepository.deleteById(id);
    }
}
