package dev.plattnericus.cases.core;

import dev.plattnericus.cases.bootstrap.DefaultFiles;
import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.catalog.CatalogLoader;
import dev.plattnericus.cases.config.Messages;
import dev.plattnericus.cases.config.PluginSettings;
import dev.plattnericus.cases.config.SoundBank;
import dev.plattnericus.cases.gui.MenuStates;
import dev.plattnericus.cases.inspect.InspectModels;
import dev.plattnericus.cases.inspect.InspectService;
import dev.plattnericus.cases.items.CaseItems;
import dev.plattnericus.cases.items.PluginKeys;
import dev.plattnericus.cases.items.SkinFormatter;
import dev.plattnericus.cases.knife.KnifeService;
import dev.plattnericus.cases.map.MapPaletteSource;
import dev.plattnericus.cases.map.MapPreviewService;
import dev.plattnericus.cases.opening.OpeningService;
import dev.plattnericus.cases.profile.ProfileService;
import dev.plattnericus.cases.render.MapDither;
import dev.plattnericus.cases.render.RenderService;
import dev.plattnericus.cases.render.SkinRenderer;
import dev.plattnericus.cases.render.TextureStore;
import dev.plattnericus.cases.shop.ShopService;
import dev.plattnericus.cases.slot.ReservedSlotService;
import dev.plattnericus.cases.storage.SkinRepository;
import dev.plattnericus.cases.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Holds the running services and the reloadable state (settings, texts, sounds, catalog, inspect
 * models). Reloads build the new state off-thread and swap it in on the server thread in one step.
 */
public final class CasesRuntime implements CasesContext {

    /** Everything a reload replaces, swapped as one unit. */
    record State(PluginSettings settings, Messages messages, SoundBank sounds, Catalog catalog,
                 SkinFormatter formatter, InspectModels inspectModels, TextureStore textures) {
    }

    private final JavaPlugin plugin;
    private final PluginKeys keys;
    private final MenuStates menuStates = new MenuStates();
    private final dev.plattnericus.cases.gallery.SkinGallery gallery = new dev.plattnericus.cases.gallery.SkinGallery(this);
    private final AtomicLong catalogVersion = new AtomicLong();
    private final AtomicBoolean reloading = new AtomicBoolean();
    private final MapPaletteSource.Palette mapPalette = MapPaletteSource.load();
    private volatile State state;
    private RenderService render;
    private CaseItems caseItems;
    private ProfileService profiles;
    private OpeningService openings;
    private KnifeService knives;
    private InspectService inspect;
    private MapPreviewService previews;
    private ShopService shop;
    private SkinRepository repository;
    private dev.plattnericus.cases.commerce.CommerceService commerce;
    private ReservedSlotService reservedSlot;
    private dev.plattnericus.cases.tradein.TradeInService tradeIns;
    public void setTradeIns(dev.plattnericus.cases.tradein.TradeInService service) { tradeIns = service; }
    @Override public dev.plattnericus.cases.tradein.TradeInService tradeIns() { return tradeIns; }
    private dev.plattnericus.cases.shop.ShopNpcAnimator npcAnimator;
    private dev.plattnericus.cases.pack.PackDistribution packDistribution;

    public CasesRuntime(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keys = new PluginKeys(plugin);
    }

    // ------------------------------------------------------------------ loading

    /** Loads every reloadable file; safe to call off the server thread. */
    State loadState(Consumer<String> warn) {
        File folder = plugin.getDataFolder();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(folder, "config.yml"));
        var bundled = DefaultFiles.open(plugin, "config.yml");
        if (bundled != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }
        PluginSettings settings = PluginSettings.load(config, warn);
        Messages messages = Messages.load(plugin, config, warn);
        SoundBank sounds = new SoundBank(new File(folder, "sounds.yml"), warn);
        TextureStore textures = new TextureStore(new File(folder, "textures"));
        CatalogLoader.Result result = new CatalogLoader(new File(folder, "catalog"), textures).load(catalogVersion.incrementAndGet());
        result.problems().forEach(p -> warn.accept(p));
        if (settings.debug()) {
            result.notes().forEach(n -> plugin.getLogger().info("[debug] " + n));
        }
        InspectModels inspectModels = new InspectModels(new File(folder, "inspect.yml"), warn);
        SkinFormatter formatter = new SkinFormatter(messages, result.catalog(), settings.display().floatDecimals(),
                settings.display().inspectDecimals(), settings.display().dateFormat());
        return new State(settings, messages, sounds, result.catalog(), formatter, inspectModels, textures);
    }

    RenderService.Engine engine(State s) {
        MapDither dither = mapPalette.dither(s.settings().rendering().ditherStrength());
        return new RenderService.Engine(new SkinRenderer(s.textures(), s.settings().rendering().wear()), s.catalog().patterns(),
                s.settings().rendering().map(), dither::dither);
    }

    void install(State s, RenderService render, CaseItems caseItems, ProfileService profiles, OpeningService openings,
                 KnifeService knives, InspectService inspect, MapPreviewService previews, ShopService shop,
                 SkinRepository repository, ReservedSlotService reservedSlot) {
        this.state = s;
        this.render = render;
        this.caseItems = caseItems;
        this.profiles = profiles;
        this.openings = openings;
        this.knives = knives;
        this.inspect = inspect;
        this.previews = previews;
        this.shop = shop;
        this.repository = repository;
        this.reservedSlot = reservedSlot;
    }

    /** Admin reload: everything except storage settings, without blocking the server thread. */
    public void reload(CommandSender sender) {
        if (!reloading.compareAndSet(false, true)) {
            messages().send(sender, "admin.reload-busy");
            return;
        }
        messages().send(sender, "admin.reload-start");
        long started = System.currentTimeMillis();
        List<String> warnings = java.util.Collections.synchronizedList(new ArrayList<>());
        CompletableFuture.supplyAsync(() -> loadState(warnings::add)).whenComplete((s, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    reloading.set(false);
                    if (error != null) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Reload failed; previous configuration kept", error);
                        messages().send(sender, "admin.reload-failed");
                        return;
                    }
                    this.state = s;
                    render.reconfigure(engine(s));
                    shop.load(new File(plugin.getDataFolder(), "shop.yml"), warnings::add);
                    commerce.load();
                    tradeIns.load();
                    npcAnimator.refreshAll();
                    packDistribution.apply();
                    reservedSlot.ensureAll();
                    for (var p : Bukkit.getOnlinePlayers()) {
                        knives.refreshHeld(p);
                        caseItems.refreshModels(p);
                        caseItems.refreshLanguage(p, catalog());
                    }
                    for (String w : warnings) {
                        plugin.getLogger().warning(w);
                    }
                    messages().send(sender, "admin.reload-done", Text.unparsed("ms", System.currentTimeMillis() - started),
                            Text.unparsed("skins", s.catalog().skins().size()), Text.unparsed("cases", s.catalog().cases().size()),
                            Text.unparsed("warnings", warnings.size()));
                }));
    }

    void setExtras(dev.plattnericus.cases.shop.ShopNpcAnimator npcAnimator,
                   dev.plattnericus.cases.pack.PackDistribution packDistribution) {
        this.npcAnimator = npcAnimator;
        this.packDistribution = packDistribution;
    }

    public dev.plattnericus.cases.shop.ShopNpcAnimator npcAnimator() {
        return npcAnimator;
    }

    public dev.plattnericus.cases.pack.PackDistribution packDistribution() {
        return packDistribution;
    }

    // ------------------------------------------------------------------ accessors

    public MapPaletteSource.Palette mapPalette() {
        return mapPalette;
    }

    public InspectModels inspectModels() {
        return state.inspectModels();
    }

    @Override
    public JavaPlugin plugin() {
        return plugin;
    }

    @Override
    public PluginSettings settings() {
        return state.settings();
    }

    @Override
    public Messages messages() {
        return state.messages();
    }

    @Override
    public SoundBank sounds() {
        return state.sounds();
    }

    @Override
    public Catalog catalog() {
        return state.catalog();
    }

    @Override
    public SkinFormatter formatter() {
        return state.formatter();
    }

    @Override
    public PluginKeys keys() {
        return keys;
    }

    @Override
    public CaseItems caseItems() {
        return caseItems;
    }

    @Override
    public ProfileService profiles() {
        return profiles;
    }

    @Override
    public OpeningService openings() {
        return openings;
    }

    @Override
    public KnifeService knives() {
        return knives;
    }

    @Override
    public InspectService inspect() {
        return inspect;
    }

    @Override
    public MapPreviewService previews() {
        return previews;
    }

    @Override
    public ShopService shop() {
        return shop;
    }

    public void setCommerce(dev.plattnericus.cases.commerce.CommerceService service) { commerce = service; }

    @Override
    public dev.plattnericus.cases.commerce.CommerceService commerce() { return commerce; }

    @Override
    public SkinRepository repository() {
        return repository;
    }

    @Override
    public RenderService render() {
        return render;
    }

    @Override
    public MenuStates menuStates() {
        return menuStates;
    }

    @Override
    public dev.plattnericus.cases.gallery.SkinGallery gallery() {
        return gallery;
    }
}
