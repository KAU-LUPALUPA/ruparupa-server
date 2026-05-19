package com.example.demo.repository;

import com.example.demo.User;
import com.example.demo.entity.HomeVisitSession;
import com.example.demo.entity.HomeVisitStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HomeVisitSessionRepository extends JpaRepository<HomeVisitSession, Long> {
    Optional<HomeVisitSession> findByHostUserAndVisitorUserAndStatus(
            User hostUser,
            User visitorUser,
            HomeVisitStatus status
    );

    List<HomeVisitSession> findByHostUserAndStatusOrderByStartedAtDesc(
            User hostUser,
            HomeVisitStatus status
    );

    List<HomeVisitSession> findByVisitorUserAndStatusOrderByStartedAtDesc(
            User visitorUser,
            HomeVisitStatus status
    );
}
