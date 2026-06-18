package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class MyPetResponseDto {
    private String petId;         // petUid 매핑
    private String ownerUserId;    // 유저 uid 매핑
    private String name;
    private String characterAssetKey;
    private StatusDto status;
    private PetTraitsDto traits;
    private InteractionEventsDto interactions;
    private String personality;
    private List<Long> equippedItemIds; // 현재는 빈 리스트
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class StatusDto {
        private int satiety;  // hunger 매핑
        private int vitality; // stamina 매핑
        private int cleanliness;
        private boolean isEgg;
        private boolean isSleep; // currentAction == "SLEEPING" 여부
    }

    @Getter
    @Builder
    public static class PetTraitsDto {
        private float activity;
        private float appetite;
        private float attention;
        private float curiosity;
        private float patience;
    }

    @Getter
    @Builder
    public static class InteractionEventsDto {
        private int feedCount;
        private int playCount;
        private int cleanCommandCount;
        private int sleepCommandCount;
        private int daysActive;
    }
}