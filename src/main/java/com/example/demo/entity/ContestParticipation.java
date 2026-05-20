package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "contest_participations")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // 생성된 참가 번호

    @Column(unique = true, nullable = false)
    private String uid; // 참가자 uid

    @Column(name = "picture_url", nullable = false)
    private String pictureUrl; // 저장된 S3 주소
}
