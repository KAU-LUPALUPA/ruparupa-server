package com.example.demo.dto;

import com.example.demo.entity.FriendRequestStatus;
import com.example.demo.entity.FriendshipStatus;
import com.example.demo.entity.HomeInvitationStatus;
import com.example.demo.entity.HomeVisitStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class FriendDto {

    // ==========================================
    // 1. 공통 에러 응답
    // ==========================================
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ErrorResponse {
        private String code;
        private String message;
    }

    // ==========================================
    // 2. 공통 데이터 모델 (MD 계약서 기준)
    // ==========================================
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendUser {
        private String userId;
        private String nickname;
        private String friendCode;
        private String displayFriendCode;
        private String avatarAssetKey;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendRequest {
        private String id;
        private FriendUser fromUser;
        private FriendUser toUser;
        private FriendRequestStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime respondedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendSummary {
        private String friendshipId;
        private FriendUser user;
        private FriendshipStatus status;
        private LocalDateTime friendsSince;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendMessage {
        private String id;
        private String friendUserId;
        private String senderUserId;
        private String text; // 계약서에 맞춰 content -> text 로 변경
        private LocalDateTime sentAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendHomeInvitation {
        private String id;
        private FriendUser fromUser;
        private FriendUser toUser;
        private HomeInvitationStatus status;
        private String message;
        private LocalDateTime createdAt;
        private LocalDateTime respondedAt;
        private LocalDateTime expiresAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendHomeSnapshot {
        private FriendUser owner;
        private FriendRoomSnapshot room;
        private FriendPetSnapshot petSnapshot;
        private LocalDateTime snapshotAt;
        private LocalDateTime visitedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendRoomSnapshot {
        private String sceneId;
        private String wallAssetKey;
        private String floorAssetKey;
        private List<FriendPlacedItem> placedItems;
        private int layoutRevision;
        private LocalDateTime updatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendPlacedItem {
        private String placedItemId;
        private String itemId;
        private String objectType;
        private String anchorType;
        private FriendAnchor anchor;
        private FriendTile tile;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendAnchor {
        private float u;
        private float v;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendTile {
        private int x;
        private int y;
        private int widthTiles;
        private int depthTiles;
        private String anchorMode;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendPetSnapshot {
        private String petId;
        private String ownerUserId;
        private String name;
        private String characterAssetKey;
        private FriendPetAppearanceSnapshot appearance;
        private FriendPetConditionSnapshot condition;
        private FriendPetSceneStateSnapshot sceneState;
        private String personality;
        private List<String> equippedItemIds;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendPetAppearanceSnapshot {
        private float headSizeScale;
        private float bodySizeScale;
        private float eyeSizeScale;
        private float noseSizeScale;
        private float mouthSizeScale;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendPetConditionSnapshot {
        private int satiety;
        private int vitality;
        private boolean isEgg;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendPetSceneStateSnapshot {
        private String action;
        private FriendAnchor anchor;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendHomeVisitSession {
        private String id;
        private FriendUser hostUser;
        private FriendUser visitorUser;
        private HomeVisitStatus status;
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;
        private LocalDateTime expiresAt;
        private FriendHomeSnapshot hostHomeSnapshot;
        private FriendPetSnapshot visitorPetSnapshot;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HomeVisitMessage {
        private String id;
        private String visitSessionId;
        private String senderUserId;
        private String text;
        private LocalDateTime sentAt;
    }

    // ==========================================
    // 3. API 요청 (Request) DTO
    // ==========================================
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SendRequest {
        private String friendCode;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class MessageRequest {
        private String text;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class SendHomeInvitationRequest {
        private String friendUserId;
        private String message;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class SendHomeVisitMessageRequest {
        private String text;
    }

    // ==========================================
    // 4. API 응답 (Response) DTO
    // ==========================================

    // 친구 신청, 거절, 취소 응답
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SingleRequestResponse {
        private FriendRequest request;
    }

    // 친구 수락 응답 (request와 friendship 동시 반환)
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AcceptRequestResponse {
        private FriendRequest request;
        private FriendSummary friendship;
    }

    // 내 친구 코드 조회
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MyFriendCodeResponse {
        private String friendCode;
        private String displayFriendCode;
    }

    // 친구 코드로 유저 검색
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendUserLookupResponse {
        private FriendUser user;
        private FriendshipStatus relationshipStatus;
    }

    // 보낸/받은 요청 목록 (이제 API가 분리되므로 각각 requests 배열만 가짐)
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendRequestListResponse {
        private List<FriendRequest> requests;
    }

    // 친구 목록 조회
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendListResponse {
        private List<FriendSummary> friends;
    }

    // 메시지 1개 전송 응답
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SingleMessageResponse {
        private FriendMessage message;
    }

    // 메시지 목록 조회 페이징 응답
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FriendMessagesResponse {
        private List<FriendMessage> messages;
        private String nextCursor;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SingleHomeInvitationResponse {
        private FriendHomeInvitation invitation;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HomeInvitationListResponse {
        private List<FriendHomeInvitation> invitations;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AcceptHomeInvitationResponse {
        private FriendHomeInvitation invitation;
        private FriendHomeSnapshot homeSnapshot;
        private FriendHomeVisitSession visitSession;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ActiveHomeVisitsResponse {
        private List<FriendHomeVisitSession> hosting;
        private List<FriendHomeVisitSession> visiting;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SingleHomeVisitSessionResponse {
        private FriendHomeVisitSession visitSession;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SingleHomeVisitMessageResponse {
        private HomeVisitMessage message;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HomeVisitMessagesResponse {
        private List<HomeVisitMessage> messages;
        private String nextCursor;
    }
}
