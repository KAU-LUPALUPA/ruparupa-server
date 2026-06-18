package com.example.demo.controller;
 
import com.example.demo.dto.GalleryDto;
import com.example.demo.service.ScreenshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
 
import java.util.List;
 
@RestController
@RequestMapping("/gallery")
@RequiredArgsConstructor
public class GalleryController {
 
    private final ScreenshotService screenshotService;
 
    // 내 스크린샷 목록 조회
    // UserInterceptor가 JWT 검증 후 currentUid를 request에 심어줌
    @GetMapping("/items")
    public ResponseEntity<GalleryDto.GalleryItemsResponse> getItems(
            @RequestAttribute("currentUid") String currentUid
    ) {
        return ResponseEntity.ok(screenshotService.getItems(currentUid));
    }
 
    // S3 Presigned PUT URL 발급
    // FE는 이 URL로 직접 S3에 PUT 업로드 후, /gallery/items 에 메타데이터 등록
    @PostMapping("/items/upload-url")
    public ResponseEntity<GalleryDto.UploadUrlResponse> getUploadUrl(
            @RequestAttribute("currentUid") String currentUid,
            @RequestBody GalleryDto.UploadUrlRequest request
    ) {
        return ResponseEntity.ok(screenshotService.generateUploadUrl(currentUid, request));
    }
 
    // S3 업로드 완료 후 메타데이터 DB 저장
    @PostMapping("/items")
    public ResponseEntity<GalleryDto.SaveMetadataResponse> saveMetadata(
            @RequestAttribute("currentUid") String currentUid,
            @RequestBody GalleryDto.SaveMetadataRequest request
    ) {
        return ResponseEntity.ok(screenshotService.saveMetadata(currentUid, request));
    }
 
    // 스크린샷 다수 삭제
    @DeleteMapping("/items")
    public ResponseEntity<GalleryDto.DeleteResponse> deleteItems(
            @RequestAttribute("currentUid") String currentUid,
            @RequestParam("imageIds") List<String> imageIds
    ) {
        return ResponseEntity.ok(screenshotService.deleteItems(currentUid, imageIds));
    }

    // 즐겨찾기 여부
    @PatchMapping("/items/{imageId}/favorite")
    public ResponseEntity<GalleryDto.FavoriteResponse> toggleFavorite(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable("imageId") String imageId,
            @RequestBody GalleryDto.FavoriteRequest request
    ) {
        return ResponseEntity.ok(screenshotService.toggleFavorite(currentUid, imageId, request));
    }
}