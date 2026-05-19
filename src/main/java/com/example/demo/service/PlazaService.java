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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlazaService {
    private static final int MAX_PARTICIPANTS = 4;
    private static final int MAX_MESSAGE_LENGTH = 120;

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
        List<PlazaChatMessage> messages = new ArrayList<>(
                messageRepository.findTop50ByPlazaIdOrderBySentAtMillisDesc(plaza.getPlazaId())
        );
        Collections.reverse(messages);

        PlazaDto.PlazaDetail detail = PlazaDto.PlazaDetail.builder()
                .plazaId(plaza.getPlazaId())
                .plazaCode("PZ" + plaza.getPlazaCode())
                .displayPlazaCode("PZ-" + plaza.getPlazaCode())
                .participants(plaza.getParticipants().stream()
                        .map(this::toParticipantResponse)
                        .collect(Collectors.toList()))
                .messages(messages.stream()
                        .map(this::toMessageResponse)
                        .collect(Collectors.toList()))
                .maxParticipants(MAX_PARTICIPANTS)
                .roomRevision(plaza.getRoomRevision())
                .build();

        return PlazaDto.PlazaRoomResponse.builder()
                .plaza(detail)
                .build();
    }

    private PlazaDto.PlazaParticipantResponse toParticipantResponse(PlazaParticipant participant) {
        Pet pet = petRepository.findById(participant.getPetId())
                .orElseThrow(() -> new CustomApiException(ErrorCode.PET_NOT_FOUND));

        return PlazaDto.PlazaParticipantResponse.builder()
                .userId(participant.getUserId())
                .nickname(participant.getNickname())
                .pet(PlazaDto.PlazaPetSnapshotResponse.builder()
                        .petId(pet.getPetUid())
                        .name(pet.getName())
                        .characterAssetKey(pet.getCharacterAssetKey())
                        .appearance(PlazaDto.PetAppearanceResponse.defaultAppearance())
                        .build())
                .position(PlazaDto.PlazaPositionResponse.builder()
                        .x(participant.getPositionX() == null ? 0.5f : participant.getPositionX())
                        .y(participant.getPositionY() == null ? 0.5f : participant.getPositionY())
                        .build())
                .joinedAtMillis(participant.getJoinedAtMillis())
                .build();
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
}
