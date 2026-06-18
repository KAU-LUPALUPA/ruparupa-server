package com.example.demo.service;

import com.example.demo.dto.PetPredictDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetPredictService {

    private final RestTemplate restTemplate = createRestTemplate();

    @Value("${ai.server.url}")
    private String aiServerUrl;

    private static RestTemplate createRestTemplate() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofMillis(1500)); // AI 서버 타임아웃 1.5초 설정
        return new RestTemplate(requestFactory);
    }

    public PetPredictDto.Response predictAction(PetPredictDto.Request request) {
        log.info("Sending prediction request to AI server: {}", request);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<PetPredictDto.Request> entity = new HttpEntity<>(request, headers);
            
            PetPredictDto.Response response = restTemplate.postForObject(aiServerUrl, entity, PetPredictDto.Response.class);
            log.info("Received prediction response from AI server: {}", response);
            return response;
        } catch (Exception e) {
            log.error("Error communicating with AI server: {}", e.getMessage(), e);
            throw new RuntimeException("AI 서버와의 통신에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
