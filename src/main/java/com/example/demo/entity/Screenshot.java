package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "screenshots")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Screenshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 외부에 노출되는 식별자 (UUID)
    // 프론트의 GalleryImage.id 에 대응
    @Column(unique = true, nullable = false)
    private String imageId;

    // 소유자 uid (User.uid 참조, FK 없이 String으로 관리)
    @Column(nullable = false)
    private String userUid;

    // S3에 저장된 파일 경로 키 (예: "screenshots/uid_xxxx.png")
    @Column(nullable = false)
    private String fileKey;

    // 파일 크기 (bytes)
    @Column(nullable = false)
    private Long size;

    // 즐겨찾기 여부
    @Builder.Default
    @Column(nullable = false)
    private boolean isFavorite = false;

    // 생성 시각 (프론트의 GalleryImage.timestamp 에 대응)
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 저장 전 imageId 자동 생성
    @PrePersist
    public void generateImageId() {
        if (this.imageId == null) {
            this.imageId = "img_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
    }
}