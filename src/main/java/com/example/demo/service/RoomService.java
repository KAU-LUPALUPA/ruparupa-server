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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {
    private static final int ROOM_WIDTH_TILES = 6;
    private static final int ROOM_DEPTH_TILES = 6;
    private static final String DEFAULT_SCENE_ID = "main_room";
    private static final String DEFAULT_WALL_ASSET_KEY = "room/walls/main_wall";
    private static final String DEFAULT_FLOOR_ASSET_KEY = "room/floors/main_floor";

    private final RoomRepository roomRepository;
    private final RoomFurnitureRepository roomFurnitureRepository;

    @Transactional(readOnly = true)
    public RoomLayoutResponseDto getMyRoomLayout(String currentUid) {
        Room room = getRoomForOwner(currentUid);
        List<RoomFurniture> furnitureList = roomFurnitureRepository.findByRoomId(room.getRoomId());
        return toResponse(room, furnitureList);
    }

    @Transactional(readOnly = true)
    public RoomLayoutValidationResponseDto validateMyRoomLayout(
            String currentUid,
            RoomLayoutValidationRequestDto request
    ) {
        Room room = getRoomForOwner(currentUid);
        List<RoomFurniture> furnitureList = roomFurnitureRepository.findByRoomId(room.getRoomId());
        RoomLayoutResponseDto.LayoutData serverLayout = toResponse(room, furnitureList).getRoomLayout();

        boolean layoutMatches = request != null &&
                request.getLocalLayoutRevision() != null &&
                request.getLocalLayoutRevision() == serverLayout.getLayoutRevision() &&
                request.getLocalLayoutHash() != null &&
                request.getLocalLayoutHash().equals(serverLayout.getLayoutHash());

        return RoomLayoutValidationResponseDto.builder()
                .syncStatus(layoutMatches ? "MATCH" : "SERVER_UPDATED")
                .serverLayoutRevision(serverLayout.getLayoutRevision())
                .serverLayoutHash(serverLayout.getLayoutHash())
                .serverUpdatedAt(serverLayout.getUpdatedAt())
                .roomLayout(layoutMatches ? null : serverLayout)
                .build();
    }

    @Transactional
    public RoomLayoutResponseDto saveMyRoomLayout(String currentUid, RoomLayoutRequestDto request) {
        if (request == null) {
            throw new CustomApiException(ErrorCode.INVALID_ROOM_LAYOUT);
        }

        Room room = getRoomForOwner(currentUid);
        Integer baseLayoutRevision = request.getBaseLayoutRevision();
        if (baseLayoutRevision != null && baseLayoutRevision != room.getLayoutRevision()) {
            throw new CustomApiException(ErrorCode.ROOM_LAYOUT_CONFLICT);
        }

        List<LayoutItem> layoutItems = toLayoutItems(request.getPlacedItems());
        validateNoOverlap(layoutItems);

        roomFurnitureRepository.deleteByRoomId(room.getRoomId());
        List<RoomFurniture> savedFurniture = roomFurnitureRepository.saveAll(
                layoutItems.stream()
                        .map(LayoutItem::furniture)
                        .peek(furniture -> furniture.setRoomId(room.getRoomId()))
                        .collect(Collectors.toList())
        );

        room.setSceneId(defaultIfBlank(room.getSceneId(), DEFAULT_SCENE_ID));
        room.setWallAssetKey(defaultIfBlank(request.getWallAssetKey(), DEFAULT_WALL_ASSET_KEY));
        room.setFloorAssetKey(defaultIfBlank(request.getFloorAssetKey(), DEFAULT_FLOOR_ASSET_KEY));
        room.setLayoutRevision(room.getLayoutRevision() + 1);
        room.setUpdatedAt(LocalDateTime.now());
        Room savedRoom = roomRepository.save(room);

        return toResponse(savedRoom, savedFurniture);
    }

    private Room getRoomForOwner(String currentUid) {
        return roomRepository.findByOwnerUserId(currentUid)
                .orElseThrow(() -> new CustomApiException(ErrorCode.ROOM_NOT_FOUND));
    }

    private List<LayoutItem> toLayoutItems(List<RoomLayoutRequestDto.PlacedRoomItem> placedItems) {
        if (placedItems == null) {
            return List.of();
        }

        List<LayoutItem> layoutItems = new ArrayList<>();
        for (RoomLayoutRequestDto.PlacedRoomItem item : placedItems) {
            layoutItems.add(toLayoutItem(item));
        }
        return layoutItems;
    }

    private LayoutItem toLayoutItem(RoomLayoutRequestDto.PlacedRoomItem item) {
        if (item == null) {
            throw new CustomApiException(ErrorCode.INVALID_ROOM_LAYOUT);
        }

        String type = normalizeType(item.getType());
        if ("WINDOW".equals(type)) {
            throw new CustomApiException(ErrorCode.INVALID_ROOM_LAYOUT);
        }

        String anchorType = defaultIfBlank(item.getAnchorType(), "FLOOR").toUpperCase(Locale.ROOT);
        if (!"FLOOR".equals(anchorType)) {
            throw new CustomApiException(ErrorCode.INVALID_ROOM_LAYOUT);
        }

        RoomLayoutRequestDto.TilePlacement tilePlacement = item.getTilePlacement();
        if (tilePlacement == null || tilePlacement.getTile() == null) {
            throw new CustomApiException(ErrorCode.INVALID_ROOM_LAYOUT);
        }

        Integer x = tilePlacement.getTile().getX();
        Integer y = tilePlacement.getTile().getY();
        if (x == null || y == null) {
            throw new CustomApiException(ErrorCode.INVALID_ROOM_LAYOUT);
        }

        TileFootprint footprint = resolveFootprint(type, tilePlacement.getFootprint());
        validateTileBounds(x, y, footprint);

        RoomFurniture furniture = new RoomFurniture();
        furniture.setType(type);
        furniture.setX(x);
        furniture.setY(y);
        furniture.setDirection(item.getRotation() == null ? 0 : item.getRotation());
        furniture.setStatus("unused");

        String anchorMode = defaultIfBlank(tilePlacement.getAnchorMode(), "CENTER");
        return new LayoutItem(furniture, footprint.widthTiles(), footprint.depthTiles(), anchorMode);
    }

    private void validateTileBounds(int x, int y, TileFootprint footprint) {
        if (
                x < 0 ||
                y < 0 ||
                footprint.widthTiles() <= 0 ||
                footprint.depthTiles() <= 0 ||
                x + footprint.widthTiles() > ROOM_WIDTH_TILES ||
                y + footprint.depthTiles() > ROOM_DEPTH_TILES
        ) {
            throw new CustomApiException(ErrorCode.INVALID_ROOM_LAYOUT);
        }
    }

    private void validateNoOverlap(List<LayoutItem> layoutItems) {
        for (int i = 0; i < layoutItems.size(); i++) {
            LayoutItem first = layoutItems.get(i);
            for (int j = i + 1; j < layoutItems.size(); j++) {
                LayoutItem second = layoutItems.get(j);
                if (overlaps(first, second)) {
                    throw new CustomApiException(ErrorCode.INVALID_ROOM_LAYOUT);
                }
            }
        }
    }

    private boolean overlaps(LayoutItem first, LayoutItem second) {
        RoomFurniture a = first.furniture();
        RoomFurniture b = second.furniture();
        return a.getX() < b.getX() + second.widthTiles() &&
                a.getX() + first.widthTiles() > b.getX() &&
                a.getY() < b.getY() + second.depthTiles() &&
                a.getY() + first.depthTiles() > b.getY();
    }

    private RoomLayoutResponseDto toResponse(Room room, List<RoomFurniture> furnitureList) {
        List<RoomLayoutResponseDto.PlacedRoomItem> placedItems = furnitureList.stream()
                .sorted(Comparator.comparing(RoomFurniture::getType, Comparator.nullsLast(String::compareTo))
                        .thenComparing(RoomFurniture::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::toPlacedItem)
                .collect(Collectors.toList());

        String wallAssetKey = defaultIfBlank(room.getWallAssetKey(), DEFAULT_WALL_ASSET_KEY);
        String floorAssetKey = defaultIfBlank(room.getFloorAssetKey(), DEFAULT_FLOOR_ASSET_KEY);

        return RoomLayoutResponseDto.builder()
                .roomLayout(RoomLayoutResponseDto.LayoutData.builder()
                        .roomId(room.getRoomId())
                        .ownerUserId(room.getOwnerUserId())
                        .sceneId(defaultIfBlank(room.getSceneId(), DEFAULT_SCENE_ID))
                        .layoutRevision(room.getLayoutRevision())
                        .layoutHash(layoutHash(wallAssetKey, floorAssetKey, placedItems))
                        .wallAssetKey(wallAssetKey)
                        .floorAssetKey(floorAssetKey)
                        .placedItems(placedItems)
                        .updatedAt(room.getUpdatedAt())
                        .build())
                .build();
    }

    private RoomLayoutResponseDto.PlacedRoomItem toPlacedItem(RoomFurniture furniture) {
        String type = normalizeType(furniture.getType());
        TileFootprint footprint = defaultFootprintFor(type);

        return RoomLayoutResponseDto.PlacedRoomItem.builder()
                .placementId(placementIdFor(type))
                .inventoryItemId(null)
                .shopItemId(type)
                .assetKey(assetKeyFor(type))
                .type(type)
                .anchorType("FLOOR")
                .tilePlacement(RoomLayoutResponseDto.TilePlacement.builder()
                        .tile(RoomLayoutResponseDto.TileCoord.builder()
                                .x(furniture.getX())
                                .y(furniture.getY())
                                .build())
                        .footprint(RoomLayoutResponseDto.TileFootprint.builder()
                                .widthTiles(footprint.widthTiles())
                                .depthTiles(footprint.depthTiles())
                                .build())
                        .anchorMode("CENTER")
                        .build())
                .wallPlacement(null)
                .scale(1f)
                .rotation(furniture.getDirection())
                .depthBias(0f)
                .build();
    }

    private String layoutHash(
            String wallAssetKey,
            String floorAssetKey,
            List<RoomLayoutResponseDto.PlacedRoomItem> placedItems
    ) {
        StringBuilder raw = new StringBuilder();
        raw.append(wallAssetKey).append('|').append(floorAssetKey);
        placedItems.stream()
                .sorted(Comparator.comparing(RoomLayoutResponseDto.PlacedRoomItem::getPlacementId))
                .forEach(item -> {
                    RoomLayoutResponseDto.TilePlacement tilePlacement = item.getTilePlacement();
                    raw.append('|')
                            .append(item.getType()).append(':')
                            .append(tilePlacement.getTile().getX()).append(',')
                            .append(tilePlacement.getTile().getY()).append(',')
                            .append(tilePlacement.getFootprint().getWidthTiles()).append('x')
                            .append(tilePlacement.getFootprint().getDepthTiles());
                });

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new CustomApiException(ErrorCode.INVALID_ROOM_LAYOUT);
        }

        String normalized = type.trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        if ("FEED_BAG".equals(normalized)) {
            return "FOOD_BAG";
        }
        return normalized;
    }

    private TileFootprint resolveFootprint(
            String type,
            RoomLayoutRequestDto.TileFootprint requestFootprint
    ) {
        TileFootprint expectedFootprint = defaultFootprintFor(type);
        if (
                requestFootprint == null ||
                requestFootprint.getWidthTiles() == null ||
                requestFootprint.getDepthTiles() == null
        ) {
            return expectedFootprint;
        }

        if (
                requestFootprint.getWidthTiles() != expectedFootprint.widthTiles() ||
                requestFootprint.getDepthTiles() != expectedFootprint.depthTiles()
        ) {
            throw new CustomApiException(ErrorCode.INVALID_ROOM_LAYOUT);
        }

        return expectedFootprint;
    }

    private TileFootprint defaultFootprintFor(String type) {
        return switch (type) {
            case "BED" -> new TileFootprint(2, 2);
            case "TOY_BOX", "FOOD_BAG" -> new TileFootprint(1, 1);
            default -> new TileFootprint(1, 1);
        };
    }

    private String assetKeyFor(String type) {
        return switch (type) {
            case "BED" -> "room/objects/bed_basic";
            case "TOY_BOX" -> "room/objects/toy_box_basic";
            case "FOOD_BAG" -> "room/objects/food_bag_basic";
            default -> "room/objects/" + type.toLowerCase(Locale.ROOT);
        };
    }

    private String placementIdFor(String type) {
        return "placement_" + type.toLowerCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private record LayoutItem(
            RoomFurniture furniture,
            int widthTiles,
            int depthTiles,
            String anchorMode
    ) {
    }

    private record TileFootprint(int widthTiles, int depthTiles) {
    }
}
