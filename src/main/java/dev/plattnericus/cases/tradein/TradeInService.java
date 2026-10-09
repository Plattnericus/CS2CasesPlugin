package dev.plattnericus.cases.tradein;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.skin.PatternInfo;
import dev.plattnericus.cases.storage.ContractRepository;
import dev.plattnericus.cases.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Contracts are independent of direct player trades and never use case-drop broadcasts. */
public final class TradeInService implements Listener {
    private final CasesContext ctx;
    private final ContractRepository repository;
    private final Map<UUID, Set<UUID>> selected = new HashMap<>();
    private final Set<UUID> committing = new java.util.HashSet<>();
    private boolean stopped;
    private org.bukkit.configuration.file.YamlConfiguration config;
    public void load() { config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(ctx.plugin().getDataFolder(), "config.yml")); }
    public TradeInService(CasesContext ctx, ContractRepository repository) { this.ctx = ctx; this.repository = repository; load(); }
    public boolean enabled() { return config.getBoolean("trade-in.enabled", true); }
    public boolean allowAdmin() { return config.getBoolean("trade-in.allow-admin-inputs", true); }
    public List<UUID> selected(Player p) { return List.copyOf(selected.getOrDefault(p.getUniqueId(), Set.of())); }
    public boolean busy(Player p) { return committing.contains(p.getUniqueId()); }
    public void toggle(Player p, UUID id) {
        if (!enabled() || busy(p) || ctx.commerce().trade(p.getUniqueId()) != null) return;
        Set<UUID> offer = selected.computeIfAbsent(p.getUniqueId(), key -> new LinkedHashSet<>());
        if (offer.remove(id)) { ctx.commerce().releaseMutation(id); return; }
        var profile = ctx.profiles().get(p); var skin = profile == null ? null : profile.get(id);
        if (skin == null || !ctx.commerce().mutable(profile, skin)) { ctx.messages(p).send(p, "commerce.locked"); return; }
        var first = offer.isEmpty() ? skin : profile.get(offer.iterator().next());
        if (!TradeInRules.compatible(ctx.catalog(), first, skin, allowAdmin())) { ctx.messages(p).send(p, "tradein.invalid"); return; }
        if (offer.size() >= TradeInRules.required(TradeInRules.target(ctx.catalog(), ctx.catalog().skin(first.skinId())))) {
            ctx.messages(p).send(p, "tradein.count"); return;
        }
        if (!ctx.commerce().reserveMutation(id)) return;
        offer.add(id); var slot = profile.slotOf(id); if (slot != null) ctx.knives().unequip(p, slot); ctx.inspect().stop(p);
    }
    private void main(Runnable run) { if (!stopped && ctx.plugin().isEnabled()) Bukkit.getScheduler().runTask(ctx.plugin(), run); }
    public void confirm(Player p) {
        if (!enabled() || !p.hasPermission("mccases.tradein") || busy(p) || ctx.commerce().trade(p.getUniqueId()) != null) return;
        var profile = ctx.profiles().get(p); if (profile == null) return;
        var ids = selected(p); var inputs = ids.stream().map(profile::get).map(s -> s == null ? null : s.copyWithStatus(s.status())).toList();
        if (inputs.stream().anyMatch(java.util.Objects::isNull)) { cancel(p); ctx.messages(p).send(p, "tradein.invalid"); return; }
        var catalog = ctx.catalog();
        dev.plattnericus.cases.catalog.Rarity target;
        try { target = TradeInRules.validate(catalog, inputs, config.getBoolean("trade-in.allow-admin-inputs", true)); }
        catch (IllegalArgumentException invalid) { ctx.messages(p).send(p, invalid.getMessage()); return; }
        committing.add(p.getUniqueId());
        UUID owner = p.getUniqueId(); UUID contract = UUID.randomUUID(); var rng = ctx.openings().roller().random();
        var source = inputs.get(rng.nextInt(inputs.size()));
        var sources = TradeInRules.sources(catalog, source, target);
        var sourceCase = sources.get(rng.nextInt(sources.size()));
        var outputPool = sourceCase.skins(target).stream().filter(def -> !source.statTrak() || def.statTrakEligible()).toList();
        var def = outputPool.get(rng.nextInt(outputPool.size()));
        int pattern = catalog.patterns().seedMin() + rng.nextInt(catalog.patterns().seedMax() - catalog.patterns().seedMin() + 1);
        double fl = TradeInRules.outputFloat(inputs, def); long seed = rng.nextLong();
        boolean admin = inputs.stream().anyMatch(s -> s.origin().admin());
        ctx.render().report(def, pattern).thenCompose(report -> {
            var result = new SkinInstance(UUID.randomUUID(), owner, def.id(), fl, pattern, seed,
                    inputs.getFirst().statTrak() && def.statTrakEligible(), 0, PatternInfo.of(report), sourceCase.id(),
                    admin ? SkinInstance.Origin.ADMIN_TRADE_IN : SkinInstance.Origin.TRADE_IN, System.currentTimeMillis(), false, SkinInstance.Status.OWNED);
            return repository.commit(contract, owner, inputs, result);
        }).whenComplete((result, error) -> main(() -> {
            committing.remove(p.getUniqueId()); release(p.getUniqueId());
            if (error != null) {
                ctx.plugin().getLogger().log(java.util.logging.Level.SEVERE, "Trade-in outcome needs reconciliation: " + contract, error);
                // Reload instead of retrying with another reward: commit acknowledgement can be ambiguous.
                if (p.isOnline()) { ctx.messages(p).send(p, "commerce.storage-error"); ctx.profiles().load(p); }
                return;
            }
            for (var input : inputs) ctx.profiles().removeLoaded(p.getUniqueId(), input.id());
            ctx.profiles().addLoaded(p.getUniqueId(), result);
            if (p.isOnline()) { ctx.messages(p).send(p, "tradein.result", Text.component("skin", ctx.formatter(p).fullName(def, result))); ctx.sounds().play(p, def.rarity().revealSound()); if (p.getOpenInventory().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) new TradeInMenu(ctx, p).open(); }
            if (def.rarity().rareSpecial() && config.getBoolean("trade-in.broadcast-gold", true)
                    && (!admin || config.getBoolean("trade-in.broadcast-admin", false))) {
                String playerName = p.getName();
                repository.claimAnnouncement(contract).whenComplete((claimed, broadcastError) -> main(() -> {
                    if (broadcastError != null) { ctx.plugin().getLogger().log(java.util.logging.Level.WARNING, "Gold broadcast claim failed " + contract, broadcastError); return; }
                    if (!claimed) return;
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        var message = ctx.messages(online).get("tradein.gold", Text.unparsed("player", playerName), Text.component("skin", ctx.formatter(online).fullName(def, result)))
                                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(ComponentDetails.details(ctx, online, def, result)));
                        online.sendMessage(message);
                        if (config.getBoolean("trade-in.broadcast-sound", true)) ctx.sounds().play(online, "tradein.gold");
                    }
                }));
            }
        }));
    }
    public void cancel(Player p) { if (!busy(p)) release(p.getUniqueId()); }
    private void release(UUID player) { var ids = selected.remove(player); if (ids != null) ids.forEach(ctx.commerce()::releaseMutation); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { cancel(event.getPlayer()); }
    public void shutdown() { stopped = true; for (UUID id : List.copyOf(selected.keySet())) release(id); }
    private static final class ComponentDetails {
        static net.kyori.adventure.text.Component details(CasesContext ctx, Player p, dev.plattnericus.cases.catalog.SkinDefinition def, SkinInstance result) {
            return ctx.formatter(p).lore(def, result, true).stream().reduce(net.kyori.adventure.text.Component.empty(), (a, b) -> a.append(b).append(net.kyori.adventure.text.Component.newline()));
        }
    }
}
