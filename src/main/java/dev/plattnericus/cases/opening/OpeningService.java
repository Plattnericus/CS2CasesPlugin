package dev.plattnericus.cases.opening;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.catalog.KeyDefinition;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.menu.SkinInspectMenu;
import dev.plattnericus.cases.items.CaseItems;
import dev.plattnericus.cases.pattern.PatternReport;
import dev.plattnericus.cases.profile.PendingJournal;
import dev.plattnericus.cases.profile.PlayerProfile;
import dev.plattnericus.cases.reward.RewardRoller;
import dev.plattnericus.cases.reward.RolledReward;
import dev.plattnericus.cases.skin.PatternInfo;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.storage.OpeningRecord;
import dev.plattnericus.cases.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Case opening state machine. Order of a real opening:
 * <ol>
 *   <li>lock: one session per player, checked and set on the server thread</li>
 *   <li>roll the reward (server RNG) and analyse its pattern off-thread</li>
 *   <li>consume case + key and journal the reward in the player file, saved immediately</li>
 *   <li>store reward + audit row in one database transaction (status PENDING)</li>
 *   <li>animate; the reward is marked OWNED at the reveal or as soon as the menu is closed</li>
 * </ol>
 * Failures before step 4 completes refund the items. Quits and crashes leave a PENDING row or a
 * journal entry that is completed exactly once on the next login.
 */
public final class OpeningService implements Listener {

    private final CasesContext ctx;
    private final PendingJournal journal;
    private final RewardRoller roller;
    private final RevealEffects effects;
    private final Map<UUID, OpeningSession> sessions = new HashMap<>();
    private final SpeechBubble bubble;
    /** Click hitboxes of running world reels; clicking one makes the clicker say the bubble text. */
    private final java.util.Set<UUID> reelHitboxes = new java.util.HashSet<>();
    private final Map<UUID, Integer> lastBubble = new HashMap<>();

    public OpeningService(CasesContext ctx, PendingJournal journal, RewardRoller roller) {
        this.ctx = ctx;
        this.journal = journal;
        this.roller = roller;
        this.effects = new RevealEffects(ctx);
        this.bubble = new SpeechBubble(ctx);
    }

    public RewardRoller roller() {
        return roller;
    }

    public boolean isOpening(Player player) {
        OpeningSession s = sessions.get(player.getUniqueId());
        return s != null && s.isActive();
    }

    /**
     * Starts an opening. {@code adminTest} skips case/key consumption; {@code keep} decides whether a
     * test reward is stored in the admin's inventory.
     */
    public void open(Player player, CaseDefinition def, boolean adminTest, boolean keep) {
        if (isOpening(player)) {
            ctx.messages().send(player, "opening.already");
            return;
        }
        PlayerProfile profile = ctx.profiles().get(player);
        if (profile == null) {
            ctx.messages().send(player, "profile.loading");
            return;
        }
        Catalog catalog = ctx.catalog();
        CaseDefinition current = catalog.caseDefinition(def.id());
        if (current == null || !current.enabled()) {
            ctx.messages().send(player, "opening.case-disabled");
            return;
        }
        KeyDefinition key = catalog.key(current.keyId());
        if (!adminTest) {
            if (key == null) {
                ctx.messages().send(player, "opening.case-disabled");
                return;
            }
            if (ctx.caseItems().count(player, CaseItems.TYPE_CASE, current.id()) < 1
                    || ctx.caseItems().count(player, CaseItems.TYPE_KEY, current.keyId()) < 1) {
                ctx.messages().send(player, "opening.missing", Text.unparsed("case", current.name()),
                        Text.unparsed("key", ctx.messages(player).label("catalog.key." + key.id(), key.name())));
                ctx.sounds().play(player, "gui.error");
                return;
            }
        }
        ctx.inspect().stop(player);
        ctx.gallery().close(player);
        OpeningSession session = new OpeningSession(player.getUniqueId(), current, catalog, adminTest, keep);
        sessions.put(player.getUniqueId(), session);

        RolledReward roll = roller.roll(current, catalog);
        ctx.render().report(roll.skin(), roll.pattern()).handle((report, error) -> {
            if (error != null) {
                ctx.plugin().getLogger().log(Level.WARNING, "Pattern analysis failed for " + roll.skin().id()
                        + " #" + roll.pattern() + ": " + error.getMessage());
                return PatternReport.none(null, null);
            }
            return report;
        }).thenAccept(report -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> commit(player, session, roll, report)));
    }

    /** Server thread: consume items, journal, then persist asynchronously. */
    private void commit(Player player, OpeningSession session, RolledReward roll, PatternReport report) {
        if (sessions.get(session.playerId) != session || session.state != OpeningSession.State.ROLLING) {
            return;
        }
        if (!player.isOnline()) {
            abort(session);
            return;
        }
        boolean testItems = false;
        if (!session.adminTest) {
            CaseItems.Consumption consumed = ctx.caseItems().consumePair(player, session.caseDef.id(), session.caseDef.keyId());
            if (consumed == null) {
                abort(session);
                ctx.messages().send(player, "opening.missing-now");
                return;
            }
            session.consumed = consumed;
            testItems = consumed.test();
        }
        SkinInstance.Origin origin;
        if (session.adminTest) {
            origin = session.keep ? SkinInstance.Origin.ADMIN : SkinInstance.Origin.TEST;
        } else {
            origin = testItems ? SkinInstance.Origin.TEST : SkinInstance.Origin.CASE;
        }
        PatternInfo info = PatternInfo.of(report);
        session.reward = roll.skin();
        session.instance = new SkinInstance(UUID.randomUUID(), player.getUniqueId(), roll.skin().id(), roll.floatValue(),
                roll.pattern(), roll.wearSeed(), roll.statTrak(), 0, info, session.caseDef.id(), origin,
                System.currentTimeMillis(), false, SkinInstance.Status.PENDING);
        session.reel = ReelBuilder.build(session.caseDef, session.catalog, roll.skin(),
                ctx.settings().opening().reelLength(), roller.random().nextLong());
        session.winnerIndex = session.reel.size() - ReelBuilder.TAIL;
        session.state = OpeningSession.State.PERSISTING;

        OpeningRecord record = new OpeningRecord(session.openingId, player.getUniqueId(), player.getName(),
                session.caseDef.id(), session.instance.id(), roll.skin().id(), roll.skin().rarity().id(),
                roll.floatValue(), roll.pattern(), roll.statTrak(), session.adminTest || testItems, session.instance.createdAt());

        if (!session.persistent()) {
            ctx.repository().logOpening(record);
            startAnimation(player, session);
            return;
        }
        if (!session.adminTest) {
            // the case/key removal and the reward reach disk in the same file save
            journal.add(player, session.instance);
            player.saveData();
        }
        SkinInstance snapshot = session.instance;
        ctx.repository().persistOpening(snapshot, record).whenComplete((v, error) ->
                Bukkit.getScheduler().runTask(ctx.plugin(), () -> afterPersist(player, session, error)));
    }

    private void afterPersist(Player player, OpeningSession session, Throwable error) {
        if (error != null) {
            ctx.plugin().getLogger().log(Level.SEVERE, "Storing case reward " + session.instance.id() + " failed", error);
            if (player.isOnline()) {
                if (!session.adminTest) {
                    // nothing was stored: undo the journal entry and give the items back
                    journal.remove(player, session.instance.id());
                    refund(player, session);
                }
                ctx.messages().send(player, "opening.storage-error");
            }
            abort(session);
            return;
        }
        if (player.isOnline() && !session.adminTest) {
            journal.remove(player, session.instance.id());
        }
        if (ctx.settings().display().logOpenings()) {
            ctx.plugin().getLogger().info(String.format(java.util.Locale.ROOT, "%s opened %s -> %s (%s %.6f, #%d%s%s) [%s]",
                    player.getName(), session.caseDef.id(), session.reward.displayName(),
                    session.catalog.wear().of(session.instance.floatValue()).shortName(), session.instance.floatValue(),
                    session.instance.pattern(), session.instance.statTrak() ? ", StatTrak" : "",
                    session.instance.patternInfo().hasClassification() ? ", " + session.instance.patternInfo().classification() : "",
                    session.instance.shortId()));
        }
        if (!player.isOnline() || sessions.get(session.playerId) != session) {
            // reward stays PENDING and is recovered on the next login
            return;
        }
        ctx.profiles().addLoaded(player.getUniqueId(), session.instance);
        startAnimation(player, session);
    }

    private void refund(Player player, OpeningSession session) {
        CaseItems.giveOrDrop(player, ctx.caseItems().caseItem(session.caseDef, 1, session.consumed.caseTest(), ctx.messages(player)));
        KeyDefinition key = session.catalog.key(session.caseDef.keyId());
        if (key != null) {
            CaseItems.giveOrDrop(player, ctx.caseItems().keyItem(key, 1, session.consumed.keyTest(), ctx.messages(player)));
        }
    }

    private void abort(OpeningSession session) {
        session.state = OpeningSession.State.ABORTED;
        sessions.remove(session.playerId, session);
    }

    // ------------------------------------------------------------------ animation

    private void startAnimation(Player player, OpeningSession session) {
        session.state = OpeningSession.State.ANIMATING;
        session.view = ctx.settings().opening().worldDisplay()
                ? new WorldReelView(ctx, player, session, this)
                : new OpeningMenu(ctx, player, session, this);
        session.tick = 0;
        session.view.open();
        ctx.sounds().play(player, "opening.start");
        Easing easing = Easing.parse(ctx.settings().opening().easing());
        int duration = ctx.settings().opening().durationTicks();
        int start = 4;
        int distance = session.winnerIndex - start;
        session.task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), () -> {
            if (session.state != OpeningSession.State.ANIMATING) {
                return;
            }
            session.tick++;
            double progress = session.tick / (double) duration;
            double position = Math.min(distance, easing.apply(progress) * distance);
            int offset = (int) Math.floor(position);
            session.view.frame(start + position);
            if (offset != session.lastOffset) {
                double before = easing.apply(Math.max(0, (session.tick - 1) / (double) duration)) * distance;
                double speed = position - before;
                float pitch = (float) (0.85 + Math.min(0.55, speed * 0.22));
                session.lastOffset = offset;
                ctx.sounds().play(player, "opening.tick", pitch);
            }
            if (session.tick >= duration) {
                session.view.frame(session.winnerIndex);
                beginReveal(player, session);
            }
        }, 1L, 1L);
    }

    private void beginReveal(Player player, OpeningSession session) {
        session.state = OpeningSession.State.REVEALING;
        session.task.cancel();
        boolean gold = session.reward.rarity().rareSpecial();
        int pause = gold ? ctx.settings().opening().goldPauseTicks() : 2;
        session.task = Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> {
            if (session.state != OpeningSession.State.REVEALING || !player.isOnline()) {
                return;
            }
            session.revealed = true;
            finalizeReward(session);
            session.view.reveal();
            effects.reveal(player, session.reward, session.instance);
            session.task = Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> {
                if (session.state != OpeningSession.State.REVEALING || !player.isOnline()) {
                    return;
                }
                session.state = OpeningSession.State.FINISHED;
                sessions.remove(session.playerId, session);
                session.view.result();
                if (gold && session.reward.isKnife() && ctx.settings().opening().knifeAutoPreview()
                        && ctx.settings().inspect().revealPreview() && session.persistent()) {
                    session.view.close();
                    ctx.inspect().start(player, session.instance, true);
                }
            }, ctx.settings().opening().revealHoldTicks());
        }, pause);
    }

    /** Marks the reward as owned (exactly once) and announces it in chat. */
    private void finalizeReward(OpeningSession session) {
        if (session.instance == null || session.finalized) {
            return;
        }
        session.finalized = true;
        if (session.persistent()) {
            session.instance.setStatus(SkinInstance.Status.OWNED);
            ctx.repository().setStatus(session.instance.id(), SkinInstance.Status.OWNED);
        }
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null) {
            effects.announce(player, session.reward, session.instance, session.caseDef, session.adminTest && !session.keep);
        }
    }

    /**
     * Teleport, world change or death during a world reel: finish instantly like closing the menu.
     */
    private void interrupt(Player player) {
        OpeningSession s = sessions.get(player.getUniqueId());
        if (s != null && s.view != null) {
            onMenuClosed(player, s);
            s.view.close();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld() || event.getFrom().distanceSquared(event.getTo()) > 64) {
            interrupt(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        interrupt(event.getEntity());
    }

    /** Menu closed: finish instantly (no reward is ever lost by closing). */
    void onMenuClosed(Player player, OpeningSession session) {
        if (sessions.get(session.playerId) != session) {
            return;
        }
        if (session.state == OpeningSession.State.ANIMATING || session.state == OpeningSession.State.REVEALING) {
            if (session.task != null) {
                session.task.cancel();
            }
            boolean wasRevealed = session.revealed;
            finalizeReward(session);
            session.state = OpeningSession.State.FINISHED;
            sessions.remove(session.playerId, session);
            if (!wasRevealed && player.isOnline()) {
                effects.reveal(player, session.reward, session.instance);
            }
        }
    }

    void openInspect(Player player, OpeningSession session) {
        if (session.instance == null || !session.persistent()) {
            return;
        }
        ctx.sounds().play(player, "gui.click");
        new SkinInspectMenu(ctx, player, session.instance, player::closeInventory).open();
    }

    void openAgain(Player player, OpeningSession session) {
        player.closeInventory();
        open(player, session.caseDef, false, false);
    }

    // ------------------------------------------------------------------ guards & cleanup

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        OpeningSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s != null && ctx.settings().opening().blockCommands()
                && (s.state == OpeningSession.State.ROLLING || s.state == OpeningSession.State.PERSISTING
                || s.state == OpeningSession.State.ANIMATING || s.state == OpeningSession.State.REVEALING)) {
            event.setCancelled(true);
            ctx.messages().send(event.getPlayer(), "opening.command-blocked");
        }
    }

    void registerReelHitbox(UUID id) {
        reelHitboxes.add(id);
    }

    void unregisterReelHitbox(UUID id) {
        reelHitboxes.remove(id);
    }

    /** Any player clicking a running reel gets the speech bubble, as often as they like. */
    private void reelClicked(Player clicker) {
        int now = Bukkit.getCurrentTick();
        Integer last = lastBubble.get(clicker.getUniqueId());
        if (last != null && now - last < 4) {
            return;
        }
        lastBubble.put(clicker.getUniqueId(), now);
        bubble.show(clicker);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onReelRightClick(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (reelHitboxes.contains(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
                reelClicked(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onReelLeftClick(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        if (reelHitboxes.contains(event.getAttacked().getUniqueId())) {
            event.setCancelled(true);
            reelClicked(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        lastBubble.remove(event.getPlayer().getUniqueId());
        bubble.remove(event.getPlayer());
        OpeningSession s = sessions.remove(event.getPlayer().getUniqueId());
        if (s == null) {
            return;
        }
        if (s.task != null) {
            s.task.cancel();
        }
        if (s.view != null) {
            s.view.close();
        }
        if (s.state == OpeningSession.State.ANIMATING || s.state == OpeningSession.State.REVEALING) {
            // not yet revealed rewards stay PENDING; the next login completes them with a message
            s.state = OpeningSession.State.FINISHED;
        } else if (s.state == OpeningSession.State.ROLLING) {
            s.state = OpeningSession.State.ABORTED;
        }
    }

    /** Plugin disable: finalize everything that is already stored. */
    public void shutdown() {
        bubble.removeAll();
        for (OpeningSession s : sessions.values().toArray(OpeningSession[]::new)) {
            if (s.task != null) {
                s.task.cancel();
            }
            if (s.state == OpeningSession.State.ANIMATING || s.state == OpeningSession.State.REVEALING) {
                finalizeReward(s);
                s.state = OpeningSession.State.FINISHED;
            }
            if (s.view != null) {
                s.view.close();
            }
        }
        sessions.clear();
    }

    public SkinDefinition rewardOf(Player player) {
        OpeningSession s = sessions.get(player.getUniqueId());
        return s == null ? null : s.reward;
    }
}
