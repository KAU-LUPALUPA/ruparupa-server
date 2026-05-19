package com.example.demo.repository;

import com.example.demo.entity.Plaza;
import com.example.demo.entity.PlazaParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlazaParticipantRepository extends JpaRepository<PlazaParticipant, Long> {
    Optional<PlazaParticipant> findByUserId(String userId);

    long countByPlaza(Plaza plaza);
}
