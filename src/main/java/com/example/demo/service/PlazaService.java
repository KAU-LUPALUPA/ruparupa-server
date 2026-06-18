package com.example.demo.service;

import com.example.demo.User;
import com.example.demo.UserRepository;
import com.example.demo.dto.PlazaDto;
import com.example.demo.entity.Pet;
import com.example.demo.entity.Plaza;
import com.example.demo.entity.PlazaChatMessage;
import com.example.demo.entity.PlazaParticipant;
import com.example.demo.exception.CustomApiException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.PetRepository;
import com.example.demo.repository.PlazaChatMessageRepository;
import com.example.demo.repository.PlazaParticipantRepository;
import com.example.demo.repository.PlazaRepository;
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

    private final PlazaRepository plazaRepository;
    private final PlazaParticipantRepository participantRepository;
    private final PlazaChatMessageRepository messageRepository;
    private final PetRepository petRepository;
    private final UserRepository userRepository;
    private final PetService petService;

    @Transactional
    public PlazaDto.PlazaRoomResponse joinRandomPlaza(String currentUid) {
        User user = findUser(currentUid);
        Plaza targetPlaza = plazaRepository.findAll().stream()
                .filter(plaza -> participantRepository.countByPlaza(plaza) < MAX_PARTICIPANTS)
                .findFirst()
                .orElseGet(this::createNewPlaza);

        return joinTargetPlaza(user, targetPlaza);
    }

    @Transactional
    public PlazaDto.PlazaRoomResponse joinPlazaByCode(String currentUid, String inputCode) {
        if (inputCode == null || inputCode.trim().isEmpty()) {
            throw new CustomApiException(ErrorCode.EMPTY_CODE);
        }

        User user = findUser(currentUid);
        String pureCode = normalizePlazaCode(inputCode);
        Plaza targetPlaza = plazaRepository.findByPlazaCode(pureCode)
                .orElseThrow(() -> new CustomApiException(ErrorCode.PLAZA_NOT_FOUND));

        boolean alreadyInTarget = participantRepository.findByUserId(currentUid)
                .map(participant -> participant.getPlaza().getPlazaId().equals(targetPlaza.getPlazaId()))
                .orElse(false);

        if (!alreadyInTarget && participantRepository.countByPlaza(targetPlaza) >= MAX_PARTICIPANTS) {
            throw new CustomApiException(ErrorCode.PLAZA_FULL);
        }

        return joinTargetPlaza(user, targetPlaza);
    }

    @Transactional(readOnly = true)
    public PlazaDto.PlazaRoomResponse getCurrentPlaza(String currentUid) {
        findUser(currentUid);
        return participantRepository.findByUserId(currentUid)
                .map(participant -> getPlazaSnapshot(participant.getPlaza().getPlazaId(), currentUid))
                .orElseGet(() -> PlazaDto.PlazaRoomResponse.builder()
                        .plaza(null)
                        .build());
    }

    @Transactional(readOnly = true)
    public PlazaDto.PlazaRoomResponse getPlazaSnapshot(String plazaId, String currentUid) {
        Plaza plaza = plazaRepository.findByPlazaId(plazaId)
                .orElseThrow(() -> new CustomApiException(ErrorCode.PLAZA_NOT_FOUND));

        boolean isParticipant = plaza.getParticipants().stream()
                .anyMatch(participant -> participant.getUserId().equals(currentUid));
        if (!isParticipant) {
            throw new CustomApiException(ErrorCode.NOT_IN_PLAZA);
        }

        return toRoomResponse(plaza);
    }

    @Transactional
    public void leavePlaza(String plazaId, String currentUid) {
        PlazaParticipant participant = participantRepository.findByUserId(currentUid)
                .filter(current -> current.getPlaza().getPlazaId().equals(plazaId))
                .orElseThrow(() -> new CustomApiException(ErrorCode.NOT_IN_PLAZA));

        Plaza plaza = participant.getPlaza();
        plaza.getParticipants().remove(participant);
        participantRepository.delete(participant);

        if (participantRepository.countByPlaza(plaza) == 0) {
            plazaRepository.delete(plaza);
            return;
        }

        incrementRevision(plaza);
    }

    @Transactional
    public PlazaDto.PlazaChatMessageEnvelopeResponse sendPlazaMessage(
            String plazaId,
            String currentUid,
            String text
    ) {
        PlazaParticipant participant = participantRepository.findByUserId(currentUid)
                .filter(current -> current.getPlaza().getPlazaId().equals(plazaId))
                .orElseThrow(() -> new CustomApiException(ErrorCode.NOT_IN_PLAZA));

        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new CustomApiException(ErrorCode.EMPTY_MESSAGE);
        }
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new CustomApiException(ErrorCode.MESSAGE_TOO_LONG);
        }

        Plaza plaza = participant.getPlaza();
        incrementRevision(plaza);

        PlazaChatMessage message = new PlazaChatMessage();
        message.setMessageId("plaza_message_" + UUID.randomUUID().toString().substring(0, 8));
        message.setPlazaId(plazaId);
        message.setSenderUserId(currentUid);
        message.setSenderNickname(participant.getNickname());
        message.setText(trimmed);
        message.setSentAtMillis(System.currentTimeMillis());
        messageRepository.save(message);

        return PlazaDto.PlazaChatMessageEnvelopeResponse.builder()
                .message(toMessageResponse(message))
                .roomRevision(plaza.getRoomRevision())
                .build();
    }

    private PlazaDto.PlazaRoomResponse joinTargetPlaza(User user, Plaza targetPlaza) {
        participantRepository.findByUserId(user.getUid()).ifPresent(existingParticipant -> {
            Plaza oldPlaza = existingParticipant.getPlaza();
            if (oldPlaza.getPlazaId().equals(targetPlaza.getPlazaId())) {
                return;
            }

            oldPlaza.getParticipants().remove(existingParticipant);
            participantRepository.delete(existingParticipant);
            if (participantRepository.countByPlaza(oldPlaza) == 0) {
                plazaRepository.delete(oldPlaza);
            } else {
                incrementRevision(oldPlaza);
            }
        });

        boolean alreadyJoined = participantRepository.findByUserId(user.getUid())
                .map(participant -> participant.getPlaza().getPlazaId().equals(targetPlaza.getPlazaId()))
                .orElse(false);
        if (!alreadyJoined) {
            Pet pet = petRepository.findByUserId(user.getId())
                    .orElseGet(() -> petService.createInitialSetupAndReturnPet(user));

            PlazaParticipant participant = new PlazaParticipant();
            participant.setPlaza(targetPlaza);
            participant.setUserId(user.getUid());
            participant.setNickname(user.getNickname());
            participant.setPetId(pet.getId());
            participant.setJoinedAtMillis(System.currentTimeMillis());
            participant.setPositionX(0.5f);
            participant.setPositionY(0.5f);
            participant.setLastUpdatedAtMillis(System.currentTimeMillis());
            participantRepository.save(participant);
            targetPlaza.getParticipants().add(participant);
            incrementRevision(targetPlaza);
        }

        return toRoomResponse(targetPlaza);
    }

    private PlazaDto.PlazaRoomResponse toRoomResponse(Plaza plaza) {
        long nowMillis = System.currentTimeMillis();
        List<PlazaParticipant> participants = plaza.getParticipants().stream()
                .sorted(Comparator.comparing(
                        PlazaParticipant::getUserId,
                        Comparator.nullsLast(String::compareTo)
                ))
                .collect(Collectors.toList());
        InteractionSnapshot interactionSnapshot = buildInteractionSnapshot(plaza, participants, nowMillis);
        List<PlazaChatMessage> messages = new ArrayList<>(
                messageRepository.findTop50ByPlazaIdOrderBySentAtMillisDesc(plaza.getPlazaId())
        );
        Collections.reverse(messages);

        PlazaDto.PlazaDetail detail = PlazaDto.PlazaDetail.builder()
                .plazaId(plaza.getPlazaId())
                .plazaCode("PZ" + plaza.getPlazaCode())
                .displayPlazaCode("PZ-" + plaza.getPlazaCode())
                .serverNowMillis(nowMillis)
                .participants(participants.stream()
                        .map(participant -> toParticipantResponse(plaza, participant, nowMillis, interactionSnapshot))
                        .collect(Collectors.toList()))
                .messages(messages.stream()
                        .map(this::toMessageResponse)
                        .collect(Collectors.toList()))
                .interactions(interactionSnapshot.responses())
                .maxParticipants(MAX_PARTICIPANTS)
                .roomRevision(plaza.getRoomRevision())
                .build();

        return PlazaDto.PlazaRoomResponse.builder()
                .plaza(detail)
                .build();
    }

    private PlazaDto.PlazaParticipantResponse toParticipantResponse(
            Plaza plaza,
            PlazaParticipant participant,
            long nowMillis,
            InteractionSnapshot interactionSnapshot
    ) {
        Pet pet = petRepository.findById(participant.getPetId())
                .orElseThrow(() -> new CustomApiException(ErrorCode.PET_NOT_FOUND));
        String participantUserId = participantKey(participant);
        MovementSnapshot movementSnapshot = participantMovementSnapshot(
                plaza.getPlazaId(),
                participantUserId,
                nowMillis,
                interactionSnapshot
        );

        return PlazaDto.PlazaParticipantResponse.builder()
                .userId(participantUserId)
                .nickname(participant.getNickname())
                .pet(PlazaDto.PlazaPetSnapshotResponse.builder()
                        .petId(pet.getPetUid())
                        .name(pet.getName())
                        .characterAssetKey(pet.getCharacterAssetKey())
                        .appearance(PlazaDto.PetAppearanceResponse.defaultAppearance())
                        .build())
                .position(toPositionResponse(movementSnapshot.current()))
                .movement(movementSnapshot.movement())
                .positionUpdatedAtMillis(movementSnapshot.updatedAtMillis())
                .joinedAtMillis(participant.getJoinedAtMillis())
                .build();
    }

    private InteractionSnapshot buildInteractionSnapshot(
            Plaza plaza,
            List<PlazaParticipant> participants,
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
        int actorIndex = stableIndex(plaza.getPlazaId(), "interaction-actor", slotIndex, participants.size());
        int targetOffset = 1 + stableIndex(
                plaza.getPlazaId(),
                "interaction-target-offset",
                slotIndex,
                participants.size() - 1
        );
        int targetIndex = (actorIndex + targetOffset) % participants.size();
        PlazaParticipant actor = participants.get(actorIndex);
        PlazaParticipant target = participants.get(targetIndex);
        String actorUserId = participantKey(actor);
        String targetUserId = participantKey(target);
        String type = PLAZA_INTERACTION_TYPES[stableIndex(
                plaza.getPlazaId(),
                "interaction-type",
                slotIndex,
                PLAZA_INTERACTION_TYPES.length
        )];

        Position actorPosition = deterministicMovementSnapshot(
                plaza.getPlazaId(),
                actorUserId,
                startedAtMillis
        ).current();
        Position targetPosition = deterministicMovementSnapshot(
                plaza.getPlazaId(),
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
                        .id("plaza_interaction_" + plaza.getPlazaId() + "_" + slotIndex)
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

    private String participantKey(PlazaParticipant participant) {
        if (participant.getUserId() != null && !participant.getUserId().isBlank()) {
            return participant.getUserId();
        }
        return "participant_" + participant.getId();
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

    private PlazaDto.PlazaChatMessageResponse toMessageResponse(PlazaChatMessage message) {
        return PlazaDto.PlazaChatMessageResponse.builder()
                .id(message.getMessageId())
                .senderUserId(message.getSenderUserId())
                .senderNickname(message.getSenderNickname())
                .text(message.getText())
                .sentAtMillis(message.getSentAtMillis())
                .build();
    }

    private User findUser(String currentUid) {
        return userRepository.findByUid(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.USER_NOT_FOUND));
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

    private Plaza createNewPlaza() {
        Plaza plaza = new Plaza();
        plaza.setPlazaId("plaza_" + UUID.randomUUID().toString().substring(0, 8));
        plaza.setPlazaCode(createUniquePlazaCode());
        plaza.setRoomRevision(0L);
        plaza.setCreatedAtMillis(System.currentTimeMillis());
        return plazaRepository.save(plaza);
    }

    private String createUniquePlazaCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 4)
                    .toUpperCase();
            if (plazaRepository.findByPlazaCode(code).isEmpty()) {
                return code;
            }
        }
        throw new CustomApiException(ErrorCode.UNKNOWN);
    }

    private void incrementRevision(Plaza plaza) {
        Long currentRevision = plaza.getRoomRevision() == null ? 0L : plaza.getRoomRevision();
        plaza.setRoomRevision(currentRevision + 1);
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
