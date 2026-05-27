package com.example.demo.service;

import com.example.demo.dto.GalleryDto;
import com.example.demo.entity.Screenshot;
import com.example.demo.exception.CustomApiException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.ScreenshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScreenshotService {

    private final ScreenshotRepository screenshotRepository;
    private final S3Uploader s3Uploader;

    // =============================================
    // GET /gallery/items
    // 내 스크린샷 목록 조회
    // =============================================
    @Transactional(readOnly = true)
    public GalleryDto.GalleryItemsResponse getItems(String userUid) {
        List<Screenshot> screenshots = screenshotRepository
                .findAllByUserUidOrderByCreatedAtDesc(userUid);

        List<GalleryDto.GalleryItem> items = screenshots.stream()
                .map(s -> GalleryDto.GalleryItem.builder()
                        .imageId(s.getImageId())
                        .imageUrl(s3Uploader.getFileUrl(s.getFileKey()))
                        .isFavorite(s.isFavorite())
                        .timestamp(s.getCreatedAt()
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli())
                        .build())
                .toList();

        return GalleryDto.GalleryItemsResponse.builder()
                .success(true)
                .data(GalleryDto.GalleryItemsResponse.GalleryItemsData.builder()
                        .items(items)
                        .build())
                .build();
    }

    // =============================================
    // POST /gallery/items/upload-url
    // S3 Presigned PUT URL 발급
    // =============================================
    public GalleryDto.UploadUrlResponse generateUploadUrl(
            String userUid,
            GalleryDto.UploadUrlRequest request
    ) {
        String extension = extractExtension(request.getFileName());
        String fileKey = "screenshots/" + userUid + "/" + UUID.randomUUID() + extension;

        String presignedUrl = s3Uploader.generatePresignedPutUrl(fileKey, request.getFileType());

        return GalleryDto.UploadUrlResponse.builder()
                .success(true)
                .data(GalleryDto.UploadUrlResponse.UploadUrlData.builder()
                        .uploadUrl(presignedUrl)
                        .fileKey(fileKey)
                        .build())
                .build();
    }

    // =============================================
    // POST /gallery/items
    // S3 업로드 완료 후 메타데이터 DB 저장
    // =============================================
    @Transactional
    public GalleryDto.SaveMetadataResponse saveMetadata(
            String userUid,
            GalleryDto.SaveMetadataRequest request
    ) {
        Screenshot screenshot = Screenshot.builder()
                .userUid(userUid)
                .fileKey(request.getFileKey())
                .size(request.getSize())
                .isFavorite(request.isFavorite())
                .build();

        Screenshot saved = screenshotRepository.save(screenshot);

        return GalleryDto.SaveMetadataResponse.builder()
                .success(true)
                .data(GalleryDto.SaveMetadataResponse.SaveMetadataData.builder()
                        .imageId(saved.getImageId())
                        .build())
                .build();
    }

    // =============================================
    // DELETE /gallery/items?imageIds=img_1,img_2
    // 스크린샷 다수 삭제 (S3 + DB)
    // =============================================
    @Transactional
    public GalleryDto.DeleteResponse deleteItems(String userUid, List<String> imageIds) {
        List<Screenshot> owned = screenshotRepository
                .findAllByImageIdInAndUserUid(imageIds, userUid);

        if (owned.isEmpty()) {
            return GalleryDto.DeleteResponse.builder().success(true).build();
        }

        List<String> fileKeys = owned.stream()
                .map(Screenshot::getFileKey)
                .toList();
        s3Uploader.deleteFiles(fileKeys);

        List<String> ownedImageIds = owned.stream()
                .map(Screenshot::getImageId)
                .toList();
        screenshotRepository.deleteAllByImageIdInAndUserUid(ownedImageIds, userUid);

        return GalleryDto.DeleteResponse.builder().success(true).build();
    }

    // =============================================
    // PATCH /gallery/items/{imageId}/favorite
    // 즐겨찾기 여부 수정
    // =============================================
    @Transactional
    public GalleryDto.FavoriteResponse toggleFavorite(
            String userUid,
            String imageId,
            GalleryDto.FavoriteRequest request
    ) {
        Screenshot screenshot = screenshotRepository
                .findByImageIdAndUserUid(imageId, userUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.SCREENSHOT_NOT_FOUND));

        screenshot.setFavorite(request.isFavorite());
        screenshotRepository.save(screenshot);

        return GalleryDto.FavoriteResponse.builder()
                .success(true)
                .data(GalleryDto.FavoriteResponse.FavoriteData.builder()
                        .imageId(imageId)
                        .isFavorite(request.isFavorite())
                        .build())
                .build();
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return ".png";
        return fileName.substring(fileName.lastIndexOf("."));
    }
}