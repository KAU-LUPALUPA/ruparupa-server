package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

public class PetPredictDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class Request {
        private double cleanliness;
        private double fullness;
        private double stamina;
        private double boredom;

        @JsonProperty("pet_x")
        private double petX;

        @JsonProperty("pet_y")
        private double petY;

        @JsonProperty("toy_placed")
        private boolean toyPlaced;

        @JsonProperty("carrying_toy")
        private boolean carryingToy;

        @JsonProperty("command_pending")
        private boolean commandPending;

        private double activeness;
        private double gluttony;
        private double patience;
        private double curiosity;
        private double loyalty;

        @JsonProperty("food_x")
        private double foodX;

        @JsonProperty("food_y")
        private double foodY;

        @JsonProperty("bed_x")
        private double bedX;

        @JsonProperty("bed_y")
        private double bedY;

        @JsonProperty("wash_x")
        private double washX;

        @JsonProperty("wash_y")
        private double washY;

        @JsonProperty("toy_x")
        private double toyX;

        @JsonProperty("toy_y")
        private double toyY;

        @JsonProperty("chest_x")
        private double chestX;

        @JsonProperty("chest_y")
        private double chestY;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    public static class Response {
        @JsonProperty("action_id")
        private int actionId;

        @JsonProperty("action_name")
        private String actionName;

        private String description;
    }
}
