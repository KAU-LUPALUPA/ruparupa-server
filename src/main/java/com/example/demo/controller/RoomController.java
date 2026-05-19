package com.example.demo.controller;

import com.example.demo.dto.RoomLayoutResponseDto;
import com.example.demo.dto.RoomLayoutRequestDto;
import com.example.demo.dto.RoomLayoutValidationRequestDto;
import com.example.demo.dto.RoomLayoutValidationResponseDto;
import com.example.demo.dto.RoomResponseDto;
import com.example.demo.entity.Pet;
import com.example.demo.entity.Room;
import com.example.demo.entity.RoomFurniture;
import com.example.demo.repository.PetRepository;
import com.example.demo.repository.RoomFurnitureRepository;
import com.example.demo.repository.RoomRepository;
import com.example.demo.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rooms") // 공통 경로 설정
@RequiredArgsConstructor
public class RoomController {

    private final PetRepository petRepository;
    private final RoomRepository roomRepository;
    private final RoomFurnitureRepository roomFurnitureRepository;
    private final RoomService roomService;

    /**
     * 1. 방 상세 레이아웃 조회 API
     * GET /rooms/me/layout
     */
    @GetMapping("/me/layout")
    public ResponseEntity<RoomLayoutResponseDto> getMyRoomLayout(
            @RequestAttribute("currentUid") String currentUid) {

        return ResponseEntity.ok(roomService.getMyRoomLayout(currentUid));
    }

    /**
     * 방 레이아웃 동기화 검증 API
     * POST /rooms/me/layout/validate
     */
    @PostMapping("/me/layout/validate")
    public ResponseEntity<RoomLayoutValidationResponseDto> validateMyRoomLayout(
            @RequestAttribute("currentUid") String currentUid,
            @RequestBody RoomLayoutValidationRequestDto request) {

        return ResponseEntity.ok(roomService.validateMyRoomLayout(currentUid, request));
    }

    /**
     * 방 레이아웃 저장 API
     * PUT /rooms/me/layout
     */
    @PutMapping("/me/layout")
    public ResponseEntity<RoomLayoutResponseDto> saveMyRoomLayout(
            @RequestAttribute("currentUid") String currentUid,
            @RequestBody RoomLayoutRequestDto request) {

        return ResponseEntity.ok(roomService.saveMyRoomLayout(currentUid, request));
    }

    /**
     * 2. 로그인 시/방 방문 시 기본 정보 조회 API (기존 로직 업데이트)
     * GET /rooms/info
     */
    @GetMapping("/info")
    public ResponseEntity<RoomResponseDto> getRoomInfo(
            @RequestAttribute("currentUid") String currentUid,
            @RequestParam(name = "petId") Long petId) {
            
        // 1. 펫 정보 가져오기
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new IllegalArgumentException("펫이 존재하지 않습니다."));

        // 소유권 검증: 내 펫(방)이 아니면 예외 발생
        if (!pet.getUser().getUid().equals(currentUid)) {
            throw new IllegalArgumentException("본인의 방 정보만 조회할 수 있습니다.");
        }

        // 2. 유저 ID로 방(Room) 정보 가져오기
        Room room = roomRepository.findByOwnerUserId(currentUid)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저의 방이 존재하지 않습니다."));

        // 3. 방에 배치된 가구 리스트 가져오기 (변경된 String roomId 사용)
        List<RoomFurniture> furnitureList = roomFurnitureRepository.findByRoomId(room.getRoomId());

        // 4. 가구 엔티티들을 DTO로 변환
        List<RoomResponseDto.FurnitureDto> furnitureDtos = furnitureList.stream()
                .map(f -> RoomResponseDto.FurnitureDto.builder()
                        .id(Math.toIntExact(f.getId()))
                        .type(f.getType())
                        .x(f.getX())
                        .y(f.getY())
                        .direction(f.getDirection())
                        .status(f.getStatus())
                        .build())
                .collect(Collectors.toList());

        // 5. 최종 응답 DTO 조립 
        RoomResponseDto.PetDto petDto = RoomResponseDto.PetDto.builder()
                .name(pet.getName())
                .satiety(pet.getSatiety())
                .vitality(pet.getVitality())
                .isSleep(pet.isSleep())
                .build();

        RoomResponseDto.RoomDto roomDto = RoomResponseDto.RoomDto.builder()
                .wallType(room.getWallAssetKey()) // wallType 대신 새로운 wallAssetKey 매핑
                .floorTileType(room.getFloorAssetKey()) // floorTileType 대신 새로운 floorAssetKey 매핑
                .furnitureList(furnitureDtos)
                .build();

        RoomResponseDto response = RoomResponseDto.builder()
                .pet(petDto)
                .room(roomDto)
                .build();

        return ResponseEntity.ok(response);
    }
}
