package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class S3Uploader {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket:loopaloopa-bucket}")
    private String bucket;

    // Presigned URL 유효 시간 (10분)
    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);

    // =============================================
    // 기존 기능: MultipartFile을 서버가 직접 S3에 업로드
    // PhotoController에서 사용
    // =============================================
    public String uploadFile(MultipartFile multipartFile) throws IOException {
        String originalFileName = multipartFile.getOriginalFilename();
        String fileName = UUID.randomUUID() + "_" + originalFileName;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .contentType(multipartFile.getContentType())
                .contentLength(multipartFile.getSize())
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(
                multipartFile.getInputStream(), multipartFile.getSize()));

        return s3Client.utilities()
                .getUrl(b -> b.bucket(bucket).key(fileName))
                .toString();
    }

    // =============================================
    // 추가 기능: Presigned PUT URL 발급
    // GalleryController(스크린샷), ContestService에서 사용
    //
    // [수정] Content-Type을 서명에 포함하지 않음
    // 포함 시 프론트가 PUT 요청 헤더와 불일치하면 S3에서 403 반환
    // =============================================
    public String generatePresignedPutUrl(String fileKey, String contentType) {
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_EXPIRATION)
                .putObjectRequest(b -> b
                        .bucket(bucket)
                        .key(fileKey)
                        // contentType은 서명에 포함하지 않음 (파라미터는 호출부 호환성을 위해 유지)
                )
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return presigned.url().toString();
    }

    // =============================================
    // 추가 기능: S3 파일 URL 조회
    // GalleryController(스크린샷)에서 사용
    // =============================================
    public String getFileUrl(String fileKey) {
        return s3Client.utilities()
                .getUrl(b -> b.bucket(bucket).key(fileKey))
                .toString();
    }

    // =============================================
    // 추가 기능: S3 파일 다수 삭제
    // GalleryController(스크린샷)에서 사용
    // =============================================
    public void deleteFiles(List<String> fileKeys) {
        if (fileKeys == null || fileKeys.isEmpty()) return;

        List<ObjectIdentifier> objects = fileKeys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();

        DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(objects).build())
                .build();

        s3Client.deleteObjects(deleteRequest);
    }
}