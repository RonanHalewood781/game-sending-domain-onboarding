package dev.gameops.domainmail.domain;

import java.util.Objects;

public record GameMailWorkload(
        PlayerGeneratedAsset asset,
        LiveEvent event,
        ModerationQueue moderationQueue) {

    public GameMailWorkload {
        Objects.requireNonNull(asset);
        Objects.requireNonNull(event);
        Objects.requireNonNull(moderationQueue);
    }

    public record PlayerGeneratedAsset(String assetId, String ownerPlayerId) {
        public PlayerGeneratedAsset {
            requireText(assetId, "assetId");
            requireText(ownerPlayerId, "ownerPlayerId");
        }
    }

    public record LiveEvent(String eventId, String displayName) {
        public LiveEvent {
            requireText(eventId, "eventId");
            requireText(displayName, "displayName");
        }
    }

    public record ModerationQueue(String queueId, int pendingReviews) {
        public ModerationQueue {
            requireText(queueId, "queueId");
            if (pendingReviews < 0) throw new IllegalArgumentException("pendingReviews must be non-negative");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
