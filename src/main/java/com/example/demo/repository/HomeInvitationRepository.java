package com.example.demo.repository;

import com.example.demo.User;
import com.example.demo.entity.HomeInvitation;
import com.example.demo.entity.HomeInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HomeInvitationRepository extends JpaRepository<HomeInvitation, Long> {
    Optional<HomeInvitation> findByFromUserAndToUserAndStatus(
            User fromUser,
            User toUser,
            HomeInvitationStatus status
    );

    List<HomeInvitation> findByToUserAndStatusOrderByCreatedAtDesc(
            User toUser,
            HomeInvitationStatus status
    );

    List<HomeInvitation> findByFromUserAndStatusOrderByCreatedAtDesc(
            User fromUser,
            HomeInvitationStatus status
    );
}
