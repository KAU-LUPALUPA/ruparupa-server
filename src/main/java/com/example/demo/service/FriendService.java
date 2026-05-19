package com.example.demo.service;
 
import com.example.demo.User;
import com.example.demo.UserRepository;
import com.example.demo.dto.FriendDto;
import com.example.demo.entity.FriendMessage;
import com.example.demo.entity.FriendRequest;
import com.example.demo.entity.FriendRequestStatus;
import com.example.demo.entity.Friendship;
import com.example.demo.entity.FriendshipStatus;
import com.example.demo.entity.HomeInvitation;
import com.example.demo.entity.HomeInvitationStatus;
import com.example.demo.entity.HomeVisitMessage;
import com.example.demo.entity.HomeVisitSession;
import com.example.demo.entity.HomeVisitStatus;
import com.example.demo.entity.Pet;
import com.example.demo.entity.Room;
import com.example.demo.entity.RoomFurniture;
import com.example.demo.exception.CustomApiException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.FriendMessageRepository;
import com.example.demo.repository.FriendRequestRepository;
import com.example.demo.repository.FriendshipRepository;
import com.example.demo.repository.HomeInvitationRepository;
import com.example.demo.repository.HomeVisitMessageRepository;
import com.example.demo.repository.HomeVisitSessionRepository;
import com.example.demo.repository.PetRepository;
import com.example.demo.repository.RoomFurnitureRepository;
import com.example.demo.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
 
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
 
@Service
@RequiredArgsConstructor
public class FriendService {
 
    private final UserRepository userRepository;
    private final FriendRequestRepository requestRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendMessageRepository messageRepository;
    private final HomeInvitationRepository homeInvitationRepository;
    private final HomeVisitSessionRepository homeVisitSessionRepository;
    private final HomeVisitMessageRepository homeVisitMessageRepository;
    private final RoomRepository roomRepository;
    private final RoomFurnitureRepository roomFurnitureRepository;
    private final PetRepository petRepository;
 
    private static final int MAX_MESSAGE_LENGTH = 120;
 
    // ==========================================
    // 헬퍼 메서드: 엔티티 -> DTO 변환
    // ==========================================
 
    // 대소문자, 구분자(-, 공백 등) 모두 허용 → DB 저장 형식(영문+숫자 대문자)으로 정규화
    private String normalizeFriendCode(String code) {
        return code == null ? "" : code.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
    }
 
    private String formatDisplayCode(String code) {
        if (code != null && code.length() > 4) {
            return code.substring(0, 4) + "-" + code.substring(4);
        }
        return code;
    }
 
    private FriendDto.FriendUser convertToUserDto(User user) {
        return FriendDto.FriendUser.builder()
                .userId(user.getUid())
                .nickname(user.getNickname())
                .friendCode(user.getFriendCode())
                .displayFriendCode(formatDisplayCode(user.getFriendCode()))
                .avatarAssetKey(null)
                .build();
    }
 
    private FriendDto.FriendRequest convertToRequestDto(FriendRequest request) {
        return FriendDto.FriendRequest.builder()
                .id("friend_request_" + request.getId())
                .fromUser(convertToUserDto(request.getFromUser()))
                .toUser(convertToUserDto(request.getToUser()))
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .respondedAt(request.getRespondedAt())
                .build();
    }
 
    private FriendDto.FriendSummary convertToFriendSummary(Friendship friendship) {
        return FriendDto.FriendSummary.builder()
                .friendshipId("friendship_" + friendship.getId())
                .user(convertToUserDto(friendship.getFriend()))
                .status(friendship.getStatus())
                .friendsSince(friendship.getFriendsSince())
                .build();
    }

    private FriendDto.FriendHomeInvitation convertToHomeInvitationDto(HomeInvitation invitation) {
        return FriendDto.FriendHomeInvitation.builder()
                .id("home_invitation_" + invitation.getId())
                .fromUser(convertToUserDto(invitation.getFromUser()))
                .toUser(convertToUserDto(invitation.getToUser()))
                .status(invitation.getStatus())
                .message(invitation.getMessage())
                .createdAt(invitation.getCreatedAt())
                .respondedAt(invitation.getRespondedAt())
                .expiresAt(invitation.getExpiresAt())
                .build();
    }

    private FriendDto.FriendPlacedItem convertToPlacedItemDto(RoomFurniture furniture) {
        TileFootprint footprint = footprintFor(furniture.getType());
        float anchorU = Math.max(0f, Math.min(1f, (furniture.getX() + 0.5f) / 10f));
        float anchorV = Math.max(0f, Math.min(1f, (furniture.getY() + 0.5f) / 10f));

        return FriendDto.FriendPlacedItem.builder()
                .placedItemId("placed_" + furniture.getId())
                .itemId(furniture.getType())
                .objectType(furniture.getType())
                .anchorType("FLOOR")
                .anchor(FriendDto.FriendAnchor.builder()
                        .u(anchorU)
                        .v(anchorV)
                        .build())
                .tile(FriendDto.FriendTile.builder()
                        .x(furniture.getX())
                        .y(furniture.getY())
                        .widthTiles(footprint.widthTiles())
                        .depthTiles(footprint.depthTiles())
                        .anchorMode("CENTER")
                        .build())
                .build();
    }

    private TileFootprint footprintFor(String type) {
        if (type == null) {
            return new TileFootprint(1, 1);
        }

        return switch (type.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "BED" -> new TileFootprint(2, 2);
            case "TOY_BOX", "FOOD_BAG", "FEED_BAG" -> new TileFootprint(1, 1);
            default -> new TileFootprint(1, 1);
        };
    }

    private record TileFootprint(int widthTiles, int depthTiles) {
    }

    private FriendDto.FriendPetSnapshot convertToPetSnapshotDto(Pet pet) {
        List<String> equippedItemIds = pet.getEquippedItemIds() == null
                ? Collections.emptyList()
                : pet.getEquippedItemIds().stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());

        return FriendDto.FriendPetSnapshot.builder()
                .petId(pet.getPetUid())
                .ownerUserId(pet.getUser().getUid())
                .name(pet.getName() == null || pet.getName().isBlank() ? "루파" : pet.getName())
                .characterAssetKey(pet.getCharacterAssetKey() == null ? "room/characters/lupa_default" : pet.getCharacterAssetKey())
                .appearance(FriendDto.FriendPetAppearanceSnapshot.builder()
                        .headSizeScale(1f)
                        .bodySizeScale(1f)
                        .eyeSizeScale(1f)
                        .noseSizeScale(1f)
                        .mouthSizeScale(1f)
                        .build())
                .condition(FriendDto.FriendPetConditionSnapshot.builder()
                        .satiety(pet.getSatiety())
                        .vitality(pet.getVitality())
                        .isEgg(pet.isEgg())
                        .build())
                .sceneState(FriendDto.FriendPetSceneStateSnapshot.builder()
                        .action("IDLE")
                        .anchor(FriendDto.FriendAnchor.builder()
                                .u(0.44f)
                                .v(0.64f)
                                .build())
                        .build())
                .personality(pet.getPersonality() == null ? "ACTIVE" : pet.getPersonality())
                .equippedItemIds(equippedItemIds)
                .build();
    }

    private FriendDto.FriendHomeSnapshot buildHomeSnapshot(User owner, LocalDateTime now) {
        Room room = roomRepository.findByOwnerUserId(owner.getUid())
                .orElseThrow(() -> new CustomApiException(ErrorCode.FRIEND_HOME_UNAVAILABLE));

        List<FriendDto.FriendPlacedItem> placedItems = roomFurnitureRepository.findByRoomId(room.getRoomId()).stream()
                .map(this::convertToPlacedItemDto)
                .collect(Collectors.toList());

        FriendDto.FriendPetSnapshot petSnapshot = petRepository.findByUserId(owner.getId())
                .map(this::convertToPetSnapshotDto)
                .orElse(null);

        return FriendDto.FriendHomeSnapshot.builder()
                .owner(convertToUserDto(owner))
                .room(FriendDto.FriendRoomSnapshot.builder()
                        .sceneId(room.getSceneId())
                        .wallAssetKey(room.getWallAssetKey())
                        .floorAssetKey(room.getFloorAssetKey())
                        .placedItems(placedItems)
                        .layoutRevision(room.getLayoutRevision())
                        .updatedAt(room.getUpdatedAt())
                        .build())
                .petSnapshot(petSnapshot)
                .snapshotAt(now)
                .visitedAt(now)
                .build();
    }

    private boolean markExpiredIfNeeded(HomeInvitation invitation, LocalDateTime now) {
        if (invitation.getStatus() == HomeInvitationStatus.PENDING
                && invitation.getExpiresAt() != null
                && invitation.getExpiresAt().isBefore(now)) {
            invitation.setStatus(HomeInvitationStatus.EXPIRED);
            invitation.setRespondedAt(now);
            return true;
        }
        return false;
    }

    private boolean markVisitExpiredIfNeeded(HomeVisitSession session, LocalDateTime now) {
        if (session.getStatus() == HomeVisitStatus.ACTIVE
                && session.getExpiresAt() != null
                && session.getExpiresAt().isBefore(now)) {
            session.setStatus(HomeVisitStatus.EXPIRED);
            session.setEndedAt(now);
            return true;
        }
        return false;
    }

    private boolean isHomeVisitParticipant(HomeVisitSession session, String currentUid) {
        return session.getHostUser().getUid().equals(currentUid)
                || session.getVisitorUser().getUid().equals(currentUid);
    }

    private HomeVisitSession getAuthorizedHomeVisitSession(String currentUid, Long sessionId) {
        HomeVisitSession session = homeVisitSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomApiException(ErrorCode.HOME_VISIT_NOT_FOUND));
        if (!isHomeVisitParticipant(session, currentUid)) {
            throw new CustomApiException(ErrorCode.NOT_HOME_VISIT_PARTICIPANT);
        }
        return session;
    }

    private HomeVisitSession getActiveHomeVisitSession(String currentUid, Long sessionId, LocalDateTime now) {
        HomeVisitSession session = getAuthorizedHomeVisitSession(currentUid, sessionId);
        if (session.getStatus() != HomeVisitStatus.ACTIVE || markVisitExpiredIfNeeded(session, now)) {
            throw new CustomApiException(ErrorCode.HOME_VISIT_NOT_ACTIVE);
        }
        return session;
    }

    private HomeVisitSession createOrReuseHomeVisitSession(HomeInvitation invitation, LocalDateTime now) {
        User host = invitation.getFromUser();
        User visitor = invitation.getToUser();
        HomeVisitSession existing = homeVisitSessionRepository
                .findByHostUserAndVisitorUserAndStatus(host, visitor, HomeVisitStatus.ACTIVE)
                .orElse(null);

        if (existing != null && !markVisitExpiredIfNeeded(existing, now)) {
            return existing;
        }

        HomeVisitSession session = HomeVisitSession.builder()
                .invitation(invitation)
                .hostUser(host)
                .visitorUser(visitor)
                .status(HomeVisitStatus.ACTIVE)
                .startedAt(now)
                .expiresAt(now.plusHours(2))
                .build();
        return homeVisitSessionRepository.save(session);
    }

    private FriendDto.FriendHomeVisitSession convertToHomeVisitSessionDto(HomeVisitSession session, LocalDateTime now) {
        FriendDto.FriendPetSnapshot visitorPetSnapshot = petRepository.findByUserId(session.getVisitorUser().getId())
                .map(this::convertToPetSnapshotDto)
                .orElse(null);

        return FriendDto.FriendHomeVisitSession.builder()
                .id("home_visit_" + session.getId())
                .hostUser(convertToUserDto(session.getHostUser()))
                .visitorUser(convertToUserDto(session.getVisitorUser()))
                .status(session.getStatus())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .expiresAt(session.getExpiresAt())
                .hostHomeSnapshot(buildHomeSnapshot(session.getHostUser(), now))
                .visitorPetSnapshot(visitorPetSnapshot)
                .build();
    }

    private FriendDto.HomeVisitMessage convertToHomeVisitMessageDto(HomeVisitMessage message) {
        return FriendDto.HomeVisitMessage.builder()
                .id("home_visit_msg_" + message.getId())
                .visitSessionId("home_visit_" + message.getSession().getId())
                .senderUserId(message.getSenderUser().getUid())
                .text(message.getContent())
                .sentAt(message.getCreatedAt())
                .build();
    }
 
    @Transactional(readOnly = true)
    public FriendDto.MyFriendCodeResponse getMyFriendCode(String currentUid) {
        User user = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
        return new FriendDto.MyFriendCodeResponse(user.getFriendCode(), formatDisplayCode(user.getFriendCode()));
    }
 
    @Transactional(readOnly = true)
    public FriendDto.FriendUserLookupResponse lookupUserByCode(String currentUid, String targetCode) {
        User currentUser = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
 
        // 빈 코드 체크 → 정규화 후 검사
        String normalizedCode = normalizeFriendCode(targetCode);
        if (normalizedCode.isEmpty()) throw new CustomApiException(ErrorCode.EMPTY_CODE);
 
        User targetUser = userRepository.findByFriendCode(normalizedCode)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
 
        if (currentUser.equals(targetUser)) throw new CustomApiException(ErrorCode.SELF_CODE);
 
        FriendshipStatus status = FriendshipStatus.NONE;
        if (friendshipRepository.existsByUserAndFriend(currentUser, targetUser)) {
            status = FriendshipStatus.ACCEPTED;
        } else if (requestRepository.existsByFromUserAndToUserAndStatus(currentUser, targetUser, FriendRequestStatus.PENDING)) {
            status = FriendshipStatus.PENDING_SENT;
        } else if (requestRepository.existsByFromUserAndToUserAndStatus(targetUser, currentUser, FriendRequestStatus.PENDING)) {
            status = FriendshipStatus.PENDING_RECEIVED;
        }
 
        return new FriendDto.FriendUserLookupResponse(convertToUserDto(targetUser), status);
    }
 
    @Transactional
    public FriendDto.SingleRequestResponse sendFriendRequest(String currentUid, FriendDto.SendRequest dto) {
        User fromUser = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
 
        // 빈 코드 체크 → 정규화 후 검사
        String normalizedCode = normalizeFriendCode(dto.getFriendCode());
        if (normalizedCode.isEmpty()) throw new CustomApiException(ErrorCode.EMPTY_CODE);
 
        User toUser = userRepository.findByFriendCode(normalizedCode)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
 
        if (fromUser.equals(toUser)) throw new CustomApiException(ErrorCode.SELF_CODE);
        if (friendshipRepository.existsByUserAndFriend(fromUser, toUser)) throw new CustomApiException(ErrorCode.ALREADY_FRIENDS);
        if (requestRepository.existsByFromUserAndToUserAndStatus(fromUser, toUser, FriendRequestStatus.PENDING))
            throw new CustomApiException(ErrorCode.REQUEST_ALREADY_SENT);
        if (requestRepository.existsByFromUserAndToUserAndStatus(toUser, fromUser, FriendRequestStatus.PENDING))
            throw new CustomApiException(ErrorCode.REQUEST_ALREADY_RECEIVED);
 
        FriendRequest request = FriendRequest.builder()
                .fromUser(fromUser)
                .toUser(toUser)
                .status(FriendRequestStatus.PENDING)
                .build();
        requestRepository.save(request);
        return new FriendDto.SingleRequestResponse(convertToRequestDto(request));
    }
 
    @Transactional
    public FriendDto.AcceptRequestResponse acceptFriendRequest(String currentUid, Long requestId) {
        FriendRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new CustomApiException(ErrorCode.REQUEST_NOT_FOUND));
 
        if (!request.getToUser().getUid().equals(currentUid)) throw new CustomApiException(ErrorCode.BLOCKED);
        if (request.getStatus() != FriendRequestStatus.PENDING) throw new CustomApiException(ErrorCode.REQUEST_NOT_PENDING);
 
        request.setStatus(FriendRequestStatus.ACCEPTED);
        request.setRespondedAt(LocalDateTime.now());
 
        Friendship friendship1 = Friendship.builder().user(request.getFromUser()).friend(request.getToUser()).status(FriendshipStatus.ACCEPTED).friendsSince(LocalDateTime.now()).build();
        Friendship friendship2 = Friendship.builder().user(request.getToUser()).friend(request.getFromUser()).status(FriendshipStatus.ACCEPTED).friendsSince(LocalDateTime.now()).build();
 
        friendshipRepository.save(friendship1);
        friendshipRepository.save(friendship2);
 
        return new FriendDto.AcceptRequestResponse(convertToRequestDto(request), convertToFriendSummary(friendship2));
    }
 
    @Transactional
    public FriendDto.SingleRequestResponse rejectFriendRequest(String currentUid, Long requestId) {
        FriendRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new CustomApiException(ErrorCode.REQUEST_NOT_FOUND));
 
        if (!request.getToUser().getUid().equals(currentUid)) throw new CustomApiException(ErrorCode.BLOCKED);
        if (request.getStatus() != FriendRequestStatus.PENDING) throw new CustomApiException(ErrorCode.REQUEST_NOT_PENDING);
 
        request.setStatus(FriendRequestStatus.REJECTED);
        request.setRespondedAt(LocalDateTime.now());
        return new FriendDto.SingleRequestResponse(convertToRequestDto(request));
    }
 
    @Transactional
    public FriendDto.SingleRequestResponse cancelFriendRequest(String currentUid, Long requestId) {
        FriendRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new CustomApiException(ErrorCode.REQUEST_NOT_FOUND));
 
        if (!request.getFromUser().getUid().equals(currentUid)) throw new CustomApiException(ErrorCode.BLOCKED);
        if (request.getStatus() != FriendRequestStatus.PENDING) throw new CustomApiException(ErrorCode.REQUEST_NOT_PENDING);
 
        request.setStatus(FriendRequestStatus.CANCELED);
        request.setRespondedAt(LocalDateTime.now());
        return new FriendDto.SingleRequestResponse(convertToRequestDto(request));
    }
 
    @Transactional(readOnly = true)
    public FriendDto.FriendRequestListResponse getReceivedRequests(String currentUid) {
        User me = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
        List<FriendRequest> requests = requestRepository.findByToUserAndStatus(me, FriendRequestStatus.PENDING);
        return new FriendDto.FriendRequestListResponse(requests.stream().map(this::convertToRequestDto).collect(Collectors.toList()));
    }
 
    @Transactional(readOnly = true)
    public FriendDto.FriendRequestListResponse getSentRequests(String currentUid) {
        User me = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
        List<FriendRequest> requests = requestRepository.findByFromUserAndStatus(me, FriendRequestStatus.PENDING);
        return new FriendDto.FriendRequestListResponse(requests.stream().map(this::convertToRequestDto).collect(Collectors.toList()));
    }
 
    @Transactional(readOnly = true)
    public FriendDto.FriendListResponse getFriendList(String currentUid) {
        User me = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
        List<Friendship> friendships = friendshipRepository.findByUserAndStatus(me, FriendshipStatus.ACCEPTED);
        return new FriendDto.FriendListResponse(friendships.stream().map(this::convertToFriendSummary).collect(Collectors.toList()));
    }

    @Transactional
    public FriendDto.SingleHomeInvitationResponse sendHomeInvitation(
            String currentUid,
            FriendDto.SendHomeInvitationRequest request
    ) {
        User sender = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));

        if (request == null) {
            throw new CustomApiException(ErrorCode.FRIEND_NOT_FOUND);
        }
        String friendUserId = request.getFriendUserId();
        if (friendUserId == null || friendUserId.isBlank()) {
            throw new CustomApiException(ErrorCode.FRIEND_NOT_FOUND);
        }

        User receiver = userRepository.findByUid(friendUserId)
                .orElseThrow(() -> new CustomApiException(ErrorCode.FRIEND_NOT_FOUND));

        if (sender.getUid().equals(receiver.getUid())) {
            throw new CustomApiException(ErrorCode.SELF_CODE);
        }
        if (!friendshipRepository.existsByUserAndFriend(sender, receiver)) {
            throw new CustomApiException(ErrorCode.NOT_FRIENDS);
        }

        LocalDateTime now = LocalDateTime.now();
        HomeVisitSession activeVisit = homeVisitSessionRepository
                .findByHostUserAndVisitorUserAndStatus(sender, receiver, HomeVisitStatus.ACTIVE)
                .orElse(null);
        if (activeVisit != null && !markVisitExpiredIfNeeded(activeVisit, now)) {
            throw new CustomApiException(ErrorCode.HOME_VISIT_ALREADY_ACTIVE);
        }

        HomeInvitation existing = homeInvitationRepository
                .findByFromUserAndToUserAndStatus(sender, receiver, HomeInvitationStatus.PENDING)
                .orElse(null);
        if (existing != null && !markExpiredIfNeeded(existing, now)) {
            throw new CustomApiException(ErrorCode.HOME_INVITATION_ALREADY_SENT);
        }

        String message = request.getMessage();
        HomeInvitation invitation = HomeInvitation.builder()
                .fromUser(sender)
                .toUser(receiver)
                .message(message == null || message.isBlank() ? null : message.trim())
                .status(HomeInvitationStatus.PENDING)
                .createdAt(now)
                .expiresAt(now.plusHours(24))
                .build();

        homeInvitationRepository.save(invitation);
        return new FriendDto.SingleHomeInvitationResponse(convertToHomeInvitationDto(invitation));
    }

    @Transactional
    public FriendDto.HomeInvitationListResponse getReceivedHomeInvitations(String currentUid) {
        User me = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();

        List<FriendDto.FriendHomeInvitation> invitations = homeInvitationRepository
                .findByToUserAndStatusOrderByCreatedAtDesc(me, HomeInvitationStatus.PENDING)
                .stream()
                .filter(invitation -> !markExpiredIfNeeded(invitation, now))
                .map(this::convertToHomeInvitationDto)
                .collect(Collectors.toList());

        return new FriendDto.HomeInvitationListResponse(invitations);
    }

    @Transactional
    public FriendDto.HomeInvitationListResponse getSentHomeInvitations(String currentUid) {
        User me = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();

        List<FriendDto.FriendHomeInvitation> invitations = homeInvitationRepository
                .findByFromUserAndStatusOrderByCreatedAtDesc(me, HomeInvitationStatus.PENDING)
                .stream()
                .filter(invitation -> !markExpiredIfNeeded(invitation, now))
                .map(this::convertToHomeInvitationDto)
                .collect(Collectors.toList());

        return new FriendDto.HomeInvitationListResponse(invitations);
    }

    @Transactional
    public FriendDto.AcceptHomeInvitationResponse acceptHomeInvitation(String currentUid, Long invitationId) {
        HomeInvitation invitation = homeInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new CustomApiException(ErrorCode.HOME_INVITATION_NOT_FOUND));

        if (!invitation.getToUser().getUid().equals(currentUid)) {
            throw new CustomApiException(ErrorCode.NOT_HOME_INVITATION_RECEIVER);
        }

        LocalDateTime now = LocalDateTime.now();
        if (invitation.getStatus() != HomeInvitationStatus.PENDING || markExpiredIfNeeded(invitation, now)) {
            throw new CustomApiException(ErrorCode.HOME_INVITATION_NOT_PENDING);
        }
        if (!friendshipRepository.existsByUserAndFriend(invitation.getToUser(), invitation.getFromUser())) {
            throw new CustomApiException(ErrorCode.NOT_FRIENDS);
        }

        invitation.setStatus(HomeInvitationStatus.ACCEPTED);
        invitation.setRespondedAt(now);
        HomeVisitSession visitSession = createOrReuseHomeVisitSession(invitation, now);

        return new FriendDto.AcceptHomeInvitationResponse(
                convertToHomeInvitationDto(invitation),
                buildHomeSnapshot(invitation.getFromUser(), now),
                convertToHomeVisitSessionDto(visitSession, now)
        );
    }

    @Transactional
    public FriendDto.SingleHomeInvitationResponse rejectHomeInvitation(String currentUid, Long invitationId) {
        HomeInvitation invitation = homeInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new CustomApiException(ErrorCode.HOME_INVITATION_NOT_FOUND));

        if (!invitation.getToUser().getUid().equals(currentUid)) {
            throw new CustomApiException(ErrorCode.NOT_HOME_INVITATION_RECEIVER);
        }

        LocalDateTime now = LocalDateTime.now();
        if (invitation.getStatus() != HomeInvitationStatus.PENDING || markExpiredIfNeeded(invitation, now)) {
            throw new CustomApiException(ErrorCode.HOME_INVITATION_NOT_PENDING);
        }

        invitation.setStatus(HomeInvitationStatus.REJECTED);
        invitation.setRespondedAt(now);
        return new FriendDto.SingleHomeInvitationResponse(convertToHomeInvitationDto(invitation));
    }

    @Transactional
    public FriendDto.SingleHomeInvitationResponse cancelHomeInvitation(String currentUid, Long invitationId) {
        HomeInvitation invitation = homeInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new CustomApiException(ErrorCode.HOME_INVITATION_NOT_FOUND));

        if (!invitation.getFromUser().getUid().equals(currentUid)) {
            throw new CustomApiException(ErrorCode.NOT_HOME_INVITATION_SENDER);
        }

        LocalDateTime now = LocalDateTime.now();
        if (invitation.getStatus() != HomeInvitationStatus.PENDING || markExpiredIfNeeded(invitation, now)) {
            throw new CustomApiException(ErrorCode.HOME_INVITATION_NOT_PENDING);
        }

        invitation.setStatus(HomeInvitationStatus.CANCELED);
        invitation.setRespondedAt(now);
        return new FriendDto.SingleHomeInvitationResponse(convertToHomeInvitationDto(invitation));
    }

    @Transactional
    public FriendDto.ActiveHomeVisitsResponse getActiveHomeVisits(String currentUid) {
        User me = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();

        List<FriendDto.FriendHomeVisitSession> hosting = homeVisitSessionRepository
                .findByHostUserAndStatusOrderByStartedAtDesc(me, HomeVisitStatus.ACTIVE)
                .stream()
                .filter(session -> !markVisitExpiredIfNeeded(session, now))
                .map(session -> convertToHomeVisitSessionDto(session, now))
                .collect(Collectors.toList());

        List<FriendDto.FriendHomeVisitSession> visiting = homeVisitSessionRepository
                .findByVisitorUserAndStatusOrderByStartedAtDesc(me, HomeVisitStatus.ACTIVE)
                .stream()
                .filter(session -> !markVisitExpiredIfNeeded(session, now))
                .map(session -> convertToHomeVisitSessionDto(session, now))
                .collect(Collectors.toList());

        return new FriendDto.ActiveHomeVisitsResponse(hosting, visiting);
    }

    @Transactional
    public FriendDto.SingleHomeVisitSessionResponse leaveHomeVisit(String currentUid, Long sessionId) {
        LocalDateTime now = LocalDateTime.now();
        HomeVisitSession session = getActiveHomeVisitSession(currentUid, sessionId, now);
        session.setStatus(HomeVisitStatus.ENDED);
        session.setEndedAt(now);

        return new FriendDto.SingleHomeVisitSessionResponse(convertToHomeVisitSessionDto(session, now));
    }

    @Transactional(readOnly = true)
    public FriendDto.HomeVisitMessagesResponse getHomeVisitMessages(String currentUid, Long sessionId) {
        HomeVisitSession session = getAuthorizedHomeVisitSession(currentUid, sessionId);
        List<FriendDto.HomeVisitMessage> messages = homeVisitMessageRepository
                .findBySessionOrderByCreatedAtAsc(session)
                .stream()
                .map(this::convertToHomeVisitMessageDto)
                .collect(Collectors.toList());

        return new FriendDto.HomeVisitMessagesResponse(messages, null);
    }

    @Transactional
    public FriendDto.SingleHomeVisitMessageResponse sendHomeVisitMessage(
            String currentUid,
            Long sessionId,
            FriendDto.SendHomeVisitMessageRequest request
    ) {
        LocalDateTime now = LocalDateTime.now();
        HomeVisitSession session = getActiveHomeVisitSession(currentUid, sessionId, now);
        User sender = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));

        String text = request == null ? null : request.getText();
        if (text == null || text.isBlank()) {
            throw new CustomApiException(ErrorCode.EMPTY_MESSAGE);
        }
        String trimmedText = text.trim();
        if (trimmedText.length() > MAX_MESSAGE_LENGTH) {
            throw new CustomApiException(ErrorCode.MESSAGE_TOO_LONG);
        }

        HomeVisitMessage message = HomeVisitMessage.builder()
                .session(session)
                .senderUser(sender)
                .content(trimmedText)
                .build();
        homeVisitMessageRepository.save(message);

        return new FriendDto.SingleHomeVisitMessageResponse(convertToHomeVisitMessageDto(message));
    }
 
    @Transactional
    public void deleteFriend(String currentUid, String friendUserId) {
        User me = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
        User friend = userRepository.findByUid(friendUserId)
                .orElseThrow(() -> new CustomApiException(ErrorCode.FRIEND_NOT_FOUND));
 
        Friendship myFriendship = friendshipRepository.findByUserAndFriend(me, friend)
                .orElseThrow(() -> new CustomApiException(ErrorCode.NOT_FRIENDS));
        Friendship friendFriendship = friendshipRepository.findByUserAndFriend(friend, me).orElse(null);
 
        friendshipRepository.delete(myFriendship);
        if (friendFriendship != null) {
            friendshipRepository.delete(friendFriendship);
        }
    }
 
    @Transactional
    public FriendDto.SingleMessageResponse sendMessage(String currentUid, String friendUserId, FriendDto.MessageRequest request) {
        User sender = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
        User receiver = userRepository.findByUid(friendUserId)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
 
        if (!friendshipRepository.existsByUserAndFriend(sender, receiver)) throw new CustomApiException(ErrorCode.NOT_FRIENDS);
 
        // 메시지 내용 검증
        String text = request == null ? null : request.getText();
        if (text == null || text.isBlank()) throw new CustomApiException(ErrorCode.EMPTY_MESSAGE);
        String trimmedText = text.trim();
        if (trimmedText.length() > MAX_MESSAGE_LENGTH) throw new CustomApiException(ErrorCode.MESSAGE_TOO_LONG);
 
        FriendMessage message = FriendMessage.builder()
                .fromUser(sender)
                .toUser(receiver)
                .content(trimmedText)
                .build();
        messageRepository.save(message);
 
        return new FriendDto.SingleMessageResponse(FriendDto.FriendMessage.builder()
                .id("msg_" + message.getId())
                .friendUserId(receiver.getUid())
                .senderUserId(sender.getUid())
                .text(message.getContent())
                .sentAt(message.getCreatedAt())
                .build());
    }
 
    @Transactional(readOnly = true)
    public FriendDto.FriendMessagesResponse getMessages(String currentUid, String friendUserId) {
        User me = userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
        User friend = userRepository.findByUid(friendUserId)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
 
        if (!friendshipRepository.existsByUserAndFriend(me, friend)) {
            throw new CustomApiException(ErrorCode.NOT_FRIENDS);
        }

        List<FriendDto.FriendMessage> msgs = messageRepository.findConversation(me, friend).stream()
                .map(m -> FriendDto.FriendMessage.builder()
                        .id("msg_" + m.getId())
                        .friendUserId(friend.getUid())
                        .senderUserId(m.getFromUser().getUid())
                        .text(m.getContent())
                        .sentAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
 
        return new FriendDto.FriendMessagesResponse(msgs, null);
    }
}
