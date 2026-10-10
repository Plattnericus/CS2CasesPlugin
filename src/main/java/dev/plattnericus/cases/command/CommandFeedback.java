package dev.plattnericus.cases.command;

import com.mojang.brigadier.tree.CommandNode;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Bukkit;

/** Report command failures to the invoking audience, with diagnostic details confined to the log. */
public final class CommandFeedback {
    private CommandFeedback() { }

    public static void usage(CasesContext ctx, CommandSender sender, String usage) {
        ctx.messages(sender).send(sender, "command.usage", Text.unparsed("usage", usage));
    }

    public static void failure(CasesContext ctx, CommandSender sender, String command, Throwable error) {
        String reference = UUID.randomUUID().toString().substring(0, 8);
        ctx.plugin().getLogger().log(Level.SEVERE, "Command /" + command + " failed [" + reference + "]", error);
        ctx.messages(sender).send(sender, "command.failed", Text.unparsed("command", command), Text.unparsed("reference", reference));
    }

    public static void main(CasesContext ctx, CommandSender sender, String name, Runnable work) {
        if (!ctx.plugin().isEnabled()) return;
        Runnable guarded = () -> {
            try { work.run(); }
            catch (RuntimeException error) { failure(ctx, sender, name, error); }
        };
        if (Bukkit.isPrimaryThread()) guarded.run();
        else Bukkit.getScheduler().runTask(ctx.plugin(), guarded);
    }

    public static <T> void complete(CasesContext ctx, CommandSender sender, String name, CompletableFuture<T> future, Consumer<T> success) {
        future.whenComplete((value, error) -> main(ctx, sender, name, () -> {
            if (error != null) failure(ctx, sender, name, error);
            else success.accept(value);
        }));
    }

    public static BasicCommand guard(CasesContext ctx, String name, BasicCommand delegate) {
        return new BasicCommand() {
            @Override public String permission() { return delegate.permission(); }
            @Override public boolean canUse(CommandSender sender) { return delegate.canUse(sender); }
            @Override public void execute(CommandSourceStack source, String[] args) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
                if (!canUse(source.getSender())) {
                    ctx.messages(source.getSender()).send(source.getSender(), "general.no-permission");
                    return;
                }
                try { delegate.execute(source, args); }
                catch (RuntimeException error) { failure(ctx, source.getSender(), name, error); }
            }
            @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
                return delegate.suggest(source, args);
            }
        };
    }

    public static CommandNode<CommandSourceStack> guardTree(CasesContext ctx, CommandNode<CommandSourceStack> node) {
        var builder = node.createBuilder();
        if (node.getCommand() != null) builder.executes(c -> {
            try { return node.getCommand().run(c); }
            catch (RuntimeException error) {
                failure(ctx, c.getSource().getSender(), "csadmin", error);
                return 0;
            }
        });
        for (var child : node.getChildren()) builder.then(guardTree(ctx, child));
        return builder.build();
    }
}
