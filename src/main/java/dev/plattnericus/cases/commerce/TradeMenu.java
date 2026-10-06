package dev.plattnericus.cases.commerce;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Own collection on the left, the partner's live offer on the right. */
public final class TradeMenu extends Menu {
    private static final int[] OWN = {9,10,11,12,18,19,20,21,27,28,29,30};
    private static final int[] OTHER = {14,15,16,17,23,24,25,26,32,33,34,35};
    private final TradeSession trade;
    private int page, displayedSeconds;

    public TradeMenu(CasesContext ctx, Player viewer, TradeSession trade) { super(ctx, viewer); this.trade = trade; }
    public boolean belongsTo(TradeSession session) { return trade == session; }
    public void refreshCountdown(long now) {
        if (!trade.committing() && displayedSeconds != trade.reviewSeconds(now)) render();
    }
    @Override protected int rows() { return 6; }
    @Override protected boolean canClick(int slot) {
        if (ctx.commerce().trade(viewer.getUniqueId()) != trade || trade.committing()) return false;
        ctx.commerce().touch(trade); return true;
    }
    @Override protected Component title() { return ctx.messages(viewer).get("trade.title", Text.unparsed("player", otherName())); }
    private String otherName() {
        Player other = Bukkit.getPlayer(trade.other(viewer.getUniqueId())); return other == null ? "?" : other.getName();
    }
    @Override protected void build() {
        var profile = ctx.profiles().get(viewer);
        if (profile == null || ctx.commerce().trade(viewer.getUniqueId()) != trade) return;
        UUID own = viewer.getUniqueId(), other = trade.other(own);
        Player partner = Bukkit.getPlayer(other);
        if (partner == null) return;
        int ownCount = trade.items(own).size(), otherCount = trade.items(other).size();
        displayedSeconds = trade.reviewSeconds(System.currentTimeMillis());
        for (int slot = 0; slot < 54; slot++) {
            Material colour = slot % 9 < 4 ? Material.BLUE_STAINED_GLASS_PANE
                    : slot % 9 > 4 ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            set(slot, GuiItems.filler(colour));
        }
        set(2, GuiItems.glowing(GuiItems.playerHead(ctx.messages(viewer), viewer,
                trade.confirmed(own) ? "trade.your-offer-head" : "trade.your-head",
                Text.unparsed("player", viewer.getName()), Text.unparsed("count", ownCount), Text.unparsed("max", TradeSession.MAX_ITEMS)), trade.confirmed(own)));
        set(6, GuiItems.glowing(GuiItems.playerHead(ctx.messages(viewer), partner, "trade.partner-head",
                Text.unparsed("player", partner.getName()), Text.unparsed("count", otherCount)), trade.confirmed(other)));

        if (trade.confirmed(own) || trade.committing()) {
            drawOffer(own, OWN, true);
            if (ownCount == 0) set(20, GuiItems.icon(ctx.messages(viewer), Material.BLUE_STAINED_GLASS_PANE, "trade.own-empty"));
        } else drawCollection(profile.owned());
        drawOffer(other, OTHER, false);
        if (otherCount == 0) set(24, GuiItems.icon(ctx.messages(viewer), Material.LIME_STAINED_GLASS_PANE, "trade.other-empty", Text.unparsed("player", partner.getName())));
        set(42, GuiItems.icon(ctx.messages(viewer), trade.confirmed(other) ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                trade.confirmed(other) ? "trade.partner-accepted" : "trade.partner-waiting", Text.unparsed("player", partner.getName())));
        drawAccept(own, ownCount, otherCount);
        set(49, GuiItems.icon(ctx.messages(viewer), Material.BARRIER, "trade.cancel"), c -> ctx.commerce().cancel(viewer));
    }
    private void drawCollection(List<SkinInstance> owned) {
        List<UUID> selected = trade.items(viewer.getUniqueId());
        List<SkinInstance> skins = owned.stream().filter(s -> ctx.catalog().skin(s.skinId()) != null
                && s.origin() != SkinInstance.Origin.TEST && (!ctx.commerce().locked(s.id()) || selected.contains(s.id())))
                .sorted(java.util.Comparator.comparing(SkinInstance::createdAt).reversed().thenComparing(SkinInstance::id)).toList();
        int pages = GuiItems.pages(skins.size(), OWN.length); page = Math.min(page, pages - 1);
        if (skins.isEmpty()) set(20, GuiItems.icon(ctx.messages(viewer), Material.BLUE_STAINED_GLASS_PANE, "trade.empty"));
        for (int i = 0; i < OWN.length && page * OWN.length + i < skins.size(); i++) {
            SkinInstance skin = skins.get(page * OWN.length + i);
            boolean offered = selected.contains(skin.id());
            set(OWN[i], icon(skin, offered ? "trade.selected" : "trade.add", offered), c -> ctx.commerce().toggle(viewer, skin.id()));
        }
        if (pages > 1) {
            set(46, GuiItems.icon(ctx.messages(viewer), Material.PAPER, "trade.page", Text.unparsed("page", page + 1), Text.unparsed("pages", pages)));
            if (page > 0) set(45, GuiItems.previous(ctx.messages(viewer), page, pages), c -> { page--; render(); });
            if (page + 1 < pages) set(48, GuiItems.next(ctx.messages(viewer), page, pages), c -> { page++; render(); });
        }
    }
    private void drawAccept(UUID own, int ownCount, int otherCount) {
        if (trade.committing()) { set(38, GuiItems.icon(ctx.messages(viewer), Material.CLOCK, "trade.saving")); return; }
        int revision = trade.revision();
        if (trade.confirmed(own)) {
            set(38, GuiItems.glowing(GuiItems.icon(ctx.messages(viewer), Material.LIME_CONCRETE, "trade.accepted"), true),
                    c -> ctx.commerce().unconfirm(viewer, revision)); return;
        }
        if (trade.empty()) { set(38, GuiItems.icon(ctx.messages(viewer), Material.GRAY_CONCRETE, "trade.no-offer")); return; }
        if (displayedSeconds > 0) {
            set(38, GuiItems.icon(ctx.messages(viewer), Material.CLOCK, "trade.review-wait", Text.unparsed("seconds", displayedSeconds))); return;
        }
        String key = ownCount > 0 && otherCount == 0 ? "trade.accept-gift" : "trade.accept-offer";
        set(38, GuiItems.icon(ctx.messages(viewer), Material.LIME_CONCRETE, key,
                Text.unparsed("own", ownCount), Text.unparsed("other", otherCount)), c -> ctx.commerce().confirm(viewer, revision));
    }
    private void drawOffer(UUID owner, int[] slots, boolean own) {
        var profile = ctx.profiles().get(owner); if (profile == null) return;
        List<UUID> items = trade.items(owner);
        for (int i = 0; i < items.size(); i++) {
            SkinInstance skin = profile.get(items.get(i)); if (skin == null) continue;
            if (own) set(slots[i], icon(skin, "trade.remove", true), c -> ctx.commerce().toggle(viewer, skin.id()));
            else set(slots[i], icon(skin, "trade.offered", false));
        }
    }
    private ItemStack icon(SkinInstance skin, String hint, boolean selected) {
        var def = ctx.catalog().skin(skin.skinId());
        if (def == null) return GuiItems.icon(ctx.messages(viewer), Material.BARRIER, "gui.inspect.missing");
        var lore = new ArrayList<>(ctx.formatter(viewer).lore(def, skin, true)); lore.add(ctx.messages(viewer).item(hint));
        Component name = ctx.formatter(viewer).fullName(def, skin);
        if (selected) name = Component.text("✓ ", net.kyori.adventure.text.format.NamedTextColor.GREEN).append(name);
        return SkinIcons.tradeIcon(def, name, lore, ctx.settings(), selected);
    }
    @Override protected void onClose() { ctx.commerce().cancel(trade, "trade.cancelled"); }
}
