package dev.plattnericus.cases.opening;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.menu.SkinInspectMenu;
import dev.plattnericus.cases.items.CaseItems;
import dev.plattnericus.cases.pattern.PatternReport;
import dev.plattnericus.cases.profile.PendingJournal;
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
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Independent opening IDs; persisted results are decoupled from every animation and visible menu. */
public final class OpeningService implements Listener {

    private final CasesContext ctx;
    private final PendingJournal journal;
    private final RewardRoller roller;
    private final RevealEffects effects;
    private final OpeningSessions sessions = new OpeningSessions();
    private final SpeechBubble bubble;
    private boolean stopped;
    private boolean pumping;
    private final OpeningQueue queue = new OpeningQueue();
    private final java.util.Set<UUID> presentationGap = new java.util.HashSet<>();
    private final Map<UUID, java.util.Deque<SkinInstance>> recent = new HashMap<>();
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
        return sessions.values().stream().anyMatch(s -> s.playerId.equals(player.getUniqueId()) && s.isActive());
    }
    public int activeCount(Player player) { return (int) sessions.values().stream().filter(s -> s.playerId.equals(player.getUniqueId())).count(); }
    public java.util.List<OpeningSession> active(Player player) { return sessions.forPlayer(player.getUniqueId()); }
    public java.util.List<SkinInstance> recent(Player player) { return java.util.List.copyOf(recent.getOrDefault(player.getUniqueId(), new java.util.ArrayDeque<>())); }
    void viewClosed(OpeningSession session) {
        sessions.remove(session.openingId, session);
        Player player = Bukkit.getPlayer(session.playerId);
        if (!stopped && player != null && session.view instanceof WorldReelView) {
            presentationGap.add(session.playerId);
            // Removed displays and new displays can otherwise share a client frame while
            // Paper flushes its tracking packets. Give the old scene two ticks to disappear.
            Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> {
                presentationGap.remove(session.playerId);
                presentNext(player);
            }, 2);
        } else if (!stopped && player != null) presentNext(player);
        pump();
    }
    public int queuedCount(Player player) { return queue.count(player.getUniqueId()); }
    public int cancelQueued(Player player) { return queue.clear(player.getUniqueId()); }
    public int visibleCount(Player player) { return (int) active(player).stream().filter(s -> s.view != null).count(); }
    private int playerLimit() { return Math.min(9, ctx.settings().opening().maxActive()); }
    public void show(Player player, UUID id) {
        OpeningSession session = sessions.get(id);
        if (session != null && session.playerId.equals(player.getUniqueId()) && session.view instanceof OpeningMenu menu) menu.open();
    }

    /**
     * Starts an opening. {@code adminTest} skips case/key consumption; {@code keep} decides whether a
     * test reward is stored in the admin's inventory.
     */
    public void open(Player player, CaseDefinition def, boolean adminTest, boolean keep) {
        if (adminTest) admit(player, def, 1, true, keep, false);
        else queue(player, def, 1);
    }

    public int available(Player player, CaseDefinition def) {
        if (stopped) return 0;
        UUID owner = player.getUniqueId();
        return Math.max(0, Math.min(
                ctx.caseItems().count(player, CaseItems.TYPE_CASE, def.id()) - sessions.reserved(owner, def.id(), false) - queue.reserved(owner, def.id(), false),
                ctx.caseItems().count(player, CaseItems.TYPE_KEY, def.keyId()) - sessions.reserved(owner, def.keyId(), true) - queue.reserved(owner, def.keyId(), true)));
    }

    public void openNine(Player player, CaseDefinition def) { queue(player, def, 9); }

    /** Reserve a complete request before starting it; waiting entries consume no items. */
    public void queue(Player player, CaseDefinition def, int amount) {
        if (stopped || !player.isOnline()) return;
        if (ctx.profiles().get(player) == null) { ctx.messages(player).send(player, "profile.loading"); return; }
        CaseDefinition current = ctx.catalog().caseDefinition(def.id());
        if (current == null || !current.enabled() || ctx.catalog().key(current.keyId()) == null) {
            ctx.messages(player).send(player, "opening.case-disabled"); return;
        }
        if (amount < 1 || amount > OpeningQueue.LIMIT - queuedCount(player) - activeCount(player)) {
            ctx.messages(player).send(player, "opening.queue-full"); return;
        }
        if (available(player, current) < amount) {
            ctx.messages(player).send(player, "opening.missing-amount", Text.unparsed("amount", amount),
                    Text.unparsed("case", current.name())); ctx.sounds().play(player, "gui.error"); return;
        }
        boolean sequence = amount > 1 || activeCount(player) > 0 || queuedCount(player) > 0;
        queue.add(player.getUniqueId(), current.id(), current.keyId(), amount);
        if (sequence) ctx.messages(player).send(player, "opening.queue-added", Text.unparsed("amount", amount),
                Text.unparsed("remaining", activeCount(player) + queuedCount(player)));
        pump();
    }

    /** Fill at most nine durable slots per player. Only one of those slots owns a view. */
    private void pump() {
        if (stopped || pumping) return;
        pumping = true;
        try {
            for (UUID owner : queue.owners()) {
                Player player = Bukkit.getPlayer(owner);
                if (player == null || !player.isOnline()) { queue.clear(owner); continue; }
                int capacity;
                while ((capacity = sessions.capacity(owner, playerLimit(), ctx.settings().opening().maxGlobal())) > 0 && queue.peek(owner) != null) {
                    var head = queue.peek(owner);
                    CaseDefinition def = ctx.catalog().caseDefinition(head.caseId());
                    int amount = Math.min(capacity, head.amount());
                    int raw = def == null ? 0 : Math.min(
                            ctx.caseItems().count(player, CaseItems.TYPE_CASE, def.id()) - sessions.reserved(owner, def.id(), false),
                            ctx.caseItems().count(player, CaseItems.TYPE_KEY, head.keyId()) - sessions.reserved(owner, head.keyId(), true));
                    if (def == null || !def.enabled() || ctx.catalog().key(head.keyId()) == null || !def.keyId().equals(head.keyId()) || raw < amount) {
                        queue.clear(owner); ctx.messages(player).send(player, "opening.queue-interrupted"); break;
                    }
                    boolean sequence = amount > 1 || activeCount(player) > 0 || queuedCount(player) > amount;
                    queue.take(owner, amount);
                    if (!admit(player, def, amount, false, false, sequence)) { queue.clear(owner); break; }
                }
            }
        } finally { pumping = false; }
    }

    private boolean admit(Player player, CaseDefinition def, int amount, boolean adminTest, boolean keep, boolean sequence) {
        if (stopped || sessions.capacity(player.getUniqueId(), playerLimit(), ctx.settings().opening().maxGlobal()) < amount) {
            ctx.messages(player).send(player, "opening.limit"); return false;
        }
        Catalog catalog = ctx.catalog();
        CaseDefinition current = catalog.caseDefinition(def.id());
        if (ctx.profiles().get(player) == null || current == null || !current.enabled()) return false;
        ctx.inspect().stop(player); ctx.gallery().close(player);
        var requested = new java.util.ArrayList<OpeningSession>();
        for (int i = 0; i < amount; i++) {
            OpeningSession session = new OpeningSession(player.getUniqueId(), current, catalog, adminTest, keep);
            session.sequence = sequence;
            requested.add(session);
        }
        if (!sessions.addAll(requested, playerLimit(), ctx.settings().opening().maxGlobal())) return false;
        for (OpeningSession session : requested) roll(player, session);
        return true;
    }

    private void presentNext(Player player) {
        if (stopped || !player.isOnline() || presentationGap.contains(player.getUniqueId())) return;
        var owned = active(player);
        if (owned.stream().anyMatch(s -> s.view != null)) return;
        // Asynchronous persistence may finish out of order; presentation retains request order.
        if (!owned.isEmpty() && owned.getFirst().state == OpeningSession.State.READY) startAnimation(player, owned.getFirst());
    }

    private void roll(Player player, OpeningSession session) {
        RolledReward roll;
        try { roll = roller.roll(session.caseDef, session.catalog); }
        catch (RuntimeException error) {
            abort(session);
            ctx.plugin().getLogger().log(Level.SEVERE, "Rolling case " + session.caseDef.id() + " failed", error);
            ctx.messages(player).send(player, "opening.case-disabled");
            return;
        }
        ctx.render().report(roll.skin(), roll.pattern()).handle((report, error) -> {
            if (error != null) {
                ctx.plugin().getLogger().log(Level.WARNING, "Pattern analysis failed for " + roll.skin().id()
                        + " #" + roll.pattern() + ": " + error.getMessage());
                return PatternReport.none(null, null);
            }
            return report;
        }).thenAccept(report -> { if (!stopped && ctx.plugin().isEnabled()) Bukkit.getScheduler().runTask(ctx.plugin(), () -> commit(player, session, roll, report)); });
    }

    /** Server thread: consume items, journal, then persist asynchronously. */
    private void commit(Player player, OpeningSession session, RolledReward roll, PatternReport report) {
        if (sessions.get(session.openingId) != session || session.state != OpeningSession.State.ROLLING) {
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
            session.state = OpeningSession.State.READY;
            presentNext(player);
            return;
        }
        if (!session.adminTest) {
            // the case/key removal and the reward reach disk in the same file save
            try { journal.add(player, session.instance, record); player.saveData(); }
            catch (RuntimeException error) { ctx.plugin().getLogger().log(Level.SEVERE, "Saving opening receipt failed " + session.openingId, error); abort(session); ctx.messages(player).send(player, "opening.storage-error"); return; }
        }
        SkinInstance snapshot = session.instance;
        ctx.repository().persistOpening(snapshot, record).whenComplete((v, error) -> {
            if (!stopped && ctx.plugin().isEnabled()) Bukkit.getScheduler().runTask(ctx.plugin(), () -> afterPersist(player, session, error));
        });
    }

    private void afterPersist(Player player, OpeningSession session, Throwable error) {
        if (error != null) {
            ctx.plugin().getLogger().log(Level.SEVERE, "Storing case reward " + session.instance.id() + " failed", error);
            // Keep the saved consumption + reward journal: a failed commit may have succeeded remotely.
            // Replaying the same instance is safe; refunding here could duplicate a committed reward.
            if (player.isOnline()) ctx.messages().send(player, "opening.storage-error");
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
        if (player.isOnline() && session.interrupted) {
            ctx.profiles().addLoaded(player.getUniqueId(), session.instance);
            finalizeReward(session); return;
        }
        if (!player.isOnline() || sessions.get(session.openingId) != session) {
            // reward stays PENDING and is recovered on the next login
            return;
        }
        ctx.profiles().addLoaded(player.getUniqueId(), session.instance);
        session.state = OpeningSession.State.READY;
        presentNext(player);
    }

    private void abort(OpeningSession session) {
        session.state = OpeningSession.State.ABORTED;
        sessions.remove(session.openingId, session);
        queue.clear(session.playerId);
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null) presentNext(player);
        pump();
    }

    // ------------------------------------------------------------------ animation

    private void startAnimation(Player player, OpeningSession session) {
        Easing easing = Easing.parse(ctx.settings().opening().easing());
        int duration = ctx.settings().opening().durationTicks();
        session.duration = duration; session.easing = easing;
        session.origin = player.getEyeLocation();
        session.state = OpeningSession.State.ANIMATING;
        session.view = ctx.settings().opening().worldDisplay()
                ? new WorldReelView(ctx, player, session, this)
                : new OpeningMenu(ctx, player, session, this);
        session.tick = 0;
        if (session.view instanceof OpeningMenu menu) {
            // Keep case preview / trading menus usable while independent hidden reels run.
            Object holder = player.getOpenInventory().getTopInventory().getHolder(false);
            if (holder instanceof OpeningMenu || player.getOpenInventory().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) menu.open();
            else menu.initializeHidden();
        } else session.view.open();
        ctx.sounds().play(player, "opening.start");
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
        int pause = Math.max(session.view.settleTicks(), gold ? ctx.settings().opening().goldPauseTicks() : 2);
        session.task = Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> {
            if (session.state != OpeningSession.State.REVEALING || !player.isOnline()) {
                return;
            }
            session.revealed = true;
            finalizeReward(session);
            session.view.reveal();
            effects.reveal(player, session.reward, session.instance, !session.sequence);
            session.task = Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> {
                if (session.state != OpeningSession.State.REVEALING || !player.isOnline()) {
                    return;
                }
                session.state = OpeningSession.State.FINISHED;
                session.view.result();
                if (session.view instanceof OpeningMenu) viewClosed(session);
                if (gold && !session.sequence && session.reward.isKnife() && ctx.settings().opening().knifeAutoPreview()
                        && ctx.settings().inspect().revealPreview() && session.persistent() && active(player).stream().noneMatch(other -> other != session) && queuedCount(player) == 0
                        && player.getOpenInventory().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) {
                    session.view.close();
                    ctx.inspect().start(player, session.instance, true);
                }
            }, session.sequence ? Math.min(20, ctx.settings().opening().revealHoldTicks()) : ctx.settings().opening().revealHoldTicks());
        }, pause);
    }

    /** Marks the reward as owned (exactly once) and announces it in chat. */
    private void finalizeReward(OpeningSession session) {
        if (session.instance == null || session.finalized) {
            return;
        }
        session.finalized = true;
        if (session.persistent()) {
            ctx.repository().setStatus(session.instance.id(), SkinInstance.Status.OWNED).whenComplete((ignored, error) -> {
                if (stopped || !ctx.plugin().isEnabled()) return;
                Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                    if (error != null) {
                        ctx.plugin().getLogger().log(Level.SEVERE, "Finalizing reward failed; retained PENDING " + session.instance.id(), error);
                        Player player = Bukkit.getPlayer(session.playerId);
                        if (player != null) ctx.messages(player).send(player, "opening.storage-error");
                        return;
                    }
                    session.instance.setStatus(SkinInstance.Status.OWNED);
                    var profile = ctx.profiles().get(session.playerId);
                    if (profile != null && profile.get(session.instance.id()) != null) profile.get(session.instance.id()).setStatus(SkinInstance.Status.OWNED);
                    publishReward(session);
                });
            });
        } else publishReward(session);
    }
    private void publishReward(OpeningSession session) {
        var results = recent.computeIfAbsent(session.playerId, id -> new java.util.ArrayDeque<>());
        results.addFirst(session.instance.copyWithStatus(SkinInstance.Status.OWNED));
        while (results.size() > 20) results.removeLast();
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null) effects.announce(player, session.reward, session.instance, session.caseDef, session.adminTest && !session.keep);
    }

    /**
     * Teleport, world change or death during a world reel: finish instantly like closing the menu.
     */
    private void interrupt(Player player) {
        queue.clear(player.getUniqueId());
        var owned = active(player);
        // Remove all slots before closing views, so close callbacks cannot start another reel.
        for (OpeningSession s : owned) sessions.remove(s.openingId, s);
        for (OpeningSession s : owned) {
            if (s.task != null) s.task.cancel();
            s.interrupted = true;
            if (s.state == OpeningSession.State.ROLLING) s.state = OpeningSession.State.ABORTED;
            else if (s.state != OpeningSession.State.PERSISTING) { finalizeReward(s); s.state = OpeningSession.State.FINISHED; }
            if (s.view != null) s.view.close();
        }
        pump();
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

    /** Closing a chest only detaches its presentation; the session keeps running. */
    void onMenuClosed(Player player, OpeningSession session) { }

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
        queue.clear(event.getPlayer().getUniqueId());
        presentationGap.remove(event.getPlayer().getUniqueId());
        lastBubble.remove(event.getPlayer().getUniqueId());
        bubble.remove(event.getPlayer());
        recent.remove(event.getPlayer().getUniqueId());
        var quitting = active(event.getPlayer());
        for (OpeningSession s : quitting) sessions.remove(s.openingId, s);
        for (OpeningSession s : quitting) {
            if (s.task != null) s.task.cancel();
            if (s.view != null) s.view.close();
            if (s.state == OpeningSession.State.ROLLING) s.state = OpeningSession.State.ABORTED;
            // PERSISTING / PENDING rewards are replayed on next login.
        }
        pump();
    }

    /** Plugin disable: finalize everything that is already stored. */
    public void shutdown() {
        stopped = true;
        queue.clear();
        presentationGap.clear();
        bubble.removeAll();
        for (OpeningSession s : sessions.values().toArray(OpeningSession[]::new)) {
            if (s.task != null) {
                s.task.cancel();
            }
            if (s.state == OpeningSession.State.READY || s.state == OpeningSession.State.ANIMATING || s.state == OpeningSession.State.REVEALING) {
                finalizeReward(s);
                s.state = OpeningSession.State.FINISHED;
            }
            if (s.view != null) {
                s.view.close();
            }
        }
        sessions.clear(); recent.clear();
    }

    public SkinDefinition rewardOf(Player player) {
        return active(player).stream().map(s -> s.reward).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
}
