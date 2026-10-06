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
    @Override protected int rows() { return 3; }
    @Override protected Component title() { return ctx.messages(viewer).get("market.purchase-title"); }
    @Override protected void build() {
        set(13, MarketMenu.listingIcon(ctx, viewer, listing, false));
        boolean own = listing.skin().owner().equals(viewer.getUniqueId());
        set(11, GuiItems.icon(ctx.messages(viewer), own ? Material.ORANGE_CONCRETE : Material.LIME_CONCRETE, own ? "market.withdraw" : "market.buy",
                Text.unparsed("price", listing.price()), Text.unparsed("currency", ctx.commerce().currency())), c -> {
            if (pending) return; pending = true;
            if (own) ctx.commerce().cancelListing(viewer, listing); else ctx.commerce().buy(viewer, listing);
            if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) viewer.closeInventory();
        });
        set(15, GuiItems.back(ctx.messages(viewer)), c -> new MarketMenu(ctx, viewer).open());
    }
}
