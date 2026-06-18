package com.example.demo.controller;

import com.example.demo.dto.MyPetResponseDto;
import com.example.demo.dto.PetPredictDto;
import com.example.demo.entity.Pet;
import com.example.demo.service.PetService;
import com.example.demo.service.PetPredictService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;
    private final PetPredictService petPredictService;

    // 1. 밥 먹이기 API
    @PostMapping("/{petId}/feed")
    public ResponseEntity<MyPetResponseDto> feedPet(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable(name = "petId") Long petId) {
        return ResponseEntity.ok(petService.feedPet(currentUid, petId));
    }

    // 2. 잠재우기 API
    @PostMapping("/{petId}/sleep")
    public ResponseEntity<MyPetResponseDto> sleepPet(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable(name = "petId") Long petId) {
        return ResponseEntity.ok(petService.sleepPet(currentUid, petId));
    }

    // 3. 놀아주기 API
    @PostMapping("/{petId}/play")
    public ResponseEntity<MyPetResponseDto> playWithPet(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable(name = "petId") Long petId) {
        return ResponseEntity.ok(petService.playWithPet(currentUid, petId));
    }

    // 내 펫 정보 조회 (없으면 생성)
    @GetMapping("/me")
    public ResponseEntity<MyPetResponseDto> getMyPet(
            @RequestAttribute("currentUid") String currentUid) {
        return ResponseEntity.ok(petService.getOrCreatePet(currentUid));
    }

    // 4. AI 행동 예측 API (1초마다 프론트에서 전송하는 데이터 기반)
    @PostMapping("/predict-action")
    public ResponseEntity<PetPredictDto.Response> predictPetAction(
            @RequestAttribute("currentUid") String currentUid,
            @RequestBody PetPredictDto.Request request) {
        return ResponseEntity.ok(petPredictService.predictAction(request));
    }
}
