package dev.plattnericus.cases.gui;

import dev.plattnericus.cases.core.CasesContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Explicit choices with a visible current selection and a safe return path. */
public final class ChoiceMenu<T> extends Menu {
    private final String titleKey;
    private final List<T> choices;
    private final T selected;
    private final Function<T, String> label;
    private final Consumer<T> choose;
    private final Runnable back, abandon;
    private boolean navigating;
    private Object navigationOwner;
    private Function<T, Material> icon = value -> Material.PAPER;

    public ChoiceMenu(CasesContext ctx, Player viewer, String titleKey, List<T> choices, T selected,
                      Function<T, String> label, Consumer<T> choose, Runnable back, Runnable abandon) {
        super(ctx, viewer);
        if (choices.isEmpty() || choices.size() > 36) throw new IllegalArgumentException("1–36 choices required");
        this.titleKey = titleKey; this.choices = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(choices));
        this.selected = selected; this.label = label; this.choose = choose; this.back = back; this.abandon = abandon;
    }
    @Override protected int rows() { return choices.size() <= 9 ? 3 : 6; }
    public ChoiceMenu<T> relatedTo(Object owner) { navigationOwner = owner; return this; }
    public ChoiceMenu<T> icons(Function<T, Material> materials) { icon = materials; return this; }
    public boolean belongsTo(Object owner) { return navigationOwner != null && navigationOwner == owner; }
    @Override protected Component title() { return ctx.messages(viewer).get(titleKey).colorIfAbsent(NamedTextColor.DARK_GRAY); }
    @Override protected void build() {
        int first = rows() == 3 ? 9 + (9 - choices.size()) / 2 : 9;
        for (int i = 0; i < choices.size(); i++) {
            T value = choices.get(i); boolean active = java.util.Objects.equals(value, selected);
            var name = Component.text((active ? "✓ " : "") + label.apply(value), active ? NamedTextColor.GREEN : NamedTextColor.WHITE);
            set(first + i, GuiItems.glowing(GuiItems.icon(active ? Material.LIME_DYE : icon.apply(value), name,
                    ctx.messages(viewer).itemList(active ? "menus.selected" : "menus.choose")), active), click -> {
                navigating = true; playClick(); choose.accept(value);
            });
        }
        set(rows() * 9 - 5, GuiItems.back(ctx.messages(viewer)), click -> { navigating = true; back.run(); });
        set(rows() * 9 - 1, GuiItems.close(ctx.messages(viewer)), click -> viewer.closeInventory());
    }
    @Override protected void onClose() { if (!navigating) abandon.run(); }
}
