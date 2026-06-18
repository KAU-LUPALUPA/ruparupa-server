package com.example.demo.service;

import com.example.demo.User;
import com.example.demo.UserRepository;
import com.example.demo.dto.ContestDto;
import com.example.demo.entity.ContestEntry;
import com.example.demo.entity.ContestGroup;
import com.example.demo.entity.ContestGroupStatus;
import com.example.demo.entity.ContestVote;
import com.example.demo.exception.CustomApiException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.ContestEntryRepository;
import com.example.demo.repository.ContestGroupRepository;
import com.example.demo.repository.ContestVoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContestService {

    private final ContestGroupRepository groupRepository;
    private final ContestEntryRepository entryRepository;
    private final ContestVoteRepository voteRepository;
    private final UserRepository userRepository;
    private final S3Uploader s3Uploader;

    private static final String S3_PREFIX = "contest/";
    private static final String IMAGE_CONTENT_TYPE = "image/png";

    /** 투표 참여 시 지급되는 일일 리워드 골드 */
    private static final int VOTE_REWARD_GOLD = 10;

    /** 최근 랭킹 기본 조회 그룹 수 */
    private static final int DEFAULT_RECENT_GROUP_LIMIT = 5;

    private static final List<ContestGroupStatus> ACTIVE_STATUSES =
            List.of(ContestGroupStatus.OPEN, ContestGroupStatus.ACTIVE);
    private static final List<ContestGroupStatus> VISIBLE_STATUSES =
            List.of(ContestGroupStatus.OPEN, ContestGroupStatus.ACTIVE);

    // -------------------------------------------------------------------------
    // 1. 참가 신청
    // -------------------------------------------------------------------------

    @Transactional
    public ContestDto.JoinResponse join(String userUid) {
        userRepository.findByUid(userUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));

        ContestEntry existingEntry = entryRepository
                .findActiveEntryByUserUid(userUid, ACTIVE_STATUSES)
                .orElse(null);
        if (existingEntry != null) {
            return createJoinResponseWithUploadUrl(userUid, existingEntry);
        }

        List<ContestGroup> openGroups =
                groupRepository.findOpenGroupsWithLock(ContestGroupStatus.OPEN);
        ContestGroup group = openGroups.isEmpty()
                ? groupRepository.save(ContestGroup.createNew())
                : openGroups.get(0);

        ContestEntry entry = ContestEntry.builder()
                .groupId(group.getGroupId())
                .userUid(userUid)
                .build();
        entryRepository.save(entry);

        long memberCount = entryRepository.countByGroupId(group.getGroupId());
        if (memberCount >= 3) {
            group.activate();
        }

        return createJoinResponseWithUploadUrl(userUid, entry);
    }

    private ContestDto.JoinResponse createJoinResponseWithUploadUrl(String userUid, ContestEntry entry) {
        String fileKey = S3_PREFIX + userUid + "_" + UUID.randomUUID() + ".png";
        String uploadUrl = null;
        String uploadErrorMessage = null;
        try {
            uploadUrl = s3Uploader.generatePresignedPutUrl(fileKey, IMAGE_CONTENT_TYPE);
        } catch (Exception e) {
            log.error("Failed to generate contest S3 presigned URL. userUid={}, fileKey={}",
                    userUid, fileKey, e);
            uploadErrorMessage = toDebugMessage(ErrorCode.CONTEST_UPLOAD_URL_FAILED.getMessage(), e);
            fileKey = null;
        }

        return ContestDto.JoinResponse.builder()
                .success(true)
                .data(ContestDto.JoinResponse.JoinData.builder()
                        .entryId(entry.getId())
                        .groupId(entry.getGroupId())
                        .uploadUrl(uploadUrl)
                        .fileKey(fileKey)
                        .imageUploadAvailable(uploadUrl != null && fileKey != null)
                        .uploadErrorMessage(uploadErrorMessage)
                        .build())
                .build();
    }

    // -------------------------------------------------------------------------
    // 2. S3 업로드 완료 확인
    // -------------------------------------------------------------------------

    @Transactional
    public ContestDto.ConfirmResponse confirm(String userUid, ContestDto.ConfirmRequest request) {
        ContestEntry entry = entryRepository.findById(request.getEntryId())
                .orElseThrow(() -> new CustomApiException(ErrorCode.CONTEST_ENTRY_NOT_FOUND));

        if (!entry.getUserUid().equals(userUid)) {
            throw new CustomApiException(ErrorCode.CONTEST_ACCESS_DENIED);
        }

        String newFileKey = request.getFileKey();
        if (newFileKey == null || !newFileKey.startsWith(S3_PREFIX)) {
            throw new IllegalArgumentException("콘테스트 이미지 파일 키가 올바르지 않습니다.");
        }

        String previousFileKey = entry.getImageKey();
        entry.confirm(newFileKey);

        if (previousFileKey != null && !previousFileKey.equals(newFileKey)) {
            try {
                s3Uploader.deleteFiles(List.of(previousFileKey));
            } catch (Exception e) {
                log.warn("Failed to delete previous contest image. entryId={}, fileKey={}",
                        entry.getId(), previousFileKey, e);
            }
        }

        return ContestDto.ConfirmResponse.builder().success(true).build();
    }

    // -------------------------------------------------------------------------
    // 3. 투표
    // -------------------------------------------------------------------------

    @Transactional
    public ContestDto.VoteResponse vote(String voterUid, ContestDto.VoteRequest request) {
        ContestEntry entry = entryRepository.findById(request.getEntryId())
                .orElseThrow(() -> new CustomApiException(ErrorCode.CONTEST_ENTRY_NOT_FOUND));

        // [테스트용] 자기 자신 투표 차단 임시 비활성화
        // if (entry.getUserUid().equals(voterUid)) {
        //     throw new CustomApiException(ErrorCode.CONTEST_CANNOT_VOTE_SELF);
        // }

        ContestGroup targetGroup = groupRepository.findByGroupId(entry.getGroupId())
                .orElseThrow(() -> new CustomApiException(ErrorCode.CONTEST_GROUP_NOT_FOUND));

        if (targetGroup.getStatus() == ContestGroupStatus.CLOSED) {
            throw new CustomApiException(ErrorCode.CONTEST_NOT_ACTIVE);
        }

        // [테스트용] 자신의 조 투표 차단 임시 비활성화
        // String myGroupId = entryRepository
        //         .findActiveEntryByUserUid(voterUid, ACTIVE_STATUSES)
        //         .map(ContestEntry::getGroupId)
        //         .orElse(null);
        // if (entry.getGroupId().equals(myGroupId)) {
        //     throw new CustomApiException(ErrorCode.CONTEST_CANNOT_VOTE_OWN_GROUP);
        // }

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        if (voteRepository.countTodayVotes(voterUid, todayStart) > 0) {
            throw new CustomApiException(ErrorCode.CONTEST_ALREADY_VOTED_TODAY);
        }

        if (voteRepository.existsByVoterUidAndEntryId(voterUid, request.getEntryId())) {
            throw new CustomApiException(ErrorCode.CONTEST_ALREADY_VOTED);
        }

        voteRepository.save(ContestVote.builder()
                .voterUid(voterUid)
                .entryId(request.getEntryId())
                .build());

        entry.incrementVote();

        userRepository.findByUid(voterUid).ifPresent(user -> {
            user.addGold(VOTE_REWARD_GOLD);
            log.info("[ContestVote] 투표 리워드 지급: voterUid={}, gold=+{}", voterUid, VOTE_REWARD_GOLD);
        });

        return ContestDto.VoteResponse.builder()
                .success(true)
                .voteCount(entry.getVoteCount())
                .rewardGold(VOTE_REWARD_GOLD)
                .build();
    }

    // -------------------------------------------------------------------------
    // 4. 투표 상태 조회
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ContestDto.VoteStatusResponse getVoteStatus(String userUid) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        boolean votedToday = voteRepository.countTodayVotes(userUid, todayStart) > 0;

        String myGroupId = entryRepository
                .findActiveEntryByUserUid(userUid, ACTIVE_STATUSES)
                .map(ContestEntry::getGroupId)
                .orElse(null);

        boolean votableGroupExists = groupRepository
                .findByStatusInOrderByCreatedAtAsc(List.of(ContestGroupStatus.ACTIVE))
                .stream()
                .anyMatch(g -> !g.getGroupId().equals(myGroupId));

        return ContestDto.VoteStatusResponse.builder()
                .success(true)
                .votedToday(votedToday)
                .votableGroupExists(votableGroupExists)
                .build();
    }

    // -------------------------------------------------------------------------
    // 5. 투표용 랜덤 조 조회
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ContestDto.GroupDetailResponse getRandomGroupForVote(String userUid) {
        String myGroupId = entryRepository
                .findActiveEntryByUserUid(userUid, ACTIVE_STATUSES)
                .map(ContestEntry::getGroupId)
                .orElse(null);

        List<ContestGroup> candidates = groupRepository
                .findByStatusInOrderByCreatedAtAsc(List.of(ContestGroupStatus.ACTIVE))
                .stream()
                .filter(g -> !g.getGroupId().equals(myGroupId))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            throw new CustomApiException(ErrorCode.CONTEST_NO_VOTABLE_GROUP);
        }

        Collections.shuffle(candidates);
        return getGroupDetail(userUid, candidates.get(0).getGroupId());
    }

    // -------------------------------------------------------------------------
    // 6. 내 그룹 조회
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ContestDto.MyGroupResponse getMyGroup(String userUid) {
        ContestEntry myEntry = entryRepository.findActiveEntryByUserUid(userUid, ACTIVE_STATUSES)
                .orElseThrow(() -> new CustomApiException(ErrorCode.CONTEST_NOT_JOINED));

        ContestGroup group = groupRepository.findByGroupId(myEntry.getGroupId())
                .orElseThrow(() -> new CustomApiException(ErrorCode.CONTEST_GROUP_NOT_FOUND));

        return ContestDto.MyGroupResponse.builder()
                .success(true)
                .data(toGroupData(group, myEntry.getId()))
                .build();
    }

    @Transactional(readOnly = true)
    public ContestDto.GroupListResponse listGroups(String userUid) {
        String myGroupId = null;
        if (userUid != null && !userUid.isBlank()) {
            myGroupId = entryRepository.findActiveEntryByUserUid(userUid, ACTIVE_STATUSES)
                    .map(ContestEntry::getGroupId)
                    .orElse(null);
        }

        String currentGroupId = myGroupId;
        List<ContestGroup> visibleGroups = groupRepository.findByStatusInOrderByCreatedAtAsc(VISIBLE_STATUSES);
        List<ContestDto.GroupSummary> groups = new java.util.ArrayList<>();
        for (int i = 0; i < visibleGroups.size(); i++) {
            ContestGroup group = visibleGroups.get(i);
            groups.add(ContestDto.GroupSummary.builder()
                    .groupId(group.getGroupId())
                    .groupNumber((long) i + 1)
                    .status(group.getStatus().name())
                    .memberCount(entryRepository.countByGroupId(group.getGroupId()))
                    .myGroup(group.getGroupId().equals(currentGroupId))
                    .build());
        }

        return ContestDto.GroupListResponse.builder()
                .success(true)
                .data(groups)
                .build();
    }

    @Transactional(readOnly = true)
    public ContestDto.GroupDetailResponse getGroupDetail(String userUid, String groupId) {
        ContestGroup group = groupRepository.findByGroupId(groupId)
                .orElseThrow(() -> new CustomApiException(ErrorCode.CONTEST_GROUP_NOT_FOUND));

        Long myEntryId = null;
        if (userUid != null && !userUid.isBlank()) {
            myEntryId = entryRepository.findByGroupIdAndUserUid(groupId, userUid)
                    .map(ContestEntry::getId)
                    .orElse(null);
        }

        return ContestDto.GroupDetailResponse.builder()
                .success(true)
                .data(toGroupData(group, myEntryId))
                .build();
    }

    // -------------------------------------------------------------------------
    // 7. 실시간 랭킹
    // -------------------------------------------------------------------------

    /**
     * 현재 진행 중인(OPEN/ACTIVE) 모든 그룹의 confirmed 참가자를
     * voteCount 내림차순으로 반환.
     */
    @Transactional(readOnly = true)
    public ContestDto.LiveRankingResponse getLiveRanking() {
        List<ContestEntry> entries = entryRepository.findLiveRankingEntries(ACTIVE_STATUSES);

        // 닉네임 일괄 조회 (N+1 방지)
        Map<String, String> nicknameMap = buildNicknameMap(entries);

        List<ContestDto.RankingEntry> rankingEntries = entries.stream()
                .map(e -> ContestDto.RankingEntry.builder()
                        .entryId(e.getId())
                        .userUid(e.getUserUid())
                        .nickname(nicknameMap.getOrDefault(e.getUserUid(), "알 수 없음"))
                        .imageUrl(e.getImageKey() != null ? s3Uploader.generatePresignedGetUrl(e.getImageKey()) : null)
                        .voteCount(e.getVoteCount())
                        .rank(null) // 실시간은 등수 미확정
                        .groupId(e.getGroupId())
                        .build())
                .collect(Collectors.toList());

        return ContestDto.LiveRankingResponse.builder()
                .success(true)
                .data(rankingEntries)
                .build();
    }

    // -------------------------------------------------------------------------
    // 8. 최근 랭킹
    // -------------------------------------------------------------------------

    /**
     * 최근 종료된 그룹들의 최종 결과를 그룹 단위로 반환.
     * @param limit 조회할 최근 그룹 수 (기본값 5)
     */
    @Transactional(readOnly = true)
    public ContestDto.RecentRankingResponse getRecentRanking(int limit) {
        // 최근 CLOSED 그룹 조회
        List<ContestGroup> closedGroups = groupRepository.findRecentClosedGroups(
                ContestGroupStatus.CLOSED,
                PageRequest.of(0, limit)
        );

        if (closedGroups.isEmpty()) {
            return ContestDto.RecentRankingResponse.builder()
                    .success(true)
                    .data(List.of())
                    .build();
        }

        List<String> groupIds = closedGroups.stream()
                .map(ContestGroup::getGroupId)
                .collect(Collectors.toList());

        // 해당 그룹들의 entry 일괄 조회
        List<ContestEntry> allEntries = entryRepository.findRecentRankingEntries(groupIds);

        // 닉네임 일괄 조회 (N+1 방지)
        Map<String, String> nicknameMap = buildNicknameMap(allEntries);

        // groupId 기준으로 entry 그룹핑
        Map<String, List<ContestEntry>> entriesByGroup = allEntries.stream()
                .collect(Collectors.groupingBy(ContestEntry::getGroupId));

        // 그룹 순서 유지하며 결과 조립
        List<ContestDto.RankingGroup> rankingGroups = closedGroups.stream()
                .map(group -> {
                    List<ContestEntry> groupEntries =
                            entriesByGroup.getOrDefault(group.getGroupId(), List.of());

                    List<ContestDto.RankingEntry> entries = groupEntries.stream()
                            .map(e -> ContestDto.RankingEntry.builder()
                                    .entryId(e.getId())
                                    .userUid(e.getUserUid())
                                    .nickname(nicknameMap.getOrDefault(e.getUserUid(), "알 수 없음"))
                                    .imageUrl(s3Uploader.generatePresignedGetUrl(e.getImageKey()))
                                    .voteCount(e.getVoteCount())
                                    .rank(e.getRank())
                                    .groupId(e.getGroupId())
                                    .build())
                            .collect(Collectors.toList());

                    return ContestDto.RankingGroup.builder()
                            .groupId(group.getGroupId())
                            .closedAt(group.getClosedAt())
                            .entries(entries)
                            .build();
                })
                .collect(Collectors.toList());

        return ContestDto.RecentRankingResponse.builder()
                .success(true)
                .data(rankingGroups)
                .build();
    }

    // -------------------------------------------------------------------------
    // 내부 헬퍼
    // -------------------------------------------------------------------------

    private ContestDto.MyGroupResponse.MyGroupData toGroupData(ContestGroup group, Long myEntryId) {
        List<ContestEntry> entries = entryRepository.findByGroupIdOrderByJoinedAtAsc(group.getGroupId());

        List<ContestDto.EntryInfo> entryInfos = entries.stream()
                .map(e -> {
                    String imageUrl = e.isConfirmed()
                            ? s3Uploader.generatePresignedGetUrl(e.getImageKey())
                            : null;
                    return ContestDto.EntryInfo.builder()
                            .entryId(e.getId())
                            .userUid(e.getUserUid())
                            .imageUrl(imageUrl)
                            .voteCount(e.getVoteCount())
                            .rank(e.getRank())
                            .confirmed(e.isConfirmed())
                            .build();
                })
                .collect(Collectors.toList());

        return ContestDto.MyGroupResponse.MyGroupData.builder()
                .groupId(group.getGroupId())
                .groupNumber(resolveGroupNumber(group))
                .status(group.getStatus().name())
                .closeAt(group.getCloseAt())
                .entries(entryInfos)
                .myEntryId(myEntryId)
                .build();
    }

    private Long resolveGroupNumber(ContestGroup targetGroup) {
        List<ContestGroup> visibleGroups = groupRepository.findByStatusInOrderByCreatedAtAsc(VISIBLE_STATUSES);
        for (int i = 0; i < visibleGroups.size(); i++) {
            if (visibleGroups.get(i).getGroupId().equals(targetGroup.getGroupId())) {
                return (long) i + 1;
            }
        }
        return targetGroup.getId();
    }

    /**
     * entry 목록의 userUid 를 기준으로 User 를 일괄 조회해 uid → nickname 맵을 반환.
     * N+1 문제를 방지하기 위해 한 번에 전체 유저를 조회.
     */
    private Map<String, String> buildNicknameMap(List<ContestEntry> entries) {
        List<String> userUids = entries.stream()
                .map(ContestEntry::getUserUid)
                .distinct()
                .collect(Collectors.toList());

        return userRepository.findByUidIn(userUids).stream()
                .collect(Collectors.toMap(User::getUid, User::getNickname));
    }

    private String toDebugMessage(String baseMessage, Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        String detail = rootCause.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = rootCause.getClass().getSimpleName();
        } else {
            detail = rootCause.getClass().getSimpleName() + ": " + detail;
        }
        return baseMessage + " (" + detail + ")";
    }
}