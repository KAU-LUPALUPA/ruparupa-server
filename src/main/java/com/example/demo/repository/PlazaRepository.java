package com.example.demo.repository;

import com.example.demo.entity.Plaza;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlazaRepository extends JpaRepository<Plaza, Long> {
    Optional<Plaza> findByPlazaId(String plazaId);

    Optional<Plaza> findByPlazaCode(String plazaCode);
}
