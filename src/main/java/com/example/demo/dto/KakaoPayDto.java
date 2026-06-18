package com.example.demo.dto;

import lombok.Getter;
import lombok.Setter;

public class KakaoPayDto {


    @Getter @Setter
    public static class ReadyRequest {
        private String itemId;       // 아이템 고유 ID
        private String itemName;     // 아이템 이름
        private int totalAmount;     // 결제 금액
        private int quantity;        // 구매 수량
    }


    @Getter @Setter
    public static class ReadyResponse {
        private String tid;                      // 결제 고유 번호
        private String next_redirect_pc_url;      // 컴퓨터 결제 페이지 URL
        private String next_redirect_mobile_url;  // 모바일 결제 페이지 URL
        private String created_at;               // 결제 준비 요청 시간
    }


    @Getter @Setter
    public static class ApproveResponse {
        private String aid;
        private String tid;
        private String cid;
        private String partner_order_id;
        private String partner_user_id;
        private String payment_method_type;
        private Amount amount;
        private String item_name;
        private int quantity;
        private String created_at;
        private String approved_at;

        @Getter @Setter
        public static class Amount {
            private int total;
            private int tax_free;
            private int vat;
        }
    }
}