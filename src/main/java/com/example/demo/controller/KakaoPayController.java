package com.example.demo.controller;

import com.example.demo.dto.KakaoPayDto;
import com.example.demo.service.KakaoPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment")
public class KakaoPayController {

    private final KakaoPayService kakaoPayService;



    @PostMapping("/ready")
    public ResponseEntity<KakaoPayDto.ReadyResponse> ready(@RequestParam String uid,
                                                           @RequestBody KakaoPayDto.ReadyRequest request) {
        log.info("🎯 [결제 준비 요청 진입] - 유저 UID: {}, 구매 아이템명: {}, 총 금액: {}원",
                uid, request.getItemName(), request.getTotalAmount());

        KakaoPayDto.ReadyResponse response = kakaoPayService.payReady(uid, request);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/success")
    public ResponseEntity<String> success(@RequestParam("uid") String uid,
                                          @RequestParam("pg_token") String pgToken) {
        log.info("📱 [카카오톡 결제 인증 성공] - 리다이렉트 완료. UID: {}, pg_token: {}", uid, pgToken);

        KakaoPayDto.ApproveResponse approveResponse = kakaoPayService.payApprove(uid, pgToken);

        log.info("💰 [최종 결제 승인 완료] 카카오페이 실인출 성공! TID: {}", approveResponse.getTid());



        log.info("🎁 [아이템 지급 처리] 유저(UID: {})의 인벤토리에 '{}' 아이템 지급을 시도합니다.", uid, approveResponse.getItem_name());

        return ResponseEntity.ok("결제가 성공적으로 완료되었습니다! 게임 화면으로 돌아가서 아이템을 확인해 보세요.");
    }



    @GetMapping("/cancel")
    public ResponseEntity<String> cancel() {
        log.warn("❌ 유저가 결제를 취소하였습니다.");
        return ResponseEntity.badRequest().body("결제가 취소되었습니다. 상점으로 돌아갑니다.");
    }


    @GetMapping("/fail")
    public ResponseEntity<String> fail() {
        log.error("🚨 결제 진행 중 오류가 발생하여 결제에 실패했습니다.");
        return ResponseEntity.badRequest().body("결제 실패. 다시 시도해 주세요.");
    }
}