package com.example.demo.repository;

import com.example.demo.entity.ContestVote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestVoteRepository extends JpaRepository<ContestVote, Long> {

    /** 중복 투표 여부 확인 */
    boolean existsByVoterUidAndEntryId(String voterUid, Long entryId);
}