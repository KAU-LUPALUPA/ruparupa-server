package com.example.demo.dto;

import lombok.Data;

@Data
public class PetStatusSyncDto {
    private int satiety;
    private int vitality;
    private int cleanliness;
    private boolean offlineSync;
}
