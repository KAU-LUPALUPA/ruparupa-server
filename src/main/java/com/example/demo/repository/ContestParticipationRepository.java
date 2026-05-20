package com.example.demo.repository;

import com.example.demo.entity.ContestParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ContestParticipationRepository extends JpaRepository<ContestParticipation, Integer> {
    Optional<ContestParticipation> findByUid(String uid);
}
