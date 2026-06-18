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
import java.util.Objects;
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

    @Transactional
    public RoomLayoutResponseDto getMyRoomLayout(String currentUid) {
        Room room = getRoomForOwner(currentUid);
        List<RoomFurniture> furnitureList = repairFurnitureLayoutIfNeeded(
                room,
                roomFurnitureRepository.findByRoomId(room.getRoomId())
        );
        return toResponse(room, furnitureList);
    }

    @Transactional
    public RoomLayoutValidationResponseDto validateMyRoomLayout(
            String currentUid,
            RoomLayoutValidationRequestDto request
    ) {
        Room room = getRoomForOwner(currentUid);
        List<RoomFurniture> furnitureList = repairFurnitureLayoutIfNeeded(
                room,
                roomFurnitureRepository.findByRoomId(room.getRoomId())
        );
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

        String anchorMode = defaultIfBlank(tilePlacement.getAnchorMode(), defaultAnchorModeFor(type));
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

    private List<RoomFurniture> repairFurnitureLayoutIfNeeded(Room room, List<RoomFurniture> furnitureList) {
        if (furnitureList == null || furnitureList.isEmpty()) {
            return List.of();
        }

        List<RoomFurniture> sortedFurniture = furnitureList.stream()
                .sorted(Comparator.comparingInt(this::typePriority)
                        .thenComparing(RoomFurniture::getId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toCollection(ArrayList::new));

        List<LayoutItem> acceptedItems = new ArrayList<>();
        boolean migrateLegacyDefaultLayout = isLegacyDefaultLayout(sortedFurniture);
        boolean changed = false;

        for (RoomFurniture furniture : sortedFurniture) {
            String type = normalizeTypeOrNull(furniture.getType());
            if (type == null) {
                continue;
            }

            TileFootprint footprint = defaultFootprintFor(type);
            boolean itemChanged = false;

            if (!type.equals(furniture.getType())) {
                furniture.setType(type);
                itemChanged = true;
            }

            LayoutItem currentItem = new LayoutItem(
                    furniture,
                    footprint.widthTiles(),
                    footprint.depthTiles(),
                    defaultAnchorModeFor(type)
            );

            if (
                    hasManagedDefaultTile(type) &&
                            (room.getLayoutRevision() == 0 || migrateLegacyDefaultLayout) &&
                            !isAtDefaultTile(type, furniture)
            ) {
                TileCoord defaultTile = defaultTileFor(type);
                furniture.setX(defaultTile.x());
                furniture.setY(defaultTile.y());
                itemChanged = true;
            }

            if (!isWithinBounds(furniture.getX(), furniture.getY(), footprint) ||
                    overlapsAny(currentItem, acceptedItems)) {
                TileCoord repairedTile = findAvailableRepairTile(type, furniture.getX(), furniture.getY(), footprint, acceptedItems);
                if (furniture.getX() != repairedTile.x() || furniture.getY() != repairedTile.y()) {
                    furniture.setX(repairedTile.x());
                    furniture.setY(repairedTile.y());
                    itemChanged = true;
                }
            }

            LayoutItem repairedItem = new LayoutItem(
                    furniture,
                    footprint.widthTiles(),
                    footprint.depthTiles(),
                    defaultAnchorModeFor(type)
            );
            acceptedItems.add(repairedItem);
            changed = changed || itemChanged;
        }

        if (changed) {
            roomFurnitureRepository.saveAll(
                    acceptedItems.stream()
                            .map(LayoutItem::furniture)
                            .collect(Collectors.toList())
            );
            room.setLayoutRevision(room.getLayoutRevision() + 1);
            room.setUpdatedAt(LocalDateTime.now());
            roomRepository.save(room);
        }

        return acceptedItems.stream()
                .map(LayoutItem::furniture)
                .collect(Collectors.toList());
    }

    private boolean overlapsAny(LayoutItem item, List<LayoutItem> acceptedItems) {
        return acceptedItems.stream().anyMatch(accepted -> overlaps(item, accepted));
    }

    private boolean isWithinBounds(int x, int y, TileFootprint footprint) {
        return x >= 0 &&
                y >= 0 &&
                footprint.widthTiles() > 0 &&
                footprint.depthTiles() > 0 &&
                x + footprint.widthTiles() <= ROOM_WIDTH_TILES &&
                y + footprint.depthTiles() <= ROOM_DEPTH_TILES;
    }

    private TileCoord findAvailableRepairTile(
            String type,
            int originalX,
            int originalY,
            TileFootprint footprint,
            List<LayoutItem> acceptedItems
    ) {
        TileCoord defaultTile = defaultTileFor(type);
        if (canPlaceAt(defaultTile.x(), defaultTile.y(), footprint, acceptedItems)) {
            return defaultTile;
        }

        int clampedX = originalX;
        int clampedY = originalY;
        if (!isWithinBounds(originalX, originalY, footprint)) {
            clampedX = Math.max(0, Math.min(originalX, ROOM_WIDTH_TILES - footprint.widthTiles()));
            clampedY = Math.max(0, Math.min(originalY, ROOM_DEPTH_TILES - footprint.depthTiles()));
        }

        TileCoord bestTile = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = 0; y <= ROOM_DEPTH_TILES - footprint.depthTiles(); y++) {
            for (int x = 0; x <= ROOM_WIDTH_TILES - footprint.widthTiles(); x++) {
                if (!canPlaceAt(x, y, footprint, acceptedItems)) {
                    continue;
                }

                int distance = Math.abs(x - clampedX) + Math.abs(y - clampedY);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestTile = new TileCoord(x, y);
                }
            }
        }

        if (bestTile != null) {
            return bestTile;
        }

        return defaultTile;
    }

    private boolean canPlaceAt(
            int x,
            int y,
            TileFootprint footprint,
            List<LayoutItem> acceptedItems
    ) {
        if (!isWithinBounds(x, y, footprint)) {
            return false;
        }

        RoomFurniture probe = new RoomFurniture();
        probe.setX(x);
        probe.setY(y);
        LayoutItem probeItem = new LayoutItem(
                probe,
                footprint.widthTiles(),
                footprint.depthTiles(),
                "CENTER"
        );
        return !overlapsAny(probeItem, acceptedItems);
    }

    private RoomLayoutResponseDto toResponse(Room room, List<RoomFurniture> furnitureList) {
        List<RoomLayoutResponseDto.PlacedRoomItem> placedItems = furnitureList.stream()
                .sorted(Comparator.comparing(RoomFurniture::getType, Comparator.nullsLast(String::compareTo))
                        .thenComparing(RoomFurniture::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::toPlacedItemOrNull)
                .filter(Objects::nonNull)
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

    private RoomLayoutResponseDto.PlacedRoomItem toPlacedItemOrNull(RoomFurniture furniture) {
        String type = normalizeTypeOrNull(furniture.getType());
        if (type == null) {
            return null;
        }
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
                        .anchorMode(defaultAnchorModeFor(type))
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
                            .append(tilePlacement.getFootprint().getDepthTiles()).append(',')
                            .append(defaultIfBlank(tilePlacement.getAnchorMode(), defaultAnchorModeFor(item.getType())));
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
        String normalized = normalizeTypeOrNull(type);
        if (normalized == null) {
            throw new CustomApiException(ErrorCode.INVALID_ROOM_LAYOUT);
        }
        return normalized;
    }

    private String normalizeTypeOrNull(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }

        String normalized = type.trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        if ("FEED_BAG".equals(normalized)) {
            return "FOOD_BAG";
        }
        return normalized;
    }

    private int typePriority(RoomFurniture furniture) {
        String type = normalizeTypeOrNull(furniture.getType());
        if (type == null) {
            return Integer.MAX_VALUE;
        }

        return switch (type) {
            case "BED" -> 0;
            case "TOY_BOX" -> 1;
            case "FOOD_BAG" -> 2;
            default -> 10;
        };
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

    private TileCoord defaultTileFor(String type) {
        return switch (type) {
            case "BED" -> new TileCoord(2, 0);
            case "TOY_BOX" -> new TileCoord(1, 4);
            case "FOOD_BAG" -> new TileCoord(4, 3);
            default -> new TileCoord(0, 0);
        };
    }

    private TileCoord legacyDefaultTileFor(String type) {
        return switch (type) {
            case "BED" -> new TileCoord(0, 0);
            case "TOY_BOX" -> new TileCoord(0, 4);
            case "FOOD_BAG" -> new TileCoord(1, 3);
            default -> null;
        };
    }

    private boolean isLegacyDefaultLayout(List<RoomFurniture> furnitureList) {
        boolean hasBed = false;
        boolean hasToyBox = false;
        boolean hasFoodBag = false;

        for (RoomFurniture furniture : furnitureList) {
            String type = normalizeTypeOrNull(furniture.getType());
            if (type == null) {
                continue;
            }

            TileCoord legacyTile = legacyDefaultTileFor(type);
            if (legacyTile == null) {
                continue;
            }

            if (!isAtTile(furniture, legacyTile)) {
                return false;
            }

            if ("BED".equals(type)) {
                hasBed = true;
            } else if ("TOY_BOX".equals(type)) {
                hasToyBox = true;
            } else if ("FOOD_BAG".equals(type)) {
                hasFoodBag = true;
            }
        }

        return hasBed && hasToyBox && hasFoodBag;
    }

    private boolean hasManagedDefaultTile(String type) {
        return legacyDefaultTileFor(type) != null;
    }

    private boolean isAtDefaultTile(String type, RoomFurniture furniture) {
        TileCoord defaultTile = defaultTileFor(type);
        return isAtTile(furniture, defaultTile);
    }

    private boolean isAtTile(RoomFurniture furniture, TileCoord tile) {
        return furniture.getX() == tile.x() && furniture.getY() == tile.y();
    }

    private String defaultAnchorModeFor(String type) {
        return "BED".equals(type) ? "FRONT_CENTER" : "CENTER";
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

    private record TileCoord(int x, int y) {
    }
}
