package dev.plattnericus.cases.shop;

import dev.plattnericus.cases.core.CasesContext;
import io.papermc.paper.entity.LookAnchor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Brings mannequin dealers to life: they turn their head towards the nearest player, greet
 * players who walk up with a wave, nod or wave now and then while someone is near, and change
 * their skin on a timer. Only loaded dealers are tracked, and nothing runs while nobody is near.
 * (A standing mannequin has no walk cycle, so legs are not animated.)
 */
public final class ShopNpcAnimator implements Listener {

    private static final int PERIOD = 4;

    private final CasesContext ctx;
    private final Set<UUID> tracked = new HashSet<>();
    private final Map<UUID, UUID> lastTarget = new HashMap<>();
    private final Map<UUID, Integer> nextGesture = new HashMap<>();
    private final Map<UUID, Integer> unsneakAt = new HashMap<>();
    private final SplittableRandom random = new SplittableRandom();
    private BukkitTask task;
    private int ticks;
    private int skinIndex;

    public ShopNpcAnimator(CasesContext ctx) {
        this.ctx = ctx;
    }

    public void start() {
        refreshAll();
        task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), this::tick, PERIOD, PERIOD);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        tracked.clear();
    }

    public void track(Entity entity) {
        if (!ctx.shop().isShop(entity)) return;
        ctx.shop().refreshDealerLabels(entity);
        if (entity instanceof Mannequin) {
            tracked.add(entity.getUniqueId());
        }
    }

    /** Applies current labels and appearance to loaded dealers on start and reload. */
    public void refreshAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) track(entity);
        }
        for (UUID id : tracked) {
            if (Bukkit.getEntity(id) instanceof Mannequin m) {
                applySkin(m);
                ctx.shop().equipCase(m);
            }
        }
    }

    private void tick() {
        ticks += PERIOD;
        ShopService.NpcSettings cfg = ctx.shop().npc();
        boolean rotateSkin = !cfg.skins().isEmpty() && ticks % (cfg.skinInterval() * 20) < PERIOD;
        if (rotateSkin) {
            skinIndex = (skinIndex + 1) % cfg.skins().size();
        }
        tracked.removeIf(id -> !(Bukkit.getEntity(id) instanceof Mannequin m) || !m.isValid());
        for (UUID id : tracked) {
            Mannequin m = (Mannequin) Bukkit.getEntity(id);
            if (m == null) {
                continue;
            }
            if (rotateSkin) {
                applySkin(m);
            }
            if (cfg.animations()) {
                animate(m, cfg);
            }
        }
    }

    private void applySkin(Mannequin m) {
        var skins = ctx.shop().npc().skins();
        if (!skins.isEmpty()) {
            m.setProfile(skins.get(skinIndex % skins.size()));
        }
    }

    private void animate(Mannequin m, ShopService.NpcSettings cfg) {
        UUID id = m.getUniqueId();
        Integer unsneak = unsneakAt.get(id);
        if (unsneak != null && ticks >= unsneak) {
            unsneakAt.remove(id);
            m.setPose(Pose.STANDING, false);
        }
        Player target = nearest(m, cfg.lookRange());
        if (target == null) {
            lastTarget.remove(id);
            return;
        }
        m.lookAt(target.getEyeLocation(), LookAnchor.EYES);
        UUID previous = lastTarget.put(id, target.getUniqueId());
        if (!target.getUniqueId().equals(previous)) {
            // someone new walked up: greet them
            m.swingMainHand();
            scheduleGesture(id, cfg);
            return;
        }
        Integer next = nextGesture.get(id);
        if (next == null) {
            scheduleGesture(id, cfg);
        } else if (ticks >= next) {
            if (random.nextInt(3) == 0 && Mannequin.validPoses().contains(Pose.SNEAKING)) {
                m.setPose(Pose.SNEAKING, true);
                unsneakAt.put(id, ticks + 8);
            } else {
                m.swingMainHand();
            }
            scheduleGesture(id, cfg);
        }
    }

    private void scheduleGesture(UUID id, ShopService.NpcSettings cfg) {
        nextGesture.put(id, ticks + cfg.gestureMinTicks() + random.nextInt(cfg.gestureMaxTicks() - cfg.gestureMinTicks() + 1));
    }

    private static Player nearest(Mannequin m, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player p : m.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(m.getLocation());
            if (d < bestD && !p.isInvisible()) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            track(e);
        }
    }

    @EventHandler
    public void onUnload(EntitiesUnloadEvent event) {
        for (Entity e : event.getEntities()) {
            UUID id = e.getUniqueId();
            if (tracked.remove(id)) {
                lastTarget.remove(id);
                nextGesture.remove(id);
                unsneakAt.remove(id);
            }
        }
    }
}
