package dev.plattnericus.cases.commerce;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class CommerceCommands {
    private CommerceCommands() { }
    public static void register(Commands commands, CasesContext ctx) {
        commands.register("trade", "Trade skins with another player", List.of("skintrade"), new BasicCommand() {
            @Override public String permission() { return "mccases.trade"; }
            @Override public void execute(CommandSourceStack source, String[] args) {
                if (!(source.getExecutor() instanceof Player player)) { ctx.messages().send(source.getSender(), "general.player-only"); return; }
                if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) { ctx.commerce().cancel(player); return; }
                if (!ready(ctx, player)) return;
                if (args.length == 0) {
                    if (ctx.commerce().trade(player.getUniqueId()) != null) { ctx.messages(player).send(player, "trade.busy"); return; }
                    new TradePlayersMenu(ctx, player).open(); return;
                }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "accept" -> { if (args.length > 2) ctx.messages(player).send(player, "trade.usage"); else ctx.commerce().accept(player, args.length == 2 ? args[1] : null); }
                    case "decline" -> ctx.commerce().decline(player);
                    default -> { if (args.length == 1) ctx.commerce().request(player, Bukkit.getPlayerExact(args[0])); else ctx.messages(player).send(player, "trade.usage"); }
                }
            }
            @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
                if (args.length > 1) return List.of();
                var choices = new java.util.ArrayList<>(List.of("accept", "decline", "cancel"));
                Bukkit.getOnlinePlayers().stream().filter(p -> source.getExecutor() != p && p.hasPermission("mccases.trade")).map(Player::getName).forEach(choices::add);
                String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
            }
        });
        commands.register("market", "Buy and sell skins", List.of("marketplace", "skinmarket"), new BasicCommand() {
            @Override public String permission() { return "mccases.market"; }
            @Override public void execute(CommandSourceStack source, String[] args) {
                if (args.length > 0 && args[0].equalsIgnoreCase("credit")) {
                    if (!source.getSender().hasPermission("mccases.admin")) { ctx.messages().send(source.getSender(), "general.no-permission"); return; }
                    if (args.length != 3) { ctx.messages().send(source.getSender(), "market.credit-usage"); return; }
                    var target = Bukkit.getOfflinePlayerIfCached(args[1]);
                    if (target == null) { ctx.messages().send(source.getSender(), "view.unknown", Text.unparsed("player", args[1])); return; }
                    try { ctx.commerce().credit(source.getSender(), target.getUniqueId(), Long.parseLong(args[2])); }
                    catch (NumberFormatException e) { ctx.messages().send(source.getSender(), "market.invalid"); }
                    return;
                }
                if (!(source.getExecutor() instanceof Player player)) { ctx.messages().send(source.getSender(), "general.player-only"); return; }
                if (!ready(ctx, player)) return;
                if (ctx.commerce().trade(player.getUniqueId()) != null) { ctx.messages(player).send(player, "trade.busy"); return; }
                if (args.length == 0) { new MarketMenu(ctx, player).open(); return; }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "balance" -> ctx.commerce().refreshBalance(player, () -> ctx.messages(player).send(player, "market.balance",
                            Text.unparsed("balance", ctx.commerce().cachedBalance(player.getUniqueId())), Text.unparsed("currency", ctx.commerce().currency())));
                    case "own" -> new MarketMenu(ctx, player).own().open();
                    case "search" -> new MarketMenu(ctx, player).search(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))).open();
                    case "sell" -> {
                        if (args.length == 1) { new SkinPickerMenu(ctx, player).open(); return; }
                        if (args.length != 3) { ctx.messages(player).send(player, "market.usage"); return; }
                        var profile = ctx.profiles().get(player); String prefix = args[1].toLowerCase(Locale.ROOT);
                        var found = profile.owned().stream().filter(s -> s.id().toString().startsWith(prefix)).toList();
                        if (prefix.length() < 8 || found.size() != 1 || !ctx.commerce().mutable(profile, found.getFirst())) {
                            ctx.messages(player).send(player, "commerce.locked"); return;
                        }
                        try {
                            long price = Long.parseLong(args[2]);
                            if (price < 1 || price > ctx.commerce().maxPrice()) throw new NumberFormatException();
                            new SellMenu(ctx, player, found.getFirst(), price).open();
                        } catch (NumberFormatException e) { ctx.messages(player).send(player, "market.invalid"); }
                    }
                    default -> ctx.messages(player).send(player, "market.usage");
                }
            }
            @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
                if (args.length <= 1) {
                    var words = new java.util.ArrayList<>(List.of("sell", "own", "search", "balance"));
                    if (source.getSender().hasPermission("mccases.admin")) words.add("credit");
                    String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                    return words.stream().filter(s -> s.startsWith(prefix)).toList();
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("sell") && source.getExecutor() instanceof Player player) {
                    var profile = ctx.profiles().get(player); if (profile == null) return List.of();
                    return profile.owned().stream().filter(s -> !ctx.commerce().locked(s.id())).map(s -> s.shortId()).filter(s -> s.startsWith(args[1])).toList();
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("credit") && source.getSender().hasPermission("mccases.admin"))
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
                return List.of();
            }
        });
    }
    private static boolean ready(CasesContext ctx, Player player) {
        if (!ctx.commerce().available()) { ctx.messages(player).send(player, "market.unavailable"); return false; }
        if (ctx.profiles().get(player) == null) { ctx.messages(player).send(player, "profile.loading"); return false; }
        if (ctx.openings().isOpening(player)) { ctx.messages(player).send(player, "opening.command-blocked"); return false; }
        return true;
    }
}
