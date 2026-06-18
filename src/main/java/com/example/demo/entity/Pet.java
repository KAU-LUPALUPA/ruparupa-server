package com.example.demo.entity;

import com.example.demo.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 펫 고유 ID (uid-랜덤10자리)
    @Column(unique = true, nullable = false)
    private String petUid;

    @OneToOne(fetch = FetchType.LAZY)
    // referencedColumnName을 "uid"로 설정하여 User 테이블의 PK가 아닌 uid 필드를 참조
    @JoinColumn(name = "user_uid", referencedColumnName = "uid", nullable = false)
    private User user;

    private String name; 
    
    private String characterAssetKey;

    @Column(nullable = false)
    private String personality; 

    @Builder.Default
    @Column(nullable = false)
    private int satiety = 100;

    @Builder.Default
    @Column(nullable = false)
    private int vitality = 100; 

    @Builder.Default
    @Column(nullable = false)
    private int cleanliness = 100;

    @Embedded
    @Builder.Default
    private PetTraits traits = new PetTraits();

    @Embedded
    @Builder.Default
    private InteractionEvents interactionEvents = new InteractionEvents();

    @Builder.Default
    @Column(nullable = false)
    private LocalDate lastEmaUpdatedDate = LocalDate.now();

    @Builder.Default
    @Column(nullable = false)
    private boolean isSleep = false; 

    @Builder.Default
    @Column(nullable = false)
    private boolean isEgg = true;

    // 장착 아이템 ID 리스트 (별도 테이블로 관리되는 ElementCollection)
    @ElementCollection
    @CollectionTable(name = "pet_equipped_items", joinColumns = @JoinColumn(name = "pet_id"))
    @Column(name = "item_id")
    @Builder.Default
    private List<Long> equippedItemIds = new ArrayList<>();

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void generatePetUid(String userUid) {
        StringBuilder sb = new StringBuilder(userUid);
        sb.append("-");
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            sb.append(random.nextInt(10));
        }
        this.petUid = sb.toString();
    }
}