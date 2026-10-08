package dev.plattnericus.cases.core;

import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.config.Messages;
import dev.plattnericus.cases.config.PluginSettings;
import dev.plattnericus.cases.config.SoundBank;
import dev.plattnericus.cases.gui.MenuStates;
import dev.plattnericus.cases.inspect.InspectService;
import dev.plattnericus.cases.items.CaseItems;
import dev.plattnericus.cases.items.PluginKeys;
import dev.plattnericus.cases.items.SkinFormatter;
import dev.plattnericus.cases.knife.KnifeService;
import dev.plattnericus.cases.map.MapPreviewService;
import dev.plattnericus.cases.opening.OpeningService;
import dev.plattnericus.cases.profile.ProfileService;
import dev.plattnericus.cases.render.RenderService;
import dev.plattnericus.cases.shop.ShopService;
import dev.plattnericus.cases.storage.SkinRepository;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Read access to the running services. It holds references only; all behaviour lives in the
 * services themselves. Values that change on reload (settings, catalog, texts) are always read
 * fresh through these accessors.
 */
public interface CasesContext {

    JavaPlugin plugin();

    PluginSettings settings();

    Messages messages();

    default Messages messages(org.bukkit.command.CommandSender viewer) {
        return messages().forAudience(viewer);
    }

    SoundBank sounds();

    Catalog catalog();

    SkinFormatter formatter();

    default SkinFormatter formatter(org.bukkit.command.CommandSender viewer) {
        return formatter().forAudience(viewer);
    }

    PluginKeys keys();

    CaseItems caseItems();

    ProfileService profiles();

    OpeningService openings();

    KnifeService knives();

    InspectService inspect();

    MapPreviewService previews();

    ShopService shop();

    dev.plattnericus.cases.commerce.CommerceService commerce();

    dev.plattnericus.cases.tradein.TradeInService tradeIns();

    SkinRepository repository();

    RenderService render();

    MenuStates menuStates();

    dev.plattnericus.cases.gallery.SkinGallery gallery();
}
