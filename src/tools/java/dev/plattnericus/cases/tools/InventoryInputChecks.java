package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.gui.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Invoke production input listeners with controlled views; no client or server emulation. */
public final class InventoryInputChecks {
    private InventoryInputChecks() { }
    @SuppressWarnings("unchecked")
    public static void run() throws Exception {
        UUID owner = UUID.randomUUID(); var holder = new AtomicReference<InventoryHolder>();
        Inventory top = (Inventory) Proxy.newProxyInstance(InventoryInputChecks.class.getClassLoader(), new Class<?>[]{Inventory.class},
                (proxy, method, args) -> method.getName().equals("getHolder") ? holder.get() : null);
        InventoryView view = (InventoryView) Proxy.newProxyInstance(InventoryInputChecks.class.getClassLoader(), new Class<?>[]{InventoryView.class},
                (proxy, method, args) -> method.getName().equals("getTopInventory") ? top : null);
        Player player = (Player) Proxy.newProxyInstance(InventoryInputChecks.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> owner;
                    case "getOpenInventory" -> view;
                    default -> throw new UnsupportedOperationException("unexpected player call " + method.getName());
                });
        holder.set(new Menu(null, player) {
            protected int rows() { return 6; }
            protected net.kyori.adventure.text.Component title() { return net.kyori.adventure.text.Component.empty(); }
            protected void build() { }
        });
        var menus = new MenuListener();
        for (int slot = 0; slot < 9; slot++) {
            var event = new PlayerItemHeldEvent(player, 4, slot); menus.onHeld(event);
            require(event.isCancelled(), "plugin inventory accepted a wheel/number-key input " + slot);
        }
        holder.set(null); var released = new PlayerItemHeldEvent(player, 4, 5); menus.onHeld(released);
        require(!released.isCancelled(), "closed menu kept equipment locked");
        System.out.println("PASS: production chest menus protect all nine held-slot requests and release them on close (controlled Paper event/view proxies); live gallery checks cover normal hotbar selection.");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
