package dev.plattnericus.cases.shop;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.gui.menu.CasePreviewMenu;
import dev.plattnericus.cases.items.CaseItems;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Dealer menu. Every offer shows its price and whether the player can afford it right now, so
 * the outcome of a click is visible before clicking.
 */
public final class ShopMenu extends Menu {

    private int page;

    public ShopMenu(CasesContext ctx, Player viewer) {
        super(ctx, viewer);
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected Component title() {
        return ctx.messages(viewer).get("shop.title");
    }

    @Override
    protected void build() {
        if (viewer.hasPermission("mccases.market")) set(46, GuiItems.icon(ctx.messages(viewer), Material.EMERALD, "market.browse"), c -> new dev.plattnericus.cases.commerce.MarketMenu(ctx, viewer).open());
        if (viewer.hasPermission("mccases.trade")) set(52, GuiItems.icon(ctx.messages(viewer), Material.WRITABLE_BOOK, "trade.choose-partner"), c -> new dev.plattnericus.cases.commerce.TradePlayersMenu(ctx, viewer).open());
        ShopService shop = ctx.shop();
        int balance = shop.balance(viewer);
        Component currencyName = Text.materialName(shop.currency());
        set(4, GuiItems.icon(shop.currency(), ctx.messages(viewer).item("shop.balance.name",
                        Text.unparsed("balance", balance), Text.component("currency", currencyName)),
                ctx.messages(viewer).itemList("shop.balance.lore")));
        List<ShopService.Offer> offers = shop.offers();
        int perPage = GuiItems.CONTENT.length;
        int pages = GuiItems.pages(offers.size(), perPage);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= offers.size()) {
                break;
            }
            ShopService.Offer offer = offers.get(idx);
            ItemStack icon = icon(offer, balance, currencyName);
            if (icon == null) {
                continue;
            }
            set(GuiItems.CONTENT[i], icon, click -> onClick(offer, click));
        }
        set(GuiItems.SLOT_PREV, GuiItems.previous(ctx.messages(viewer), page, pages), c -> {
            if (page > 0) {
                page--;
                playClick();
                render();
            }
        });
        set(GuiItems.SLOT_CENTER, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
        set(GuiItems.SLOT_BACK, GuiItems.icon(ctx.messages(viewer), Material.ENDER_CHEST, "gui.cases.to-skins"), c -> {
            playClick();
            ctx.gallery().open(viewer, null);
        });
        set(GuiItems.SLOT_EXTRA, GuiItems.icon(ctx.messages(viewer), Material.CHEST_MINECART, "gui.skins.to-cases"), c -> {
            playClick();
            new dev.plattnericus.cases.gui.menu.CasesMenu(ctx, viewer).open();
        });
        set(GuiItems.SLOT_NEXT, GuiItems.next(ctx.messages(viewer), page, pages), c -> {
            if (page + 1 < pages) {
                page++;
                playClick();
                render();
            }
        });
    }

    private ItemStack icon(ShopService.Offer offer, int balance, Component currencyName) {
        ItemStack icon;
        if (offer.type().equals(CaseItems.TYPE_KEY)) {
            var key = ctx.catalog().key(offer.id());
            if (key == null) {
                return null;
            }
            icon = ctx.caseItems().keyIcon(key, ctx.messages(viewer));
        } else {
            CaseDefinition def = ctx.catalog().caseDefinition(offer.id());
            if (def == null) {
                return null;
            }
            icon = ctx.caseItems().caseIcon(def, ctx.messages(viewer));
        }
        boolean affordable = balance >= offer.price();
        boolean affordableFive = balance >= (long) offer.price() * 5;
        icon.editMeta(meta -> {
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.addAll(ctx.messages(viewer).itemList("shop.offer",
                    Text.unparsed("price", offer.price()), Text.unparsed("price5", (long) offer.price() * 5),
                    Text.component("currency", currencyName)));
            lore.add(ctx.messages(viewer).item(affordable ? "shop.affordable" : "shop.not-affordable",
                    Text.unparsed("missing", Math.max(0, offer.price() - balance)), Text.component("currency", currencyName)));
            if (affordableFive) {
                lore.add(ctx.messages(viewer).item("shop.affordable-five"));
            }
            lore.addAll(ctx.messages(viewer).itemList(offer.type().equals(CaseItems.TYPE_CASE) ? "shop.hint-case" : "shop.hint-key"));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(affordable ? Boolean.TRUE : null);
        });
        return icon;
    }

    private void onClick(ShopService.Offer offer, ClickType click) {
        if (click.isRightClick() && offer.type().equals(CaseItems.TYPE_CASE)) {
            CaseDefinition def = ctx.catalog().caseDefinition(offer.id());
            if (def != null) {
                playClick();
                new CasePreviewMenu(ctx, viewer, def, () -> new ShopMenu(ctx, viewer).open()).open();
            }
            return;
        }
        int amount = click.isShiftClick() ? 5 : 1;
        ShopService.Result result = ctx.shop().buy(viewer, offer, amount);
        switch (result) {
            case OK -> {
                ctx.sounds().play(viewer, "shop.buy");
                ctx.messages(viewer).send(viewer, "shop.bought", Text.unparsed("amount", amount),
                        Text.unparsed("name", offerName(offer)), Text.unparsed("price", (long) offer.price() * amount),
                        Text.component("currency", Text.materialName(ctx.shop().currency())));
            }
            case NOT_ENOUGH -> {
                ctx.sounds().play(viewer, "shop.deny");
                ctx.messages(viewer).send(viewer, "shop.not-enough");
            }
            case INVENTORY_FULL -> {
                ctx.sounds().play(viewer, "shop.deny");
                ctx.messages(viewer).send(viewer, "shop.inventory-full");
            }
            case UNAVAILABLE -> ctx.sounds().play(viewer, "shop.deny");
        }
        render();
    }

    private String offerName(ShopService.Offer offer) {
        if (offer.type().equals(CaseItems.TYPE_KEY)) {
            var key = ctx.catalog().key(offer.id());
            return key == null ? offer.id() : ctx.messages(viewer).label("catalog.key." + key.id(), key.name());
        }
        CaseDefinition def = ctx.catalog().caseDefinition(offer.id());
        return def == null ? offer.id() : def.name();
    }
}
