package dev.plattnericus.cases.gui;

import dev.plattnericus.cases.core.CasesContext;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class of every menu. Items in a menu are pure icons: all clicks are cancelled by
 * {@link MenuListener} before a button handler runs, so nothing can be taken out or put in.
 */
public abstract class Menu implements InventoryHolder {

    protected final CasesContext ctx;
    protected final Player viewer;
    private final Map<Integer, Consumer<ClickType>> handlers = new HashMap<>();
    private Inventory inventory;

    protected Menu(CasesContext ctx, Player viewer) {
        this.ctx = ctx;
        this.viewer = viewer;
    }

    protected abstract int rows();

    protected abstract Component title();

    protected abstract void build();

    public void initializeHidden() {
        inventory = Bukkit.createInventory(this, rows() * 9, title());
        render();
    }

    public void open() {
        ctx.menuStates().get(viewer.getUniqueId()).inventoryRequestVersion++;
        inventory = Bukkit.createInventory(this, rows() * 9, title());
        render();
        viewer.openInventory(inventory);
    }

    /** Rebuilds all icons in place (no reopen, so the cursor and title stay). */
    public void render() {
        handlers.clear();
        inventory.clear();
        build();
        ItemStack filler = GuiItems.filler(ctx.settings().opening().filler());
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack current = inventory.getItem(i);
            if (current == null || current.isEmpty()) {
                inventory.setItem(i, filler);
            }
        }
    }

    @SuppressWarnings("deprecation") // Paper exposes title updates only through this legacy method.
    public void refreshLanguage() {
        onLanguageChange();
        render();
        viewer.getOpenInventory().setTitle(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(title()));
    }

    protected void onLanguageChange() {
    }

    protected void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    protected void set(int slot, ItemStack item, Consumer<ClickType> handler) {
        inventory.setItem(slot, item);
        handlers.put(slot, handler);
    }

    void click(int slot, ClickType type) {
        if (!canClick(slot)) return;
        Consumer<ClickType> handler = handlers.get(slot);
        if (handler != null) {
            handler.accept(type);
        }
    }

    protected boolean canClick(int slot) { return true; }

    /** Called after the inventory was closed for any reason. */
    protected void onClose() {
    }

    void closed() {
        onClose();
    }

    public Player viewer() {
        return viewer;
    }

    protected void playClick() {
        ctx.sounds().play(viewer, "gui.click");
    }

    protected void playError() {
        ctx.sounds().play(viewer, "gui.error");
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
