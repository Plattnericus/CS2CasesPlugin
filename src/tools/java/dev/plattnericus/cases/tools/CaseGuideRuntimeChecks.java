package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.gui.menu.CasesMenu;
import dev.plattnericus.cases.gui.GuiItems;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import java.util.*;

/** Real Paper inventories and click handlers: ranking, filters, navigation and icon protection. */
public final class CaseGuideRuntimeChecks {
    private CaseGuideRuntimeChecks() { }
    public static void run(CommandSender sender, Player player, CasesContext ctx) throws Exception {
        ctx.inspect().stop(player); ctx.gallery().close(player); new CasesMenu(ctx, player).open();
        var menu = player.getOpenInventory().getTopInventory().getHolder(false);
        var original = ids(player); require(original.size() == ctx.catalog().cases().stream().filter(dev.plattnericus.cases.catalog.CaseDefinition::enabled).count(), "case coverage");
        double previous = Double.POSITIVE_INFINITY;
        for (String id : original) {
            var m = ctx.caseGuide().measure(ctx.catalog().caseDefinition(id), ctx.catalog(), ctx.shop().offers());
            require(m.value() <= previous + 1e-12, "value order"); previous = m.value();
        }
        // MenuListener deliberately ignores a second logical click in the same tick.
        // Exercise each action on a separate tick, as a real client would.
        List<Runnable> steps = new ArrayList<>();
        steps.add(() -> { click(player, 0); long price = -1;
        for (String id : ids(player)) {
            var m = ctx.caseGuide().measure(ctx.catalog().caseDefinition(id),ctx.catalog(),ctx.shop().offers());
            if(m.cost()!=null){ require(m.cost()>=price,"price order");price=m.cost(); }
        } });
        steps.add(() -> { click(player, 2);
        for (String id : ids(player)) require(ctx.caseGuide().measure(ctx.catalog().caseDefinition(id),ctx.catalog(),ctx.shop().offers()).favoriteChance()>0,"favorite filter");
        });
        steps.add(() -> { click(player, 2);
        for (String id : ids(player)) require(ctx.caseGuide().measure(ctx.catalog().caseDefinition(id),ctx.catalog(),ctx.shop().offers()).knives().containsKey("bayonet"),"specific knife filter");
        });
        List<String> beforePreview = new ArrayList<>();
        steps.add(() -> { beforePreview.addAll(ids(player)); require(!beforePreview.isEmpty(),"knife fixture missing"); click(player,9); });
        steps.add(() -> { click(player,45); require(player.getOpenInventory().getTopInventory().getHolder(false)==menu && ids(player).equals(beforePreview),"preview lost filters"); });
        steps.add(() -> { field(menu,"budget",0L);((CasesMenu)menu).render();require(ids(player).isEmpty(),"zero budget"); });
        steps.add(() -> { click(player,52);require(ids(player).equals(original),"reset filters"); });
        steps.add(() -> { field(menu,"search","operation_breakout_case");((CasesMenu)menu).render();require(ids(player).equals(List.of("operation_breakout_case")),"case ID search"); });
        steps.add(() -> { click(player,52);
        var click=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,9,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,0);
        Bukkit.getPluginManager().callEvent(click);require(click.isCancelled(),"case icon hotbar mutation");player.closeInventory();new CasesMenu(ctx,player).open();
        sender.sendMessage("PASS CASE GUIDE: all cases, correct value/full-price order, favorites and individual knife filters, preview/back state, zero budget, exact ID search, reset and hotbar protection.");
        });
        next(steps.iterator(), ctx);
    }
    private static void next(Iterator<Runnable> steps, CasesContext ctx) {
        if (!steps.hasNext()) return;
        Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> {
            try { steps.next().run(); next(steps,ctx); }
            catch (RuntimeException failure) { ctx.plugin().getLogger().log(java.util.logging.Level.SEVERE,"Case guide runtime check failed",failure); }
        },2);
    }
    private static void field(Object menu,String name,Object value) {
        try { var field=menu.getClass().getDeclaredField(name);field.setAccessible(true);field.set(menu,value); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    public static void click(Player p,int slot) { Bukkit.getPluginManager().callEvent(new InventoryClickEvent(p.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL)); }
    private static List<String> ids(Player p) {
        List<String> out=new ArrayList<>();
        for(int slot:GuiItems.CONTENT){var item=p.getOpenInventory().getTopInventory().getItem(slot);if(item==null)continue;var model=item.getItemMeta().getItemModel();if(model!=null && model.getKey().startsWith("case/"))out.add(model.getKey().substring(5));}
        return out;
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
