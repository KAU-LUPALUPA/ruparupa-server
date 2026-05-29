package com.example.demo.exception;
 
import com.example.demo.dto.FriendDto;
import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
 
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
 
    // 1. 커스텀 에러 (친구 API 등)
    @ExceptionHandler(CustomApiException.class)
    public ResponseEntity<FriendDto.ErrorResponse> handleCustomApiException(CustomApiException e) {
        FriendDto.ErrorResponse errorResponse = FriendDto.ErrorResponse.builder()
                .code(e.getErrorCode().name())
                .message(e.getMessage())
                .build();
 
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(errorResponse);
    }
 
    // 2. IllegalStateException / IllegalArgumentException
    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<FriendDto.ErrorResponse> handleStandardExceptions(RuntimeException e) {
        FriendDto.ErrorResponse errorResponse = FriendDto.ErrorResponse.builder()
                .code("BAD_REQUEST")
                .message(e.getMessage())
                .build();
 
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler({DataAccessException.class, PersistenceException.class})
    public ResponseEntity<FriendDto.ErrorResponse> handleDatabaseException(Exception e) {
        log.error("Database exception", e);

        FriendDto.ErrorResponse errorResponse = FriendDto.ErrorResponse.builder()
                .code(ErrorCode.DATABASE_ERROR.name())
                .message(toDebugMessage(ErrorCode.DATABASE_ERROR.getMessage(), e))
                .build();

        return ResponseEntity
                .status(ErrorCode.DATABASE_ERROR.getStatus())
                .body(errorResponse);
    }
 
    // 3. 그 외 분류되지 않은 모든 예외 → UNKNOWN으로 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<FriendDto.ErrorResponse> handleUnknownException(Exception e) {
        log.error("Unhandled server exception", e);

        FriendDto.ErrorResponse errorResponse = FriendDto.ErrorResponse.builder()
                .code(ErrorCode.UNKNOWN.name())
                .message(toDebugMessage(ErrorCode.UNKNOWN.getMessage(), e))
                .build();
 
        return ResponseEntity
                .status(ErrorCode.UNKNOWN.getStatus())
                .body(errorResponse);
    }

    private String toDebugMessage(String baseMessage, Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        String detail = rootCause.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = rootCause.getClass().getSimpleName();
        } else {
            detail = rootCause.getClass().getSimpleName() + ": " + detail;
        }

        return baseMessage + " (" + detail + ")";
    }
}
