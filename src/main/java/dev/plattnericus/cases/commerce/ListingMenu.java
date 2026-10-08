package dev.plattnericus.cases.commerce;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.storage.CommerceRepository.Listing;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class ListingMenu extends Menu {
    private final Listing listing;
    private boolean pending;
    public ListingMenu(CasesContext ctx, Player viewer, Listing listing) { super(ctx, viewer); this.listing = listing; }
    @Override protected int rows() { return 6; }
    @Override protected Component title() { return ctx.messages(viewer).get("market.purchase-title"); }
    @Override protected void build() {
        set(22, MarketMenu.listingIcon(ctx, viewer, listing, false));
        if (!ctx.commerce().available(listing)) {
            set(38, GuiItems.icon(ctx.messages(viewer), Material.BARRIER, "market.sold-marker"));
            set(45, GuiItems.back(ctx.messages(viewer)), c -> new MarketMenu(ctx, viewer).open()); return;
        }
        set(42, GuiItems.icon(ctx.messages(viewer), Material.SPYGLASS, "market.inspect"), c -> {
            var def = ctx.catalog().skin(listing.skin().skinId());
            if (def == null) return;
            viewer.closeInventory();
            ctx.previews().show(viewer, def, listing.skin().pattern(), listing.skin().floatValue(), listing.skin().wearSeed(),
                    ctx.formatter(viewer).fullName(def, listing.skin()), this::open);
        });
        boolean own = listing.skin().owner().equals(viewer.getUniqueId());
        set(38, GuiItems.icon(ctx.messages(viewer), own ? Material.ORANGE_CONCRETE : Material.LIME_CONCRETE, own ? "market.withdraw" : "market.buy",
                Text.unparsed("price", listing.price()), Text.unparsed("currency", ctx.commerce().currency())), c -> {
            if (pending) return; pending = true;
            if (own) ctx.commerce().cancelListing(viewer, listing); else ctx.commerce().buy(viewer, listing);
            if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) viewer.closeInventory();
        });
        set(45, GuiItems.back(ctx.messages(viewer)), c -> new MarketMenu(ctx, viewer).open());
    }
}
