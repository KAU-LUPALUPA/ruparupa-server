package com.example.demo.controller;

import com.example.demo.dto.ContestParticipationResponseDto;
import com.example.demo.service.ContestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/contest")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;

    @PostMapping("/participate")
    public ResponseEntity<ContestParticipationResponseDto> participate(
            @RequestAttribute("currentUid") String currentUid,
            @RequestParam("picture") MultipartFile picture) {
        try {
            ContestParticipationResponseDto response = contestService.participate(currentUid, picture);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new RuntimeException("참가 등록에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
