package com.example.demo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plaza_participants")
@Getter
@Setter
@NoArgsConstructor
public class PlazaParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plaza_id")
    private Plaza plaza;

    private String userId;
    private String nickname;
    private Long petId;
    private Long joinedAtMillis;
    private Float positionX;
    private Float positionY;
    private Float targetX;
    private Float targetY;
    private Long moveStartedAtMillis;
    private Long moveDurationMillis;
    private Long lastUpdatedAtMillis;
}
