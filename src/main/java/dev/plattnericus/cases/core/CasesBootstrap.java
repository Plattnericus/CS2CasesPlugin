package dev.plattnericus.cases.core;

import dev.plattnericus.cases.bootstrap.DefaultFiles;
import dev.plattnericus.cases.command.AdminCommand;
import dev.plattnericus.cases.command.PlayerCommands;
import dev.plattnericus.cases.gui.MenuListener;
import dev.plattnericus.cases.inspect.InspectService;
import dev.plattnericus.cases.items.CaseItems;
import dev.plattnericus.cases.items.ItemProtectionListener;
import dev.plattnericus.cases.items.ItemSigner;
import dev.plattnericus.cases.knife.KnifeCosmetics;
import dev.plattnericus.cases.knife.KnifeService;
import dev.plattnericus.cases.knife.StatTrakListener;
import dev.plattnericus.cases.map.MapPreviewService;
import dev.plattnericus.cases.map.MapViewPool;
import dev.plattnericus.cases.opening.OpeningService;
import dev.plattnericus.cases.profile.PendingJournal;
import dev.plattnericus.cases.profile.ProfileService;
import dev.plattnericus.cases.render.RenderService;
import dev.plattnericus.cases.reward.RewardRoller;
import dev.plattnericus.cases.shop.ShopListener;
import dev.plattnericus.cases.shop.ShopService;
import dev.plattnericus.cases.slot.ReservedSlotService;
import dev.plattnericus.cases.storage.Database;
import dev.plattnericus.cases.storage.SkinRepository;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;

/** Builds and tears down the service graph. */
public final class CasesBootstrap {

    private final JavaPlugin plugin;
    private CasesRuntime runtime;
    private Database database;
    private RenderService render;
    private OpeningService openings;
    private InspectService inspect;
    private MapPreviewService previews;
    private dev.plattnericus.cases.shop.ShopNpcAnimator npcAnimator;
    private dev.plattnericus.cases.pack.PackDistribution packDistribution;

    public CasesBootstrap(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enable() {
        long started = System.currentTimeMillis();
        try {
            int written = DefaultFiles.extractMissing(plugin);
            if (written > 0) {
                plugin.getLogger().info("Installed " + written + " default file(s).");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not install default files", e);
            return false;
        }
        runtime = new CasesRuntime(plugin);
        CasesRuntime.State state = runtime.loadState(w -> plugin.getLogger().warning(w));

        database = new Database(state.settings().storage(), plugin.getDataFolder(), plugin.getLogger());
        try {
            database.open();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database unavailable - MCCases stays disabled", e);
            return false;
        }
        SkinRepository repository = new SkinRepository(database);
        ItemSigner signer;
        try {
            signer = new ItemSigner(plugin.getDataFolder());
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not read or create secret.key", e);
            return false;
        }

        render = new RenderService(state.settings().rendering().threads(), state.settings().rendering().previewCache(),
                state.settings().rendering().reportCache(), state.settings().rendering().floatBucket(), runtime.engine(state));
        CaseItems caseItems = new CaseItems(runtime.keys(), signer, runtime::settings, runtime::messages);
        PendingJournal journal = new PendingJournal(runtime.keys());
        ProfileService profiles = new ProfileService(plugin, repository, journal, runtime::messages, runtime::formatter,
                id -> runtime.catalog().skin(id));
        MapViewPool pool = new MapViewPool(plugin.getDataFolder(), plugin.getLogger(), runtime.mapPalette().table());
        pool.load();
        previews = new MapPreviewService(plugin, pool, () -> render, runtime::settings, runtime::messages, runtime::sounds);
        openings = new OpeningService(runtime, journal, new RewardRoller());
        KnifeService knives = new KnifeService(runtime, new KnifeCosmetics(runtime.keys()));
        inspect = new InspectService(runtime, runtime::inspectModels);
        ShopService shop = new ShopService(runtime);
        ReservedSlotService reservedSlot = new ReservedSlotService(runtime);
        runtime.install(state, render, caseItems, profiles, openings, knives, inspect, previews, shop, repository, reservedSlot);
        var commerce = new dev.plattnericus.cases.commerce.CommerceService(runtime,
                new dev.plattnericus.cases.storage.CommerceRepository(database));
        runtime.setCommerce(commerce);
        commerce.load(); commerce.start(); profiles.lockCheck(commerce::locked);
        profiles.beforeLoad(commerce.payments()::recover);
        runtime.setTradeIns(new dev.plattnericus.cases.tradein.TradeInService(runtime, new dev.plattnericus.cases.storage.ContractRepository(database)));
        shop.load(new File(plugin.getDataFolder(), "shop.yml"), w -> plugin.getLogger().warning(w));
        profiles.onLoad(knives::refreshHeld);

        npcAnimator = new dev.plattnericus.cases.shop.ShopNpcAnimator(runtime);
        packDistribution = new dev.plattnericus.cases.pack.PackDistribution(plugin, runtime::settings);
        try {
            packDistribution.extract();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not extract the bundled resource pack", e);
        }
        packDistribution.apply();
        runtime.setExtras(npcAnimator, packDistribution);

        register(new MenuListener(), profiles, previews, openings, knives, new StatTrakListener(runtime), inspect, commerce, commerce.input(), runtime.tradeIns(),
                new ShopListener(runtime), reservedSlot, new ItemProtectionListener(caseItems), new CaseItemListener(runtime),
                npcAnimator, packDistribution, runtime.gallery(),
                new dev.plattnericus.cases.config.ClientLanguageListener(runtime, reservedSlot::ensure));
        npcAnimator.start();

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            PlayerCommands.register(event.registrar(), runtime);
            new AdminCommand(runtime).register(event.registrar());
        });

        // players already online (plugin reloaded by a plugin manager)
        for (Player p : Bukkit.getOnlinePlayers()) {
            profiles.load(p);
            reservedSlot.ensure(p);
        }
        plugin.getLogger().info("Enabled with " + state.catalog().skins().size() + " skins in " + state.catalog().cases().size()
                + " cases (" + (System.currentTimeMillis() - started) + " ms). Made by Plattnericus - plattnericus.dev");
        return true;
    }

    private void register(Listener... listeners) {
        for (Listener l : listeners) {
            Bukkit.getPluginManager().registerEvents(l, plugin);
        }
    }

    public void disable() {
        if (npcAnimator != null) {
            npcAnimator.stop();
        }
        if (packDistribution != null) {
            packDistribution.stop();
        }
        if (runtime != null) {
            if (runtime.tradeIns() != null) runtime.tradeIns().shutdown();
            if (runtime.commerce() != null) runtime.commerce().shutdown();
            runtime.gallery().shutdown();
        }
        if (openings != null) {
            openings.shutdown();
        }
        if (inspect != null) {
            inspect.shutdown();
        }
        if (previews != null) {
            previews.shutdown();
        }
        HandlerList.unregisterAll(plugin);
        Bukkit.getScheduler().cancelTasks(plugin);
        if (render != null) {
            render.close();
        }
        if (database != null) {
            database.close();
        }
    }
}
