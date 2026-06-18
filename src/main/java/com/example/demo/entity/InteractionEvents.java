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
public class InteractionEvents {

    @Builder.Default
    private int feedCount = 0;

    @Builder.Default
    private int playCount = 0;

    @Builder.Default
    private int cleanCommandCount = 0;

    @Builder.Default
    private int sleepCommandCount = 0;
    
    @Builder.Default
    private int daysActive = 1;

    public void resetDaily() {
        this.feedCount = 0;
        this.playCount = 0;
        this.cleanCommandCount = 0;
        this.sleepCommandCount = 0;
    }
}
