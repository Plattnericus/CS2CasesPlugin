package dev.plattnericus.cases.tools;

import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.core.CasesRuntime;
import dev.plattnericus.cases.skin.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.lang.reflect.Field;
import java.util.*;

/** Frozen real inspect frames for the two-client screenshot audit; never included in MCCases.jar. */
public final class VisualRuntimeChecks {
    private VisualRuntimeChecks() { }
    @SuppressWarnings("unchecked")
    public static void frame(CommandSender sender, Player player, CasesContext ctx, String[] args) throws Exception {
        Player observer = Bukkit.getPlayerExact(args[2]); if (observer == null) throw new IllegalArgumentException("observer offline");
        if (args[3].equals("stop")) { ctx.inspect().stop(player); sender.sendMessage("VISUAL STOP"); return; }
        var weapon = ctx.catalog().weapon(args[3]); if (weapon == null) throw new IllegalArgumentException("unknown weapon");
        boolean play = args[5].equals("play");
        int variant = Integer.parseInt(args[4]), tick = play ? 0 : Integer.parseInt(args[5]), angle = Integer.parseInt(args[7]);
        boolean hand = args[6].equals("hand");
        ctx.gallery().close(player); player.closeInventory(); observer.closeInventory(); ctx.inspect().stop(player);
        World world = player.getWorld(); world.setTime(6000); world.setStorm(false);
        for (Player p : List.of(player, observer)) { p.setGameMode(GameMode.CREATIVE); p.setAllowFlight(true); p.setFlying(true); }
        player.teleport(new Location(world, .5, 100, .5, 0, 0));
        double[][] positions = {{.5, 100, 3.5}, {-2.5, 100, 1.5}, {3.5, 100, 1.5}, {.5, 100, -2.5}};
        double[] position = positions[Math.floorMod(angle, positions.length)];
        double dx = .16-position[0], dy = 100.82-(position[1]+observer.getEyeHeight()), dz = .76-position[2];
        observer.teleport(new Location(world, position[0], position[1], position[2], (float)Math.toDegrees(Math.atan2(-dx,dz)), (float)Math.toDegrees(Math.atan2(-dy,Math.hypot(dx,dz)))));
        field(ctx.inspect(), "cooldowns", Map.class).clear();
        var models = ((CasesRuntime)ctx).inspectModels();
        Map<String,List<String>> pools = field(models, "animationPools", Map.class);
        List<String> original = pools.get(weapon.id()); String animation = original.get(variant);
        var def = ctx.catalog().skins().stream().filter(s -> s.weapon().id().equals(weapon.id()) && s.finish().id().contains("doppler")).findFirst()
                .orElseGet(() -> ctx.catalog().skins().stream().filter(s -> s.weapon().id().equals(weapon.id())).sorted(Comparator.comparing(dev.plattnericus.cases.catalog.SkinDefinition::id)).findFirst().orElseThrow());
        var instance = new SkinInstance(UUID.randomUUID(), player.getUniqueId(), def.id(), .02, 0, 0, false, 0, PatternInfo.NONE, null, SkinInstance.Origin.TEST, 0, false, SkinInstance.Status.OWNED);
        pools.put(weapon.id(), List.of(animation));
        try { if (!ctx.inspect().start(player, instance, false, hand)) throw new IllegalStateException("inspect start failed"); }
        finally { pools.put(weapon.id(), original); }
        Map<UUID,Object> sessions = field(ctx.inspect(), "sessions", Map.class); Object session = sessions.get(player.getUniqueId());
        if (!play) field(session, "task", BukkitTask.class).cancel();
        var apply = ctx.inspect().getClass().getDeclaredMethod("apply",session.getClass(),int.class,int.class); apply.setAccessible(true);
        apply.invoke(ctx.inspect(), session, tick, 0);
        var hide = ctx.inspect().getClass().getDeclaredMethod("setHandVisible", Player.class,boolean.class,boolean.class); hide.setAccessible(true); hide.invoke(null,player,false,false);
        for (var entity : player.getWorld().getEntities()) if (entity instanceof org.bukkit.entity.Display && entity.getPersistentDataContainer().has(ctx.keys().displayEntity)) {
            if (player.canSee(entity) && observer.canSee(entity)) throw new IllegalStateException("owner scene leaked to observer");
        }
        sender.sendMessage("VISUAL READY " + weapon.id() + " " + animation + " tick=" + tick + " hand=" + hand + " left=" + player.getMainHand() + " angle=" + angle);
    }
    private static <T> T field(Object instance, String name, Class<T> type) throws Exception {
        Field field = instance.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(instance));
    }
}
