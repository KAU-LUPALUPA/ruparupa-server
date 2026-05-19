package com.example.demo.repository;

import com.example.demo.entity.PlazaChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlazaChatMessageRepository extends JpaRepository<PlazaChatMessage, Long> {
    List<PlazaChatMessage> findTop50ByPlazaIdOrderBySentAtMillisDesc(String plazaId);
}
