package dev.plattnericus.cases.gui.menu;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.GuiItems;
import dev.plattnericus.cases.gui.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Yes/no question before anything destructive. */
public final class ConfirmMenu extends Menu {

    private final ItemStack subject;
    private final Runnable yes;
    private final Runnable no;

    public ConfirmMenu(CasesContext ctx, Player viewer, ItemStack subject, Runnable yes, Runnable no) {
        super(ctx, viewer);
        this.subject = subject;
        this.yes = yes;
        this.no = no;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected Component title() {
        return ctx.messages(viewer).get("gui.confirm.title");
    }

    @Override
    protected void build() {
        set(13, subject);
        set(11, GuiItems.icon(ctx.messages(viewer), Material.LIME_CONCRETE, "gui.confirm.yes"), c -> {
            ctx.sounds().play(viewer, "gui.click");
            yes.run();
        });
        set(15, GuiItems.icon(ctx.messages(viewer), Material.RED_CONCRETE, "gui.confirm.no"), c -> {
            playClick();
            no.run();
        });
    }
}
