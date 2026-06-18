package com.example.demo.repository;

import com.example.demo.entity.Screenshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScreenshotRepository extends JpaRepository<Screenshot, Long> {

    // 내 스크린샷 전체 조회 (최신순)
    List<Screenshot> findAllByUserUidOrderByCreatedAtDesc(String userUid);

    // 특정 스크린샷 조회 (소유자 검증 포함)
    Optional<Screenshot> findByImageIdAndUserUid(String imageId, String userUid);

    // 여러 imageId + 소유자로 조회 (삭제 전 소유권 확인용)
    List<Screenshot> findAllByImageIdInAndUserUid(List<String> imageIds, String userUid);

    // 여러 imageId 일괄 삭제
    @Modifying
    @Query("DELETE FROM Screenshot s WHERE s.imageId IN :imageIds AND s.userUid = :userUid")
    void deleteAllByImageIdInAndUserUid(
            @Param("imageIds") List<String> imageIds,
            @Param("userUid") String userUid
    );
}