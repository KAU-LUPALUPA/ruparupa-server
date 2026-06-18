package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "contest_groups")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 외부 노출용 식별자 (예: "cg_a1b2c3d4")
     * User.uid, Pet.petUid 패턴과 동일하게 관리
     */
    @Column(unique = true, nullable = false)
    private String groupId;

    // 그룹 상태 : OPEN → ACTIVE → CLOSED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ContestGroupStatus status = ContestGroupStatus.OPEN;

    @Column(nullable = false)
    private LocalDateTime closeAt;

    // 스케줄러가 CLOSED 처리 후 기록
    private LocalDateTime closedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 새 그룹 생성
    public static ContestGroup createNew() {
        String newGroupId = "cg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        LocalDateTime nextMidnight = LocalDate.now().plusDays(1).atStartOfDay();

        return ContestGroup.builder()
                .groupId(newGroupId)
                .status(ContestGroupStatus.OPEN)
                .closeAt(nextMidnight)
                .build();
    }

    /** 3명이 모이면 ACTIVE 로 전환 */
    public void activate() {
        this.status = ContestGroupStatus.ACTIVE;
    }

    /** 스케줄러가 자정 종료 처리 */
    public void close() {
        this.status = ContestGroupStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
    }
}