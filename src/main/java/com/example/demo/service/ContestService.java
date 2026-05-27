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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContestService {

    private final ContestGroupRepository groupRepository;
    private final ContestEntryRepository entryRepository;
    private final ContestVoteRepository voteRepository;
    private final UserRepository userRepository;
    private final S3Uploader s3Uploader;

    private static final String S3_PREFIX = "contest/";
    private static final String IMAGE_CONTENT_TYPE = "image/png";

    // OPEN / ACTIVE 상태 목록 (findActiveEntryByUserUid 파라미터용)
    private static final List<ContestGroupStatus> ACTIVE_STATUSES =
            List.of(ContestGroupStatus.OPEN, ContestGroupStatus.ACTIVE);

    // 1. 참가 신청

    @Transactional
    public ContestDto.JoinResponse join(String userUid) {
        // ① 유저 존재 확인
        userRepository.findByUid(userUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));

        // ② 이미 OPEN 또는 ACTIVE 그룹에 참가 중인지 확인
        entryRepository.findActiveEntryByUserUid(userUid, ACTIVE_STATUSES).ifPresent(e -> {
            throw new CustomApiException(ErrorCode.CONTEST_ALREADY_JOINED);
        });

        // ③ OPEN 그룹 조회 (비관적 잠금) — 없으면 새 그룹 생성
        //    List로 받아 첫 번째 사용 (Optional 다중결과 예외 방지)
        List<ContestGroup> openGroups =
                groupRepository.findOpenGroupsWithLock(ContestGroupStatus.OPEN);
        ContestGroup group = openGroups.isEmpty()
                ? groupRepository.save(ContestGroup.createNew())
                : openGroups.get(0);

        // ④ ContestEntry 생성 (이미지는 아직 없음 → confirmed=false)
        ContestEntry entry = ContestEntry.builder()
                .groupId(group.getGroupId())
                .userUid(userUid)
                .build();
        entryRepository.save(entry);

        // ⑤ 3명이 모이면 ACTIVE 로 전환
        long memberCount = entryRepository.countByGroupId(group.getGroupId());
        if (memberCount >= 3) {
            group.activate();
        }

        // ⑥ S3 Presigned PUT URL 발급
        String fileKey = S3_PREFIX + userUid + "_" + UUID.randomUUID() + ".png";
        String uploadUrl = s3Uploader.generatePresignedPutUrl(fileKey, IMAGE_CONTENT_TYPE);

        return ContestDto.JoinResponse.builder()
                .success(true)
                .data(ContestDto.JoinResponse.JoinData.builder()
                        .entryId(entry.getId())
                        .groupId(group.getGroupId())
                        .uploadUrl(uploadUrl)
                        .fileKey(fileKey)
                        .build())
                .build();
    }

    // 2. S3 업로드 완료 확인

    @Transactional
    public ContestDto.ConfirmResponse confirm(String userUid, ContestDto.ConfirmRequest request) {
        ContestEntry entry = entryRepository.findById(request.getEntryId())
                .orElseThrow(() -> new CustomApiException(ErrorCode.CONTEST_ENTRY_NOT_FOUND));

        if (!entry.getUserUid().equals(userUid)) {
            throw new CustomApiException(ErrorCode.CONTEST_ACCESS_DENIED);
        }

        if (entry.isConfirmed()) {
            return ContestDto.ConfirmResponse.builder().success(true).build();
        }

        entry.confirm(request.getFileKey());

        return ContestDto.ConfirmResponse.builder().success(true).build();
    }

    // 3. 투표

    @Transactional
    public ContestDto.VoteResponse vote(String voterUid, ContestDto.VoteRequest request) {
        // ① entry 존재 확인
        ContestEntry entry = entryRepository.findById(request.getEntryId())
                .orElseThrow(() -> new CustomApiException(ErrorCode.CONTEST_ENTRY_NOT_FOUND));

        // ② 자기 자신 투표 차단
        if (entry.getUserUid().equals(voterUid)) {
            throw new CustomApiException(ErrorCode.CONTEST_CANNOT_VOTE_SELF);
        }

        // ③ 그룹이 ACTIVE 인지 확인
        ContestGroup group = groupRepository.findByGroupId(entry.getGroupId())
                .orElseThrow(() -> new CustomApiException(ErrorCode.CONTEST_GROUP_NOT_FOUND));

        if (group.getStatus() != ContestGroupStatus.ACTIVE) {
            throw new CustomApiException(ErrorCode.CONTEST_NOT_ACTIVE);
        }

        // ④ 중복 투표 확인
        if (voteRepository.existsByVoterUidAndEntryId(voterUid, request.getEntryId())) {
            throw new CustomApiException(ErrorCode.CONTEST_ALREADY_VOTED);
        }

        // ⑤ 투표 저장 + voteCount 증가
        voteRepository.save(ContestVote.builder()
                .voterUid(voterUid)
                .entryId(request.getEntryId())
                .build());

        entry.incrementVote();

        return ContestDto.VoteResponse.builder()
                .success(true)
                .voteCount(entry.getVoteCount())
                .build();
    }

    // 4. 내 그룹 조회
    @Transactional(readOnly = true)
    public ContestDto.MyGroupResponse getMyGroup(String userUid) {
        ContestEntry myEntry = entryRepository.findActiveEntryByUserUid(userUid, ACTIVE_STATUSES)
                .orElseThrow(() -> new CustomApiException(ErrorCode.CONTEST_NOT_JOINED));

        ContestGroup group = groupRepository.findByGroupId(myEntry.getGroupId())
                .orElseThrow(() -> new CustomApiException(ErrorCode.CONTEST_GROUP_NOT_FOUND));

        List<ContestEntry> entries = entryRepository.findByGroupId(group.getGroupId());

        List<ContestDto.EntryInfo> entryInfos = entries.stream()
                .map(e -> {
                    String imageUrl = e.isConfirmed()
                            ? s3Uploader.getFileUrl(e.getImageKey())
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

        return ContestDto.MyGroupResponse.builder()
                .success(true)
                .data(ContestDto.MyGroupResponse.MyGroupData.builder()
                        .groupId(group.getGroupId())
                        .status(group.getStatus().name())
                        .closeAt(group.getCloseAt())
                        .entries(entryInfos)
                        .myEntryId(myEntry.getId())
                        .build())
                .build();
    }
}