package dev.plattnericus.cases.inspect;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

/** A running inspect scene: its display entities, timeline position and last anchor. */
final class InspectSession {

    record PartEntity(ModelPart part, Display entity, boolean observers) {
    }

    final UUID player;
    final InspectAnimation animation;
    final List<PartEntity> parts;
    final boolean reveal;
    final boolean leftHand;
    final boolean bodyHand;
    final float scale;
    final InspectModels.Anchor anchor;
    final InspectModels.Anchor handAnchor;
    final float handScale;
    BukkitTask task;
    int tick;
    int sampleIndex;
    Location lastAnchor;
    Location lastHandAnchor;

    InspectSession(UUID player, InspectAnimation animation, List<PartEntity> parts, boolean reveal, boolean leftHand,
                   boolean bodyHand, float scale, InspectModels.Anchor anchor, InspectModels.Anchor handAnchor, float handScale) {
        this.player = player;
        this.animation = animation;
        this.parts = parts;
        this.reveal = reveal;
        this.leftHand = leftHand;
        this.bodyHand = bodyHand;
        this.scale = scale;
        this.anchor = anchor;
        this.handAnchor = handAnchor;
        this.handScale = handScale;
    }
}
