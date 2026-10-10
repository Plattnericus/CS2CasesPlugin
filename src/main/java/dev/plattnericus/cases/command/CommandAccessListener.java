package dev.plattnericus.cases.command;

import dev.plattnericus.cases.core.CasesContext;
import java.util.Locale;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/** Give denied players feedback before Paper's hidden Brigadier root rejects the command. */
public final class CommandAccessListener implements Listener {
    private final CasesContext ctx;
    public CommandAccessListener(CasesContext ctx) { this.ctx = ctx; }

    public static String permission(String message) {
        String root = message.stripLeading();
        if (!root.startsWith("/")) return null;
        root = root.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (root.startsWith("mccases:")) root = root.substring("mccases:".length());
        return switch (root) {
            case "csadmin", "mccases" -> "mccases.admin";
            case "skins", "inventory", "knife", "knives", "cases", "openings" -> "mccases.use";
            case "inspect" -> "mccases.inspect";
            case "tradein", "tradeup" -> "mccases.tradein";
            case "trade", "skintrade" -> "mccases.trade";
            case "market", "marketplace", "skinmarket" -> "mccases.market";
            default -> null;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) {
        String permission = permission(event.getMessage());
        if (permission == null || event.getPlayer().hasPermission(permission)) return;
        event.setCancelled(true);
        ctx.messages(event.getPlayer()).send(event.getPlayer(), "general.no-permission");
    }
}
