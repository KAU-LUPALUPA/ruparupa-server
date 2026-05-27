package com.example.demo.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class ContestDto {


    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class JoinResponse {
        private boolean success;
        private JoinData data;

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
        public static class JoinData {
            /** 생성된 entry PK (이후 confirm 요청에 사용) */
            private Long entryId;
            /** 소속 그룹 ID */
            private String groupId;
            /**
             * S3 Presigned PUT URL
             * FE는 이 URL에 캐릭터 사진을 PUT 한 뒤 /contest/confirm 을 호출해야 함
             */
            private String uploadUrl;
            /**
             * S3 파일 키 (confirm 시 그대로 전달)
             */
            private String fileKey;
        }
    }


    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConfirmRequest {
        /** join 응답에서 받은 entryId */
        private Long entryId;
        /** join 응답에서 받은 fileKey (S3에 실제로 저장된 키) */
        private String fileKey;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConfirmResponse {
        private boolean success;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class VoteRequest {
        /** 투표할 ContestEntry.id */
        private Long entryId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class VoteResponse {
        private boolean success;
        /** 투표 후 해당 entry 의 최신 voteCount */
        private int voteCount;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MyGroupResponse {
        private boolean success;
        private MyGroupData data;

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
        public static class MyGroupData {
            private String groupId;
            /** OPEN / ACTIVE / CLOSED */
            private String status;
            /** 자동 종료 예정 시각 */
            private LocalDateTime closeAt;
            /** 그룹 내 참가자 목록 */
            private List<EntryInfo> entries;
            /** 내 entry ID (투표 받을 때 필요) */
            private Long myEntryId;
        }
    }

    /** 그룹 조회 시 하나의 참가자 정보 */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EntryInfo {
        private Long entryId;
        private String userUid;
        /**
         * S3 이미지 URL (confirmed=true 일 때만 노출, 아니면 null)
         */
        private String imageUrl;
        private int voteCount;
        /** 종료 후 부여된 등수 (진행 중이면 null) */
        private Integer rank;
        /** S3 업로드 완료 여부 */
        private boolean confirmed;
    }
}