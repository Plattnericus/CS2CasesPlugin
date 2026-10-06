package dev.plattnericus.cases.commerce;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class TradePlayersMenu extends Menu {
    private int page;
    public TradePlayersMenu(CasesContext ctx, Player viewer) { super(ctx, viewer); }
    @Override protected int rows() { return 6; }
    @Override protected Component title() { return ctx.messages(viewer).get("trade.players-title"); }
    @Override protected void build() {
        var players = Bukkit.getOnlinePlayers().stream().filter(p -> !p.equals(viewer) && p.hasPermission("mccases.trade") && viewer.canSee(p))
                .sorted(java.util.Comparator.comparing(Player::getName)).toList();
        int pages = GuiItems.pages(players.size(), GuiItems.CONTENT.length); page = Math.min(page, pages - 1);
        if (players.isEmpty()) set(31, GuiItems.icon(ctx.messages(viewer), Material.LIGHT_GRAY_DYE, "trade.no-players"));
        for (int i = 0; i < GuiItems.CONTENT.length && page * GuiItems.CONTENT.length + i < players.size(); i++) {
            Player target = players.get(page * GuiItems.CONTENT.length + i);
            set(GuiItems.CONTENT[i], GuiItems.playerHead(ctx.messages(viewer), target, "trade.player", Text.unparsed("player", target.getName())), c -> {
                ctx.commerce().request(viewer, target); viewer.closeInventory();
            });
        }
        set(45, GuiItems.back(ctx.messages(viewer)), c -> ctx.gallery().open(viewer, null));
        set(48, GuiItems.previous(ctx.messages(viewer), page, pages), c -> { if (page > 0) { page--; render(); } });
        set(50, GuiItems.next(ctx.messages(viewer), page, pages), c -> { if (page + 1 < pages) { page++; render(); } });
        set(49, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
    }
}
