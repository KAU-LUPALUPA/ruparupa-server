package com.example.demo.repository;

import com.example.demo.entity.ContestVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ContestVoteRepository extends JpaRepository<ContestVote, Long> {

    /** 중복 투표 여부 확인 (같은 entry에 재투표 방지) */
    boolean existsByVoterUidAndEntryId(String voterUid, Long entryId);

    /**
     * 오늘(자정 이후) 투표 횟수 반환.
     * JPQL은 COUNT > 0 비교를 지원하지 않으므로 Long으로 받아 서비스에서 비교.
     */
    @Query("""
        SELECT COUNT(v) FROM ContestVote v
        WHERE v.voterUid = :voterUid
          AND v.votedAt >= :todayStart
    """)
    Long countTodayVotes(
            @Param("voterUid") String voterUid,
            @Param("todayStart") LocalDateTime todayStart
    );
}