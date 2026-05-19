package com.example.demo.repository;

import com.example.demo.entity.HomeVisitMessage;
import com.example.demo.entity.HomeVisitSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HomeVisitMessageRepository extends JpaRepository<HomeVisitMessage, Long> {
    List<HomeVisitMessage> findBySessionOrderByCreatedAtAsc(HomeVisitSession session);
}
