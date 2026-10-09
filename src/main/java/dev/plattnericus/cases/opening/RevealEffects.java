package dev.plattnericus.cases.opening;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.Rarity;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Rarity dependent finish: restrained sounds for common drops, a short particle accent for
 * Covert and a separate title reveal for rare special items.
 */
final class RevealEffects {

    private final CasesContext ctx;

    RevealEffects(CasesContext ctx) {
        this.ctx = ctx;
    }

    void reveal(Player player, SkinDefinition reward, SkinInstance instance, boolean overlay) {
        Rarity rarity = reward.rarity();
        ctx.sounds().play(player, rarity.revealSound());
        // Allow callers to suppress the overlay while retaining the reveal sound.
        Component name = ctx.formatter(player).name(reward, instance);
        if (!overlay) { player.sendActionBar(name); return; }
        if (rarity.rareSpecial()) {
            player.showTitle(Title.title(ctx.messages(player).get("opening.reveal.rare-title"), name,
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2600), Duration.ofMillis(600))));
            burst(player, rarity.color(), 46, 0.9);
            Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> {
                if (player.isOnline() && instance != null) {
                    player.sendActionBar(ctx.formatter(player).actionBar(reward, instance));
                }
            }, 36L);
        } else if (rarity.order() >= 4) {
            player.showTitle(Title.title(Component.empty(), name,
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1600), Duration.ofMillis(400))));
            burst(player, rarity.color(), 22, 0.7);
        } else {
            player.sendActionBar(name);
        }
    }

    void announce(Player player, SkinDefinition reward, SkinInstance instance, CaseDefinition caseDef, boolean testOnly) {
        Component full = ctx.formatter(player).fullName(reward, instance);
        ctx.messages(player).send(player, testOnly ? "opening.result-test" : "opening.result",
                Text.component("skin", full), Text.unparsed("case", caseDef.name()),
                Text.unparsed("float", ctx.formatter(player).floatText(instance.floatValue(), false)),
                Text.unparsed("pattern", instance.pattern()));
        if (!testOnly && instance.origin() == SkinInstance.Origin.CASE && reward.rarity().rareSpecial() && ctx.settings().opening().broadcastRare()) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other != player) {
                    ctx.messages(other).send(other, "opening.broadcast", Text.unparsed("player", player.getName()),
                            Text.component("skin", ctx.formatter(other).fullName(reward, instance)));
                }
            }
        }
    }

    private void burst(Player player, int rgb, int count, double radius) {
        Location base = player.getLocation().add(0, 1.0, 0);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb & 0xFFFFFF), 1.1f);
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            Location p = base.clone().add(Math.cos(a) * radius, Math.sin(a * 3) * 0.25, Math.sin(a) * radius);
            player.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
        }
        player.spawnParticle(Particle.END_ROD, base, Math.max(4, count / 6), 0.35, 0.45, 0.35, 0.02);
    }
}
