package dev.plattnericus.cases.opening;

import dev.plattnericus.cases.core.CasesContext;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A short speech bubble above a player's head when they open a case. The text display rides on the
 * player as a passenger, so it follows every movement without any per-tick work, and pops in and out
 * with a small scale animation. Everyone nearby sees it.
 */
final class SpeechBubble {

    private record Bubble(TextDisplay display, BukkitTask task) {
    }

    private final CasesContext ctx;
    private final Map<UUID, Bubble> bubbles = new HashMap<>();

    SpeechBubble(CasesContext ctx) {
        this.ctx = ctx;
    }

    void show(Player player) {
        if (!ctx.settings().opening().speechBubble()) {
            return;
        }
        remove(player);
        AxisAngle4f none = new AxisAngle4f();
        TextDisplay bubble = player.getWorld().spawn(player.getLocation(), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(ctx.keys().displayEntity, PersistentDataType.BYTE, (byte) 1);
            d.text(ctx.messages(player).get("opening.bubble"));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(230, 255, 255, 255));
            d.setShadowed(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(0, 0.45f, 0), none, new Vector3f(0.01f, 0.01f, 0.01f), none));
        });
        player.addPassenger(bubble);
        // let the client see the small start state before growing
        Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> scale(bubble, 0.9f, 4), 2L);
        int duration = ctx.settings().opening().speechBubbleTicks();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> {
            scale(bubble, 0.01f, 4);
            Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> {
                Bubble current = bubbles.get(player.getUniqueId());
                if (current != null && current.display() == bubble) {
                    bubbles.remove(player.getUniqueId());
                }
                bubble.remove();
            }, 5L);
        }, duration);
        bubbles.put(player.getUniqueId(), new Bubble(bubble, task));
    }

    private static void scale(TextDisplay d, float s, int ticks) {
        if (!d.isValid()) {
            return;
        }
        AxisAngle4f none = new AxisAngle4f();
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(new Transformation(new Vector3f(0, 0.45f, 0), none, new Vector3f(s, s, s), none));
    }

    void remove(Player player) {
        Bubble b = bubbles.remove(player.getUniqueId());
        if (b != null) {
            b.task().cancel();
            b.display().remove();
        }
    }

    void removeAll() {
        for (Bubble b : bubbles.values()) {
            b.task().cancel();
            b.display().remove();
        }
        bubbles.clear();
    }
}
