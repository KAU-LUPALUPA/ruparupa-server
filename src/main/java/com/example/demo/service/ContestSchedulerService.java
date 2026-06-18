package com.example.demo.service;

import com.example.demo.User;
import com.example.demo.UserRepository;
import com.example.demo.entity.ContestEntry;
import com.example.demo.entity.ContestGroup;
import com.example.demo.entity.ContestGroupStatus;
import com.example.demo.repository.ContestEntryRepository;
import com.example.demo.repository.ContestGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContestSchedulerService {

    private final ContestGroupRepository groupRepository;
    private final ContestEntryRepository entryRepository;
    private final UserRepository userRepository;

    //등수별 골드 리워드. 요구사항 변경 시 이 배열만 수정하면 됨.
    private static final int[] REWARDS = {300, 150, 50};

    // 자정 자동 종료 스케줄러
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void closeExpiredContests() {
        LocalDateTime now = LocalDateTime.now();
        log.info("[ContestScheduler] 자정 종료 스케줄러 시작: {}", now);

        List<ContestGroup> expiredGroups =
                groupRepository.findByStatusAndCloseAtBefore(ContestGroupStatus.ACTIVE, now);

        if (expiredGroups.isEmpty()) {
            log.info("[ContestScheduler] 종료 대상 그룹 없음");
            return;
        }

        for (ContestGroup group : expiredGroups) {
            try {
                processGroupClose(group);
            } catch (Exception e) {
                // 한 그룹 처리 실패가 다른 그룹 종료에 영향을 주지 않도록 예외를 잡아 로깅
                log.error("[ContestScheduler] 그룹 종료 처리 실패: groupId={}, error={}",
                        group.getGroupId(), e.getMessage(), e);
            }
        }

        log.info("[ContestScheduler] 스케줄러 종료: {}개 그룹 처리 완료", expiredGroups.size());
    }

    private void processGroupClose(ContestGroup group) {
        log.info("[ContestScheduler] 그룹 종료 처리 시작: groupId={}", group.getGroupId());

        // voteCount 내림차순, joinedAt 오름차순 정렬
        List<ContestEntry> allEntries =
                entryRepository.findByGroupIdOrderByVoteCountDescJoinedAtAsc(group.getGroupId());

        // confirmed=true 인 entry 만 등수 / 리워드 대상
        List<ContestEntry> confirmedEntries = allEntries.stream()
                .filter(ContestEntry::isConfirmed)
                .toList();

        for (int i = 0; i < confirmedEntries.size(); i++) {
            ContestEntry entry = confirmedEntries.get(i);
            int rank = i + 1;           // 1등, 2등, 3등
            entry.setRank(rank);

            // rewards 배열 범위 내인 경우만 골드 지급 (혹시 3명 미만인 케이스 방어)
            if (i < REWARDS.length) {
                int reward = REWARDS[i];
                userRepository.findByUid(entry.getUserUid()).ifPresent(user -> {
                    user.addGold(reward);
                    log.info("[ContestScheduler] 리워드 지급: userUid={}, rank={}, gold=+{}",
                            entry.getUserUid(), rank, reward);
                });
            }
        }

        // confirmed=false 인 entry 에도 최하위 등수 부여 (표시 목적)
        // 실제 리워드는 위에서 confirmed 기준으로 이미 지급했으므로 중복 없음
        int baseRank = confirmedEntries.size() + 1;
        for (ContestEntry unconfirmedEntry : allEntries) {
            if (!unconfirmedEntry.isConfirmed()) {
                unconfirmedEntry.setRank(baseRank);
            }
        }

        // 그룹 상태 CLOSED 처리
        group.close();
        log.info("[ContestScheduler] 그룹 종료 완료: groupId={}", group.getGroupId());
    }

    // OPEN 상태에서 자정을 넘긴 그룹 정리
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void closeStaleOpenGroups() {
        LocalDateTime now = LocalDateTime.now();
        log.info("[ContestScheduler] OPEN 그룹 정리 스케줄러 시작: {}", now);

        List<ContestGroup> staleGroups =
                groupRepository.findByStatusAndCloseAtBefore(ContestGroupStatus.OPEN, now);

        for (ContestGroup group : staleGroups) {
            log.info("[ContestScheduler] 미완성 OPEN 그룹 CLOSED 처리: groupId={}", group.getGroupId());
            group.close();
        }

        log.info("[ContestScheduler] OPEN 그룹 정리 완료: {}개 처리", staleGroups.size());
    }
}