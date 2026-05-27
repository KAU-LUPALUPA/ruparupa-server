package com.example.demo.repository;

import com.example.demo.entity.ContestEntry;
import com.example.demo.entity.ContestGroupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContestEntryRepository extends JpaRepository<ContestEntry, Long> {

    /** 특정 그룹의 entry 수 (3명 여부 판단) */
    long countByGroupId(String groupId);

    /** 특정 그룹의 모든 entry 목록 */
    List<ContestEntry> findByGroupId(String groupId);

    /**
     * 특정 그룹의 entry를 voteCount 내림차순, joinedAt 오름차순으로 정렬.
     * 동점일 경우 먼저 참가한 유저가 상위 등수를 얻음.
     */
    List<ContestEntry> findByGroupIdOrderByVoteCountDescJoinedAtAsc(String groupId);

    /** 유저가 특정 그룹에 이미 참가했는지 확인 */
    boolean existsByGroupIdAndUserUid(String groupId, String userUid);

    /**
     * 유저가 현재 참가 중인 OPEN 또는 ACTIVE 그룹의 entry 조회. :statuses 파라미터로 ContestGroupStatus enum 값을 직접 전달.
     */
    @Query("""
        SELECT e FROM ContestEntry e
        JOIN ContestGroup g ON e.groupId = g.groupId
        WHERE e.userUid = :userUid
          AND g.status IN :statuses
        """)
    Optional<ContestEntry> findActiveEntryByUserUid(
            @Param("userUid") String userUid,
            @Param("statuses") List<ContestGroupStatus> statuses
    );

    Optional<ContestEntry> findByGroupIdAndUserUid(String groupId, String userUid);
}