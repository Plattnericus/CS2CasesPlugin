package dev.plattnericus.cases.command;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.MenuStates;
import dev.plattnericus.cases.gui.menu.CasesMenu;
import dev.plattnericus.cases.gui.menu.SkinInventoryMenu;
import dev.plattnericus.cases.profile.PlayerProfile;
import dev.plattnericus.cases.skin.SkinInstance;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import dev.plattnericus.cases.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /inventory, /skins, /knife, /cases and /inspect. */
public final class PlayerCommands {

    private PlayerCommands() {
    }

    public static void register(Commands commands, CasesContext ctx) {
        dev.plattnericus.cases.commerce.CommerceCommands.register(commands, ctx);
        commands.register("skins", "Opens the skin gallery; vanilla opens the inventory menu", List.of("inventory"),
                inventoryCommand(ctx, MenuStates.Category.ALL));
        commands.register("knife", "Opens your or another player's knife collection", List.of("knives"),
                inventoryCommand(ctx, MenuStates.Category.KNIVES));
        commands.register("cases", "Case contents and sequential opening queue", List.of(), new BasicCommand() {
            @Override public String permission() { return "mccases.use"; }
            @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
                if (args.length <= 1) return List.of("open", "cancel");
                if (args.length == 2 && args[0].equalsIgnoreCase("open")) return ctx.catalog().cases().stream().filter(c -> c.enabled()).map(c -> c.id()).toList();
                if (args.length == 3 && args[0].equalsIgnoreCase("open")) return List.of("1", "9", "25", "50", "90", "100");
                return List.of();
            }
            @Override public void execute(CommandSourceStack source, String[] args) {
                if (!(source.getExecutor() instanceof Player p)) { ctx.messages().send(source.getSender(), "general.player-only"); return; }
                if (args.length == 0) { new CasesMenu(ctx, p).open(); return; }
                if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
                    ctx.messages(p).send(p, "opening.queue-cancelled", Text.unparsed("amount", ctx.openings().cancelQueued(p))); return;
                }
                if (args.length == 3 && args[0].equalsIgnoreCase("open")) {
                    var def = ctx.catalog().caseDefinition(args[1]);
                    try { int amount = Integer.parseInt(args[2]);
                        if (def != null && amount > 0 && amount <= 1000) { ctx.openings().queue(p, def, amount); return; }
                    } catch (NumberFormatException ignored) { }
                }
                ctx.messages(p).send(p, "opening.queue-usage");
            }
        });
        commands.register("tradein", "CS2-inspired skin contracts", List.of("tradeup"), command(ctx, "mccases.tradein", p -> {
            if (ctx.tradeIns().enabled() && ctx.profiles().get(p) != null && ctx.commerce().trade(p.getUniqueId()) == null) new dev.plattnericus.cases.tradein.TradeInMenu(ctx, p).open();
            else ctx.messages(p).send(p, "tradein.unavailable");
        }));
        commands.register("openings", "Active openings and recent results", List.of(), command(ctx, "mccases.use", p -> new dev.plattnericus.cases.opening.ActiveOpeningsMenu(ctx, p).open()));
        commands.register("inspect", "Inspects your held weapon or equipped knife; hand places it at the skin hand in F5", List.of(), new BasicCommand() {
            @Override public String permission() { return "mccases.inspect"; }
            @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
                return args.length <= 1 ? List.of("hand", "view") : List.of();
            }
            @Override public void execute(CommandSourceStack source, String[] args) {
                if (!(source.getExecutor() instanceof Player p)) {
                    ctx.messages().send(source.getSender(), "general.player-only");
                    return;
                }
                PlayerProfile profile = ctx.profiles().get(p);
                if (profile == null) {
                    ctx.messages().send(p, "profile.loading");
                    return;
                }
                ctx.knives().refreshHeld(p);
                SkinInstance knife = ctx.knives().heldSkin(p);
                if (knife == null) knife = profile.equippedKnifeInstance();
                if (knife == null) {
                    ctx.messages().send(p, "inspect.no-knife");
                    return;
                }
                if (args.length > 0 && (args[0].equalsIgnoreCase("hand") || args[0].equalsIgnoreCase("view")))
                    ctx.inspect().setBodyHandMode(p, args[0].equalsIgnoreCase("hand"));
                ctx.inspect().start(p, knife, false);
            }
        });
    }

    /** Own gallery, own vanilla menu, or another player's read-only gallery. */
    private static BasicCommand inventoryCommand(CasesContext ctx, MenuStates.Category category) {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                if (!(source.getExecutor() instanceof Player player)) {
                    ctx.messages().send(source.getSender(), "general.player-only");
                    return;
                }
                MenuStates.State state = ctx.menuStates().get(player.getUniqueId());
                long requestVersion = ++state.inventoryRequestVersion;
                if (args.length > 1) {
                    ctx.messages().send(player, "inventory.usage");
                    return;
                }
                if (args.length == 1 && args[0].equalsIgnoreCase("vanilla")) {
                    if (ctx.profiles().get(player) == null) {
                        ctx.messages().send(player, "profile.loading");
                        return;
                    }
                    ctx.gallery().close(player);
                    new SkinInventoryMenu(ctx, player).category(category).open();
                    return;
                }
                if (args.length == 0 || args[0].equalsIgnoreCase(player.getName())) {
                    ctx.gallery().open(player, category);
                    return;
                }
                if (!player.hasPermission("mccases.view")) {
                    ctx.messages().send(player, "general.no-permission");
                    return;
                }
                OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    target = Bukkit.getOfflinePlayerIfCached(args[0]);
                }
                if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
                    ctx.messages().send(player, "view.unknown", Text.unparsed("player", args[0]));
                    return;
                }
                String name = target.getName() == null ? args[0] : target.getName();
                ctx.profiles().snapshot(target.getUniqueId()).whenComplete((profile, error) ->
                        Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                            if (!player.isOnline() || ctx.menuStates().get(player.getUniqueId()) != state
                                    || state.inventoryRequestVersion != requestVersion) {
                                return;
                            }
                            if (error != null || profile == null) {
                                ctx.messages().send(player, "view.unknown", Text.unparsed("player", name));
                                return;
                            }
                            state.category = category;
                            state.page = 0;
                            ctx.gallery().openOther(player, profile, name);
                        }));
            }

            @Override
            public Collection<String> suggest(CommandSourceStack source, String[] args) {
                if (args.length > 1) {
                    return List.of();
                }
                String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                List<String> suggestions = new ArrayList<>();
                if ("vanilla".startsWith(prefix)) suggestions.add("vanilla");
                boolean mayViewOthers = source.getSender().hasPermission("mccases.view");
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> mayViewOthers || p.equals(source.getExecutor()))
                        .map(Player::getName)
                        .filter(n -> !n.equalsIgnoreCase("vanilla"))
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .forEach(suggestions::add);
                return suggestions;
            }

            @Override
            public String permission() {
                return "mccases.use";
            }
        };
    }

    private static BasicCommand command(CasesContext ctx, String permission, java.util.function.Consumer<Player> action) {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                if (!(source.getExecutor() instanceof Player player)) {
                    ctx.messages().send(source.getSender(), "general.player-only");
                    return;
                }
                action.accept(player);
            }

            @Override
            public String permission() {
                return permission;
            }
        };
    }
}
