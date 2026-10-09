package dev.plattnericus.cases.opening;

import dev.plattnericus.cases.catalog.Rarity;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.config.PluginSettings;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Public reel with immutable item identities and a bounded, moving display window. */
final class WorldReelView implements OpeningView {

    private static final AxisAngle4f NO_ROTATION = new AxisAngle4f();
    /** Ticks between keyframes; the client interpolates linearly in between. */
    private static final int STEP = 2;
    private static final int WARMUP = 1;

    private final CasesContext ctx;
    private final OpeningService service;
    private final List<org.bukkit.entity.Interaction> hitboxes = new ArrayList<>();
    private final Player player;
    private final OpeningSession session;
    private final PluginSettings.WorldReel cfg;
    private final double window;
    private record Slot(ItemDisplay item, BlockDisplay bar, int spawnedAt) { }
    private final Map<Integer, Slot> slots = new java.util.LinkedHashMap<>();
    private final List<Entity> all = new ArrayList<>();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private TextDisplay title;
    private Location anchor;
    private Location baseAnchor;
    private double sceneScale = 1;
    private int layoutIndex = -1, layoutCount;
    private double lastKeyframe = Double.NaN;
    private int ticksSinceKeyframe = STEP;
    private BukkitTask cleanup;
    private boolean closed;

    WorldReelView(CasesContext ctx, Player player, OpeningSession session, OpeningService service) {
        this.ctx = ctx;
        this.service = service;
        this.player = player;
        this.session = session;
        this.cfg = ctx.settings().opening().world();
        this.window = (cfg.visibleItems() / 2.0) * cfg.spacing();
    }

    @Override
    public void open() {
        baseAnchor = anchor();
        anchor = baseAnchor.clone();
        double width = cfg.visibleItems() * cfg.spacing() + 0.2;
        double half = cfg.itemScale() / 2;
        all.add(block(Material.BLACK_CONCRETE, new Vector3f((float) -width / 2, (float) (-half - 0.17), -0.06f),
                new Vector3f((float) width, (float) (cfg.itemScale() + 0.36), 0.02f)));
        all.add(block(Material.GOLD_BLOCK, new Vector3f(-0.012f, (float) (half + 0.04), -0.02f), new Vector3f(0.024f, 0.1f, 0.02f)));
        all.add(block(Material.GOLD_BLOCK, new Vector3f(-0.012f, (float) (-half - 0.14), -0.02f), new Vector3f(0.024f, 0.1f, 0.02f)));
        title = anchor.getWorld().spawn(anchor, TextDisplay.class, d -> {
            common(d);
            d.text(ctx.messages(player).get("opening.world.title", Text.unparsed("player", player.getName()),
                    Text.unparsed("case", session.caseDef.name())));
            d.setBackgroundColor(Color.fromARGB(150, 10, 12, 16));
            d.setLineWidth(280);
            float textScale = .6f;
            d.setTransformation(new Transformation(new Vector3f(0, (float) (half + 0.2), 0), NO_ROTATION,
                    new Vector3f(textScale), NO_ROTATION));
        });
        all.add(title);
        clickTargets(half);
        place(4, 4, 0);
    }

    /** Recenter every live scene, including held rewards, whenever one is added or removed. */
    void layout(int index, int count) {
        if (closed || layoutIndex == index && layoutCount == count) return;
        layoutIndex = index; layoutCount = count;
        double distance = baseAnchor.toVector().distance(session.origin.toVector());
        var cell = OpeningLayout.cell(index, count, cfg.visibleItems() * cfg.spacing() + .2,
                cfg.itemScale() + .65, distance);
        Vector right = baseAnchor.getDirection().multiply(-1).crossProduct(new Vector(0, 1, 0)).normalize();
        Location next = baseAnchor.clone().add(right.multiply(cell.right())).add(0, cell.up(), 0);
        double ratio = cell.scale() / sceneScale;
        for (Entity entity : all) {
            if (entity instanceof Display display) {
                Transformation pose = display.getTransformation();
                pose.getTranslation().mul((float) ratio); pose.getScale().mul((float) ratio);
                display.setInterpolationDuration(0); display.setTransformation(pose);
                display.setTeleportDuration(4); display.teleport(next);
            } else if (entity instanceof org.bukkit.entity.Interaction box) {
                Vector offset = box.getLocation().toVector().subtract(anchor.toVector()).multiply(ratio);
                box.teleport(next.clone().add(offset));
                box.setInteractionWidth(box.getInteractionWidth() * (float) ratio);
                box.setInteractionHeight(box.getInteractionHeight() * (float) ratio);
            }
        }
        sceneScale = cell.scale(); anchor = next;
    }

    private Transformation scaled(Transformation pose) {
        pose.getTranslation().mul((float) sceneScale); pose.getScale().mul((float) sceneScale);
        return pose;
    }

    /**
     * Interaction hitboxes along the visible strip. Hitboxes are axis aligned, so the strip is
     * covered by one small box per item slot instead of one big box.
     */
    private void clickTargets(double half) {
        Vector forward = anchor.getDirection().multiply(-1).setY(0).normalize();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        int slots = cfg.visibleItems();
        float size = (float) Math.min(cfg.spacing(), cfg.itemScale() + 0.2);
        for (int i = 0; i < slots; i++) {
            double x = (i - (slots - 1) / 2.0) * cfg.spacing();
            Location at = anchor.clone().add(right.clone().multiply(x)).add(0, -half - 0.1, 0);
            org.bukkit.entity.Interaction box = anchor.getWorld().spawn(at, org.bukkit.entity.Interaction.class, e -> {
                e.setPersistent(false);
                e.getPersistentDataContainer().set(ctx.keys().displayEntity, PersistentDataType.BYTE, (byte) 1);
                e.setInteractionWidth(size);
                e.setInteractionHeight((float) (cfg.itemScale() + 0.2));
                e.setResponsive(true);
            });
            hitboxes.add(box);
            all.add(box);
            service.registerReelHitbox(box.getUniqueId());
        }
    }

    /** In front of the player at eye height, pulled closer if a wall is in the way, facing the player. */
    private Location anchor() {
        Location eye = session.origin == null ? player.getEyeLocation() : session.origin.clone();
        Vector forward = eye.getDirection().setY(0);
        if (forward.lengthSquared() < 1e-6) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        double distance = cfg.distance();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, forward, distance + 0.5, FluidCollisionMode.NEVER, true);
        if (hit != null) {
            distance = Math.max(1.2, eye.toVector().distance(hit.getHitPosition()) - 0.5);
        }
        Location loc = eye.clone().add(forward.multiply(distance)).add(0, cfg.height(), 0);
        loc.setYaw(eye.getYaw() + 180);
        loc.setPitch(0);
        return loc;
    }

    private void common(Display d) {
        d.setPersistent(false);
        d.getPersistentDataContainer().set(ctx.keys().displayEntity, PersistentDataType.BYTE, (byte) 1);
        d.setViewRange((float) cfg.viewRange());
        d.setBrightness(new Display.Brightness(15, 15));
        d.setShadowRadius(0);
        d.setTeleportDuration(0);
    }

    private BlockDisplay block(Material material, Vector3f translation, Vector3f scale) {
        return anchor.getWorld().spawn(anchor, BlockDisplay.class, d -> {
            common(d);
            d.setBlock(material.createBlockData());
            d.setTransformation(new Transformation(translation, NO_ROTATION, scale, NO_ROTATION));
        });
    }

    /**
     * Called every tick with the exact reel position. A keyframe is sent every {@link #STEP} ticks,
     * aimed at where the reel will be {@link #STEP} ticks later, so the client interpolation stays
     * on the curve. The final frame is always sent and lands exactly on the winner.
     */
    @Override
    public void frame(double center) {
        if (closed) {
            return;
        }
        boolean last = center >= session.winnerIndex - 1e-9;
        ticksSinceKeyframe++;
        if (!last && ticksSinceKeyframe < STEP) {
            return;
        }
        double target = last ? session.winnerIndex : predict(center);
        if (target == lastKeyframe) {
            return;
        }
        place(center, target, last ? 1 : STEP);
        ticksSinceKeyframe = 0;
        lastKeyframe = target;
    }

    /** Position {@link #STEP} ticks ahead on the session's easing curve. */
    private double predict(double center) {
        int duration = session.duration;
        Easing easing = session.easing;
        int distance = session.winnerIndex - 4;
        double t = Math.min(duration, session.tick + STEP) / (double) duration;
        return Math.max(center, Math.min(session.winnerIndex, 4 + easing.apply(t) * distance));
    }

    /** Keep the current and next window. A slot's reel identity never changes in the client. */
    private void place(double current, double target, int duration) {
        int padding = cfg.visibleItems() / 2 + 2;
        int first = Math.max(0, (int) Math.floor(current) - padding);
        double preload = session.duration == 0 ? target : 4 + session.easing.apply(
                Math.min(session.duration, session.tick + 2 * STEP + WARMUP) / (double) session.duration) * (session.winnerIndex - 4);
        int last = Math.min(session.reel.size() - 1, (int) Math.ceil(Math.max(target, preload)) + padding);
        for (var iterator = slots.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (entry.getKey() < first || entry.getKey() > last) {
                Slot slot = entry.getValue(); slot.item().remove(); slot.bar().remove();
                all.remove(slot.item()); all.remove(slot.bar()); iterator.remove();
            }
        }
        for (int index = first; index <= last; index++) {
            Slot slot = slots.get(index);
            if (slot == null) {
                SkinDefinition def = session.reel.get(index);
                final int reelIndex = index;
                ItemDisplay item = anchor.getWorld().spawn(anchor, ItemDisplay.class, d -> {
                    common(d); d.setItemStack(icon(def, false));
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    d.setInterpolationDuration(0);
                    d.setTransformation(itemPose(reelIndex, target));
                });
                BlockDisplay bar = anchor.getWorld().spawn(anchor, BlockDisplay.class, d -> {
                    common(d); d.setBlock(barBlock(def.rarity()));
                    d.setInterpolationDuration(0);
                    d.setTransformation(barPose(reelIndex, target));
                });
                slot = new Slot(item, bar, session.tick); slots.put(index, slot); all.add(item); all.add(bar);
            }
            // A newly tracked display has no previous client render state. Interpolating its
            // first metadata packet can blend from the default unit-scale pose. Preload far
            // enough along the curve to initialize invisible entries before they reach the
            // strip, then update every entry on the same keyframe schedule.
            int interpolation = session.tick - slot.spawnedAt() < WARMUP ? 0 : duration;
            keyframe(slot.item(), itemPose(index, target), interpolation);
            keyframe(slot.bar(), barPose(index, target), interpolation);
        }
    }

    private void keyframe(Display display, Transformation pose, int duration) {
        // Resetting start_interpolation without changing the pose restarts the previous client
        // tween. At the edge that makes an already hidden item jump back into view forever.
        if (display.getTransformation().equals(pose)) return;
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(duration);
        display.setTransformation(pose);
    }

    private float visibility(float x) {
        return (float) Math.clamp((window - Math.abs(x)) / (cfg.spacing() * .65), 0, 1);
    }
    private float falloff(float x) {
        double edge = Math.min(1, Math.abs(x) / window);
        return (float) ((1 - .35 * edge * edge) * visibility(x));
    }
    private Transformation itemPose(int index, double center) {
        float x = (float) ((index - center) * cfg.spacing());
        float scale = (float) cfg.itemScale() * falloff(x);
        // Both endpoints stay inside the strip, including when a fast keyframe crosses its
        // boundary. Client interpolation must never carry a still-visible sprite beyond it.
        float boundedX = (float) Math.clamp(x, -window, window);
        return scaled(new Transformation(new Vector3f(boundedX, 0, 0), NO_ROTATION, new Vector3f(scale), NO_ROTATION));
    }
    private Transformation barPose(int index, double center) {
        float x = (float) ((index - center) * cfg.spacing());
        float visible = visibility(x), width = (float) (cfg.spacing() * .82) * falloff(x);
        float boundedX = (float) Math.clamp(x, -window, window);
        return scaled(new Transformation(new Vector3f(boundedX - width / 2, (float) (-cfg.itemScale() / 2 - .09), -.04f), NO_ROTATION,
                new Vector3f(width, .035f * visible, .02f), NO_ROTATION));
    }

    @Override
    public int settleTicks() { return STEP + 1; }

    @Override
    public void reveal() {
        if (closed) {
            return;
        }
        SkinDefinition reward = session.reward;
        int w = session.winnerIndex;
        ItemDisplay winner = slots.get(w).item();
        for (Slot slot : slots.values()) {
            Transformation barPose = slot.bar().getTransformation(); barPose.getScale().zero(); keyframe(slot.bar(), barPose, 8);
            if (slot.item() != winner) {
                Transformation pose = slot.item().getTransformation(); pose.getScale().zero(); keyframe(slot.item(), pose, 8);
            }
        }
        // the same entity that scrolled in: only the gold mystery icon is replaced by the real item
        if (reward.rarity().rareSpecial()) {
            winner.setItemStack(icon(reward, true));
        }
        float big = (float) (cfg.itemScale() * 1.6);
        winner.setInterpolationDelay(0);
        winner.setInterpolationDuration(8);
        winner.setTransformation(scaled(new Transformation(new Vector3f(0, 0.04f, 0.08f), NO_ROTATION,
                new Vector3f(big, big, big), NO_ROTATION)));
        winner.setGlowColorOverride(Color.fromRGB(reward.rarity().color() & 0xFFFFFF));
        winner.setGlowing(true);
        title.text(ctx.formatter(player).fullName(reward, session.instance));
        World world = anchor.getWorld();
        if (reward.rarity().order() >= 4 || reward.rarity().rareSpecial()) {
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(reward.rarity().color() & 0xFFFFFF), 1.2f);
            world.spawnParticle(Particle.DUST, anchor, 40, 0.6, 0.35, 0.1, 0, dust);
            world.spawnParticle(Particle.END_ROD, anchor, 10, 0.4, 0.3, 0.1, 0.02);
            ctx.sounds().playAt(anchor, reward.rarity().revealSound());
        }
    }

    @Override
    public void result() {
        if (closed || cleanup != null) {
            return;
        }
        cleanup = Bukkit.getScheduler().runTaskLater(ctx.plugin(), this::close, cfg.holdTicks());
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (cleanup != null) {
            cleanup.cancel();
        }
        for (org.bukkit.entity.Interaction box : hitboxes) {
            service.unregisterReelHitbox(box.getUniqueId());
        }
        hitboxes.clear();
        for (Entity e : all) {
            e.remove();
        }
        all.clear(); slots.clear();
        service.viewClosed(session);
    }

    private ItemStack icon(SkinDefinition def, boolean revealedWinner) {
        if (def.rarity().rareSpecial() && !revealedWinner) {
            return icons.computeIfAbsent("?", k -> new ItemStack(Material.GOLD_INGOT));
        }
        return icons.computeIfAbsent(def.id(), k -> SkinIcons.icon(def, ctx.formatter(player).name(def, null), List.of(),
                ctx.settings(), false));
    }

    /** Rarity color as a solid block (the rarity pane material with "_CONCRETE" instead of "_STAINED_GLASS_PANE"). */
    private static BlockData barBlock(Rarity rarity) {
        Material m = Material.matchMaterial(rarity.pane().replace("_STAINED_GLASS_PANE", "_CONCRETE"));
        return (m != null && m.isBlock() ? m : Material.WHITE_CONCRETE).createBlockData();
    }
}
