package com.example.demo.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomLayoutRequestDto {
    private Integer baseLayoutRevision;
    private String wallAssetKey;
    private String floorAssetKey;
    private List<PlacedRoomItem> placedItems;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlacedRoomItem {
        private String placementId;
        private String inventoryItemId;
        private String shopItemId;
        private String assetKey;
        private String type;
        private String anchorType;
        private TilePlacement tilePlacement;
        private WallPlacement wallPlacement;
        private Float scale;
        private Integer rotation;
        private Float depthBias;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TilePlacement {
        private TileCoord tile;
        private TileFootprint footprint;
        private String anchorMode;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TileCoord {
        private Integer x;
        private Integer y;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TileFootprint {
        private Integer widthTiles;
        private Integer depthTiles;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WallPlacement {
        private String face;
        private Float u;
        private Float v;
    }
}
