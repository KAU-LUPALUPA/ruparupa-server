package com.example.demo.service;

import com.example.demo.dto.KakaoPayDto;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Service
public class KakaoPayService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String ADMIN_KEY = "bbcc21b1f3e18ef22409917d4977c94e";
    private final String CID = "TC0ONETIME"; // 테스트용 가맹점 코드

    private final Map<String, String> tidStorage = new HashMap<>();
    private final Map<String, String> orderStorage = new HashMap<>();

    public KakaoPayDto.ReadyResponse payReady(String uid, KakaoPayDto.ReadyRequest request) {

        String partnerOrderId = "order_" + uid + "_" + System.currentTimeMillis();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "SECRET_KEY " + ADMIN_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> params = new HashMap<>();
        params.put("cid", CID);
        params.put("partner_order_id", partnerOrderId);
        params.put("partner_user_id", uid);
        params.put("item_name", request.getItemName());
        params.put("quantity", request.getQuantity());
        params.put("total_amount", request.getTotalAmount());
        params.put("tax_free_amount", 0);
        params.put("approval_url", "http://localhost:8080/payment/success?uid=" + uid);
        params.put("cancel_url", "http://localhost:8080/payment/cancel");
        params.put("fail_url", "http://localhost:8080/payment/fail");

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(params, headers);

        KakaoPayDto.ReadyResponse readyResponse = restTemplate.postForObject(
                "https://open-api.kakaopay.com/online/v1/ddp/ready",
                requestEntity,
                KakaoPayDto.ReadyResponse.class
        );

        if (readyResponse != null) {
            tidStorage.put(uid, readyResponse.getTid());
            orderStorage.put(uid, partnerOrderId);
            return readyResponse;
        }

        throw new RuntimeException("카카오페이 결제 준비 생성 실패");
    }

    public KakaoPayDto.ApproveResponse payApprove(String uid, String pgToken) {
        String tid = tidStorage.get(uid);
        String partnerOrderId = orderStorage.get(uid);

        if (tid == null || partnerOrderId == null) {
            throw new IllegalArgumentException("진행 중인 결제 세션 정보가 존재하지 않습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "SECRET_KEY " + ADMIN_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> params = new HashMap<>();
        params.put("cid", CID);
        params.put("tid", tid);
        params.put("partner_order_id", partnerOrderId);
        params.put("partner_user_id", uid);
        params.put("pg_token", pgToken);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(params, headers);

        KakaoPayDto.ApproveResponse approveResponse = restTemplate.postForObject(
                "https://open-api.kakaopay.com/online/v1/ddp/approve",
                requestEntity,
                KakaoPayDto.ApproveResponse.class
        );

        if (approveResponse != null) {
            tidStorage.remove(uid);
            orderStorage.remove(uid);
            return approveResponse;
        }

        throw new RuntimeException("카카오페이 최종 결제 승인 실패");
    }
}