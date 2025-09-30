package com.example.restaurant.repositories;

import com.example.restaurant.models.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Client findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT c FROM Client c JOIN c.account a WHERE a.username = :username")
    Optional<Client> findByAccountUsername(@Param("username") String username);
}