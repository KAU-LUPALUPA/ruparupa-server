package com.example.demo.repository;
// 받은 메시지 목록을 최신순으로 가져올 창고

import com.example.demo.User;
import com.example.demo.entity.FriendMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface FriendMessageRepository extends JpaRepository<FriendMessage, Long> {
    
    // 내가 받은 메시지들을 최신순(내림차순)으로 가져오기
    List<FriendMessage> findByToUserOrderByCreatedAtDesc(User toUser);

    @Query("""
            select m
            from FriendMessage m
            where (m.fromUser = :firstUser and m.toUser = :secondUser)
               or (m.fromUser = :secondUser and m.toUser = :firstUser)
            order by m.createdAt asc
            """)
    List<FriendMessage> findConversation(
            @Param("firstUser") User firstUser,
            @Param("secondUser") User secondUser
    );
}
