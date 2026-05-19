package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plaza_chat_messages")
@Getter
@Setter
@NoArgsConstructor
public class PlazaChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String messageId;
    private String plazaId;
    private String senderUserId;
    private String senderNickname;

    @Column(length = 120)
    private String text;

    private Long sentAtMillis;
}
