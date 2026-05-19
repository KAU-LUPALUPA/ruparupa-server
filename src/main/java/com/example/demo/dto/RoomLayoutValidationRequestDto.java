package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomLayoutValidationRequestDto {
    private Integer localLayoutRevision;
    private String localLayoutHash;
    private String localUpdatedAt;
}
