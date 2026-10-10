package dev.plattnericus.cases.commerce;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.profile.PlayerProfile;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.storage.CommerceRepository;
import dev.plattnericus.cases.storage.CommerceRepository.Listing;
import dev.plattnericus.cases.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Coordinates the server-thread views with durable ownership transfers. */
public final class CommerceService implements Listener {
    private record Invitation(UUID sender, long expires) { }
    private final CasesContext ctx;
    private final CommerceRepository repository;
    private final Map<UUID, Listing> listings = new HashMap<>();
    private final EmeraldPayments payments;
    private final ChatInput input;
    public ChatInput input() { return input; }
    private final Map<UUID, Invitation> invitations = new HashMap<>();
    private final Map<UUID, TradeSession> trades = new HashMap<>();
    private final Map<TradeSession, Long> activity = new HashMap<>();
    private final Set<UUID> locked = new HashSet<>(), busyListings = new HashSet<>();
    private boolean loaded, enabled = true, stopped;
    private long minPrice = 1, maxPrice = 1_000_000;
    private boolean includeOffhand = true, partialClaims = true;
    private int maxDelivery = 2304;
    public int maxDelivery() { return maxDelivery; }
    public boolean partialClaims() { return partialClaims; }
    private int tradeLimit = 12, reviewMillis = 2000;
    private int listingLimit = 20, requestSeconds = 60, idleSeconds = 300;

    private BukkitTask timer;

    public CommerceService(CasesContext ctx, CommerceRepository repository) { this.ctx = ctx; this.repository = repository; payments = new EmeraldPayments(ctx, repository.emeralds()); input = new ChatInput(ctx); }
    public CommerceRepository repository() { return repository; }
    public void load() {
        var y = YamlConfiguration.loadConfiguration(new File(ctx.plugin().getDataFolder(), "market.yml"));
        enabled = y.getBoolean("enabled", true);
        minPrice = Math.clamp(y.getLong("emeralds.min-price", 1), 1, 1_000_000);
        includeOffhand = y.getBoolean("emeralds.include-offhand", true);
        partialClaims = y.getBoolean("emeralds.partial-claims", true);
        maxDelivery = Math.clamp(y.getInt("emeralds.max-items-per-claim", 2304), 1, 2304);
        tradeLimit = Math.clamp(y.getInt("trade-max-skins", 12), 1, 12);
        reviewMillis = Math.clamp(y.getInt("trade-review-millis", 2000), 500, 10000);
        maxPrice = Math.clamp(y.getLong("emeralds.max-price", 1_000_000), minPrice, 1_000_000);
        listingLimit = Math.clamp(y.getInt("max-listings-per-player", 20), 1, 1000);
        requestSeconds = Math.clamp(y.getInt("trade-request-seconds", 60), 10, 600);
        idleSeconds = Math.clamp(y.getInt("trade-idle-seconds", 300), 30, 3600);

    }
    public void start() {
        reloadListings();
        timer = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), () -> {
            long now = System.currentTimeMillis();
            invitations.entrySet().removeIf(e -> {
                if (e.getValue().expires() > now) return false;
                notifyPlayer(e.getValue().sender(), "trade.expired"); notifyPlayer(e.getKey(), "trade.expired"); return true;
            });
            for (TradeSession trade : new HashSet<>(trades.values())) {
                if (!trade.committing() && now - activity.getOrDefault(trade, now) >= idleSeconds * 1000L) cancel(trade, "trade.expired");
                else for (UUID id : List.of(trade.first(), trade.second())) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.getOpenInventory().getTopInventory().getHolder(false) instanceof TradeMenu menu
                            && menu.belongsTo(trade)) menu.refreshCountdown(now);
                }
            }
        }, 5, 5);
    }
    public void reloadListings() { finish(repository.listings(), all -> { listings.clear(); all.forEach(l -> listings.put(l.id(), l)); loaded = true; refreshMarketViews(); }, null); }
    public String currency() { return ctx.messages().raw("market.currency"); }
    public boolean includeOffhand() { return includeOffhand; }
    public long minPrice() { return minPrice; }
    public EmeraldPayments payments() { return payments; }
    public long maxPrice() { return maxPrice; }
    public boolean available() { return loaded && enabled && !stopped; }
    public List<Listing> listings() { return List.copyOf(listings.values()); }
    public Listing listing(UUID id) { return listings.get(id); }
    public boolean available(Listing listing) {
        Listing current = listings.get(listing.id());
        return current != null && current.price() == listing.price() && current.skin().id().equals(listing.skin().id()) && current.skin().owner().equals(listing.skin().owner());
    }
    public boolean locked(UUID id) { return locked.contains(id); }
    public boolean reserveMutation(UUID id) { return locked.add(id); }
    public void releaseMutation(UUID id) { locked.remove(id); }
    public TradeSession trade(UUID player) { return trades.get(player); }
    public void touch(TradeSession trade) {
        if (trades.get(trade.first()) == trade && !trade.committing()) activity.put(trade, System.currentTimeMillis());
    }
    public boolean mutable(PlayerProfile profile, SkinInstance skin) {
        if (profile == null || profile.get(skin.id()) != skin || !profile.owner().equals(skin.owner())
                || skin.status() != SkinInstance.Status.OWNED || locked(skin.id())) return false;
        PlayerProfile live = ctx.profiles().get(profile.owner());
        return live == null || live == profile;
    }
    public long inventoryBalance(Player player) { return EmeraldItems.count(player.getInventory(), includeOffhand); }
    public void refreshBalance(Player player, Runnable done) { payments.refresh(player, done); }

    public void request(Player sender, Player target) {
        if (target == null || sender.equals(target) || !sender.canSee(target) || !sender.hasPermission("mccases.trade")
                || !target.hasPermission("mccases.trade") || !canUse(sender) || !canUse(target)) {
            ctx.messages(sender).send(sender, "trade.unavailable"); return;
        }
        if (trades.containsKey(sender.getUniqueId()) || trades.containsKey(target.getUniqueId())
                || invitations.containsKey(target.getUniqueId()) || invitations.values().stream().anyMatch(i -> i.sender().equals(sender.getUniqueId()))) {
            ctx.messages(sender).send(sender, "trade.busy"); return;
        }
        invitations.put(target.getUniqueId(), new Invitation(sender.getUniqueId(), System.currentTimeMillis() + requestSeconds * 1000L));
        ctx.messages(sender).send(sender, "trade.sent", Text.unparsed("player", target.getName()), Text.unparsed("seconds", requestSeconds));
        ctx.messages(target).send(target, "trade.received", Text.unparsed("player", sender.getName()));
        target.sendMessage(ctx.messages(target).get("trade.accept-link")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/trade accept " + sender.getName()))
                .append(net.kyori.adventure.text.Component.text("  "))
                .append(ctx.messages(target).get("trade.decline-link")
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/trade decline"))));
    }
    public void accept(Player target, String expectedSender) {
        TradeSession active = trade(target.getUniqueId());
        if (active != null) {
            if (active.committing()) { ctx.messages(target).send(target, "trade.busy"); return; }
            Player partner = Bukkit.getPlayer(active.other(target.getUniqueId()));
            if (expectedSender == null || partner != null && partner.getName().equalsIgnoreCase(expectedSender)) confirm(target);
            else ctx.messages(target).send(target, "trade.no-request");
            return;
        }
        Invitation invite = invitations.get(target.getUniqueId());
        Player sender = invite == null ? null : Bukkit.getPlayer(invite.sender());
        if (invite == null || sender == null || invite.expires() < System.currentTimeMillis()
                || expectedSender != null && !sender.getName().equalsIgnoreCase(expectedSender)) {
            ctx.messages(target).send(target, "trade.no-request"); return;
        }
        if (!sender.hasPermission("mccases.trade") || !target.hasPermission("mccases.trade")
                || !canUse(sender) || !canUse(target) || trades.containsKey(sender.getUniqueId()) || trades.containsKey(target.getUniqueId())) {
            ctx.messages(target).send(target, "trade.busy"); return;
        }
        invitations.entrySet().removeIf(e -> e.getKey().equals(sender.getUniqueId()) || e.getKey().equals(target.getUniqueId())
                || e.getValue().sender().equals(sender.getUniqueId()) || e.getValue().sender().equals(target.getUniqueId()));
        TradeSession trade = new TradeSession(sender.getUniqueId(), target.getUniqueId(), tradeLimit, reviewMillis);
        trades.put(trade.first(), trade); trades.put(trade.second(), trade); activity.put(trade, System.currentTimeMillis());
        ctx.gallery().close(sender); ctx.gallery().close(target); ctx.inspect().stop(sender); ctx.inspect().stop(target);
        new TradeMenu(ctx, sender, trade).open(); new TradeMenu(ctx, target, trade).open();
    }
    public void decline(Player player) {
        Invitation invite = invitations.remove(player.getUniqueId());
        if (invite == null) { ctx.messages(player).send(player, "trade.no-request"); return; }
        notifyPlayer(invite.sender(), "trade.declined"); ctx.messages(player).send(player, "trade.declined");
    }
    public void toggle(Player player, UUID id) {
        TradeSession trade = trade(player.getUniqueId());
        if (trade == null || trade.committing()) return;
        SkinInstance skin = ctx.profiles().get(player).get(id);
        boolean offered = trade.items(player.getUniqueId()).contains(id);
        if (!offered && (skin == null || !mutable(ctx.profiles().get(player), skin) || skin.origin() == SkinInstance.Origin.TEST)) {
            ctx.messages(player).send(player, "commerce.locked"); return;
        }
        if (!trade.toggle(player.getUniqueId(), id)) { ctx.messages(player).send(player, "trade.limit"); return; }
        if (offered) locked.remove(id);
        else {
            locked.add(id);
            var slot = ctx.profiles().get(player).slotOf(id); if (slot != null) ctx.knives().unequip(player, slot);
            ctx.inspect().stop(player);
        }
        activity.put(trade, System.currentTimeMillis()); refreshTrade(trade);
    }
    public void confirm(Player player) {
        TradeSession trade = trade(player.getUniqueId());
        if (trade != null) confirm(player, trade.revision());
    }
    public void confirm(Player player, int expectedRevision) {
        TradeSession trade = trade(player.getUniqueId());
        if (trade == null || trade.committing()) return;
        if (!canUse(player) || !player.hasPermission("mccases.trade")) { cancel(trade, "trade.cancelled"); return; }
        if (trade.revision() != expectedRevision) { ctx.messages(player).send(player, "trade.changed"); return; }
        if (!trade.confirm(player.getUniqueId(), expectedRevision, System.currentTimeMillis())) { ctx.messages(player).send(player, "trade.wait"); return; }
        activity.put(trade, System.currentTimeMillis()); refreshTrade(trade);
        if (!trade.ready()) return;
        Player other = Bukkit.getPlayer(trade.other(player.getUniqueId()));
        if (other == null || !canUse(other) || !other.hasPermission("mccases.trade")) { cancel(trade, "trade.cancelled"); return; }
        trade.beginCommit(); refreshTrade(trade);
        List<CommerceRepository.Transfer> transfers = new ArrayList<>();
        for (UUID owner : List.of(trade.first(), trade.second())) for (UUID skin : trade.items(owner))
            transfers.add(new CommerceRepository.Transfer(skin, owner, trade.other(owner)));
        finish(repository.trade(trade.first(), trade.second(), transfers), skins -> {
            for (SkinInstance skin : skins) applyTransfer(trade.other(skin.owner()), skin);
            end(trade); closeTradeViews(trade); notifyPlayer(trade.first(), "trade.completed"); notifyPlayer(trade.second(), "trade.completed");
            for (UUID id : List.of(trade.first(), trade.second())) { Player p = Bukkit.getPlayer(id); if (p != null) ctx.sounds().play(p, "trade.complete"); }
        }, player, () -> { end(trade); closeTradeViews(trade); notifyPlayer(trade.first(), "trade.failed"); notifyPlayer(trade.second(), "trade.failed"); });
    }
    public void cancel(Player player) {
        cancel(player, false);
    }
    /** Explicit command cancellation also acknowledges invitations and idle/no-op requests. */
    public void cancel(Player player, boolean feedback) {
        TradeSession trade = trade(player.getUniqueId());
        if (feedback && trade != null && trade.committing()) { ctx.messages(player).send(player, "trade.busy"); return; }
        if (trade != null) cancel(trade, "trade.cancelled");
        boolean removed = invitations.entrySet().removeIf(e -> e.getKey().equals(player.getUniqueId()) || e.getValue().sender().equals(player.getUniqueId()));
        if (feedback && trade == null) ctx.messages(player).send(player, removed ? "trade.cancelled" : "trade.no-request");
    }
    public void unconfirm(Player player, int expectedRevision) {
        TradeSession trade = trade(player.getUniqueId());
        if (trade == null || trade.committing()) return;
        if (!canUse(player) || !player.hasPermission("mccases.trade")) { cancel(trade, "trade.cancelled"); return; }
        if (trade.revision() != expectedRevision) { ctx.messages(player).send(player, "trade.changed"); return; }
        if (trade.unconfirm(player.getUniqueId())) {
            activity.put(trade, System.currentTimeMillis()); refreshTrade(trade);
        }
    }
    public void cancel(TradeSession trade, String message) {
        if (trade.committing() || trades.get(trade.first()) != trade) return;
        end(trade); closeTradeViews(trade); notifyPlayer(trade.first(), message); notifyPlayer(trade.second(), message);
        for (UUID id : List.of(trade.first(), trade.second())) { Player p = Bukkit.getPlayer(id); if (p != null) ctx.sounds().play(p, "trade.cancel"); }
    }
    private void end(TradeSession trade) {
        trades.remove(trade.first(), trade); trades.remove(trade.second(), trade); activity.remove(trade);
        locked.removeAll(trade.items(trade.first())); locked.removeAll(trade.items(trade.second()));
    }
    private void closeTradeViews(TradeSession trade) {
        for (UUID id : List.of(trade.first(), trade.second())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && (p.getOpenInventory().getTopInventory().getHolder(false) instanceof TradeMenu menu && menu.belongsTo(trade)
                    || p.getOpenInventory().getTopInventory().getHolder(false) instanceof SkinPickerMenu picker && picker.belongsTo(trade)
                    || p.getOpenInventory().getTopInventory().getHolder(false) instanceof dev.plattnericus.cases.gui.ChoiceMenu<?> choices && choices.belongsTo(trade))) p.closeInventory();
        }
    }
    private void refreshTrade(TradeSession trade) {
        for (UUID id : List.of(trade.first(), trade.second())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getOpenInventory().getTopInventory().getHolder(false) instanceof TradeMenu menu && menu.belongsTo(trade)) menu.render();
            else if (p != null && p.getOpenInventory().getTopInventory().getHolder(false) instanceof SkinPickerMenu picker && picker.belongsTo(trade)) picker.render();
        }
    }

    public void list(Player seller, SkinInstance skin, long price) {
        if (!canUse(seller) || !seller.hasPermission("mccases.market") || !mutable(ctx.profiles().get(seller), skin)
                || skin.origin() == SkinInstance.Origin.TEST || trade(seller.getUniqueId()) != null) {
            ctx.messages(seller).send(seller, "commerce.locked"); return;
        }
        if (price < minPrice || price > maxPrice) { ctx.messages(seller).send(seller, "market.invalid"); return; }
        locked.add(skin.id()); ctx.inspect().stop(seller);
        finish(repository.list(seller.getUniqueId(), seller.getName(), skin.id(), price, maxPrice, listingLimit), listing -> {
            locked.remove(skin.id()); skin.setStatus(SkinInstance.Status.LISTED);
            ctx.profiles().addLoaded(seller.getUniqueId(), listing.skin());
            PlayerProfile profile = ctx.profiles().get(seller); if (profile != null) {
                for (var slot : dev.plattnericus.cases.profile.EquipSlot.values())
                    if (skin.id().equals(profile.equipped(slot))) { profile.setEquipped(slot, null); ctx.knives().strip(seller, slot); }
            }
            listings.put(listing.id(), listing);
            ctx.messages(seller).send(seller, "market.listed", Text.unparsed("price", price), Text.unparsed("currency", currency()));
            if (seller.isOnline()) new MarketMenu(ctx, seller).own().open(); refreshMarketViews();
        }, seller, () -> locked.remove(skin.id()));
    }
    public void cancelListing(Player seller, Listing listing) {
        if (!listing.skin().owner().equals(seller.getUniqueId()) || !beginListing(seller, listing)) return;
        finish(repository.cancel(seller.getUniqueId(), listing.id()), skin -> {
            releaseListing(listing); listings.remove(listing.id()); ctx.profiles().addLoaded(seller.getUniqueId(), skin);
            ctx.messages(seller).send(seller, "market.cancelled"); if (seller.isOnline()) new MarketMenu(ctx, seller).own().open(); refreshMarketViews();
        }, seller, () -> releaseListing(listing));
    }
    public void buy(Player buyer, Listing listing) {
        if (listing.skin().owner().equals(buyer.getUniqueId()) || ctx.catalog().skin(listing.skin().skinId()) == null || !beginListing(buyer, listing)) return;
        if (inventoryBalance(buyer) < listing.price()) { releaseListing(listing); ctx.messages(buyer).send(buyer, "market.emerald-not-enough"); return; }
        payments.buy(buyer, listing, skin -> {
            listings.remove(listing.id()); applyTransfer(listing.skin().owner(), skin);
            if (buyer.isOnline()) ctx.messages(buyer).send(buyer, "market.bought", Text.unparsed("price", listing.price()), Text.unparsed("currency", currency()));
            notifyPlayer(listing.skin().owner(), "market.emerald-sold");
            Player seller = Bukkit.getPlayer(listing.skin().owner());
            if (seller != null) refreshBalance(seller, this::refreshMarketViews);
            if (buyer.isOnline()) new MarketMenu(ctx, buyer).open();
            refreshMarketViews();
        }, () -> releaseListing(listing));
    }

    private boolean beginListing(Player player, Listing listing) {
        if (!canUse(player) || !player.hasPermission("mccases.market") || trade(player.getUniqueId()) != null
                || !available(listing) || !busyListings.add(listing.id())) {
            ctx.messages(player).send(player, "market.unavailable"); return false;
        }
        locked.add(listing.skin().id()); return true;
    }
    private void releaseListing(Listing listing) { busyListings.remove(listing.id()); locked.remove(listing.skin().id()); }
    private void applyTransfer(UUID previousOwner, SkinInstance skin) {
        ctx.profiles().removeLoaded(previousOwner, skin.id()); ctx.profiles().addLoaded(skin.owner(), skin);
        Player old = Bukkit.getPlayer(previousOwner);
        if (old != null) { ctx.inspect().stop(old); ctx.gallery().close(old); ctx.knives().refreshHeld(old); }
        Player next = Bukkit.getPlayer(skin.owner()); if (next != null) ctx.gallery().close(next);
    }
    private boolean canUse(Player player) { return available() && player.isOnline() && ctx.profiles().get(player) != null && !payments.busy(player.getUniqueId()) && (ctx.tradeIns() == null || !ctx.tradeIns().busy(player)); }
    private void refreshMarketViews() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof MarketMenu menu) menu.render();
            else if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof ListingMenu menu) menu.render();
        }
    }
    private void notifyPlayer(UUID id, String key) { Player p = Bukkit.getPlayer(id); if (p != null) ctx.messages(p).send(p, key); }
    private <T> void finish(CompletableFuture<T> future, Consumer<T> success, org.bukkit.command.CommandSender audience) {
        finish(future, success, audience, () -> { });
    }
    private <T> void finish(CompletableFuture<T> future, Consumer<T> success, org.bukkit.command.CommandSender audience, Runnable failed) {
        future.whenComplete((result, error) -> {
            if (stopped || !ctx.plugin().isEnabled()) return;
            Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                if (error == null) { success.accept(result); return; }
                failed.run(); Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
                if (cause instanceof CommerceRepository.Rejected rejection) {
                    if (audience != null) ctx.messages(audience).send(audience, switch (rejection.reason()) {
                        case FUNDS -> "market.emerald-not-enough"; case LIMIT -> "market.emerald-limit"; case INVALID -> "market.invalid"; case UNAVAILABLE -> "market.unavailable";
                    });
                } else {
                    ctx.plugin().getLogger().log(Level.SEVERE, "Commerce transaction failed", error);
                    if (audience != null) ctx.messages(audience).send(audience, "commerce.storage-error");
                }
            });
        });
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) { cancel(event.getPlayer()); payments.forget(event.getPlayer().getUniqueId()); }
    public void shutdown() {
        input.shutdown();
        if (timer != null) timer.cancel();
        for (TradeSession trade : new HashSet<>(trades.values())) cancel(trade, "trade.cancelled"); stopped = true;
    }
}
