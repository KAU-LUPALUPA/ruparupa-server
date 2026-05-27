package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "contest_entries",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_contest_entry_group_user",
        columnNames = {"group_id", "user_uid"}
    )
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 그룹 ID (ContestGroup.groupId 참조, String으로 관리)
    @Column(name = "group_id", nullable = false)
    private String groupId;

    // 참가자 UID (User.uid 참조, String으로 관리)
    @Column(name = "user_uid", nullable = false)
    private String userUid;

    // S3에 저장된 캐릭터 사진 키 (예: "contest/user_xxx_uuid.png")
    @Column
    private String imageKey;

    /**
     * S3 업로드 완료 여부
     * false : presigned URL 발급만 된 상태 (아직 이미지 없음)
     * true  : FE가 /contest/confirm 으로 업로드 완료를 알린 상태
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean confirmed = false;

    // 받은 투표수
    @Builder.Default
    @Column(nullable = false)
    private int voteCount = 0;

    /**
     * 최종 등수 (null = 아직 종료 전 / 1·2·3 = 종료 후 스케줄러가 부여)
     * 동점 처리: voteCount 동일 시 joinedAt 이 빠른 쪽이 상위 등수
     */
    @Column
    private Integer rank;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    /** 투표 수 1 증가 */
    public void incrementVote() {
        this.voteCount++;
    }

    /** S3 업로드 완료 처리 */
    public void confirm(String imageKey) {
        this.imageKey = imageKey;
        this.confirmed = true;
    }
}