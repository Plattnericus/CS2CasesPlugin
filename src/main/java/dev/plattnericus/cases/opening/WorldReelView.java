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

/** Public reel with a bounded ring of display entities reused outside the visible window. */
final class WorldReelView implements OpeningView {

    private static final AxisAngle4f NO_ROTATION = new AxisAngle4f();
    /** Ticks between keyframes; the client interpolates linearly in between. */
    private static final int STEP = 2;

    private final CasesContext ctx;
    private final OpeningService service;
    private final List<org.bukkit.entity.Interaction> hitboxes = new ArrayList<>();
    private final Player player;
    private final OpeningSession session;
    private final PluginSettings.WorldReel cfg;
    private final double window;
    private final List<ItemDisplay> items = new ArrayList<>();
    private final List<BlockDisplay> bars = new ArrayList<>();
    private int[] represented;
    private final List<Entity> all = new ArrayList<>();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private TextDisplay title;
    private Location anchor;
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
        anchor = anchor();
        double width = cfg.visibleItems() * cfg.spacing() + 0.2;
        double half = cfg.itemScale() / 2;
        all.add(block(Material.BLACK_STAINED_GLASS, new Vector3f((float) -width / 2, (float) (-half - 0.17), -0.06f),
                new Vector3f((float) width, (float) (cfg.itemScale() + 0.36), 0.02f)));
        all.add(block(Material.GOLD_BLOCK, new Vector3f(-0.012f, (float) (half + 0.04), -0.02f), new Vector3f(0.024f, 0.1f, 0.02f)));
        all.add(block(Material.GOLD_BLOCK, new Vector3f(-0.012f, (float) (-half - 0.14), -0.02f), new Vector3f(0.024f, 0.1f, 0.02f)));
        represented = new int[cfg.visibleItems() + 4]; java.util.Arrays.fill(represented, -1);
        List<SkinDefinition> reel = session.reel;
        for (int i = 0; i < cfg.visibleItems() + 4; i++) {
            SkinDefinition def = reel.get(i);
            ItemStack stack = icon(def, false);
            ItemDisplay item = anchor.getWorld().spawn(anchor, ItemDisplay.class, d -> {
                common(d);
                d.setItemStack(stack);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            });
            BlockData barBlock = barBlock(def.rarity());
            BlockDisplay bar = anchor.getWorld().spawn(anchor, BlockDisplay.class, d -> {
                common(d);
                d.setBlock(barBlock);
            });
            items.add(item);
            bars.add(bar);
            all.add(item);
            all.add(bar);
        }
        title = anchor.getWorld().spawn(anchor, TextDisplay.class, d -> {
            common(d);
            d.text(ctx.messages(player).get("opening.world.title", Text.unparsed("player", player.getName()),
                    Text.unparsed("case", session.caseDef.name())));
            d.setBackgroundColor(Color.fromARGB(150, 10, 12, 16));
            d.setTransformation(new Transformation(new Vector3f(0, (float) (half + 0.2), 0), NO_ROTATION,
                    new Vector3f(0.6f, 0.6f, 0.6f), NO_ROTATION));
        });
        all.add(title);
        clickTargets(half);
        place(4, 0);
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
        Location eye = player.getEyeLocation();
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
        // Separate lanes for concurrent openings, including the result hold period.
        Vector right = eye.getDirection().setY(0).normalize().crossProduct(new Vector(0, 1, 0)).normalize();
        int column = session.lane % 3;
        int row = session.lane / 3;
        loc.add(right.multiply((column == 0 ? 0 : column == 1 ? -1 : 1) * (cfg.visibleItems() * cfg.spacing() + 0.7)))
                .add(0, row * (cfg.itemScale() + 0.85), 0);
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
        place(target, last ? 1 : STEP);
        ticksSinceKeyframe = 0;
        lastKeyframe = target;
    }

    /** Position {@link #STEP} ticks ahead on the session's easing curve. */
    private double predict(double center) {
        int duration = ctx.settings().opening().durationTicks();
        Easing easing = Easing.parse(ctx.settings().opening().easing());
        int distance = session.winnerIndex - 4;
        double t = Math.min(duration, session.tick + STEP) / (double) duration;
        return Math.max(center, Math.min(session.winnerIndex, 4 + easing.apply(t) * distance));
    }

    /** Puts the strip so that reel index {@code center} is under the marker. */
    private void place(double center, int duration) {
        float half = (float) (cfg.itemScale() / 2);
        for (int i = 0; i < items.size(); i++) {
            int first = (int) Math.floor(center) - items.size() / 2;
            int index = first + Math.floorMod(i - first, items.size());
            int safeIndex = Math.clamp(index, 0, session.reel.size() - 1);
            SkinDefinition def = session.reel.get(safeIndex);
            if (represented[i] != safeIndex) { items.get(i).setItemStack(icon(def, false)); bars.get(i).setBlock(barBlock(def.rarity())); represented[i] = safeIndex; }
            float x = (float) ((index - center) * cfg.spacing());
            float edge = (float) Math.min(1, Math.abs(x) / (window + 0.0001));
            boolean visible = Math.abs(x) <= window;
            float s = visible ? (float) (cfg.itemScale() * (1 - 0.35 * edge * edge)) : 0f;
            float barWidth = visible ? (float) (cfg.spacing() * 0.82 * (1 - 0.35 * edge * edge)) : 0f;
            ItemDisplay item = items.get(i);
            item.setInterpolationDelay(0);
            item.setInterpolationDuration(duration);
            item.setTransformation(new Transformation(new Vector3f(x, 0, 0), NO_ROTATION, new Vector3f(s, s, s), NO_ROTATION));
            BlockDisplay bar = bars.get(i);
            bar.setInterpolationDelay(0);
            bar.setInterpolationDuration(duration);
            bar.setTransformation(new Transformation(new Vector3f(x - barWidth / 2, -half - 0.09f, -0.04f), NO_ROTATION,
                    new Vector3f(barWidth, visible ? 0.035f : 0f, 0.02f), NO_ROTATION));
        }
    }

    @Override
    public void reveal() {
        if (closed) {
            return;
        }
        SkinDefinition reward = session.reward;
        int w = session.winnerIndex;
        ItemDisplay winner = items.get(Math.floorMod(w, items.size()));
        // the same entity that scrolled in: only the gold mystery icon is replaced by the real item
        if (reward.rarity().rareSpecial()) {
            winner.setItemStack(icon(reward, true));
        }
        float big = (float) (cfg.itemScale() * 1.6);
        winner.setInterpolationDelay(0);
        winner.setInterpolationDuration(8);
        winner.setTransformation(new Transformation(new Vector3f(0, 0.04f, 0.08f), NO_ROTATION,
                new Vector3f(big, big, big), NO_ROTATION));
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
        all.clear();
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
