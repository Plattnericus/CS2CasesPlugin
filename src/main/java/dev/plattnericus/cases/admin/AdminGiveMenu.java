package dev.plattnericus.cases.admin;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.Rarity;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import dev.plattnericus.cases.items.SkinIcons;
import dev.plattnericus.cases.profile.PlayerProfile;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Give a skin to a player: pick a case, then a skin. Left click rolls float and pattern like a case
 * drop, right click gives a StatTrak copy.
 */
public final class AdminGiveMenu extends Menu {

    private final PlayerProfile owner;
    private final String ownerName;
    private final CaseDefinition selected;
    private final Runnable back;
    private int page;

    public AdminGiveMenu(CasesContext ctx, Player viewer, PlayerProfile owner, String ownerName, CaseDefinition selected,
                         Runnable back) {
        super(ctx, viewer);
        this.owner = owner;
        this.ownerName = ownerName;
        this.selected = selected;
        this.back = back;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected Component title() {
        return ctx.messages(viewer).get("admin.manage.give-title", Text.unparsed("player", ownerName));
    }

    @Override
    protected void build() {
        List<Object> entries = new ArrayList<>();
        if (selected == null) {
            entries.addAll(ctx.catalog().cases());
        } else {
            List<Rarity> rarities = new ArrayList<>(ctx.catalog().raritiesOrdered());
            Collections.reverse(rarities);
            for (Rarity r : rarities) {
                entries.addAll(selected.skins(r));
            }
        }
        int perPage = GuiItems.CONTENT.length;
        int pages = GuiItems.pages(entries.size(), perPage);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= entries.size()) {
                break;
            }
            Object e = entries.get(idx);
            if (e instanceof CaseDefinition c) {
                set(GuiItems.CONTENT[i], ctx.caseItems().caseIcon(c, ctx.messages(viewer)), click -> {
                    playClick();
                    new AdminGiveMenu(ctx, viewer, owner, ownerName, c, back).open();
                });
            } else if (e instanceof SkinDefinition skin) {
                List<Component> lore = new ArrayList<>(ctx.formatter(viewer).previewLore(skin));
                lore.addAll(ctx.messages(viewer).itemList("admin.manage.give-hint"));
                set(GuiItems.CONTENT[i], SkinIcons.icon(skin, ctx.formatter(viewer).name(skin, null), lore, ctx.settings(), false),
                        click -> give(skin, click));
            }
        }
        set(GuiItems.SLOT_BACK, GuiItems.back(ctx.messages(viewer)), c -> {
            playClick();
            if (selected == null) {
                back.run();
            } else {
                new AdminGiveMenu(ctx, viewer, owner, ownerName, null, back).open();
            }
        });
        set(GuiItems.SLOT_PREV, GuiItems.previous(ctx.messages(viewer), page, pages), c -> {
            if (page > 0) {
                page--;
                playClick();
                render();
            }
        });
        set(GuiItems.SLOT_NEXT, GuiItems.next(ctx.messages(viewer), page, pages), c -> {
            if (page + 1 < pages) {
                page++;
                playClick();
                render();
            }
        });
        set(GuiItems.SLOT_CENTER, GuiItems.close(ctx.messages(viewer)), c -> viewer.closeInventory());
    }

    private void give(SkinDefinition skin, ClickType click) {
        playClick();
        new AdminActions(ctx).giveSkin(owner.owner(), skin, null, null, click.isRightClick(), inst -> {
            if (inst == null) {
                ctx.messages(viewer).send(viewer, "opening.storage-error");
                return;
            }
            // offline owners are shown from a snapshot that the live service does not update
            if (ctx.profiles().get(owner.owner()) != owner) {
                owner.put(inst);
            }
            ctx.messages(viewer).send(viewer, "admin.skin-given", Text.component("skin", ctx.formatter(viewer).fullName(skin, inst)),
                    Text.unparsed("player", ownerName), Text.unparsed("id", inst.shortId()));
        });
    }
}
