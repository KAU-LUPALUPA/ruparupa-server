package com.example.demo.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class GalleryDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UploadUrlRequest {
        private String fileName;
        private String fileType;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UploadUrlResponse {
        private boolean success;
        private UploadUrlData data;

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
        public static class UploadUrlData {
            private String uploadUrl;   // S3 Presigned PUT URL (FE가 직접 이 URL로 업로드)
            private String fileKey;     // S3 key (이후 메타데이터 등록 시 사용)
        }
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SaveMetadataRequest {
        private String fileKey;
        private Long size;
        private boolean isFavorite;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SaveMetadataResponse {
        private boolean success;
        private SaveMetadataData data;

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
        public static class SaveMetadataData {
            private String imageId;
        }
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GalleryItemsResponse {
        private boolean success;
        private GalleryItemsData data;

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
        public static class GalleryItemsData {
            private List<GalleryItem> items;
        }
    }

    // 갤러리 항목 하나 (프론트 GalleryImage 모델에 대응)
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GalleryItem {
        private String imageId;         // 프론트 GalleryImage.id
        private String imageUrl;        // S3 퍼블릭 URL (프론트 GalleryImage.filePath 대체)
        private boolean isFavorite;     // 프론트 GalleryImage.isFavorite
        private long timestamp;         // epoch millis (프론트 GalleryImage.timestamp)
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DeleteResponse {
        private boolean success;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FavoriteRequest {
        private boolean isFavorite;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FavoriteResponse {
        private boolean success;
        private FavoriteData data;

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
        public static class FavoriteData {
            private String imageId;
            private boolean isFavorite;
        }
    }
}