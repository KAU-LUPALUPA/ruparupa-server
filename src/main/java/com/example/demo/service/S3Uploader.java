package com.example.demo.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class S3Uploader {

    private final AmazonS3 amazonS3Client;

    @Value("${cloud.aws.s3.bucket:loopaloopa-bucket}")
    private String bucket;

    // Presigned URL 유효 시간 (10분)
    private static final long PRESIGNED_URL_EXPIRATION_MS = 1000 * 60 * 10;

    // =============================================
    // 기존 기능: MultipartFile을 서버가 직접 S3에 업로드
    // PhotoController에서 사용
    // =============================================
    public String uploadFile(MultipartFile multipartFile) throws IOException {
        String originalFileName = multipartFile.getOriginalFilename();
        String fileName = UUID.randomUUID().toString() + "_" + originalFileName;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(multipartFile.getSize());
        metadata.setContentType(multipartFile.getContentType());

        amazonS3Client.putObject(bucket, fileName, multipartFile.getInputStream(), metadata);

        return amazonS3Client.getUrl(bucket, fileName).toString();
    }

    // =============================================
    // 추가 기능: Presigned PUT URL 발급
    // GalleryController(스크린샷)에서 사용
    // =============================================
    public String generatePresignedPutUrl(String fileKey, String contentType) {
        Date expiration = new Date(System.currentTimeMillis() + PRESIGNED_URL_EXPIRATION_MS);

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, fileKey)
                .withMethod(HttpMethod.PUT)
                .withExpiration(expiration)
                .withContentType(contentType);

        URL url = amazonS3Client.generatePresignedUrl(request);
        return url.toString();
    }

    // =============================================
    // 추가 기능: S3 파일 URL 조회
    // GalleryController(스크린샷)에서 사용
    // =============================================
    public String getFileUrl(String fileKey) {
        return amazonS3Client.getUrl(bucket, fileKey).toString();
    }

    // =============================================
    // 추가 기능: S3 파일 다수 삭제
    // GalleryController(스크린샷)에서 사용
    // =============================================
    public void deleteFiles(List<String> fileKeys) {
        if (fileKeys == null || fileKeys.isEmpty()) return;

        DeleteObjectsRequest deleteRequest = new DeleteObjectsRequest(bucket)
                .withKeys(fileKeys.stream()
                        .map(DeleteObjectsRequest.KeyVersion::new)
                        .toList());
        amazonS3Client.deleteObjects(deleteRequest);
    }
}