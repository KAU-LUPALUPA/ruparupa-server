package com.example.demo.service;

import com.example.demo.User;
import com.example.demo.UserRepository;
import com.example.demo.entity.Pet;
import com.example.demo.entity.Room;
import com.example.demo.entity.RoomFurniture;
import com.example.demo.repository.PetRepository;
import com.example.demo.repository.RoomFurnitureRepository;
import com.example.demo.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import com.example.demo.dto.MyPetResponseDto;
import com.example.demo.entity.InteractionEvents;
import com.example.demo.entity.PetTraits;
import java.time.LocalDate;

import java.util.List;
import java.util.Random;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomFurnitureRepository roomFurnitureRepository;

    @Transactional
    public MyPetResponseDto getOrCreatePet(String currentUid) {
        User user = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        Pet pet = petRepository.findByUserId(user.getId())
                .orElseGet(() -> createInitialSetupAndReturnPet(user));

        return convertToDto(pet);
    }

    public MyPetResponseDto convertToDto(Pet pet) {
        return MyPetResponseDto.builder()
                .petId(pet.getId().toString())
                .ownerUserId(pet.getUser().getUid())
                .name(pet.getName())
                .characterAssetKey(pet.getCharacterAssetKey())
                .personality(pet.getPersonality())
                .equippedItemIds(pet.getEquippedItemIds()) // 리스트 그대로 반환
                .updatedAt(pet.getUpdatedAt())
                .status(MyPetResponseDto.StatusDto.builder()
                        .satiety(pet.getSatiety())
                        .vitality(pet.getVitality())
                        .cleanliness(pet.getCleanliness())
                        .isEgg(pet.isEgg())
                        .isSleep(pet.isSleep()) // 필드 직접 매핑
                        .build())
                .traits(MyPetResponseDto.PetTraitsDto.builder()
                        .activity(pet.getTraits().getActivity())
                        .appetite(pet.getTraits().getAppetite())
                        .attention(pet.getTraits().getAttention())
                        .curiosity(pet.getTraits().getCuriosity())
                        .patience(pet.getTraits().getPatience())
                        .build())
                .interactions(MyPetResponseDto.InteractionEventsDto.builder()
                        .feedCount(pet.getInteractionEvents().getFeedCount())
                        .playCount(pet.getInteractionEvents().getPlayCount())
                        .cleanCommandCount(pet.getInteractionEvents().getCleanCommandCount())
                        .sleepCommandCount(pet.getInteractionEvents().getSleepCommandCount())
                        .daysActive(pet.getInteractionEvents().getDaysActive())
                        .build())
                .build();
    }

    @Transactional
    public Pet createInitialSetupAndReturnPet(User user) {
        String[] personalities = {"ACTIVE", "CALM", "LAZY"};
        String randomPersonality = personalities[new Random().nextInt(personalities.length)];

        Pet initialPet = Pet.builder()
                .user(user)
                .name("루파")
                .characterAssetKey("room/characters/lupa_default")
                .personality(randomPersonality)
                .satiety(100)
                .vitality(100)
                .isEgg(true)
                .isSleep(false) // 초기값 설정
                .equippedItemIds(Collections.emptyList()) // 초기 아이템 없음
                .build();
                
        initialPet.generatePetUid(user.getUid());
        Pet savedPet = petRepository.save(initialPet);

        // 방 데이터 생성 (새로운 규격 적용)
        Room room = Room.builder()
                .ownerUserId(user.getUid())
                .sceneId("main_room")
                .layoutRevision(0)
                .wallAssetKey("room/walls/main_wall")
                .floorAssetKey("room/floors/main_floor")
                .build();
        room.generateRoomId(user.getUid());
        Room savedRoom = roomRepository.save(room);

        // 가구 세팅 시 savedRoom.getRoomId() (String) 사용
        RoomFurniture bed = new RoomFurniture();
        bed.setRoomId(savedRoom.getRoomId());
        bed.setType("BED");
        bed.setX(2); bed.setY(0); bed.setDirection(0);

        RoomFurniture toyBox = new RoomFurniture();
        toyBox.setRoomId(savedRoom.getRoomId());
        toyBox.setType("TOY_BOX");
        toyBox.setX(1); toyBox.setY(4); toyBox.setDirection(0);

        RoomFurniture feedBag = new RoomFurniture();
        feedBag.setRoomId(savedRoom.getRoomId());
        feedBag.setType("FOOD_BAG");
        feedBag.setX(4); feedBag.setY(3); feedBag.setDirection(0);

        roomFurnitureRepository.saveAll(List.of(bed, toyBox, feedBag));
        
        return savedPet;
    }

    // 1. 밥 먹이기 (사료 봉투 상호작용)
    @Transactional
    public MyPetResponseDto feedPet(String currentUid, Long petId) {
        Pet pet = petRepository.findById(petId).orElseThrow(() -> new IllegalArgumentException("펫이 존재하지 않습니다."));
        
        // 소유권 검증 로직
        if (!pet.getUser().getUid().equals(currentUid)) {
            throw new IllegalArgumentException("본인의 펫만 조작할 수 있습니다.");
        }

        // 자정 동기화 (지연 평가)
        pet = syncMidnightUpdates(pet);
        applyTimeDecay(pet);

        // 상태 검증
        if (pet.isSleep()) throw new IllegalStateException("펫이 자고 있습니다.");

        if (pet.getSatiety() >= 100) {
            throw new IllegalStateException("펫이 이미 배가 부릅니다.");
        }

        pet.setSatiety(Math.min(pet.getSatiety() + 30, 100));
        pet.getInteractionEvents().setFeedCount(pet.getInteractionEvents().getFeedCount() + 1);
        return convertToDto(petRepository.save(pet));
    }

    // 2. 잠재우기 (침대 상호작용)
    @Transactional
    public MyPetResponseDto sleepPet(String currentUid, Long petId) {
        Pet pet = petRepository.findById(petId).orElseThrow(() -> new IllegalArgumentException("펫이 존재하지 않습니다."));
        
        // 소유권 검증 로직
        if (!pet.getUser().getUid().equals(currentUid)) {
            throw new IllegalArgumentException("본인의 펫만 조작할 수 있습니다.");
        }

        // 자정 동기화 (지연 평가)
        pet = syncMidnightUpdates(pet);
        applyTimeDecay(pet);

        // 상태 검증
        if (pet.isSleep()) {
            throw new IllegalStateException("펫이 이미 자고 있습니다.");
        }

        pet.setVitality(Math.min(pet.getVitality() + 30, 100));
        pet.getInteractionEvents().setSleepCommandCount(pet.getInteractionEvents().getSleepCommandCount() + 1);
        return convertToDto(petRepository.save(pet));
    }

    // 3. 놀아주기 (장난감 박스 상호작용)
    @Transactional
    public MyPetResponseDto playWithPet(String currentUid, Long petId) {
        Pet pet = petRepository.findById(petId).orElseThrow(() -> new IllegalArgumentException("펫이 존재하지 않습니다."));
        
        // 소유권 검증 로직
        if (!pet.getUser().getUid().equals(currentUid)) {
            throw new IllegalArgumentException("본인의 펫만 조작할 수 있습니다.");
        }

        // 자정 동기화 (지연 평가)
        pet = syncMidnightUpdates(pet);
        applyTimeDecay(pet);

        // 상태 검증
        if (pet.isSleep()) {
            throw new IllegalStateException("펫이 자고 있어서 놀 수 없습니다.");
        }
        if (pet.getVitality() < 20) {
            throw new IllegalStateException("비타민이 부족하여 놀 수 없습니다.");
        }

        pet.setVitality(Math.max(pet.getVitality() - 20, 0));
        pet.setSatiety(Math.max(pet.getSatiety() - 10, 0));
        pet.getInteractionEvents().setPlayCount(pet.getInteractionEvents().getPlayCount() + 1);
        return convertToDto(petRepository.save(pet));
    }

    // 4. 씻기기 (욕조/청소 상호작용)
    @Transactional
    public MyPetResponseDto cleanPet(String currentUid, Long petId) {
        Pet pet = petRepository.findById(petId).orElseThrow(() -> new IllegalArgumentException("펫이 존재하지 않습니다."));
        
        if (!pet.getUser().getUid().equals(currentUid)) {
            throw new IllegalArgumentException("본인의 펫만 조작할 수 있습니다.");
        }

        pet = syncMidnightUpdates(pet);
        applyTimeDecay(pet);

        if (pet.isSleep()) {
            throw new IllegalStateException("펫이 자고 있습니다.");
        }
        if (pet.getCleanliness() >= 100) {
            throw new IllegalStateException("펫이 이미 깨끗합니다.");
        }

        pet.setCleanliness(Math.min(pet.getCleanliness() + 50, 100));
        pet.getInteractionEvents().setCleanCommandCount(pet.getInteractionEvents().getCleanCommandCount() + 1);
        return convertToDto(petRepository.save(pet));
    }

    // 자정(Midnight) 특성 EMA 동기화 및 행동 제한 초기화 로직
    @Transactional
    public Pet syncMidnightUpdates(Pet pet) {
        LocalDate now = LocalDate.now();
        if (pet.getLastEmaUpdatedDate() != null && pet.getLastEmaUpdatedDate().isBefore(now)) {
            InteractionEvents events = pet.getInteractionEvents();
            PetTraits traits = pet.getTraits();
            float alpha = 0.05f;

            // EMA 반영 시 하루 최대 5회의 행동까지만 유효 타격으로 계산하여 과반영 방지
            int effectivePlay = Math.min(events.getPlayCount(), 5);
            int effectiveFeed = Math.min(events.getFeedCount(), 5);
            int effectiveClean = Math.min(events.getCleanCommandCount(), 5);
            int effectiveSleep = Math.min(events.getSleepCommandCount(), 5);

            float targetActivity = Math.max(0f, Math.min(1f, (effectivePlay * 0.5f) / 7f / 3.0f));
            float targetAppetite = Math.max(0f, Math.min(1f, (effectiveFeed * 1.0f) / 7f / 2.0f));
            float targetAttention = Math.max(0.1f, Math.min(1f, (events.getDaysActive() * 1.0f + (effectiveClean + effectiveSleep) * 0.3f) / 7f / 4.0f));

            traits.setActivity(Math.max(0f, Math.min(1f, traits.getActivity() * (1 - alpha) + targetActivity * alpha)));
            traits.setAppetite(Math.max(0f, Math.min(1f, traits.getAppetite() * (1 - alpha) + targetAppetite * alpha)));
            traits.setAttention(Math.max(0.1f, Math.min(1f, traits.getAttention() * (1 - alpha) + targetAttention * alpha)));

            events.setDaysActive(events.getDaysActive() + 1);
            events.resetDaily();
            pet.setLastEmaUpdatedDate(now);
            
            return petRepository.save(pet);
        }
        return pet;
    }

    // 시간 경과에 따른 자연 감소분 반영 (지연 평가)
    private void applyTimeDecay(Pet pet) {
        if (pet.getUpdatedAt() == null) return;
        long elapsedSeconds = java.time.temporal.ChronoUnit.SECONDS.between(pet.getUpdatedAt(), java.time.LocalDateTime.now());
        if (elapsedSeconds <= 0) return;

        float appetiteMod = 0.5f + pet.getTraits().getAppetite();
        float activityMod = 0.5f + pet.getTraits().getActivity();

        int satietyDecay = (int) ((elapsedSeconds / 600L) * appetiteMod);
        int vitalityDecay = (int) ((elapsedSeconds / 900L) * activityMod);
        int cleanlinessDecay = (int) ((elapsedSeconds / 1800L) * activityMod);

        pet.setSatiety(Math.max(0, pet.getSatiety() - satietyDecay));
        pet.setVitality(Math.max(0, pet.getVitality() - vitalityDecay));
        pet.setCleanliness(Math.max(0, pet.getCleanliness() - cleanlinessDecay));
        
        pet.setUpdatedAt(java.time.LocalDateTime.now());
    }

    @Transactional
    public MyPetResponseDto syncStatus(String currentUid, Long petId, com.example.demo.dto.PetStatusSyncDto request) {
        Pet pet = petRepository.findById(petId).orElseThrow(() -> new IllegalArgumentException("펫이 존재하지 않습니다."));
        
        if (!pet.getUser().getUid().equals(currentUid)) {
            throw new IllegalArgumentException("본인의 펫만 조작할 수 있습니다.");
        }

        // 자정 경과 확인 (동기화 전 선행 처리)
        syncMidnightUpdates(pet);
        
        // 서버 측에서 시간 경과에 따른 감소분을 먼저 적용합니다.
        applyTimeDecay(pet);

        int oldSatiety = pet.getSatiety();
        int oldVitality = pet.getVitality();
        int oldCleanliness = pet.getCleanliness();

        int diffSatiety = oldSatiety - request.getSatiety();
        int diffVitality = oldVitality - request.getVitality();
        int diffCleanliness = oldCleanliness - request.getCleanliness();

        // 클라이언트에서 계산한 값과 서버에서 시간 기반으로 계산한 값(자연 감소) 간의 오차가 일정 수준 이상이면 조작으로 간주
        // 오프라인이든 온라인(In-game)이든, 정상적인 경우 서버가 똑같이 decay를 적용했으므로 diff는 거의 0에 가까워야 함.
        // 다만 상호작용(씻기기 등)이나 프론트엔드의 세세한 반올림 차이를 허용하기 위해 여유값을 둠.
        if (request.isOfflineSync()) {
            if (diffSatiety < -10 || diffVitality < -10 || diffCleanliness < -10) {
                throw new IllegalArgumentException("Offline sync cannot significantly increase stats. (Client requested higher stats than server calculated)");
            } else if (Math.abs(diffSatiety) > 30 || Math.abs(diffVitality) > 30 || Math.abs(diffCleanliness) > 30) {
                throw new IllegalArgumentException("Offline decay exceeded the allowed threshold. Potential manipulation detected.");
            }
        } else {
            // 인게임 동기화의 경우, 자율 행동(Auto Groom) 등으로 인해 청결도가 오르거나 밥을 먹일 수 있으므로 (증가) 방향은 크게 허용하되 감소는 조작 방지
            if (diffSatiety > 30 || diffVitality > 30 || diffCleanliness > 30) {
                throw new IllegalArgumentException("In-game sync decay mismatch exceeded the allowed threshold.");
            }
        }

        pet.setSatiety(request.getSatiety());
        pet.setVitality(request.getVitality());
        pet.setCleanliness(request.getCleanliness());
        pet.setUpdatedAt(java.time.LocalDateTime.now());

        return convertToDto(petRepository.save(pet));
    }

    @Transactional
    public MyPetResponseDto updateTraitsDebug(String currentUid, Long petId, com.example.demo.dto.PetTraitsSyncDto request) {
        Pet pet = petRepository.findById(petId).orElseThrow(() -> new IllegalArgumentException("펫이 존재하지 않습니다."));
        
        if (!pet.getUser().getUid().equals(currentUid)) {
            throw new IllegalArgumentException("본인의 펫만 조작할 수 있습니다.");
        }

        PetTraits traits = pet.getTraits();
        traits.setActivity(request.getActivity());
        traits.setPatience(request.getPatience());
        traits.setCuriosity(request.getCuriosity());
        traits.setAppetite(request.getAppetite());
        traits.setAttention(request.getAttention());
        
        return convertToDto(petRepository.save(pet));
    }
}
