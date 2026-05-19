package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RoomLayoutValidationResponseDto {
    private String syncStatus;
    private Integer serverLayoutRevision;
    private String serverLayoutHash;
    private LocalDateTime serverUpdatedAt;
    private RoomLayoutResponseDto.LayoutData roomLayout;
}
