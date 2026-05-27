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

    // S3 업로드 완료 후 서버에 알림 (entry 에 imageKey 기록)

    @PostMapping("/confirm")
    public ResponseEntity<ContestDto.ConfirmResponse> confirm(
            @RequestAttribute("currentUid") String currentUid,
            @RequestBody ContestDto.ConfirmRequest request
    ) {
        return ResponseEntity.ok(contestService.confirm(currentUid, request));
    }

    @PostMapping("/vote")
    public ResponseEntity<ContestDto.VoteResponse> vote(
            @RequestAttribute("currentUid") String currentUid,
            @RequestBody ContestDto.VoteRequest request
    ) {
        return ResponseEntity.ok(contestService.vote(currentUid, request));
    }

    // 내가 속한 현재 그룹 정보 조회

    /**
     * 콘테스트 화면 진입 시 호출.
     * 현재 OPEN 또는 ACTIVE 상태의 내 그룹과 참가자 목록, 투표 수를 반환.
     * 참가 중인 그룹이 없으면 CONTEST_NOT_JOINED 에러 반환.
     */
    @GetMapping("/my")
    public ResponseEntity<ContestDto.MyGroupResponse> getMyGroup(
            @RequestAttribute("currentUid") String currentUid
    ) {
        return ResponseEntity.ok(contestService.getMyGroup(currentUid));
    }
}