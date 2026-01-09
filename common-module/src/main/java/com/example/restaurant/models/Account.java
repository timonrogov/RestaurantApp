package com.example.restaurant.models;

import com.example.restaurant.enums.AccountStatus;
import com.example.restaurant.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account")
public class Account implements UserDetails { // Реализация интерфейса Spring Security
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ИЗМЕНЕНИЕ: Храним Enum как строку
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    // ИЗМЕНЕНИЕ: Храним Enum как строку
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Column(name = "username", nullable = false, unique = true) // Уникальный логин
    private String username;

    @Column(name = "password", nullable = false) // Пароль (хэшируется)
    private String password;


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Используем имя Enum (CLIENT, EMPLOYEE) для роли
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == AccountStatus.ACTIVE;
    }
}