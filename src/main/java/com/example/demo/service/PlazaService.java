package com.example.demo.service;

import com.example.demo.dto.PlazaDto;
import com.example.demo.exception.CustomApiException;
import com.example.demo.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlazaService {
    private static final int MAX_PARTICIPANTS = 4;
    private static final int MAX_MESSAGE_LENGTH = 120;
    private static final long PLAZA_MOVE_CYCLE_MS = 5_000L;
    private static final long PLAZA_MOVE_DURATION_MS = 2_000L;
    private static final long PLAZA_INTERACTION_CYCLE_MS = 12_000L;
    private static final long PLAZA_INTERACTION_DURATION_MS = 4_800L;
    private static final long PLAZA_INTERACTION_APPROACH_MS = 1_200L;
    private static final long PLAZA_INTERACTION_SETTLE_MS = 1_200L;
    private static final String DEFAULT_PLAZA_PET_NAME = "루파";
    private static final String DEFAULT_PLAZA_CHARACTER_ASSET_KEY = "room/characters/lupa_default";
    private static final float PLAZA_MIN_X = 0.16f;
    private static final float PLAZA_MAX_X = 0.84f;
    private static final float PLAZA_MIN_Y = 0.48f;
    private static final float PLAZA_MAX_Y = 0.86f;
    private static final String[] PLAZA_INTERACTION_TYPES = {
            "GREET",
            "PLAY",
            "REST",
            "FOLLOW"
    };

    private static final ConcurrentMap<String, MemoryPlaza> PLAZAS_BY_ID = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> PLAZA_ID_BY_CODE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> PLAZA_ID_BY_USER_ID = new ConcurrentHashMap<>();

    @Transactional
    public synchronized PlazaDto.PlazaRoomResponse joinRandomPlaza(String currentUid, String currentNickname) {
        MemoryPlaza targetPlaza = PLAZAS_BY_ID.values().stream()
                .filter(plaza -> plaza.participants.size() < MAX_PARTICIPANTS)
                .findFirst()
                .orElseGet(this::createNewPlaza);

        return joinTargetPlaza(currentUid, displayNickname(currentUid, currentNickname), targetPlaza);
    }

    @Transactional
    public synchronized PlazaDto.PlazaRoomResponse joinPlazaByCode(
            String currentUid,
            String currentNickname,
            String inputCode
    ) {
        if (inputCode == null || inputCode.trim().isEmpty()) {
            throw new CustomApiException(ErrorCode.EMPTY_CODE);
        }

        String pureCode = normalizePlazaCode(inputCode);
        MemoryPlaza targetPlaza = plazaByCode(pureCode);
        if (targetPlaza == null) {
            throw new CustomApiException(ErrorCode.PLAZA_NOT_FOUND);
        }

        boolean alreadyInTarget = targetPlaza.participants.containsKey(currentUid);
        if (!alreadyInTarget && targetPlaza.participants.size() >= MAX_PARTICIPANTS) {
            throw new CustomApiException(ErrorCode.PLAZA_FULL);
        }

        return joinTargetPlaza(currentUid, displayNickname(currentUid, currentNickname), targetPlaza);
    }

    @Transactional(readOnly = true)
    public synchronized PlazaDto.PlazaRoomResponse getCurrentPlaza(String currentUid) {
        MemoryPlaza plaza = currentPlazaForUser(currentUid);
        if (plaza == null) {
            return PlazaDto.PlazaRoomResponse.builder()
                    .plaza(null)
                    .build();
        }

        return toRoomResponse(plaza);
    }

    @Transactional(readOnly = true)
    public synchronized PlazaDto.PlazaRoomResponse getPlazaSnapshot(String plazaId, String currentUid) {
        MemoryPlaza plaza = PLAZAS_BY_ID.get(plazaId);
        if (plaza == null) {
            throw new CustomApiException(ErrorCode.PLAZA_NOT_FOUND);
        }

        if (!plaza.participants.containsKey(currentUid)) {
            throw new CustomApiException(ErrorCode.NOT_IN_PLAZA);
        }

        return toRoomResponse(plaza);
    }

    @Transactional
    public synchronized void leavePlaza(String plazaId, String currentUid) {
        MemoryPlaza plaza = currentPlazaForUser(currentUid);
        if (plaza == null || !plaza.plazaId.equals(plazaId)) {
            throw new CustomApiException(ErrorCode.NOT_IN_PLAZA);
        }

        MemoryParticipant participant = plaza.participants.remove(currentUid);
        if (participant == null) {
            PLAZA_ID_BY_USER_ID.remove(currentUid, plazaId);
            throw new CustomApiException(ErrorCode.NOT_IN_PLAZA);
        }

        PLAZA_ID_BY_USER_ID.remove(currentUid, plazaId);
        if (plaza.participants.isEmpty()) {
            removePlaza(plaza);
            return;
        }

        incrementRevision(plaza);
    }

    @Transactional
    public synchronized PlazaDto.PlazaChatMessageEnvelopeResponse sendPlazaMessage(
            String plazaId,
            String currentUid,
            String text
    ) {
        MemoryPlaza plaza = PLAZAS_BY_ID.get(plazaId);
        MemoryParticipant participant = plaza == null ? null : plaza.participants.get(currentUid);
        if (plaza == null || participant == null) {
            throw new CustomApiException(ErrorCode.NOT_IN_PLAZA);
        }

        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new CustomApiException(ErrorCode.EMPTY_MESSAGE);
        }
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new CustomApiException(ErrorCode.MESSAGE_TOO_LONG);
        }

        MemoryChatMessage message = new MemoryChatMessage(
                "plaza_message_" + UUID.randomUUID().toString().substring(0, 8),
                plazaId,
                currentUid,
                participant.nickname(),
                trimmed,
                System.currentTimeMillis()
        );
        plaza.messages.add(message);
        while (plaza.messages.size() > 50) {
            plaza.messages.remove(0);
        }
        incrementRevision(plaza);

        return PlazaDto.PlazaChatMessageEnvelopeResponse.builder()
                .message(toMessageResponse(message))
                .roomRevision(plaza.roomRevision)
                .build();
    }

    private PlazaDto.PlazaRoomResponse joinTargetPlaza(
            String currentUid,
            String nickname,
            MemoryPlaza targetPlaza
    ) {
        MemoryPlaza oldPlaza = currentPlazaForUser(currentUid);
        if (oldPlaza != null && !oldPlaza.plazaId.equals(targetPlaza.plazaId)) {
            oldPlaza.participants.remove(currentUid);
            if (oldPlaza.participants.isEmpty()) {
                removePlaza(oldPlaza);
            } else {
                incrementRevision(oldPlaza);
            }
        }

        if (!targetPlaza.participants.containsKey(currentUid)) {
            long nowMillis = System.currentTimeMillis();
            MemoryParticipant participant = new MemoryParticipant(
                    currentUid,
                    nickname,
                    "plaza_pet_" + currentUid,
                    DEFAULT_PLAZA_PET_NAME,
                    DEFAULT_PLAZA_CHARACTER_ASSET_KEY,
                    nowMillis
            );
            targetPlaza.participants.put(currentUid, participant);
            incrementRevision(targetPlaza);
        }

        PLAZA_ID_BY_USER_ID.put(currentUid, targetPlaza.plazaId);
        return toRoomResponse(targetPlaza);
    }

    private PlazaDto.PlazaRoomResponse toRoomResponse(MemoryPlaza plaza) {
        long nowMillis = System.currentTimeMillis();
        List<MemoryParticipant> participants = plaza.participants.values().stream()
                .sorted(Comparator.comparing(
                        MemoryParticipant::userId,
                        Comparator.nullsLast(String::compareTo)
                ))
                .collect(Collectors.toList());
        InteractionSnapshot interactionSnapshot = buildInteractionSnapshot(plaza, participants, nowMillis);

        PlazaDto.PlazaDetail detail = PlazaDto.PlazaDetail.builder()
                .plazaId(plaza.plazaId)
                .plazaCode("PZ" + plaza.plazaCode)
                .displayPlazaCode("PZ-" + plaza.plazaCode)
                .serverNowMillis(nowMillis)
                .participants(participants.stream()
                        .map(participant -> toParticipantResponse(plaza, participant, nowMillis, interactionSnapshot))
                        .collect(Collectors.toList()))
                .messages(plaza.messages.stream()
                        .map(this::toMessageResponse)
                        .collect(Collectors.toList()))
                .interactions(interactionSnapshot.responses())
                .maxParticipants(MAX_PARTICIPANTS)
                .roomRevision(plaza.roomRevision)
                .build();

        return PlazaDto.PlazaRoomResponse.builder()
                .plaza(detail)
                .build();
    }

    private PlazaDto.PlazaParticipantResponse toParticipantResponse(
            MemoryPlaza plaza,
            MemoryParticipant participant,
            long nowMillis,
            InteractionSnapshot interactionSnapshot
    ) {
        String participantUserId = participantKey(participant);
        MovementSnapshot movementSnapshot = participantMovementSnapshot(
                plaza.plazaId,
                participantUserId,
                nowMillis,
                interactionSnapshot
        );

        return PlazaDto.PlazaParticipantResponse.builder()
                .userId(participantUserId)
                .nickname(participant.nickname())
                .pet(PlazaDto.PlazaPetSnapshotResponse.builder()
                        .petId(participant.petId())
                        .name(participant.petName())
                        .characterAssetKey(participant.characterAssetKey())
                        .appearance(PlazaDto.PetAppearanceResponse.defaultAppearance())
                        .build())
                .position(toPositionResponse(movementSnapshot.current()))
                .movement(movementSnapshot.movement())
                .positionUpdatedAtMillis(movementSnapshot.updatedAtMillis())
                .joinedAtMillis(participant.joinedAtMillis())
                .build();
    }

    private InteractionSnapshot buildInteractionSnapshot(
            MemoryPlaza plaza,
            List<MemoryParticipant> participants,
            long nowMillis
    ) {
        if (participants.size() < 2) {
            return InteractionSnapshot.empty();
        }

        long elapsedInCycle = Math.floorMod(nowMillis, PLAZA_INTERACTION_CYCLE_MS);
        if (elapsedInCycle >= PLAZA_INTERACTION_DURATION_MS + PLAZA_INTERACTION_SETTLE_MS) {
            return InteractionSnapshot.empty();
        }

        long slotIndex = Math.floorDiv(nowMillis, PLAZA_INTERACTION_CYCLE_MS);
        long startedAtMillis = slotIndex * PLAZA_INTERACTION_CYCLE_MS;
        int actorIndex = stableIndex(plaza.plazaId, "interaction-actor", slotIndex, participants.size());
        int targetOffset = 1 + stableIndex(
                plaza.plazaId,
                "interaction-target-offset",
                slotIndex,
                participants.size() - 1
        );
        int targetIndex = (actorIndex + targetOffset) % participants.size();
        MemoryParticipant actor = participants.get(actorIndex);
        MemoryParticipant target = participants.get(targetIndex);
        String actorUserId = participantKey(actor);
        String targetUserId = participantKey(target);
        String type = PLAZA_INTERACTION_TYPES[stableIndex(
                plaza.plazaId,
                "interaction-type",
                slotIndex,
                PLAZA_INTERACTION_TYPES.length
        )];

        Position actorPosition = deterministicMovementSnapshot(
                plaza.plazaId,
                actorUserId,
                startedAtMillis
        ).current();
        Position targetPosition = deterministicMovementSnapshot(
                plaza.plazaId,
                targetUserId,
                startedAtMillis
        ).current();
        Map<String, Position> movementTargets = interactionMovementTargets(
                type,
                actorUserId,
                targetUserId,
                actorPosition,
                targetPosition,
                slotIndex
        );
        Map<String, Position> facingTargets = interactionFacingTargets(
                actorUserId,
                targetUserId,
                movementTargets
        );
        List<PlazaDto.PlazaInteractionResponse> responses = elapsedInCycle < PLAZA_INTERACTION_DURATION_MS
                ? List.of(PlazaDto.PlazaInteractionResponse.builder()
                        .id("plaza_interaction_" + plaza.plazaId + "_" + slotIndex)
                        .type(type)
                        .actorUserId(actorUserId)
                        .targetUserId(targetUserId)
                        .textByUserId(interactionTextByUserId(type, actorUserId, targetUserId, slotIndex))
                        .startedAtMillis(startedAtMillis)
                        .durationMillis(PLAZA_INTERACTION_DURATION_MS)
                        .movementTargetByUserId(toPositionResponseMap(movementTargets))
                        .facingTargetByUserId(toPositionResponseMap(facingTargets))
                        .animationByUserId(interactionAnimations(type, actorUserId, targetUserId))
                        .build())
                : Collections.emptyList();

        return new InteractionSnapshot(
                startedAtMillis,
                PLAZA_INTERACTION_DURATION_MS,
                PLAZA_INTERACTION_SETTLE_MS,
                movementTargets,
                responses
        );
    }

    private MovementSnapshot participantMovementSnapshot(
            String plazaId,
            String userId,
            long nowMillis,
            InteractionSnapshot interactionSnapshot
    ) {
        Position interactionTarget = interactionSnapshot.movementTargetFor(userId);
        if (interactionTarget == null) {
            return deterministicMovementSnapshot(plazaId, userId, nowMillis);
        }

        long interactionStartedAtMillis = interactionSnapshot.startedAtMillis();
        long interactionEndedAtMillis = interactionSnapshot.activeUntilMillis();
        Position interactionStart = deterministicMovementSnapshot(
                plazaId,
                userId,
                interactionStartedAtMillis
        ).current();

        if (nowMillis <= interactionEndedAtMillis) {
            long approachDurationMillis = Math.min(
                    PLAZA_INTERACTION_APPROACH_MS,
                    interactionSnapshot.durationMillis()
            );
            float progress = (float) (nowMillis - interactionStartedAtMillis) / approachDurationMillis;
            Position current = interpolate(interactionStart, interactionTarget, progress);
            return new MovementSnapshot(
                    current,
                    PlazaDto.PlazaMovementResponse.builder()
                            .from(toPositionResponse(interactionStart))
                            .to(toPositionResponse(interactionTarget))
                            .startedAtMillis(interactionStartedAtMillis)
                            .durationMillis(approachDurationMillis)
                            .build(),
                    interactionStartedAtMillis
            );
        }

        long settleEndedAtMillis = interactionSnapshot.settleUntilMillis();
        if (nowMillis < settleEndedAtMillis) {
            Position settleTarget = deterministicMovementSnapshot(
                    plazaId,
                    userId,
                    settleEndedAtMillis
            ).current();
            float progress = (float) (nowMillis - interactionEndedAtMillis) /
                    interactionSnapshot.settleDurationMillis();
            Position current = interpolate(interactionTarget, settleTarget, progress);
            return new MovementSnapshot(
                    current,
                    PlazaDto.PlazaMovementResponse.builder()
                            .from(toPositionResponse(interactionTarget))
                            .to(toPositionResponse(settleTarget))
                            .startedAtMillis(interactionEndedAtMillis)
                            .durationMillis(interactionSnapshot.settleDurationMillis())
                            .build(),
                    interactionEndedAtMillis
            );
        }

        return deterministicMovementSnapshot(plazaId, userId, nowMillis);
    }

    private MovementSnapshot deterministicMovementSnapshot(
            String plazaId,
            String userId,
            long nowMillis
    ) {
        long slotIndex = Math.floorDiv(nowMillis, PLAZA_MOVE_CYCLE_MS);
        long slotStartMillis = slotIndex * PLAZA_MOVE_CYCLE_MS;
        long elapsedMillis = nowMillis - slotStartMillis;
        Position from = deterministicPosition(plazaId, userId, slotIndex);
        Position to = deterministicPosition(plazaId, userId, slotIndex + 1);

        if (elapsedMillis < PLAZA_MOVE_DURATION_MS) {
            float progress = (float) elapsedMillis / PLAZA_MOVE_DURATION_MS;
            Position current = interpolate(from, to, progress);
            return new MovementSnapshot(
                    current,
                    PlazaDto.PlazaMovementResponse.builder()
                            .from(toPositionResponse(from))
                            .to(toPositionResponse(to))
                            .startedAtMillis(slotStartMillis)
                            .durationMillis(PLAZA_MOVE_DURATION_MS)
                            .build(),
                    slotStartMillis
            );
        }

        return new MovementSnapshot(to, null, slotStartMillis + PLAZA_MOVE_DURATION_MS);
    }

    private Position deterministicPosition(String plazaId, String userId, long slotIndex) {
        float x = PLAZA_MIN_X + stableUnit(plazaId, userId, "x", slotIndex) * (PLAZA_MAX_X - PLAZA_MIN_X);
        float y = PLAZA_MIN_Y + stableUnit(plazaId, userId, "y", slotIndex) * (PLAZA_MAX_Y - PLAZA_MIN_Y);
        return new Position(clamp(x, PLAZA_MIN_X, PLAZA_MAX_X), clamp(y, PLAZA_MIN_Y, PLAZA_MAX_Y));
    }

    private Map<String, Position> interactionMovementTargets(
            String type,
            String actorUserId,
            String targetUserId,
            Position actorPosition,
            Position targetPosition,
            long slotIndex
    ) {
        Position center = midpoint(actorPosition, targetPosition);
        float xOffset = type.equals("FOLLOW") ? 0.09f : 0.055f;
        float yOffset = type.equals("PLAY")
                ? (stableIndex(actorUserId, targetUserId, slotIndex, 2) == 0 ? -0.045f : 0.045f)
                : 0f;

        Position actorTarget;
        Position targetTarget;
        if (type.equals("FOLLOW")) {
            float direction = stableIndex(actorUserId, "follow-direction", slotIndex, 2) == 0 ? -1f : 1f;
            actorTarget = new Position(
                    clamp(actorPosition.x() + xOffset * direction, PLAZA_MIN_X, PLAZA_MAX_X),
                    clamp(actorPosition.y() + yOffset, PLAZA_MIN_Y, PLAZA_MAX_Y)
            );
            targetTarget = new Position(
                    clamp(actorTarget.x() - 0.065f * direction, PLAZA_MIN_X, PLAZA_MAX_X),
                    clamp(actorTarget.y() + 0.025f, PLAZA_MIN_Y, PLAZA_MAX_Y)
            );
        } else {
            actorTarget = new Position(
                    clamp(center.x() - xOffset, PLAZA_MIN_X, PLAZA_MAX_X),
                    clamp(center.y() + yOffset, PLAZA_MIN_Y, PLAZA_MAX_Y)
            );
            targetTarget = new Position(
                    clamp(center.x() + xOffset, PLAZA_MIN_X, PLAZA_MAX_X),
                    clamp(center.y() - yOffset, PLAZA_MIN_Y, PLAZA_MAX_Y)
            );
        }

        Map<String, Position> targets = new LinkedHashMap<>();
        targets.put(actorUserId, actorTarget);
        targets.put(targetUserId, targetTarget);
        return targets;
    }

    private Map<String, Position> interactionFacingTargets(
            String actorUserId,
            String targetUserId,
            Map<String, Position> movementTargets
    ) {
        Position actorTarget = movementTargets.get(actorUserId);
        Position targetTarget = movementTargets.get(targetUserId);
        if (actorTarget == null || targetTarget == null) {
            return Collections.emptyMap();
        }

        Map<String, Position> facingTargets = new LinkedHashMap<>();
        facingTargets.put(actorUserId, targetTarget);
        facingTargets.put(targetUserId, actorTarget);
        return facingTargets;
    }

    private Map<String, String> interactionTextByUserId(
            String type,
            String actorUserId,
            String targetUserId,
            long slotIndex
    ) {
        String[][] phrases = switch (type) {
            case "PLAY" -> new String[][]{
                    {"같이 놀자!", "좋아!"},
                    {"뛰어볼까?", "가자!"},
                    {"장난칠래?", "응!"}
            };
            case "REST" -> new String[][]{
                    {"잠깐 쉬자", "그래!"},
                    {"여기 편하다", "좋다"},
                    {"숨 좀 돌리자", "응"}
            };
            case "FOLLOW" -> new String[][]{
                    {"따라와!", "갈게!"},
                    {"이쪽이야!", "알겠어!"},
                    {"같이 가자!", "좋아!"}
            };
            default -> new String[][]{
                    {"안녕!", "반가워!"},
                    {"여기 있었구나!", "응!"},
                    {"좋은 하루야!", "너도!"}
            };
        };
        String[] selected = phrases[stableIndex(type, "phrase", slotIndex, phrases.length)];
        Map<String, String> textByUserId = new LinkedHashMap<>();
        textByUserId.put(actorUserId, selected[0]);
        textByUserId.put(targetUserId, selected[1]);
        return textByUserId;
    }

    private Map<String, String> interactionAnimations(
            String type,
            String actorUserId,
            String targetUserId
    ) {
        if (!type.equals("REST")) {
            return Collections.emptyMap();
        }

        Map<String, String> animations = new LinkedHashMap<>();
        animations.put(actorUserId, "South");
        animations.put(targetUserId, "South");
        return animations;
    }

    private Map<String, PlazaDto.PlazaPositionResponse> toPositionResponseMap(Map<String, Position> positions) {
        return positions.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> toPositionResponse(entry.getValue()),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    private PlazaDto.PlazaPositionResponse toPositionResponse(Position position) {
        return PlazaDto.PlazaPositionResponse.builder()
                .x(position.x())
                .y(position.y())
                .build();
    }

    private Position interpolate(Position from, Position to, float progress) {
        float clampedProgress = clamp(progress, 0f, 1f);
        return new Position(
                from.x() + (to.x() - from.x()) * clampedProgress,
                from.y() + (to.y() - from.y()) * clampedProgress
        );
    }

    private Position midpoint(Position first, Position second) {
        return new Position(
                (first.x() + second.x()) * 0.5f,
                (first.y() + second.y()) * 0.5f
        );
    }

    private String participantKey(MemoryParticipant participant) {
        if (participant.userId() != null && !participant.userId().isBlank()) {
            return participant.userId();
        }
        return "participant_" + participant.joinedAtMillis();
    }

    private int stableIndex(String first, String second, long slotIndex, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.floorMod(Objects.hash(first, second, slotIndex), size);
    }

    private float stableUnit(String plazaId, String userId, String salt, long slotIndex) {
        return Math.floorMod(Objects.hash(plazaId, userId, salt, slotIndex), 10_000) / 9_999f;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private PlazaDto.PlazaChatMessageResponse toMessageResponse(MemoryChatMessage message) {
        return PlazaDto.PlazaChatMessageResponse.builder()
                .id(message.messageId())
                .senderUserId(message.senderUserId())
                .senderNickname(message.senderNickname())
                .text(message.text())
                .sentAtMillis(message.sentAtMillis())
                .build();
    }

    private String normalizePlazaCode(String inputCode) {
        String compact = inputCode.trim()
                .replace("-", "")
                .replace(" ", "")
                .toUpperCase();

        if (!compact.startsWith("PZ")) {
            compact = "PZ" + compact;
        }

        if (!compact.matches("^PZ[A-Z0-9]{4,6}$")) {
            throw new CustomApiException(ErrorCode.INVALID_PLAZA_CODE);
        }

        return compact.substring(2);
    }

    private MemoryPlaza currentPlazaForUser(String userId) {
        String plazaId = PLAZA_ID_BY_USER_ID.get(userId);
        if (plazaId == null) {
            return null;
        }

        MemoryPlaza plaza = PLAZAS_BY_ID.get(plazaId);
        if (plaza == null || !plaza.participants.containsKey(userId)) {
            PLAZA_ID_BY_USER_ID.remove(userId, plazaId);
            return null;
        }

        return plaza;
    }

    private MemoryPlaza plazaByCode(String code) {
        String plazaId = PLAZA_ID_BY_CODE.get(code);
        if (plazaId == null) {
            return null;
        }

        MemoryPlaza plaza = PLAZAS_BY_ID.get(plazaId);
        if (plaza == null) {
            PLAZA_ID_BY_CODE.remove(code, plazaId);
        }
        return plaza;
    }

    private MemoryPlaza createNewPlaza() {
        MemoryPlaza plaza = new MemoryPlaza(
                "plaza_" + UUID.randomUUID().toString().substring(0, 8),
                createUniquePlazaCode(),
                System.currentTimeMillis()
        );
        PLAZAS_BY_ID.put(plaza.plazaId, plaza);
        PLAZA_ID_BY_CODE.put(plaza.plazaCode, plaza.plazaId);
        return plaza;
    }

    private String createUniquePlazaCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 4)
                    .toUpperCase();
            if (!PLAZA_ID_BY_CODE.containsKey(code)) {
                return code;
            }
        }
        throw new CustomApiException(ErrorCode.UNKNOWN);
    }

    private void removePlaza(MemoryPlaza plaza) {
        plaza.participants.keySet().forEach(userId -> PLAZA_ID_BY_USER_ID.remove(userId, plaza.plazaId));
        PLAZAS_BY_ID.remove(plaza.plazaId);
        PLAZA_ID_BY_CODE.remove(plaza.plazaCode, plaza.plazaId);
    }

    private void incrementRevision(MemoryPlaza plaza) {
        plaza.roomRevision += 1;
    }

    private String displayNickname(String currentUid, String currentNickname) {
        if (currentNickname != null && !currentNickname.isBlank()) {
            return currentNickname;
        }
        if (currentUid == null || currentUid.isBlank()) {
            return "사용자";
        }
        return currentUid;
    }

    private static final class MemoryPlaza {
        private final String plazaId;
        private final String plazaCode;
        private final long createdAtMillis;
        private long roomRevision = 0L;
        private final Map<String, MemoryParticipant> participants = new LinkedHashMap<>();
        private final List<MemoryChatMessage> messages = new ArrayList<>();

        private MemoryPlaza(String plazaId, String plazaCode, long createdAtMillis) {
            this.plazaId = plazaId;
            this.plazaCode = plazaCode;
            this.createdAtMillis = createdAtMillis;
        }
    }

    private record MemoryParticipant(
            String userId,
            String nickname,
            String petId,
            String petName,
            String characterAssetKey,
            long joinedAtMillis
    ) {
    }

    private record MemoryChatMessage(
            String messageId,
            String plazaId,
            String senderUserId,
            String senderNickname,
            String text,
            long sentAtMillis
    ) {
    }

    private record Position(float x, float y) {
    }

    private record MovementSnapshot(
            Position current,
            PlazaDto.PlazaMovementResponse movement,
            long updatedAtMillis
    ) {
    }

    private record InteractionSnapshot(
            long startedAtMillis,
            long durationMillis,
            long settleDurationMillis,
            Map<String, Position> movementTargetByUserId,
            List<PlazaDto.PlazaInteractionResponse> responses
    ) {
        private static InteractionSnapshot empty() {
            return new InteractionSnapshot(
                    0L,
                    0L,
                    0L,
                    Collections.emptyMap(),
                    Collections.emptyList()
            );
        }

        private Position movementTargetFor(String userId) {
            return movementTargetByUserId.get(userId);
        }

        private long activeUntilMillis() {
            return startedAtMillis + durationMillis;
        }

        private long settleUntilMillis() {
            return activeUntilMillis() + settleDurationMillis;
        }
    }
}
