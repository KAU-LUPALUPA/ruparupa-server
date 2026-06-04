package com.example.demo.repository;

import com.example.demo.entity.ContestGroup;
import com.example.demo.entity.ContestGroupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ContestGroupRepository extends JpaRepository<ContestGroup, Long> {

    /**
     * OPEN 상태 그룹 목록을 createdAt 오름차순으로 조회 (참가 신청용).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM ContestGroup g WHERE g.status = :status ORDER BY g.createdAt ASC")
    List<ContestGroup> findOpenGroupsWithLock(@Param("status") ContestGroupStatus status);

    List<ContestGroup> findByStatusInOrderByCreatedAtAsc(List<ContestGroupStatus> statuses);

    Optional<ContestGroup> findByGroupId(String groupId);

    /** 스케줄러용: 특정 상태이면서 closeAt 이 기준 시각 이전인 그룹 목록 */
    List<ContestGroup> findByStatusAndCloseAtBefore(ContestGroupStatus status, LocalDateTime now);

    /**
     * 최근 랭킹용: CLOSED 상태 그룹을 closedAt 내림차순으로 최근 N개 조회.
     * Pageable로 limit 제어 (예: PageRequest.of(0, 5) → 최근 5개 그룹).
     */
    @Query("""
        SELECT g FROM ContestGroup g
        WHERE g.status = :status
          AND g.closedAt IS NOT NULL
        ORDER BY g.closedAt DESC
        """)
    List<ContestGroup> findRecentClosedGroups(
            @Param("status") ContestGroupStatus status,
            Pageable pageable
    );
}