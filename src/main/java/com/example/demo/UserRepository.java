package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoId(Long kakaoId);

    Optional<User> findByNickname(String nickname);

    Optional<User> findByUid(String uid);

    Optional<User> findByFriendCode(String friendCode);

    /** 랭킹 조회 시 닉네임 일괄 조회용 (N+1 방지) */
    List<User> findByUidIn(List<String> uids);
}