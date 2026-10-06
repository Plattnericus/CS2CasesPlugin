package dev.plattnericus.cases;

import dev.plattnericus.cases.core.CasesBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MCCases - server-side CS2 style cases, skins, patterns and knives.
 *
 * @author Plattnericus (plattnericus.dev)
 */
public final class MCCasesPlugin extends JavaPlugin {

    private CasesBootstrap bootstrap;

    @Override
    public void onEnable() {
        bootstrap = new CasesBootstrap(this);
        if (!bootstrap.enable()) {
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) {
            bootstrap.disable();
        }
    }
}
