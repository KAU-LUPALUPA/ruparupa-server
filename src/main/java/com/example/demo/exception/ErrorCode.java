package com.example.demo.exception;
 
import lombok.Getter;
import lombok.RequiredArgsConstructor;
 
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 친구 코드 관련
    EMPTY_CODE(400, "코드가 비어 있습니다."),
    SELF_CODE(400, "자신의 코드는 입력할 수 없습니다."),
 
    // 유저/친구 조회
    USER_NOT_FOUND(404, "해당 유저를 찾을 수 없습니다."),
    FRIEND_NOT_FOUND(404, "친구 정보를 찾을 수 없습니다."),
    NOT_FRIENDS(403, "친구 관계가 아니라 접근할 수 없습니다."),
    ALREADY_FRIENDS(409, "이미 친구인 유저입니다."),
 
    // 친구 요청
    REQUEST_ALREADY_SENT(409, "이미 내가 보낸 친구 요청이 있습니다."),
    REQUEST_ALREADY_RECEIVED(409, "이미 상대에게서 받은 요청이 있습니다."),
    REQUEST_NOT_FOUND(404, "친구 요청을 찾을 수 없습니다."),
    REQUEST_NOT_PENDING(409, "이미 처리된 친구 요청입니다."),
 
    // 친구 집 방문
    HOME_INVITATION_ALREADY_SENT(409, "이미 보낸 집 초대가 있습니다."),
    HOME_INVITATION_NOT_FOUND(404, "집 초대를 찾을 수 없습니다."),
    HOME_INVITATION_NOT_PENDING(409, "이미 처리되었거나 만료된 집 초대입니다."),
    NOT_HOME_INVITATION_RECEIVER(403, "내가 받은 집 초대만 수락할 수 있습니다."),
    NOT_HOME_INVITATION_SENDER(403, "내가 보낸 집 초대만 취소할 수 있습니다."),
    FRIEND_HOME_UNAVAILABLE(503, "친구 집 정보를 불러올 수 없습니다."),
    HOME_VISIT_ALREADY_ACTIVE(409, "이미 진행 중인 친구 집 방문이 있습니다."),
    HOME_VISIT_NOT_FOUND(404, "친구 집 방문 세션을 찾을 수 없습니다."),
    HOME_VISIT_NOT_ACTIVE(409, "진행 중인 친구 집 방문이 아닙니다."),
    NOT_HOME_VISIT_PARTICIPANT(403, "해당 친구 집 방문 참가자가 아닙니다."),
 
    // 메시지
    EMPTY_MESSAGE(400, "메시지가 비어 있습니다."),
    MESSAGE_TOO_LONG(400, "메시지가 최대 길이를 초과합니다."),

    // 광장
    INVALID_PLAZA_CODE(400, "광장 코드 형식이 올바르지 않습니다."),
    PLAZA_NOT_FOUND(404, "광장을 찾을 수 없습니다."),
    PLAZA_FULL(409, "광장 정원이 가득 찼습니다."),
    NOT_IN_PLAZA(403, "해당 광장 참가자가 아닙니다."),
    PET_NOT_FOUND(404, "펫 정보를 찾을 수 없습니다."),

    // 방 레이아웃
    ROOM_NOT_FOUND(404, "방 정보를 찾을 수 없습니다."),
    INVALID_ROOM_LAYOUT(400, "방 레이아웃 형식이 올바르지 않습니다."),
    ROOM_LAYOUT_CONFLICT(409, "서버의 방 레이아웃이 더 최신입니다."),
 
    // 공통
    BLOCKED(403, "접근 권한이 없습니다."),
    UNKNOWN(500, "서버 내부 오류가 발생했습니다.");
 
    private final int status;
    private final String message;
}
