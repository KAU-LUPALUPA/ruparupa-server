package com.example.demo.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetTraits {
    
    @Builder.Default
    private float activity = 0.5f;

    @Builder.Default
    private float appetite = 0.5f;

    @Builder.Default
    private float attention = 0.5f;

    @Builder.Default
    private float curiosity = 0.5f;

    @Builder.Default
    private float patience = 0.5f;
}
