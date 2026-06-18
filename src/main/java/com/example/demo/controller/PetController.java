package com.example.demo.controller;

import com.example.demo.dto.MyPetResponseDto;
import com.example.demo.entity.Pet;
import com.example.demo.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

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

    // 4. 씻기기 API
    @PostMapping("/{petId}/clean")
    public ResponseEntity<MyPetResponseDto> cleanPet(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable(name = "petId") Long petId) {
        return ResponseEntity.ok(petService.cleanPet(currentUid, petId));
    }

    // 5. 상태 동기화 API (접속 중 및 오프라인)
    @PatchMapping("/{petId}/status")
    public ResponseEntity<MyPetResponseDto> syncStatus(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable(name = "petId") Long petId,
            @RequestBody com.example.demo.dto.PetStatusSyncDto requestDto) {
        return ResponseEntity.ok(petService.syncStatus(currentUid, petId, requestDto));
    }

    // 내 펫 정보 조회 (없으면 생성)
    @GetMapping("/me")
    public ResponseEntity<MyPetResponseDto> getMyPet(
            @RequestAttribute("currentUid") String currentUid) {
        return ResponseEntity.ok(petService.getOrCreatePet(currentUid));
    }

    // [디버그용] 캐릭터 특성 수치 강제 조작 API
    @PutMapping("/{petId}/traits/debug")
    public ResponseEntity<MyPetResponseDto> updateTraitsDebug(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable(name = "petId") Long petId,
            @RequestBody com.example.demo.dto.PetTraitsSyncDto requestDto) {
        return ResponseEntity.ok(petService.updateTraitsDebug(currentUid, petId, requestDto));
    }
}