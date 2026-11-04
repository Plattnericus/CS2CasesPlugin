package dev.plattnericus.cases.command;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/** /casesplugin [help] - lists the commands the sender may use. */
public final class HelpCommand {

    private record Entry(String permission, String usage, String key) { }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("mccases.use", "/skins", "skins"),
            new Entry("mccases.use", "/knife", "knife"),
            new Entry("mccases.use", "/cases [open <case> <amount> | cancel]", "cases"),
            new Entry("mccases.use", "/openings", "openings"),
            new Entry("mccases.tradein", "/tradein", "tradein"),
            new Entry("mccases.inspect", "/inspect [hand | view]", "inspect"),
            new Entry("mccases.trade", "/trade <player>", "trade"),
            new Entry("mccases.market", "/market", "market"),
            new Entry("mccases.admin", "/csadmin", "admin"),
            new Entry(null, "/casesplugin", "help"));

    private HelpCommand() { }

    public static void register(Commands commands, CasesContext ctx) {
        commands.register("casesplugin", "Lists all MCCases commands", List.of(), CommandFeedback.guard(ctx, "casesplugin", new BasicCommand() {
            @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
                return args.length <= 1 ? List.of("help") : List.of();
            }

            @Override public void execute(CommandSourceStack source, String[] args) {
                if (args.length > 1 || args.length == 1 && !args[0].equalsIgnoreCase("help")) {
                    CommandFeedback.usage(ctx, source.getSender(), "/casesplugin [help]");
                    return;
                }
                send(ctx, source.getSender());
            }
        }));
    }

    private static void send(CasesContext ctx, CommandSender to) {
        var messages = ctx.messages(to);
        messages.send(to, "help.header");
        for (Entry entry : ENTRIES) {
            if (entry.permission() == null || to.hasPermission(entry.permission())) {
                messages.send(to, "help.entry", Text.unparsed("usage", entry.usage()),
                        Text.unparsed("description", messages.raw("help.commands." + entry.key())));
            }
        }
    }
}
