package dev.plattnericus.cases.commerce;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** One-shot private text input; asynchronous chat never touches inventories or live GUI state. */
public final class ChatInput implements Listener {
    private record Pending(Player player, Consumer<String> accepted, Runnable cancelled, BukkitTask timeout) { }
    private final ConcurrentHashMap<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final CasesContext ctx;
    public ChatInput(CasesContext ctx) { this.ctx = ctx; }
    public void ask(Player p, String prompt, Consumer<String> accepted, Runnable cancelled) {
        Pending old = pending.remove(p.getUniqueId()); if (old != null) old.timeout().cancel();
        p.closeInventory(); ctx.messages(p).send(p, prompt);
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> {
            Pending expired = pending.remove(p.getUniqueId());
            if (expired != null && p.isOnline()) { ctx.messages(p).send(p, "input.timeout"); expired.cancelled().run(); }
        }, 600);
        pending.put(p.getUniqueId(), new Pending(p, accepted, cancelled, timeout));
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Pending input = pending.remove(event.getPlayer().getUniqueId()); if (input == null) return;
        event.setCancelled(true); String value = Text.plain(event.message()).strip();
        if (!ctx.plugin().isEnabled()) return;
        Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            input.timeout().cancel(); Player p = input.player();
            if (!p.isOnline() || Bukkit.getPlayer(p.getUniqueId()) != p) return;
            if (value.equalsIgnoreCase("cancel") || value.equalsIgnoreCase("abbrechen")) input.cancelled().run();
            else input.accepted().accept(value.length() > 100 ? value.substring(0, 100) : value);
        });
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { Pending input = pending.remove(event.getPlayer().getUniqueId()); if (input != null) input.timeout().cancel(); }
    public void shutdown() { pending.values().forEach(p -> p.timeout().cancel()); pending.clear(); }
}
