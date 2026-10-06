package dev.plattnericus.cases.storage;

import java.util.UUID;

/** Audit log row: one per case opening, including test openings. */
public record OpeningRecord(UUID openingId, UUID owner, String ownerName, String caseId, UUID instanceId,
                            String skinId, String rarity, double floatValue, int pattern, boolean statTrak,
                            boolean test, long openedAt) {
}
