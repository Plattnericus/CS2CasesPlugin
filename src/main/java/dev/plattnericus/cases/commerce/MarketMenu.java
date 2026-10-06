package dev.plattnericus.cases.commerce;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.storage.CommerceRepository.Listing;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

public final class MarketMenu extends Menu {
    private boolean own;
    private int page, sort, category;
    private String query = "";
    public MarketMenu(CasesContext ctx, Player viewer) { super(ctx, viewer); }
    public MarketMenu own() { own = true; return this; }
    public MarketMenu search(String text) { query = text.toLowerCase(Locale.ROOT); return this; }
    @Override public void open() {
        ctx.gallery().close(viewer); super.open();
        ctx.commerce().refreshBalance(viewer, () -> { if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) render(); });
    }
    @Override protected int rows() { return 6; }
    @Override protected Component title() { return ctx.messages(viewer).get(own ? "market.own-title" : "market.title"); }
    @Override protected void build() {
        var service = ctx.commerce();
        set(1, GuiItems.glowing(GuiItems.icon(ctx.messages(viewer), Material.CHEST, "market.browse"), !own), c -> { own = false; page = 0; refreshLanguage(); });
        set(3, GuiItems.glowing(GuiItems.icon(ctx.messages(viewer), Material.ENDER_CHEST, "market.own"), own), c -> { own = true; page = 0; refreshLanguage(); });
        set(5, GuiItems.icon(ctx.messages(viewer), Material.EMERALD, "market.sell"), c -> new SkinPickerMenu(ctx, viewer).open());
        Long balance = service.cachedBalance(viewer.getUniqueId());
        set(7, GuiItems.icon(ctx.messages(viewer), Material.GOLD_INGOT, "market.wallet", Text.unparsed("balance", balance == null ? "…" : balance), Text.unparsed("currency", service.currency())), c -> service.refreshBalance(viewer, this::render));
        Comparator<Listing> comparator = switch (sort) {
            case 1 -> Comparator.comparingLong(Listing::price); case 2 -> Comparator.comparingLong(Listing::price).reversed();
            case 3 -> Comparator.comparingDouble(l -> l.skin().floatValue()); default -> Comparator.comparingLong(Listing::createdAt).reversed();
        };
        var offers = service.listings().stream().filter(l -> !own || l.skin().owner().equals(viewer.getUniqueId()))
                .filter(l -> {
                    var def = ctx.catalog().skin(l.skin().skinId());
                    return def != null && (category == 0 || (category == 1) == def.isKnife())
                            && (query.isBlank() || (def.id() + " " + Text.plain(ctx.formatter(viewer).fullName(def, l.skin())) + " " + l.sellerName()).toLowerCase(Locale.ROOT).contains(query));
                }).sorted(comparator.thenComparing(l -> l.id().toString())).toList();
        int pages = GuiItems.pages(offers.size(), GuiItems.CONTENT.length); page = Math.min(page, pages - 1);
        if (!service.available()) set(31, GuiItems.icon(ctx.messages(viewer), Material.CLOCK, "gui.loading"));
        else if (offers.isEmpty()) set(31, GuiItems.icon(ctx.messages(viewer), Material.LIGHT_GRAY_DYE, own ? "market.own-empty" : "market.empty"));
        for (int i = 0; i < GuiItems.CONTENT.length && page * GuiItems.CONTENT.length + i < offers.size(); i++) {
            Listing listing = offers.get(page * GuiItems.CONTENT.length + i);
            set(GuiItems.CONTENT[i], listingIcon(ctx, viewer, listing, true), c -> new ListingMenu(ctx, viewer, listing).open());
        }
        set(45, GuiItems.icon(ctx.messages(viewer), Material.CHEST_MINECART, "gui.cases.to-skins"), c -> ctx.gallery().open(viewer, null));
        set(47, GuiItems.icon(ctx.messages(viewer), Material.HOPPER, "market.sort", Text.unparsed("sort", ctx.messages(viewer).raw("market.sorts." + sort))), c -> { sort = Math.floorMod(sort + (c.isRightClick() ? -1 : 1), 4); page = 0; render(); });
        set(48, GuiItems.previous(ctx.messages(viewer), page, pages), c -> { if (page > 0) { page--; render(); } });
        set(49, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
        set(50, GuiItems.next(ctx.messages(viewer), page, pages), c -> { if (page + 1 < pages) { page++; render(); } });
        set(51, GuiItems.icon(ctx.messages(viewer), Material.SPYGLASS, "market.filter", Text.unparsed("category", ctx.messages(viewer).raw("market.categories." + category))), c -> { category = (category + 1) % 3; page = 0; render(); });
        set(53, GuiItems.icon(ctx.messages(viewer), Material.COMPASS, "market.search", Text.unparsed("query", query)), c -> { query = ""; page = 0; render(); });
    }
    public static org.bukkit.inventory.ItemStack listingIcon(CasesContext ctx, Player viewer, Listing listing, boolean hint) {
        var def = ctx.catalog().skin(listing.skin().skinId());
        if (def == null) return GuiItems.icon(ctx.messages(viewer), Material.BARRIER, "gui.inspect.missing");
        var lore = new ArrayList<>(ctx.formatter(viewer).lore(def, listing.skin(), true));
        lore.addAll(ctx.messages(viewer).itemList("market.offer", Text.unparsed("price", listing.price()), Text.unparsed("currency", ctx.commerce().currency()), Text.unparsed("seller", listing.sellerName())));
        if (hint) lore.add(ctx.messages(viewer).item(listing.skin().owner().equals(viewer.getUniqueId()) ? "market.manage-hint" : "market.buy-hint"));
        return SkinIcons.icon(def, ctx.formatter(viewer).fullName(def, listing.skin()), lore, ctx.settings(), false);
    }
}
