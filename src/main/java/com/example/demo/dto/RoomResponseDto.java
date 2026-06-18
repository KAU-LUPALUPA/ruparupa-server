package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RoomResponseDto {

    private PetDto pet;
    private RoomDto room;

    @Getter
    @Builder
    public static class PetDto {
        private String name;
        private int satiety;
        private int vitality; // ⭐ 기존 energy에서 vitality로 수정됨!
        private int cleanliness;
        private boolean isSleep;
    }

    @Getter
    @Builder
    public static class RoomDto {
        private String wallType;
        private String floorTileType;
        private List<FurnitureDto> furnitureList;
    }

    @Getter
    @Builder
    public static class FurnitureDto {
        private int id;
        private String type;
        private float x;
        private float y;
        private int direction;
        private String status;
    }
}