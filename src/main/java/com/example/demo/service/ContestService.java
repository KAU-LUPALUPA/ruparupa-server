package com.example.demo.service;

import com.example.demo.dto.ContestParticipationResponseDto;
import com.example.demo.entity.ContestParticipation;
import com.example.demo.repository.ContestParticipationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ContestService {

    private final S3Uploader s3Uploader;
    private final ContestParticipationRepository contestParticipationRepository;

    @Transactional
    public ContestParticipationResponseDto participate(String uid, MultipartFile picture) throws IOException {
        // 1. S3 업로드 경로 설정: contest/Participation_list/{uid}
        String customS3Key = "contest/Participation_list/" + uid;
        
        // S3에 업로드 진행 (지정된 key로 저장)
        String pictureUrl = s3Uploader.uploadFile(picture, customS3Key);

        // 2. 이미 참가 정보가 존재하는지 조회
        Optional<ContestParticipation> optionalParticipation = contestParticipationRepository.findByUid(uid);

        ContestParticipation participation;
        if (optionalParticipation.isPresent()) {
            // 존재하면 사진 URL 변경
            participation = optionalParticipation.get();
            participation.setPictureUrl(pictureUrl);
            participation = contestParticipationRepository.save(participation);
        } else {
            // 존재하지 않으면 새 레코드 생성 및 저장
            participation = ContestParticipation.builder()
                    .uid(uid)
                    .pictureUrl(pictureUrl)
                    .build();
            participation = contestParticipationRepository.save(participation);
        }

        // 3. 응답 DTO 조립
        ContestParticipationResponseDto.Data dataDto = ContestParticipationResponseDto.Data.builder()
                .id(participation.getId())
                .uid(participation.getUid())
                .pictureUrl(participation.getPictureUrl())
                .build();

        return ContestParticipationResponseDto.builder()
                .status("success")
                .data(dataDto)
                .build();
    }
}
