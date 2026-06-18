package com.example.demo.controller;

import com.example.demo.dto.ContestDto;
import com.example.demo.service.ContestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contest")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;

    // 콘테스트 참가 신청 + S3 presigned PUT URL 발급
    @PostMapping("/join")
    public ResponseEntity<ContestDto.JoinResponse> join(
            @RequestAttribute("currentUid") String currentUid
    ) {
        return ResponseEntity.ok(contestService.join(currentUid));
    }

    // S3 업로드 완료 후 서버에 알림
    @PostMapping("/confirm")
    public ResponseEntity<ContestDto.ConfirmResponse> confirm(
            @RequestAttribute("currentUid") String currentUid,
            @RequestBody ContestDto.ConfirmRequest request
    ) {
        return ResponseEntity.ok(contestService.confirm(currentUid, request));
    }

    /**
     * 투표 실행.
     * - 자신의 entry 에는 투표 불가
     * - 자신이 속한 조에는 투표 불가
     * - 1인 1일 1표 (자정 초기화)
     * - 오늘 첫 투표 시 rewardGold 만큼 골드 지급
     */
    @PostMapping("/vote")
    public ResponseEntity<ContestDto.VoteResponse> vote(
            @RequestAttribute("currentUid") String currentUid,
            @RequestBody ContestDto.VoteRequest request
    ) {
        return ResponseEntity.ok(contestService.vote(currentUid, request));
    }

    /**
     * 오늘 투표 여부 및 투표 가능한 조 존재 여부 조회.
     * 투표 화면 진입 시 버튼 활성화 여부 판단에 사용.
     */
    @GetMapping("/vote/status")
    public ResponseEntity<ContestDto.VoteStatusResponse> getVoteStatus(
            @RequestAttribute("currentUid") String currentUid
    ) {
        return ResponseEntity.ok(contestService.getVoteStatus(currentUid));
    }

    /**
     * 투표용 랜덤 조 조회.
     * 자신이 속한 조는 제외하고 ACTIVE 상태 그룹 중 랜덤으로 1개 반환.
     */
    @GetMapping("/vote/random")
    public ResponseEntity<ContestDto.GroupDetailResponse> getRandomGroupForVote(
            @RequestAttribute("currentUid") String currentUid
    ) {
        return ResponseEntity.ok(contestService.getRandomGroupForVote(currentUid));
    }

    // 전체 그룹 목록 조회
    @GetMapping("/groups")
    public ResponseEntity<ContestDto.GroupListResponse> listGroups(
            @RequestAttribute(value = "currentUid", required = false) String currentUid
    ) {
        return ResponseEntity.ok(contestService.listGroups(currentUid));
    }

    // 특정 그룹 상세 조회
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<ContestDto.GroupDetailResponse> getGroupDetail(
            @RequestAttribute(value = "currentUid", required = false) String currentUid,
            @PathVariable String groupId
    ) {
        return ResponseEntity.ok(contestService.getGroupDetail(currentUid, groupId));
    }

    /**
     * 내 그룹 조회.
     * 현재 OPEN 또는 ACTIVE 상태의 내 그룹과 참가자 목록, 투표 수를 반환.
     */
    @GetMapping("/my")
    public ResponseEntity<ContestDto.MyGroupResponse> getMyGroup(
            @RequestAttribute("currentUid") String currentUid
    ) {
        return ResponseEntity.ok(contestService.getMyGroup(currentUid));
    }

    /**
     * 실시간 랭킹 조회.
     * 현재 진행 중인 모든 그룹의 참가자를 voteCount 기준 내림차순으로 반환.
     */
    @GetMapping("/ranking/live")
    public ResponseEntity<ContestDto.LiveRankingResponse> getLiveRanking(
            @RequestAttribute("currentUid") String currentUid
    ) {
        return ResponseEntity.ok(contestService.getLiveRanking());
    }

    /**
     * 최근 랭킹 조회.
     * 최근 종료된 그룹들의 최종 결과를 그룹 단위로 반환.
     * @param limit 조회할 그룹 수 (기본값 5, 최대 20)
     */
    @GetMapping("/ranking/recent")
    public ResponseEntity<ContestDto.RecentRankingResponse> getRecentRanking(
            @RequestAttribute("currentUid") String currentUid,
            @RequestParam(defaultValue = "5") int limit
    ) {
        int safeLimit = Math.min(limit, 20); // 최대 20개 그룹으로 제한
        return ResponseEntity.ok(contestService.getRecentRanking(safeLimit));
    }
}