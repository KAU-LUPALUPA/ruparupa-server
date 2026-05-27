package com.example.demo.entity;

/**
 * 콘테스트 그룹 상태
 * OPEN   : 참가 인원 모집 중 (1~2명)
 * ACTIVE : 3명이 모두 모여 투표 진행 중
 * CLOSED : 자정에 스케줄러에 의해 종료됨
 */

public enum ContestGroupStatus {
    OPEN,
    ACTIVE,
    CLOSED
}