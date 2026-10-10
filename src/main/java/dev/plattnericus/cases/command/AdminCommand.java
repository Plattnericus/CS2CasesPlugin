package dev.plattnericus.cases.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.plattnericus.cases.admin.PatternBrowserMenu;
import dev.plattnericus.cases.admin.PatternScanner;
import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.KeyDefinition;
import dev.plattnericus.cases.catalog.Rarity;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesRuntime;
import dev.plattnericus.cases.items.CaseItems;
import dev.plattnericus.cases.pattern.PatternReport;
import dev.plattnericus.cases.profile.PlayerProfile;
import dev.plattnericus.cases.profile.EquipSlot;
import dev.plattnericus.cases.reward.RewardRoller;
import dev.plattnericus.cases.skin.PatternInfo;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /csadmin - every administrative function. Requires mccases.admin. */
public final class AdminCommand {

    private final CasesRuntime ctx;

    public AdminCommand(CasesRuntime ctx) {
        this.ctx = ctx;
    }

    public void register(Commands commands) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("csadmin")
                .requires(s -> s.getSender().hasPermission("mccases.admin"))
                .executes(c -> help(c.getSource().getSender()));

        root.then(Commands.literal("givecase").then(Commands.argument("player", ArgumentTypes.player())
                .then(Commands.argument("case", StringArgumentType.word()).suggests(suggest(() -> ctx.catalog().cases().stream().map(CaseDefinition::id).toList()))
                        .executes(c -> giveCase(c, 1, false))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(c -> giveCase(c, IntegerArgumentType.getInteger(c, "amount"), false))
                                .then(Commands.literal("test").executes(c -> giveCase(c, IntegerArgumentType.getInteger(c, "amount"), true)))))));

        root.then(Commands.literal("givekey").then(Commands.argument("player", ArgumentTypes.player())
                .then(Commands.argument("key", StringArgumentType.word()).suggests(suggest(() -> ctx.catalog().keys().stream().map(KeyDefinition::id).toList()))
                        .executes(c -> giveKey(c, 1, false))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(c -> giveKey(c, IntegerArgumentType.getInteger(c, "amount"), false))
                                .then(Commands.literal("test").executes(c -> giveKey(c, IntegerArgumentType.getInteger(c, "amount"), true)))))));

        root.then(Commands.literal("giveskin").then(Commands.argument("player", ArgumentTypes.player())
                .then(Commands.argument("skin", StringArgumentType.word()).suggests(skins())
                        .executes(c -> giveSkin(c, null, null, null))
                        .then(Commands.argument("float", DoubleArgumentType.doubleArg(0, 1))
                                .executes(c -> giveSkin(c, DoubleArgumentType.getDouble(c, "float"), null, null))
                                .then(Commands.argument("pattern", IntegerArgumentType.integer(0, 99999))
                                        .executes(c -> giveSkin(c, DoubleArgumentType.getDouble(c, "float"), IntegerArgumentType.getInteger(c, "pattern"), null))
                                        .then(Commands.argument("stattrak", BoolArgumentType.bool())
                                                .executes(c -> giveSkin(c, DoubleArgumentType.getDouble(c, "float"),
                                                        IntegerArgumentType.getInteger(c, "pattern"), BoolArgumentType.getBool(c, "stattrak")))))))));

        root.then(instanceCommand("removeskin", null, (c, p, inst) -> removeSkin(c.getSource().getSender(), p, inst)));
        root.then(instanceCommand("equip", null, (c, p, inst) -> {
            SkinDefinition def = ctx.catalog().skin(inst.skinId());
            return equip(c.getSource().getSender(), p, inst, def != null && def.isKnife() ? EquipSlot.KNIFE : EquipSlot.BOW);
        }));
        root.then(instanceCommand("equipslot", Commands.argument("slot", StringArgumentType.word())
                .suggests(suggest(() -> List.of("knife", "bow", "crossbow"))), (c, p, inst) -> {
            EquipSlot slot = EquipSlot.byId(StringArgumentType.getString(c, "slot"));
            if (slot == null) {
                ctx.messages(c.getSource().getSender()).send(c.getSource().getSender(), "equip.wrong-slot");
                return 0;
            }
            return equip(c.getSource().getSender(), p, inst, slot);
        }));
        root.then(instanceCommand("setfloat", Commands.argument("value", DoubleArgumentType.doubleArg(0, 1)), (c, p, inst) -> {
            var changed = inst.copyWithStatus(inst.status());
            changed.setFloatValue(DoubleArgumentType.getDouble(c, "value"));
            return saveRoll(c.getSource().getSender(), p, inst, changed, false);
        }));
        root.then(instanceCommand("setpattern", Commands.argument("value", IntegerArgumentType.integer(0, 99999)), (c, p, inst) -> {
            var changed = inst.copyWithStatus(inst.status());
            changed.setPattern(IntegerArgumentType.getInteger(c, "value"));
            return saveRoll(c.getSource().getSender(), p, inst, changed, true);
        }));
        root.then(instanceCommand("setstattrak", Commands.argument("value", BoolArgumentType.bool()), (c, p, inst) -> {
            var changed = inst.copyWithStatus(inst.status());
            changed.setStatTrak(BoolArgumentType.getBool(c, "value"));
            return saveRoll(c.getSource().getSender(), p, inst, changed, false);
        }));

        root.then(Commands.literal("manage").then(Commands.argument("name", StringArgumentType.word())
                .suggests(suggest(() -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()))
                .executes(this::manage)));
        root.then(Commands.literal("list").then(Commands.argument("player", ArgumentTypes.player()).executes(this::list)));
        root.then(Commands.literal("history").then(Commands.argument("player", ArgumentTypes.player()).executes(this::history)));

        root.then(Commands.literal("testcase").then(Commands.argument("case", StringArgumentType.word())
                .suggests(suggest(() -> ctx.catalog().cases().stream().map(CaseDefinition::id).toList()))
                .executes(c -> testCase(c, false))
                .then(Commands.literal("keep").executes(c -> testCase(c, true)))));

        root.then(Commands.literal("odds").then(Commands.argument("case", StringArgumentType.word())
                .suggests(suggest(() -> ctx.catalog().cases().stream().map(CaseDefinition::id).toList()))
                .executes(this::odds)));

        root.then(Commands.literal("preview").then(Commands.argument("skin", StringArgumentType.word()).suggests(skins())
                .executes(c -> preview(c, 0, 0.01))
                .then(Commands.argument("pattern", IntegerArgumentType.integer(0, 99999))
                        .executes(c -> preview(c, IntegerArgumentType.getInteger(c, "pattern"), 0.01))
                        .then(Commands.argument("float", DoubleArgumentType.doubleArg(0, 1))
                                .executes(c -> preview(c, IntegerArgumentType.getInteger(c, "pattern"), DoubleArgumentType.getDouble(c, "float")))))));

        root.then(Commands.literal("pattern").then(Commands.argument("skin", StringArgumentType.word()).suggests(skins())
                .then(Commands.argument("pattern", IntegerArgumentType.integer(0, 99999)).executes(this::patternDebug))));

        root.then(Commands.literal("browser").then(Commands.argument("skin", StringArgumentType.word()).suggests(skins())
                .executes(c -> browser(c, 0))
                .then(Commands.argument("pattern", IntegerArgumentType.integer(0, 99999))
                        .executes(c -> browser(c, IntegerArgumentType.getInteger(c, "pattern"))))));

        root.then(Commands.literal("scan").then(Commands.argument("skin", StringArgumentType.word()).suggests(skins())
                .executes(this::scanMetrics)
                .then(Commands.argument("metric", StringArgumentType.word()).suggests(metrics())
                        .executes(c -> scan(c, 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(c -> scan(c, IntegerArgumentType.getInteger(c, "count")))))));

        root.then(Commands.literal("shop")
                .then(Commands.literal("spawn")
                        .executes(c -> spawnDealer(c, null))
                        .then(Commands.literal("villager").executes(c -> spawnDealer(c, "villager")))
                        .then(Commands.literal("mannequin").executes(c -> spawnDealer(c, "mannequin"))))
                .then(Commands.literal("remove").executes(c -> {
                    if (!(c.getSource().getExecutor() instanceof Player p)) {
                        return playerOnly(c.getSource().getSender());
                    }
                    ctx.messages().send(p, ctx.shop().removeNearest(p) ? "admin.shop-removed" : "admin.shop-none");
                    return Command.SINGLE_SUCCESS;
                })));

        root.then(Commands.literal("reload").executes(c -> {
            ctx.reload(c.getSource().getSender());
            return Command.SINGLE_SUCCESS;
        }));
        root.then(Commands.literal("exportpack").executes(c -> exportPack(c.getSource().getSender())));
        root.then(Commands.literal("info").executes(c -> info(c.getSource().getSender())));

        commands.register((com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack>) CommandFeedback.guardTree(ctx, root.build()), "MCCases administration", List.of("mccases"));
    }

    // ------------------------------------------------------------------ helpers

    @FunctionalInterface
    private interface InstanceAction {
        int run(CommandContext<CommandSourceStack> c, Player player, SkinInstance instance) throws CommandSyntaxException;
    }

    private LiteralArgumentBuilder<CommandSourceStack> instanceCommand(String name,
                                                                      com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> valueArg,
                                                                      InstanceAction action) {
        var idArg = Commands.argument("id", StringArgumentType.word()).suggests(instanceIds());
        com.mojang.brigadier.Command<CommandSourceStack> exec = c -> {
            Player p = player(c);
            PlayerProfile profile = ctx.profiles().get(p);
            if (profile == null) {
                ctx.messages().send(c.getSource().getSender(), "profile.loading");
                return 0;
            }
            SkinInstance inst = profile.find(StringArgumentType.getString(c, "id"));
            if (inst == null) {
                ctx.messages().send(c.getSource().getSender(), "admin.instance-unknown");
                return 0;
            }
            if (ctx.commerce().locked(inst.id())) { ctx.messages().send(c.getSource().getSender(), "commerce.locked"); return 0; }
            return action.run(c, p, inst);
        };
        if (valueArg == null) {
            idArg.executes(exec);
        } else {
            idArg.then(valueArg.executes(exec));
        }
        return Commands.literal(name).then(Commands.argument("player", ArgumentTypes.player()).then(idArg));
    }

    private static Player player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(c.getSource()).getFirst();
    }

    private static SuggestionProvider<CommandSourceStack> suggest(java.util.function.Supplier<List<String>> values) {
        return (c, b) -> {
            String prefix = b.getRemainingLowerCase();
            for (String v : values.get()) {
                if (v.startsWith(prefix)) {
                    b.suggest(v);
                }
            }
            return b.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> skins() {
        return (c, b) -> {
            String prefix = b.getRemainingLowerCase();
            int n = 0;
            for (SkinDefinition s : ctx.catalog().skins()) {
                if (s.id().startsWith(prefix) && n++ < 200) {
                    b.suggest(s.id());
                }
            }
            return b.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> instanceIds() {
        return (c, b) -> {
            try {
                PlayerProfile profile = ctx.profiles().get(player(c));
                if (profile != null) {
                    for (SkinInstance s : profile.owned()) {
                        if (s.shortId().startsWith(b.getRemainingLowerCase())) {
                            b.suggest(s.shortId());
                        }
                    }
                }
            } catch (CommandSyntaxException | RuntimeException ignored) {
                // no player resolved yet
            }
            return b.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> metrics() {
        return (c, b) -> {
            SkinDefinition skin = ctx.catalog().skin(StringArgumentType.getString(c, "skin"));
            if (skin == null) {
                return b.buildFuture();
            }
            return PatternScanner.metrics(ctx.render(), skin).thenApply(list -> {
                for (String m : list) {
                    if (m.startsWith(b.getRemainingLowerCase())) {
                        b.suggest(m);
                    }
                }
                return b.build();
            }).exceptionally(e -> b.build());
        };
    }

    private int ok(CommandSender sender) {
        ctx.messages().send(sender, "admin.done");
        return Command.SINGLE_SUCCESS;
    }

    private int playerOnly(CommandSender sender) {
        ctx.messages().send(sender, "general.player-only");
        return 0;
    }

    private int help(CommandSender sender) {
        for (String line : ctx.messages(sender).rawList("admin.help")) {
            sender.sendMessage(Text.mm(line));
        }
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------ actions

    private int giveCase(CommandContext<CommandSourceStack> c, int amount, boolean test) throws CommandSyntaxException {
        Player p = player(c);
        CaseDefinition def = ctx.catalog().caseDefinition(StringArgumentType.getString(c, "case"));
        if (def == null) {
            ctx.messages().send(c.getSource().getSender(), "admin.unknown", Text.unparsed("what", ctx.messages(c.getSource().getSender()).raw("admin.labels.case")));
            return 0;
        }
        CaseItems.giveOrDrop(p, ctx.caseItems().caseItem(def, amount, test, ctx.messages(p)));
        ctx.messages().send(c.getSource().getSender(), "admin.given", Text.unparsed("amount", amount),
                Text.unparsed("name", def.name()), Text.unparsed("player", p.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int giveKey(CommandContext<CommandSourceStack> c, int amount, boolean test) throws CommandSyntaxException {
        Player p = player(c);
        KeyDefinition def = ctx.catalog().key(StringArgumentType.getString(c, "key"));
        if (def == null) {
            ctx.messages().send(c.getSource().getSender(), "admin.unknown", Text.unparsed("what", ctx.messages(c.getSource().getSender()).raw("admin.labels.key")));
            return 0;
        }
        CaseItems.giveOrDrop(p, ctx.caseItems().keyItem(def, amount, test, ctx.messages(p)));
        ctx.messages().send(c.getSource().getSender(), "admin.given", Text.unparsed("amount", amount),
                Text.unparsed("name", def.name()), Text.unparsed("player", p.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int giveSkin(CommandContext<CommandSourceStack> c, Double fl, Integer pattern, Boolean statTrak) throws CommandSyntaxException {
        Player p = player(c);
        CommandSender sender = c.getSource().getSender();
        SkinDefinition skin = ctx.catalog().skin(StringArgumentType.getString(c, "skin"));
        if (skin == null) {
            ctx.messages().send(sender, "admin.unknown", Text.unparsed("what", ctx.messages(c.getSource().getSender()).raw("admin.labels.skin")));
            return 0;
        }
        if (ctx.profiles().get(p) == null) {
            ctx.messages().send(sender, "profile.loading");
            return 0;
        }
        if (!validProperties(sender, skin, fl, Boolean.TRUE.equals(statTrak))) return 0;
        RewardRoller roller = ctx.openings().roller();
        double floatValue = fl != null ? fl : skin.minFloat() + roller.random().nextDouble() * (skin.maxFloat() - skin.minFloat());
        int seed = pattern != null ? pattern : ctx.catalog().patterns().seedMin()
                + roller.random().nextInt(ctx.catalog().patterns().seedMax() - ctx.catalog().patterns().seedMin() + 1);
        boolean st = statTrak != null ? statTrak && skin.weapon().statTrak() : false;
        long wearSeed = roller.random().nextLong();
        CommandFeedback.complete(ctx, sender, "csadmin giveskin", ctx.render().report(skin, seed), report -> {
                    SkinInstance inst = new SkinInstance(UUID.randomUUID(), p.getUniqueId(), skin.id(), floatValue, seed, wearSeed,
                            st, 0, PatternInfo.of(report), "admin", SkinInstance.Origin.ADMIN, System.currentTimeMillis(), false,
                            SkinInstance.Status.OWNED);
                    CommandFeedback.complete(ctx, sender, "csadmin giveskin", ctx.repository().insert(inst), v -> {
                        ctx.profiles().addLoaded(p.getUniqueId(), inst);
                        ctx.messages().send(sender, "admin.skin-given", Text.component("skin", ctx.formatter(sender).fullName(skin, inst)),
                                Text.unparsed("player", p.getName()), Text.unparsed("id", inst.shortId()));
                    });
                });
        return Command.SINGLE_SUCCESS;
    }

    private int removeSkin(CommandSender sender, Player p, SkinInstance inst) {
        if (!ctx.commerce().reserveMutation(inst.id())) { ctx.messages(sender).send(sender, "commerce.locked"); return 0; }
        PlayerProfile profile = ctx.profiles().get(p);
        ctx.repository().removeOwnedAndUnequip(profile.owner(), inst.id()).whenComplete((count, error) ->
                CommandFeedback.main(ctx, sender, "csadmin removeskin", () -> {
                    ctx.commerce().releaseMutation(inst.id());
                    if (error != null) { CommandFeedback.failure(ctx, sender, "csadmin removeskin", error); return; }
                    if (count != 1) { ctx.messages(sender).send(sender, "command.state-changed"); return; }
                    var slot = profile.slotOf(inst.id());
                    if (slot != null) ctx.knives().unequip(p, slot);
                    inst.setStatus(SkinInstance.Status.REMOVED);
                    profile.remove(inst.id());
                    ok(sender);
                }));
        return Command.SINGLE_SUCCESS;
    }

    /** Writes forced values; a pattern change re-runs the analysis first. */
    private int saveRoll(CommandSender sender, Player p, SkinInstance inst, SkinInstance changed, boolean reanalyse) {
        SkinDefinition skin = ctx.catalog().skin(inst.skinId());
        if (skin == null) {
            ctx.messages(sender).send(sender, "admin.instance-unknown");
            return 0;
        }
        if (!validProperties(sender, skin, changed.floatValue(), changed.statTrak())) return 0;
        if (!ctx.commerce().reserveMutation(inst.id())) { ctx.messages(sender).send(sender, "commerce.locked"); return 0; }
        Runnable store = () -> {
            ctx.repository().updateRoll(changed).whenComplete((count, error) -> CommandFeedback.main(ctx, sender, "csadmin edit", () -> {
                ctx.commerce().releaseMutation(inst.id());
                if (error != null) { CommandFeedback.failure(ctx, sender, "csadmin edit", error); return; }
                if (count != 1) { ctx.messages(sender).send(sender, "command.state-changed"); return; }
                inst.setFloatValue(changed.floatValue()); inst.setPattern(changed.pattern());
                inst.setStatTrak(changed.statTrak()); inst.setPatternInfo(changed.patternInfo());
                if (p.isOnline()) ctx.knives().refreshHeld(p);
                ok(sender);
            }));
        };
        if (!reanalyse) {
            store.run();
            return Command.SINGLE_SUCCESS;
        }
        ctx.render().report(skin, changed.pattern()).whenComplete((r, error) -> CommandFeedback.main(ctx, sender, "csadmin setpattern", () -> {
                    if (error != null) { ctx.commerce().releaseMutation(inst.id()); CommandFeedback.failure(ctx, sender, "csadmin setpattern", error); return; }
                    changed.setPatternInfo(PatternInfo.of(r));
                    store.run();
                }));
        return Command.SINGLE_SUCCESS;
    }

    private boolean validProperties(CommandSender sender, SkinDefinition skin, Double fl, boolean statTrak) {
        if (fl != null && (!Double.isFinite(fl) || fl < skin.minFloat() || fl > skin.maxFloat())) {
            ctx.messages(sender).send(sender, "command.float-range", Text.unparsed("min", skin.minFloat()), Text.unparsed("max", skin.maxFloat())); return false;
        }
        if (statTrak && !skin.weapon().statTrak()) { ctx.messages(sender).send(sender, "command.stattrak-unavailable"); return false; }
        return true;
    }

    private int equip(CommandSender sender, Player player, SkinInstance inst, EquipSlot slot) {
        ctx.knives().equip(player, inst, slot, success -> {
            if (success) ok(sender);
            else if (sender != player) ctx.messages(sender).send(sender, "command.equip-failed");
        });
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Player p = player(c);
        CommandSender sender = c.getSource().getSender();
        PlayerProfile profile = ctx.profiles().get(p);
        if (profile == null) {
            ctx.messages().send(sender, "profile.loading");
            return 0;
        }
        ctx.messages().send(sender, "admin.list-header", Text.unparsed("player", p.getName()), Text.unparsed("count", profile.owned().size()));
        for (SkinInstance s : profile.owned()) {
            SkinDefinition def = ctx.catalog().skin(s.skinId());
            Component name = def == null ? Component.text(s.skinId()) : ctx.formatter(sender).fullName(def, s);
            sender.sendMessage(Text.mm("<dark_gray>[<gray><id></gray>]</dark_gray> ", Text.unparsed("id", s.shortId()))
                    .append(name)
                    .append(Text.mm(" <dark_gray>" + Text.formatFloat(s.floatValue(), 6) + " #" + s.pattern()))
                    .clickEvent(ClickEvent.suggestCommand("/csadmin removeskin " + p.getName() + " " + s.shortId())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int history(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Player p = player(c);
        CommandSender sender = c.getSource().getSender();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());
        ctx.repository().history(p.getUniqueId(), 15).whenComplete((rows, e) -> CommandFeedback.main(ctx, sender, "csadmin", () -> {
            if (e != null) {
                CommandFeedback.failure(ctx, sender, "csadmin history", e);
                return;
            }
            ctx.messages().send(sender, "admin.history-header", Text.unparsed("player", p.getName()));
            for (var r : rows) {
                SkinDefinition def = ctx.catalog().skin(r.skinId());
                sender.sendMessage(Text.mm("<dark_gray>" + f.format(Instant.ofEpochMilli(r.openedAt())) + " <gray>"
                        + Text.escape(r.caseId()) + " <dark_gray>→ <white>" + Text.escape(def == null ? r.skinId() : def.displayName())
                        + " <dark_gray>" + Text.formatFloat(r.floatValue(), 6) + " #" + r.pattern()
                        + (r.statTrak() ? " <#cf6a32>ST" : "") + (r.test() ? " <red>TEST" : "")));
            }
        }));
        return Command.SINGLE_SUCCESS;
    }

    /** Opens a player's skins (online or offline) in the management menu. */
    private int manage(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getExecutor() instanceof Player viewer)) {
            return playerOnly(c.getSource().getSender());
        }
        String name = StringArgumentType.getString(c, "name");
        org.bukkit.OfflinePlayer target = Bukkit.getPlayerExact(name);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(name);
        }
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
            ctx.messages().send(viewer, "view.unknown", Text.unparsed("player", name));
            return 0;
        }
        String shown = target.getName() == null ? name : target.getName();
        ctx.profiles().snapshot(target.getUniqueId()).whenComplete((profile, error) -> CommandFeedback.main(ctx, viewer, "csadmin manage", () -> {
            if (error != null) { CommandFeedback.failure(ctx, viewer, "csadmin manage", error); return; }
            if (profile == null || !viewer.isOnline()) {
                ctx.messages().send(viewer, "view.unknown", Text.unparsed("player", shown));
                return;
            }
            new dev.plattnericus.cases.gui.menu.SkinInventoryMenu(ctx, viewer, profile, shown, false).admin().open();
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int testCase(CommandContext<CommandSourceStack> c, boolean keep) {
        if (!(c.getSource().getExecutor() instanceof Player p)) {
            return playerOnly(c.getSource().getSender());
        }
        CaseDefinition def = ctx.catalog().caseDefinition(StringArgumentType.getString(c, "case"));
        if (def == null) {
            ctx.messages().send(p, "admin.unknown", Text.unparsed("what", ctx.messages(c.getSource().getSender()).raw("admin.labels.case")));
            return 0;
        }
        ctx.openings().open(p, def, true, keep);
        return Command.SINGLE_SUCCESS;
    }

    private int odds(CommandContext<CommandSourceStack> c) {
        CommandSender sender = c.getSource().getSender();
        CaseDefinition def = ctx.catalog().caseDefinition(StringArgumentType.getString(c, "case"));
        if (def == null) {
            ctx.messages().send(sender, "admin.unknown", Text.unparsed("what", ctx.messages(c.getSource().getSender()).raw("admin.labels.case")));
            return 0;
        }
        ctx.messages().send(sender, "admin.odds", Text.unparsed("case", def.name()), Text.unparsed("count", def.size()),
                Text.unparsed("chance", Text.formatFloat(def.statTrakChance() * 100, 1)));
        for (Rarity r : ctx.catalog().raritiesOrdered()) {
            double chance = RewardRoller.chance(def, ctx.catalog(), r);
            if (chance > 0) {
                sender.sendMessage(Text.mm("<c>" + Text.escape(ctx.messages(sender).label("rarity." + r.id(), r.name())) + "</c> <white>" + String.format(Locale.ROOT, "%.3f", chance * 100)
                        + "% <dark_gray>(" + def.skins(r).size() + ")", Text.color("c", r.color())));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int preview(CommandContext<CommandSourceStack> c, int seed, double fl) {
        if (!(c.getSource().getExecutor() instanceof Player p)) {
            return playerOnly(c.getSource().getSender());
        }
        SkinDefinition skin = ctx.catalog().skin(StringArgumentType.getString(c, "skin"));
        if (skin == null) {
            ctx.messages().send(p, "admin.unknown", Text.unparsed("what", ctx.messages(c.getSource().getSender()).raw("admin.labels.skin")));
            return 0;
        }
        ctx.previews().show(p, skin, seed, fl, 0, ctx.formatter(p).name(skin, null), null);
        return Command.SINGLE_SUCCESS;
    }

    private int patternDebug(CommandContext<CommandSourceStack> c) {
        CommandSender sender = c.getSource().getSender();
        SkinDefinition skin = ctx.catalog().skin(StringArgumentType.getString(c, "skin"));
        int seed = IntegerArgumentType.getInteger(c, "pattern");
        if (skin == null) {
            ctx.messages().send(sender, "admin.unknown", Text.unparsed("what", ctx.messages(c.getSource().getSender()).raw("admin.labels.skin")));
            return 0;
        }
        ctx.render().report(skin, seed).whenComplete((r, e) -> CommandFeedback.main(ctx, sender, "csadmin", () -> {
            if (e != null) {
                CommandFeedback.failure(ctx, sender, "csadmin pattern", e);
                return;
            }
            ctx.messages().send(sender, "admin.debug.title", Text.unparsed("skin", skin.displayName()), Text.unparsed("seed", seed));
            if (r.variantName() != null) {
                ctx.messages().send(sender, "admin.debug.variant", Text.unparsed("variant", r.variantName()));
            }
            ctx.messages().send(sender, "admin.debug.classification", Text.unparsed("classification", r.classification() == null ? "-" : r.classification()),
                    Text.unparsed("mode", r.manual() ? ctx.messages(sender).raw("admin.labels.manual") : ctx.messages(sender).raw("admin.labels.automatic")));
            if (r.fadePercent() != null) {
                ctx.messages().send(sender, "admin.debug.fade", Text.unparsed("fade", r.fadePercent()));
            }
            for (Map.Entry<String, Double> m : r.metrics().entrySet()) {
                if (!m.getKey().equals("fade")) {
                    sender.sendMessage(Text.mm("<dark_gray> " + m.getKey() + " <gray>" + Text.formatFloat(m.getValue() * 100, 2) + "%"));
                }
            }
            if (sender instanceof Player p) {
                ctx.previews().show(p, skin, seed, Math.max(skin.minFloat(), Math.min(skin.maxFloat(), 0.01)), 0,
                        Text.item("<white>" + Text.escape(skin.displayName()) + " #" + seed), null);
            }
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int browser(CommandContext<CommandSourceStack> c, int seed) {
        if (!(c.getSource().getExecutor() instanceof Player p)) {
            return playerOnly(c.getSource().getSender());
        }
        SkinDefinition skin = ctx.catalog().skin(StringArgumentType.getString(c, "skin"));
        if (skin == null) {
            ctx.messages().send(p, "admin.unknown", Text.unparsed("what", ctx.messages(c.getSource().getSender()).raw("admin.labels.skin")));
            return 0;
        }
        new PatternBrowserMenu(ctx, p, skin, seed).open();
        return Command.SINGLE_SUCCESS;
    }

    private int scanMetrics(CommandContext<CommandSourceStack> c) {
        CommandSender sender = c.getSource().getSender();
        SkinDefinition skin = ctx.catalog().skin(StringArgumentType.getString(c, "skin"));
        if (skin == null) {
            ctx.messages().send(sender, "admin.unknown", Text.unparsed("what", ctx.messages(c.getSource().getSender()).raw("admin.labels.skin")));
            return 0;
        }
        CommandFeedback.complete(ctx, sender, "csadmin scan", PatternScanner.metrics(ctx.render(), skin), list ->
                ctx.messages().send(sender, "admin.debug.metrics", Text.unparsed("metrics", list.isEmpty()
                        ? ctx.messages(sender).raw("admin.labels.none") : String.join(", ", list))));
        return Command.SINGLE_SUCCESS;
    }

    private int scan(CommandContext<CommandSourceStack> c, int count) {
        CommandSender sender = c.getSource().getSender();
        SkinDefinition skin = ctx.catalog().skin(StringArgumentType.getString(c, "skin"));
        String metric = StringArgumentType.getString(c, "metric");
        if (skin == null) {
            ctx.messages().send(sender, "admin.unknown", Text.unparsed("what", ctx.messages(c.getSource().getSender()).raw("admin.labels.skin")));
            return 0;
        }
        ctx.messages().send(sender, "admin.scan-start", Text.unparsed("skin", skin.displayName()), Text.unparsed("metric", metric));
        PatternScanner.scan(ctx.render(), skin, ctx.catalog().patterns().seedMin(), ctx.catalog().patterns().seedMax(), metric, count)
                .whenComplete((hits, e) -> CommandFeedback.main(ctx, sender, "csadmin", () -> {
                    if (e != null || hits == null) {
                        CommandFeedback.failure(ctx, sender, "csadmin scan", e != null ? e : new IllegalStateException("Missing scan result"));
                        return;
                    }
                    if (hits.isEmpty()) {
                        ctx.messages().send(sender, "admin.debug.no-metric", Text.unparsed("metric", metric));
                        return;
                    }
                    int rank = 1;
                    for (PatternScanner.Hit h : hits) {
                        String value = metric.equals("fade") ? Text.formatFloat(h.value(), 1) + "%" : Text.formatFloat(h.value() * 100, 1) + "%";
                        sender.sendMessage(Text.mm("<dark_gray>" + rank++ + ". <white>#" + h.seed() + " <gray>" + value
                                        + (h.report().classification() != null ? " <yellow>" + Text.escape(h.report().classification()) : ""))
                                .clickEvent(ClickEvent.runCommand("/csadmin pattern " + skin.id() + " " + h.seed())));
                    }
                }));
        return Command.SINGLE_SUCCESS;
    }

    private int spawnDealer(CommandContext<CommandSourceStack> c, String type) {
        // players spawn the dealer where they stand, the console at the main world spawn
        org.bukkit.Location at = c.getSource().getExecutor() instanceof Player p ? p.getLocation()
                : Bukkit.getWorlds().getFirst().getSpawnLocation();
        org.bukkit.entity.Entity npc;
        if (type == null) {
            npc = ctx.shop().spawnNpc(at);
        } else if (type.equals("mannequin")) {
            npc = ctx.shop().spawnMannequin(at);
        } else {
            npc = ctx.shop().spawnVillager(at);
        }
        ctx.npcAnimator().track(npc);
        ctx.messages().send(c.getSource().getSender(), "admin.shop-spawned");
        return Command.SINGLE_SUCCESS;
    }

    private int exportPack(CommandSender sender) {
        File target = ctx.packDistribution().file();
        ctx.messages().send(sender, "admin.export-start");
        ctx.render().submit(() -> {
            try {
                return dev.plattnericus.cases.pack.PackExporter.export(ctx.catalog(), ctx.render().engine().renderer(),
                        ctx.settings().resourcePack().namespace(), "Plattnericus", target);
            } catch (java.io.IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }).whenComplete((result, e) -> CommandFeedback.main(ctx, sender, "csadmin", () -> {
            if (e != null) {
                ctx.plugin().getLogger().log(java.util.logging.Level.WARNING, "Pack export failed", e);
                ctx.messages().send(sender, "admin.debug.export-failed", Text.unparsed("error", e.getMessage()));
                return;
            }
            if (!result.failures().isEmpty()) {
                ctx.messages(sender).send(sender, "command.pack-incomplete", Text.unparsed("count", result.failures().size()));
                result.failures().forEach(f -> ctx.plugin().getLogger().warning("Pack export skipped " + f));
                return;
            }
            if (ctx.packDistribution().running()) {
                try {
                    ctx.packDistribution().reloadFile();
                    ctx.packDistribution().sendAll();
                } catch (java.io.IOException io) {
                    CommandFeedback.failure(ctx, sender, "csadmin exportpack", io); return;
                }
            }
            ctx.messages().send(sender, "admin.export-done", Text.unparsed("skins", result.skins()), Text.unparsed("file", target.getPath()));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandSender sender) {
        sender.sendMessage(Text.mm("<gold>MCCases <gray>" + ctx.plugin().getPluginMeta().getVersion() + " <dark_gray>by Plattnericus (plattnericus.dev)"));
        ctx.messages().send(sender, "admin.info.catalog", Text.unparsed("skins", ctx.catalog().skins().size()), Text.unparsed("cases", ctx.catalog().cases().size()), Text.unparsed("weapons", ctx.catalog().weapons().size()));
        ctx.messages().send(sender, "admin.info.cache", Text.unparsed("previews", ctx.render().cachedPreviews()), Text.unparsed("pack", ctx.settings().resourcePack().enabled()));
        ctx.messages().send(sender, "admin.info.web", Text.unparsed("url", ctx.packDistribution().running() ? ctx.packDistribution().url() : ctx.messages(sender).raw("admin.labels.off")));
        return Command.SINGLE_SUCCESS;
    }
}
