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
            /** S3 파일 키 (confirm 시 그대로 전달) */
            private String fileKey;
            /**
             * true면 uploadUrl/fileKey로 이미지 업로드 가능.
             * false면 조 매칭은 완료됐지만 이미지 업로드 URL 발급은 실패한 상태.
             */
            private boolean imageUploadAvailable;
            /** 이미지 업로드 URL 발급 실패 사유 */
            private String uploadErrorMessage;
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
        /** 투표 참여 리워드로 받은 골드 (첫 투표 시에만 지급, 이미 투표했으면 0) */
        private int rewardGold;
    }

    /**
     * GET /contest/vote/status 응답.
     * 오늘 투표 여부 및 남은 대상 그룹 존재 여부를 프론트에 전달.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class VoteStatusResponse {
        private boolean success;
        /** 오늘(자정 기준) 이미 투표했으면 true */
        private boolean votedToday;
        /** 내 조를 제외한 투표 가능한 ACTIVE 그룹이 1개 이상 있으면 true */
        private boolean votableGroupExists;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MyGroupResponse {
        private boolean success;
        private MyGroupData data;

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
        public static class MyGroupData {
            private String groupId;
            private Long groupNumber;
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

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GroupListResponse {
        private boolean success;
        private List<GroupSummary> data;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GroupSummary {
        private String groupId;
        private Long groupNumber;
        /** OPEN / ACTIVE */
        private String status;
        private long memberCount;
        private boolean myGroup;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GroupDetailResponse {
        private boolean success;
        private MyGroupResponse.MyGroupData data;
    }

    /** 그룹 조회 시 하나의 참가자 정보 */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EntryInfo {
        private Long entryId;
        private String userUid;
        /** S3 이미지 URL (confirmed=true 일 때만 노출, 아니면 null) */
        private String imageUrl;
        private int voteCount;
        /** 종료 후 부여된 등수 (진행 중이면 null) */
        private Integer rank;
        /** S3 업로드 완료 여부 */
        private boolean confirmed;
    }

    // -------------------------------------------------------------------------
    // 랭킹
    // -------------------------------------------------------------------------

    /**
     * GET /contest/ranking/live 응답.
     * 현재 진행 중인 모든 그룹의 참가자를 voteCount 기준으로 정렬해 반환.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LiveRankingResponse {
        private boolean success;
        private List<RankingEntry> data;
    }

    /**
     * GET /contest/ranking/recent 응답.
     * 최근 종료된 그룹들의 최종 결과를 그룹 단위로 묶어 반환.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RecentRankingResponse {
        private boolean success;
        private List<RankingGroup> data;
    }

    /** 랭킹 목록에서 한 참가자를 나타내는 DTO */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RankingEntry {
        private Long entryId;
        private String userUid;
        private String nickname;
        private String imageUrl;
        private int voteCount;
        /** 실시간 랭킹은 null, 최근 랭킹은 최종 등수(1~3) */
        private Integer rank;
        /** 소속 그룹 ID */
        private String groupId;
    }

    /** 최근 랭킹에서 하나의 종료된 그룹 결과 */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RankingGroup {
        private String groupId;
        /** 종료 시각 */
        private LocalDateTime closedAt;
        /** 1등~3등 순서로 정렬된 참가자 목록 */
        private List<RankingEntry> entries;
    }
}