package com.example.demo.controller;
 
import com.example.demo.dto.FriendDto;
import com.example.demo.exception.CustomApiException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
 
@RestController
@RequestMapping("/friends")
@RequiredArgsConstructor
public class FriendController {
 
    private final FriendService friendService;
 
    // friend_request_1 형식의 id에서 숫자만 추출
    private Long parseRequestId(String requestId) {
        try {
            return Long.parseLong(requestId.replace("friend_request_", ""));
        } catch (NumberFormatException e) {
            throw new CustomApiException(ErrorCode.REQUEST_NOT_FOUND);
        }
    }

    private Long parseHomeInvitationId(String invitationId) {
        try {
            return Long.parseLong(invitationId.replace("home_invitation_", ""));
        } catch (NumberFormatException e) {
            throw new CustomApiException(ErrorCode.HOME_INVITATION_NOT_FOUND);
        }
    }

    private Long parseHomeVisitId(String visitSessionId) {
        try {
            return Long.parseLong(visitSessionId.replace("home_visit_", ""));
        } catch (NumberFormatException e) {
            throw new CustomApiException(ErrorCode.HOME_VISIT_NOT_FOUND);
        }
    }
 
    // 1. 내 친구 코드 조회
    // GET /friends/users/me/friend-code
    @GetMapping("/users/me/friend-code")
    public ResponseEntity<FriendDto.MyFriendCodeResponse> getMyFriendCode(
            @RequestAttribute("currentUid") String currentUid) {
        return ResponseEntity.ok(friendService.getMyFriendCode(currentUid));
    }
 
    // 2. 친구 코드로 타인 유저 조회
    // GET /friends/users/by-code?friendCode=XXXX
    @GetMapping("/users/by-code")
    public ResponseEntity<FriendDto.FriendUserLookupResponse> lookupUserByCode(
            @RequestAttribute("currentUid") String currentUid,
            @RequestParam String friendCode) {
        return ResponseEntity.ok(friendService.lookupUserByCode(currentUid, friendCode));
    }
 
    // 3. 친구 신청 보내기
    // POST /friends/requests
    @PostMapping("/requests")
    public ResponseEntity<FriendDto.SingleRequestResponse> sendFriendRequest(
            @RequestAttribute("currentUid") String currentUid,
            @RequestBody FriendDto.SendRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(friendService.sendFriendRequest(currentUid, dto));
    }
 
    // 4. 받은 친구 요청 목록 조회
    // GET /friends/requests/received
    @GetMapping("/requests/received")
    public ResponseEntity<FriendDto.FriendRequestListResponse> getReceivedRequests(
            @RequestAttribute("currentUid") String currentUid) {
        return ResponseEntity.ok(friendService.getReceivedRequests(currentUid));
    }
 
    // 5. 보낸 친구 요청 목록 조회
    // GET /friends/requests/sent
    @GetMapping("/requests/sent")
    public ResponseEntity<FriendDto.FriendRequestListResponse> getSentRequests(
            @RequestAttribute("currentUid") String currentUid) {
        return ResponseEntity.ok(friendService.getSentRequests(currentUid));
    }
 
    // 6. 친구 요청 수락
    // POST /friends/requests/friend_request_1/accept
    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<FriendDto.AcceptRequestResponse> acceptFriendRequest(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String requestId) {
        return ResponseEntity.ok(friendService.acceptFriendRequest(currentUid, parseRequestId(requestId)));
    }
 
    // 7. 친구 요청 거절
    // POST /friends/requests/friend_request_1/reject
    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<FriendDto.SingleRequestResponse> rejectFriendRequest(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String requestId) {
        return ResponseEntity.ok(friendService.rejectFriendRequest(currentUid, parseRequestId(requestId)));
    }
 
    // 8. 친구 요청 취소
    // POST /friends/requests/friend_request_1/cancel
    @PostMapping("/requests/{requestId}/cancel")
    public ResponseEntity<FriendDto.SingleRequestResponse> cancelFriendRequest(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String requestId) {
        return ResponseEntity.ok(friendService.cancelFriendRequest(currentUid, parseRequestId(requestId)));
    }
 
    // 9. 내 친구 목록 조회
    // GET /friends
    @GetMapping
    public ResponseEntity<FriendDto.FriendListResponse> getFriendList(
            @RequestAttribute("currentUid") String currentUid) {
        return ResponseEntity.ok(friendService.getFriendList(currentUid));
    }

    // 10. 친구 집 초대 보내기
    // POST /friends/home-invitations
    @PostMapping("/home-invitations")
    public ResponseEntity<FriendDto.SingleHomeInvitationResponse> sendHomeInvitation(
            @RequestAttribute("currentUid") String currentUid,
            @RequestBody FriendDto.SendHomeInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(friendService.sendHomeInvitation(currentUid, request));
    }

    // 11. 받은 친구 집 초대 조회
    // GET /friends/home-invitations/received
    @GetMapping("/home-invitations/received")
    public ResponseEntity<FriendDto.HomeInvitationListResponse> getReceivedHomeInvitations(
            @RequestAttribute("currentUid") String currentUid) {
        return ResponseEntity.ok(friendService.getReceivedHomeInvitations(currentUid));
    }

    // 12. 보낸 친구 집 초대 조회
    // GET /friends/home-invitations/sent
    @GetMapping("/home-invitations/sent")
    public ResponseEntity<FriendDto.HomeInvitationListResponse> getSentHomeInvitations(
            @RequestAttribute("currentUid") String currentUid) {
        return ResponseEntity.ok(friendService.getSentHomeInvitations(currentUid));
    }

    // 13. 친구 집 초대 수락 및 방문 스냅샷 조회
    // POST /friends/home-invitations/home_invitation_1/accept
    @PostMapping("/home-invitations/{invitationId}/accept")
    public ResponseEntity<FriendDto.AcceptHomeInvitationResponse> acceptHomeInvitation(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String invitationId) {
        return ResponseEntity.ok(friendService.acceptHomeInvitation(currentUid, parseHomeInvitationId(invitationId)));
    }

    // 14. 친구 집 초대 거절
    // POST /friends/home-invitations/home_invitation_1/reject
    @PostMapping("/home-invitations/{invitationId}/reject")
    public ResponseEntity<FriendDto.SingleHomeInvitationResponse> rejectHomeInvitation(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String invitationId) {
        return ResponseEntity.ok(friendService.rejectHomeInvitation(currentUid, parseHomeInvitationId(invitationId)));
    }

    // 15. 보낸 친구 집 초대 취소
    // POST /friends/home-invitations/home_invitation_1/cancel
    @PostMapping("/home-invitations/{invitationId}/cancel")
    public ResponseEntity<FriendDto.SingleHomeInvitationResponse> cancelHomeInvitation(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String invitationId) {
        return ResponseEntity.ok(friendService.cancelHomeInvitation(currentUid, parseHomeInvitationId(invitationId)));
    }

    // 16. 진행 중인 친구 집 방문 세션 조회
    // GET /friends/home-visits/active
    @GetMapping("/home-visits/active")
    public ResponseEntity<FriendDto.ActiveHomeVisitsResponse> getActiveHomeVisits(
            @RequestAttribute("currentUid") String currentUid) {
        return ResponseEntity.ok(friendService.getActiveHomeVisits(currentUid));
    }

    // 17. 친구 집 방문 종료
    // POST /friends/home-visits/home_visit_1/leave
    @PostMapping("/home-visits/{visitSessionId}/leave")
    public ResponseEntity<FriendDto.SingleHomeVisitSessionResponse> leaveHomeVisit(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String visitSessionId) {
        return ResponseEntity.ok(friendService.leaveHomeVisit(currentUid, parseHomeVisitId(visitSessionId)));
    }

    // 18. 친구 집 방문 채팅 메시지 조회
    // GET /friends/home-visits/home_visit_1/messages
    @GetMapping("/home-visits/{visitSessionId}/messages")
    public ResponseEntity<FriendDto.HomeVisitMessagesResponse> getHomeVisitMessages(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String visitSessionId) {
        return ResponseEntity.ok(friendService.getHomeVisitMessages(currentUid, parseHomeVisitId(visitSessionId)));
    }

    // 19. 친구 집 방문 채팅 메시지 보내기
    // POST /friends/home-visits/home_visit_1/messages
    @PostMapping("/home-visits/{visitSessionId}/messages")
    public ResponseEntity<FriendDto.SingleHomeVisitMessageResponse> sendHomeVisitMessage(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String visitSessionId,
            @RequestBody FriendDto.SendHomeVisitMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(friendService.sendHomeVisitMessage(currentUid, parseHomeVisitId(visitSessionId), request));
    }
 
    // 20. 친구 삭제
    // DELETE /friends/{friendUserId}
    @DeleteMapping("/{friendUserId}")
    public ResponseEntity<Void> deleteFriend(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String friendUserId) {
        friendService.deleteFriend(currentUid, friendUserId);
        return ResponseEntity.noContent().build();
    }
 
    // 21. 친구에게 메시지 보내기
    // POST /friends/{friendUserId}/messages
    @PostMapping("/{friendUserId}/messages")
    public ResponseEntity<FriendDto.SingleMessageResponse> sendMessage(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String friendUserId,
            @RequestBody FriendDto.MessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(friendService.sendMessage(currentUid, friendUserId, request));
    }
 
    // 22. 특정 친구와의 메시지 목록 조회
    // GET /friends/{friendUserId}/messages
    @GetMapping("/{friendUserId}/messages")
    public ResponseEntity<FriendDto.FriendMessagesResponse> getMessages(
            @RequestAttribute("currentUid") String currentUid,
            @PathVariable String friendUserId) {
        return ResponseEntity.ok(friendService.getMessages(currentUid, friendUserId));
    }
}
