package com.example.demo.config;

import com.example.demo.JwtUtil;
import com.example.demo.User;
import com.example.demo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class UserInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil; 

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        
        // 1. HTTP 헤더에서 "Authorization" 추출
        String authHeader = request.getHeader("Authorization");

        // 2. 토큰이 없거나 "Bearer "로 시작하지 않으면 쫓아냄
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\": \"UNAUTHORIZED\", \"message\": \"인증 토큰이 없거나 형식이 잘못되었습니다.\"}");
            return false;
        }

        // 3. "Bearer " 글자를 떼고 진짜 암호문(토큰)만 가져옴
        String token = authHeader.substring(7);

        try {
            // 4. 토큰 만료 여부 검사
            if (jwtUtil.isTokenExpired(token)) {
                throw new RuntimeException("토큰이 만료되었습니다.");
            }

            // 5. 토큰에서 UID 해독
            String uid = jwtUtil.extractUid(token);

            // 6. 진짜 존재하는 유저인지 확인
            User user = userRepository.findByUid(uid)
                    .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));

            // 7. 통과! 컨트롤러에서 쓰기 편하게 request 안에 사용자 정보를 넣어둠
            request.setAttribute("currentUid", user.getUid());
            request.setAttribute("currentNickname", user.getNickname());
            return true;

        } catch (Exception e) {
            // 토큰이 위조되었거나 에러가 나면 401 에러 반환
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\": \"INVALID_TOKEN\", \"message\": \"유효하지 않은 토큰입니다.\"}");
            return false;
        }
    }
}
