package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "contest_votes",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_contest_vote_voter_entry",
        columnNames = {"voter_uid", "entry_id"}
    )
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 투표한 유저의 UID (User.uid 참조)
     */
    @Column(name = "voter_uid", nullable = false)
    private String voterUid;

    /**
     * 투표 대상 entry PK (ContestEntry.id 참조)
     * unique 제약으로 한 entry에 중복 투표 불가
     */
    @Column(name = "entry_id", nullable = false)
    private Long entryId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime votedAt;
}