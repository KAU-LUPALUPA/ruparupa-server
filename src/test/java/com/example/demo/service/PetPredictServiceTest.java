package com.example.demo.service;

import com.example.demo.dto.PetPredictDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PetPredictServiceTest {

    @InjectMocks
    private PetPredictService petPredictService;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(petPredictService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(petPredictService, "aiServerUrl", "http://test-ai-server/predict/details");
    }

    @Test
    void testPredictAction_Success() {
        // Arrange
        PetPredictDto.Request request = new PetPredictDto.Request();
        request.setCleanliness(0.8);
        request.setFullness(0.7);

        PetPredictDto.Response mockResponse = PetPredictDto.Response.builder()
                .actionId(1)
                .actionName("WANDER")
                .description("방 안을 배회(WANDER)합니다.")
                .build();

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(PetPredictDto.Response.class)))
                .thenReturn(mockResponse);

        // Act
        PetPredictDto.Response result = petPredictService.predictAction(request);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getActionId());
        assertEquals("WANDER", result.getActionName());
        assertEquals("방 안을 배회(WANDER)합니다.", result.getDescription());
    }

    @Test
    void testPredictAction_Failure() {
        // Arrange
        PetPredictDto.Request request = new PetPredictDto.Request();
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(PetPredictDto.Response.class)))
                .thenThrow(new RuntimeException("Connection timed out"));

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            petPredictService.predictAction(request);
        });
        assertTrue(exception.getMessage().contains("AI 서버와의 통신에 실패했습니다"));
    }
}
