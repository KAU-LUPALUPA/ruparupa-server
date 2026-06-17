package com.example.demo.service;

import com.example.demo.dto.RoomLayoutRequestDto;
import com.example.demo.dto.RoomLayoutResponseDto;
import com.example.demo.dto.RoomLayoutValidationRequestDto;
import com.example.demo.dto.RoomLayoutValidationResponseDto;
import com.example.demo.entity.Room;
import com.example.demo.entity.RoomFurniture;
import com.example.demo.exception.CustomApiException;
import com.example.demo.exception.ErrorCode;
import com.example.demo.repository.RoomFurnitureRepository;
import com.example.demo.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class RoomServiceTest {
    private static final String OWNER_UID = "room-layout-owner";
    private static final String ROOM_ID = "room-layout-owner-0000000001";

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomFurnitureRepository roomFurnitureRepository;

    @BeforeEach
    void setUp() {
        roomFurnitureRepository.deleteAll();
        roomRepository.deleteAll();
    }

    @Test
    void validateMyRoomLayoutReturnsMatchWhenRevisionAndHashAreCurrent() {
        seedRoomWithFurniture();
        RoomLayoutResponseDto.LayoutData currentLayout = roomService
                .getMyRoomLayout(OWNER_UID)
                .getRoomLayout();

        RoomLayoutValidationResponseDto response = roomService.validateMyRoomLayout(
                OWNER_UID,
                RoomLayoutValidationRequestDto.builder()
                        .localLayoutRevision(currentLayout.getLayoutRevision())
                        .localLayoutHash(currentLayout.getLayoutHash())
                        .localUpdatedAt("stale-but-not-used-for-match")
                        .build()
        );

        assertEquals("MATCH", response.getSyncStatus());
        assertEquals(currentLayout.getLayoutRevision(), response.getServerLayoutRevision());
        assertEquals(currentLayout.getLayoutHash(), response.getServerLayoutHash());
        assertNull(response.getRoomLayout());
    }

    @Test
    void validateMyRoomLayoutReturnsServerUpdatedWithLatestLayoutWhenHashDiffers() {
        seedRoomWithFurniture();
        RoomLayoutResponseDto.LayoutData currentLayout = roomService
                .getMyRoomLayout(OWNER_UID)
                .getRoomLayout();

        RoomLayoutValidationResponseDto response = roomService.validateMyRoomLayout(
                OWNER_UID,
                RoomLayoutValidationRequestDto.builder()
                        .localLayoutRevision(currentLayout.getLayoutRevision())
                        .localLayoutHash("sha256:outdated")
                        .localUpdatedAt(currentLayout.getUpdatedAt().toString())
                        .build()
        );

        assertEquals("SERVER_UPDATED", response.getSyncStatus());
        assertEquals(currentLayout.getLayoutRevision(), response.getServerLayoutRevision());
        assertEquals(currentLayout.getLayoutHash(), response.getServerLayoutHash());
        assertNotNull(response.getRoomLayout());
        assertEquals(ROOM_ID, response.getRoomLayout().getRoomId());
    }

    @Test
    void saveMyRoomLayoutIncrementsRevisionAndChangesHash() {
        seedRoomWithFurniture();
        RoomLayoutResponseDto.LayoutData before = roomService
                .getMyRoomLayout(OWNER_UID)
                .getRoomLayout();

        RoomLayoutResponseDto.LayoutData after = roomService.saveMyRoomLayout(
                OWNER_UID,
                RoomLayoutRequestDto.builder()
                        .baseLayoutRevision(before.getLayoutRevision())
                        .wallAssetKey(before.getWallAssetKey())
                        .floorAssetKey(before.getFloorAssetKey())
                        .placedItems(List.of(
                                placedItem("BED", 2, 2, 2, 2),
                                placedItem("TOY_BOX", 0, 4, 1, 1)
                        ))
                        .build()
        ).getRoomLayout();

        assertEquals(before.getLayoutRevision() + 1, after.getLayoutRevision());
        assertNotEquals(before.getLayoutHash(), after.getLayoutHash());
        assertEquals(2, after.getPlacedItems().size());
    }

    @Test
    void getMyRoomLayoutRepairsOutOfBoundsFurnitureCoordinates() {
        seedRoomWithFurniture();
        Room room = roomRepository.findByOwnerUserId(OWNER_UID).orElseThrow();
        RoomFurniture bed = roomFurnitureRepository.findByRoomId(room.getRoomId()).stream()
                .filter(furniture -> "BED".equals(furniture.getType()))
                .findFirst()
                .orElseThrow();
        bed.setX(99);
        bed.setY(-3);
        roomFurnitureRepository.save(bed);

        RoomLayoutResponseDto.LayoutData repairedLayout = roomService
                .getMyRoomLayout(OWNER_UID)
                .getRoomLayout();
        RoomLayoutResponseDto.PlacedRoomItem repairedBed = placedItemByType(repairedLayout, "BED");

        assertEquals(0, repairedBed.getTilePlacement().getTile().getX());
        assertEquals(0, repairedBed.getTilePlacement().getTile().getY());
        assertEquals(4, repairedLayout.getLayoutRevision());
    }

    @Test
    void getMyRoomLayoutRepairsOverlappingFurnitureCoordinates() {
        seedRoomWithFurniture();
        Room room = roomRepository.findByOwnerUserId(OWNER_UID).orElseThrow();
        RoomFurniture toyBox = roomFurnitureRepository.findByRoomId(room.getRoomId()).stream()
                .filter(furniture -> "TOY_BOX".equals(furniture.getType()))
                .findFirst()
                .orElseThrow();
        toyBox.setX(1);
        toyBox.setY(1);
        roomFurnitureRepository.save(toyBox);

        RoomLayoutResponseDto.LayoutData repairedLayout = roomService
                .getMyRoomLayout(OWNER_UID)
                .getRoomLayout();
        RoomLayoutResponseDto.PlacedRoomItem repairedToyBox = placedItemByType(repairedLayout, "TOY_BOX");

        assertEquals(0, repairedToyBox.getTilePlacement().getTile().getX());
        assertEquals(4, repairedToyBox.getTilePlacement().getTile().getY());
        assertTrue(repairedLayout.getLayoutHash().startsWith("sha256:"));
    }

    @Test
    void saveMyRoomLayoutRejectsConflictingBaseRevision() {
        seedRoomWithFurniture();

        CustomApiException exception = assertThrows(
                CustomApiException.class,
                () -> roomService.saveMyRoomLayout(
                        OWNER_UID,
                        RoomLayoutRequestDto.builder()
                                .baseLayoutRevision(0)
                                .placedItems(List.of(placedItem("BED", 0, 0, 2, 2)))
                                .build()
                )
        );

        assertEquals(ErrorCode.ROOM_LAYOUT_CONFLICT, exception.getErrorCode());
    }

    @Test
    void saveMyRoomLayoutRejectsOverlappingItems() {
        seedRoomWithFurniture();
        int currentRevision = roomService.getMyRoomLayout(OWNER_UID)
                .getRoomLayout()
                .getLayoutRevision();

        CustomApiException exception = assertThrows(
                CustomApiException.class,
                () -> roomService.saveMyRoomLayout(
                        OWNER_UID,
                        RoomLayoutRequestDto.builder()
                                .baseLayoutRevision(currentRevision)
                                .placedItems(List.of(
                                        placedItem("BED", 0, 0, 2, 2),
                                        placedItem("TOY_BOX", 1, 1, 1, 1)
                                ))
                                .build()
                )
        );

        assertEquals(ErrorCode.INVALID_ROOM_LAYOUT, exception.getErrorCode());
    }

    @Test
    void saveMyRoomLayoutRejectsOutOfBoundsAndFootprintMismatch() {
        seedRoomWithFurniture();
        int currentRevision = roomService.getMyRoomLayout(OWNER_UID)
                .getRoomLayout()
                .getLayoutRevision();

        CustomApiException outOfBounds = assertThrows(
                CustomApiException.class,
                () -> roomService.saveMyRoomLayout(
                        OWNER_UID,
                        RoomLayoutRequestDto.builder()
                                .baseLayoutRevision(currentRevision)
                                .placedItems(List.of(placedItem("BED", 5, 5, 2, 2)))
                                .build()
                )
        );
        CustomApiException footprintMismatch = assertThrows(
                CustomApiException.class,
                () -> roomService.saveMyRoomLayout(
                        OWNER_UID,
                        RoomLayoutRequestDto.builder()
                                .baseLayoutRevision(currentRevision)
                                .placedItems(List.of(placedItem("BED", 0, 0, 1, 1)))
                                .build()
                )
        );

        assertEquals(ErrorCode.INVALID_ROOM_LAYOUT, outOfBounds.getErrorCode());
        assertEquals(ErrorCode.INVALID_ROOM_LAYOUT, footprintMismatch.getErrorCode());
    }

    private void seedRoomWithFurniture() {
        Room room = Room.builder()
                .roomId(ROOM_ID)
                .ownerUserId(OWNER_UID)
                .sceneId("main_room")
                .layoutRevision(3)
                .wallAssetKey("room/walls/main_wall")
                .floorAssetKey("room/floors/main_floor")
                .updatedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        roomRepository.save(room);

        RoomFurniture bed = furniture("BED", 0, 0);
        RoomFurniture toyBox = furniture("TOY_BOX", 0, 4);
        roomFurnitureRepository.saveAll(List.of(bed, toyBox));
    }

    private RoomFurniture furniture(String type, int x, int y) {
        RoomFurniture furniture = new RoomFurniture();
        furniture.setRoomId(ROOM_ID);
        furniture.setType(type);
        furniture.setX(x);
        furniture.setY(y);
        furniture.setDirection(0);
        furniture.setStatus("unused");
        return furniture;
    }

    private RoomLayoutRequestDto.PlacedRoomItem placedItem(
            String type,
            int x,
            int y,
            int widthTiles,
            int depthTiles
    ) {
        return RoomLayoutRequestDto.PlacedRoomItem.builder()
                .placementId("placement_" + type.toLowerCase())
                .shopItemId(type)
                .assetKey("room/objects/" + type.toLowerCase())
                .type(type)
                .anchorType("FLOOR")
                .tilePlacement(RoomLayoutRequestDto.TilePlacement.builder()
                        .tile(RoomLayoutRequestDto.TileCoord.builder()
                                .x(x)
                                .y(y)
                                .build())
                        .footprint(RoomLayoutRequestDto.TileFootprint.builder()
                                .widthTiles(widthTiles)
                                .depthTiles(depthTiles)
                                .build())
                        .anchorMode("CENTER")
                        .build())
                .rotation(0)
                .build();
    }

    private RoomLayoutResponseDto.PlacedRoomItem placedItemByType(
            RoomLayoutResponseDto.LayoutData layout,
            String type
    ) {
        return layout.getPlacedItems().stream()
                .filter(item -> type.equals(item.getType()))
                .findFirst()
                .orElseThrow();
    }
}
