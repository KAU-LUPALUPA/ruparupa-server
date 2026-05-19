package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

public class PlazaDto {
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinByCodeRequest {
        private String code;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageRequest {
        private String text;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlazaRoomResponse {
        private PlazaDetail plaza;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlazaDetail {
        private String plazaId;
        private String plazaCode;
        private String displayPlazaCode;
        private List<PlazaParticipantResponse> participants;
        private List<PlazaChatMessageResponse> messages;
        private int maxParticipants;
        private Long roomRevision;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlazaParticipantResponse {
        private String userId;
        private String nickname;
        private PlazaPetSnapshotResponse pet;
        private PlazaPositionResponse position;
        private Long joinedAtMillis;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlazaPetSnapshotResponse {
        private String petId;
        private String name;
        private String characterAssetKey;
        private PetAppearanceResponse appearance;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PetAppearanceResponse {
        private float headSizeScale;
        private float bodySizeScale;
        private float eyeSizeScale;
        private float noseSizeScale;
        private float mouthSizeScale;

        public static PetAppearanceResponse defaultAppearance() {
            return PetAppearanceResponse.builder()
                    .headSizeScale(1.0f)
                    .bodySizeScale(1.0f)
                    .eyeSizeScale(1.0f)
                    .noseSizeScale(1.0f)
                    .mouthSizeScale(1.0f)
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlazaPositionResponse {
        private float x;
        private float y;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlazaChatMessageEnvelopeResponse {
        private PlazaChatMessageResponse message;
        private Long roomRevision;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlazaChatMessageResponse {
        private String id;
        private String senderUserId;
        private String senderNickname;
        private String text;
        private Long sentAtMillis;
    }
}
