package dev.plattnericus.cases.shop;

import dev.plattnericus.cases.catalog.CaseDefinition;
import dev.plattnericus.cases.catalog.Catalog;
import dev.plattnericus.cases.catalog.KeyDefinition;
import dev.plattnericus.cases.core.CasesContext;
import dev.plattnericus.cases.items.CaseItems;
import dev.plattnericus.cases.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Case dealer villager: offers, prices in a vanilla currency item and atomic purchases. */
public final class ShopService {

    public record Offer(String type, String id, int price) {
    }

    public enum Result { OK, NOT_ENOUGH, INVENTORY_FULL, UNAVAILABLE }

    private final CasesContext ctx;
    private Material currency = Material.EMERALD;
    private String villagerName = "Case Dealer";
    private String profession = "cartographer";
    private NpcSettings npc = NpcSettings.defaults();
    private boolean naturalEnabled = true;
    private int naturalEvery = 3;
    private double naturalDistance = 96;
    private boolean naturalWander = true;
    private DealerSkins dealerSkins;
    private List<?> skinEntries = List.of();

    /**
     * Dealer appearance. {@code type} is villager or mannequin (a player model with a skin).
     * Skins are player names or texture properties; they rotate every {@code skinInterval} seconds.
     */
    public record NpcSettings(String type, String description, List<ResolvableProfile> skins, int skinInterval,
                              boolean animations, double lookRange, int gestureMinTicks, int gestureMaxTicks,
                              boolean holdCase) {

        static NpcSettings defaults() {
            return new NpcSettings("mannequin", "", List.of(), 300, true, 8, 100, 240, true);
        }

        public boolean mannequin() {
            return type.equals("mannequin");
        }
    }
    private int defaultCasePrice = 3;
    private final Map<String, Integer> casePrices = new HashMap<>();
    private final Set<String> excluded = new HashSet<>();
    private final Map<String, Integer> keyPrices = new HashMap<>();

    public ShopService(CasesContext ctx) {
        this.ctx = ctx;
    }

    public void load(File file, Consumer<String> warn) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        Material m = Material.matchMaterial(y.getString("currency", "EMERALD"));
        if (m == null || !m.isItem()) {
            warn.accept("shop.yml: currency is not an item - using EMERALD");
            m = Material.EMERALD;
        }
        currency = m;
        // empty = take the name from the language file
        villagerName = y.getString("villager.name", "");
        profession = y.getString("villager.profession", "cartographer").toLowerCase(Locale.ROOT);
        npc = loadNpc(y, warn);
        naturalEnabled = y.getBoolean("natural-spawn.enabled", true);
        naturalEvery = Math.max(1, y.getInt("natural-spawn.every", 3));
        naturalDistance = Math.max(0, y.getDouble("natural-spawn.min-distance", 96));
        naturalWander = y.getBoolean("natural-spawn.wander", true);
        defaultCasePrice = Math.max(1, y.getInt("cases.default-price", 3));
        casePrices.clear();
        ConfigurationSection prices = y.getConfigurationSection("cases.prices");
        if (prices != null) {
            for (String id : prices.getKeys(false)) {
                casePrices.put(id, Math.max(1, prices.getInt(id)));
            }
        }
        excluded.clear();
        excluded.addAll(y.getStringList("cases.exclude"));
        keyPrices.clear();
        ConfigurationSection keys = y.getConfigurationSection("keys");
        if (keys != null) {
            for (String id : keys.getKeys(false)) {
                keyPrices.put(id, Math.max(1, keys.getInt(id)));
            }
        }
    }

    public Material currency() {
        return currency;
    }

    public List<Offer> offers() {
        Catalog catalog = ctx.catalog();
        List<Offer> out = new ArrayList<>();
        keyPrices.forEach((id, price) -> {
            if (catalog.key(id) != null) {
                out.add(new Offer(CaseItems.TYPE_KEY, id, price));
            }
        });
        for (CaseDefinition def : catalog.cases()) {
            if (def.enabled() && !excluded.contains(def.id())) {
                out.add(new Offer(CaseItems.TYPE_CASE, def.id(), casePrices.getOrDefault(def.id(), defaultCasePrice)));
            }
        }
        return out;
    }

    /** Only plain currency items count (no renamed or otherwise modified stacks). */
    public int balance(Player player) {
        ItemStack plain = new ItemStack(currency);
        int total = 0;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s != null && s.isSimilar(plain)) {
                total += s.getAmount();
            }
        }
        return total;
    }

    public ItemStack product(Offer offer, int amount, Player player) {
        Catalog catalog = ctx.catalog();
        if (offer.type().equals(CaseItems.TYPE_KEY)) {
            KeyDefinition key = catalog.key(offer.id());
            return key == null ? null : ctx.caseItems().keyItem(key, amount, false, ctx.messages(player));
        }
        CaseDefinition def = catalog.caseDefinition(offer.id());
        return def == null ? null : ctx.caseItems().caseItem(def, amount, false, ctx.messages(player));
    }

    /** Checks, charges and delivers in one server-thread step. */
    public Result buy(Player player, Offer offer, int amount) {
        if (offer == null || amount < 1 || amount > 64 || !offers().contains(offer)) {
            return Result.UNAVAILABLE;
        }
        ItemStack product = product(offer, amount, player);
        if (product == null) {
            return Result.UNAVAILABLE;
        }
        long cost = (long) offer.price() * amount;
        if (balance(player) < cost) {
            return Result.NOT_ENOUGH;
        }
        ItemStack plain = new ItemStack(currency);
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) contents[i] = contents[i].clone();
        }
        long left = cost;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack s = contents[i];
            if (s == null || !s.isSimilar(plain)) {
                continue;
            }
            int take = (int) Math.min(left, s.getAmount());
            left -= take;
            if (take == s.getAmount()) {
                contents[i] = null;
            } else {
                s.setAmount(s.getAmount() - take);
            }
        }
        // Plan delivery after payment: spending an entire currency stack may free a slot.
        // Commit only when both operations fit, so a failed purchase never charges the player.
        int remaining = product.getAmount();
        for (ItemStack stack : contents) {
            if (stack == null || !stack.isSimilar(product)) continue;
            int add = Math.min(remaining, Math.max(0, stack.getMaxStackSize() - stack.getAmount()));
            stack.setAmount(stack.getAmount() + add);
            remaining -= add;
        }
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            if (contents[i] != null && !contents[i].isEmpty()) continue;
            ItemStack stack = product.clone();
            int add = Math.min(remaining, stack.getMaxStackSize());
            stack.setAmount(add);
            contents[i] = stack;
            remaining -= add;
        }
        if (remaining > 0) return Result.INVENTORY_FULL;
        player.getInventory().setStorageContents(contents);
        return Result.OK;
    }

    private NpcSettings loadNpc(YamlConfiguration y, Consumer<String> warn) {
        String type = y.getString("npc.type", "mannequin").toLowerCase(Locale.ROOT);
        if (!type.equals("villager") && !type.equals("mannequin")) {
            warn.accept("shop.yml: npc.type must be villager or mannequin - using villager");
            type = "villager";
        }
        skinEntries = y.getList("npc.skins", List.of());
        List<ResolvableProfile> skins = skins(warn);
        int min = Math.max(20, y.getInt("npc.gesture-interval-ticks.min", 100));
        int max = Math.max(min, y.getInt("npc.gesture-interval-ticks.max", 240));
        return new NpcSettings(type, y.getString("npc.description", ""), skins,
                Math.max(10, y.getInt("npc.skin-interval-seconds", 300)), y.getBoolean("npc.animations", true),
                y.getDouble("npc.look-range", 8), min, max, y.getBoolean("npc.hold-case", true));
    }

    /** Builds the skin list; player names use the stored skin and are looked up once when it is missing. */
    private List<ResolvableProfile> skins(Consumer<String> warn) {
        if (dealerSkins == null) dealerSkins = new DealerSkins(ctx.plugin());
        List<ResolvableProfile> skins = new ArrayList<>();
        for (Object entry : skinEntries) {
            if (entry instanceof String name) {
                if (!name.matches("[A-Za-z0-9_]{1,16}")) {
                    warn.accept("shop.yml: npc.skins entry " + name + " is not a valid player name");
                    continue;
                }
                ResolvableProfile stored = dealerSkins.stored(name);
                if (stored != null) {
                    skins.add(stored);
                } else {
                    skins.add(ResolvableProfile.resolvableProfile().name(name).build());
                    dealerSkins.fetch(name, this::skinStored);
                }
            } else if (entry instanceof Map<?, ?> map && map.get("texture") != null) {
                Object signature = map.get("signature");
                skins.add(DealerSkins.textured(String.valueOf(map.get("texture")), signature == null ? null : String.valueOf(signature)));
            } else {
                warn.accept("shop.yml: npc.skins entries must be a player name or {texture, signature}");
            }
        }
        return List.copyOf(skins);
    }

    /** A looked-up skin was saved: switch the dealers to the stored copy. */
    private void skinStored() {
        npc = new NpcSettings(npc.type(), npc.description(), skins(w -> { }), npc.skinInterval(), npc.animations(),
                npc.lookRange(), npc.gestureMinTicks(), npc.gestureMaxTicks(), npc.holdCase());
        if (ctx.npcAnimator() != null) ctx.npcAnimator().refreshAll();
    }

    private String dealerName() {
        return villagerName.isBlank() ? ctx.messages().raw("shop.npc-name") : villagerName;
    }

    public NpcSettings npc() {
        return npc;
    }

    // ------------------------------------------------------------------ dealer entity

    /** Spawns the dealer configured in shop.yml (villager or mannequin). */
    public Entity spawnNpc(Location location) {
        return npc.mannequin() ? spawnMannequin(location) : spawnVillager(location);
    }

    /** Updates stored dealer labels, keeping explicit shop.yml names and descriptions. */
    public void refreshDealerLabels(Entity entity) {
        if (!isShop(entity) || !(entity instanceof Mannequin || entity instanceof Villager)) return;
        if (isDriver(entity)) {
            entity.setCustomNameVisible(false);
            return;
        }
        entity.customName(Text.mm(dealerName()));
        entity.setCustomNameVisible(true);
        if (entity instanceof Mannequin mannequin) {
            mannequin.setDescription(Text.mm(npc.description().isBlank()
                    ? ctx.messages().raw("shop.npc-description") : npc.description()));
        }
    }

    public Mannequin spawnMannequin(Location location) {
        return location.getWorld().spawn(location, Mannequin.class, m -> {
            m.setImmovable(true);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setPersistent(true);
            m.setRemoveWhenFarAway(false);
            if (!npc.skins().isEmpty()) {
                m.setProfile(npc.skins().getFirst());
            }
            equipCase(m);
            m.getPersistentDataContainer().set(ctx.keys().shopVillager, PersistentDataType.BYTE, (byte) 1);
            refreshDealerLabels(m);
        });
    }

    /** Gives the mannequin a case to hold (the plain menu icon, never a usable case). */
    public void equipCase(Mannequin m) {
        if (!npc.holdCase()) {
            m.getEquipment().setItemInMainHand(null);
            return;
        }
        List<CaseDefinition> cases = ctx.catalog().cases();
        if (!cases.isEmpty()) {
            m.getEquipment().setItemInMainHand(ctx.caseItems().caseIcon(cases.getFirst()));
        }
    }

    public boolean naturalEnabled() {
        return naturalEnabled;
    }

    /** Identity of the village a chunk belongs to, or null when no village reaches into it. */
    public Long villageId(org.bukkit.Chunk chunk) {
        var structures = io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.STRUCTURE);
        for (var generated : chunk.getStructures()) {
            var key = structures.getKey(generated.getStructure());
            if (key == null || !key.value().contains("village")) continue;
            var box = generated.getBoundingBox();
            return ((long) Math.floor(box.getCenterX()) << 32) ^ ((long) Math.floor(box.getCenterZ()) & 0xffffffffL);
        }
        return null;
    }

    /**
     * Counts a village the first time it is seen and tells whether it is one that gets a dealer:
     * every {@code natural-spawn.every}-th village of a world. The count is stored in the world.
     */
    public boolean claimVillage(org.bukkit.World world, long village) {
        var data = world.getPersistentDataContainer();
        long[] seen = data.getOrDefault(ctx.keys().shopVillages, PersistentDataType.LONG_ARRAY, new long[0]);
        for (long id : seen) {
            if (id == village) return false;
        }
        long[] grown = java.util.Arrays.copyOf(seen, seen.length + 1);
        grown[seen.length] = village;
        int count = data.getOrDefault(ctx.keys().shopVillageCount, PersistentDataType.INTEGER, 0) + 1;
        data.set(ctx.keys().shopVillages, PersistentDataType.LONG_ARRAY, grown);
        data.set(ctx.keys().shopVillageCount, PersistentDataType.INTEGER, count);
        return count % naturalEvery == 0;
    }

    public double naturalDistance() {
        return naturalDistance;
    }

    /** True when a dealer stands within the given distance (blocks). */
    public boolean dealerNear(Location location, double radius) {
        for (Entity e : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (isShop(e)) return true;
        }
        return false;
    }

    /** Dealer placed by command: stands still. */
    public Villager spawnVillager(Location location) {
        return location.getWorld().spawn(location, Villager.class, v -> makeDealer(v, false));
    }

    /** Turns a villager into the visible villager dealer; a wandering one keeps its AI. */
    public void makeDealer(Villager v, boolean wander) {
        v.setAI(wander);
        protect(v);
        v.setVillagerLevel(5);
        Villager.Profession p = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(profession));
        if (p != null) {
            v.setProfession(p);
        }
        if (wander) v.getPersistentDataContainer().set(ctx.keys().shopWander, PersistentDataType.BYTE, (byte) 1);
        v.getPersistentDataContainer().set(ctx.keys().shopVillager, PersistentDataType.BYTE, (byte) 1);
        refreshDealerLabels(v);
    }

    private static void protect(Villager v) {
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setCollidable(false);
        v.setPersistent(true);
        v.setRemoveWhenFarAway(false);
        v.setBreed(false);
        v.setAgeLock(true);
    }

    /**
     * Turns a village villager into the dealer from shop.yml. A mannequin cannot walk, so the
     * villager stays as its hidden body and the mannequin follows it; with wandering off the
     * villager is replaced. Returns the entities to track.
     */
    public List<Entity> makeNatural(Villager v) {
        if (!npc.mannequin()) {
            makeDealer(v, naturalWander);
            return List.of(v);
        }
        Mannequin m = spawnMannequin(v.getLocation());
        if (!naturalWander) {
            v.remove();
            return List.of(m);
        }
        protect(v);
        v.setInvisible(true);
        v.setProfession(Villager.Profession.NITWIT);
        v.customName(null);
        v.setCustomNameVisible(false);
        v.getPersistentDataContainer().set(ctx.keys().shopVillager, PersistentDataType.BYTE, (byte) 1);
        v.getPersistentDataContainer().set(ctx.keys().shopWander, PersistentDataType.BYTE, (byte) 1);
        v.getPersistentDataContainer().set(ctx.keys().shopLink, PersistentDataType.STRING, m.getUniqueId().toString());
        m.getPersistentDataContainer().set(ctx.keys().shopLink, PersistentDataType.STRING, v.getUniqueId().toString());
        return List.of(v, m);
    }

    /** The hidden villager that carries a walking mannequin dealer. */
    public boolean isDriver(Entity entity) {
        return entity instanceof Villager && entity.getPersistentDataContainer().has(ctx.keys().shopLink, PersistentDataType.STRING);
    }

    public boolean wanders(Entity entity) {
        return entity.getPersistentDataContainer().has(ctx.keys().shopWander, PersistentDataType.BYTE);
    }

    /** The other half of a walking mannequin dealer, or null. */
    public java.util.UUID linked(Entity entity) {
        String id = entity.getPersistentDataContainer().get(ctx.keys().shopLink, PersistentDataType.STRING);
        try {
            return id == null ? null : java.util.UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isShop(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(ctx.keys().shopVillager, PersistentDataType.BYTE);
    }

    /** Removes the closest dealer within 6 blocks. */
    public boolean removeNearest(Player player) {
        Entity best = null;
        double bestD = 36;
        for (Entity e : player.getNearbyEntities(6, 6, 6)) {
            if (isShop(e)) {
                double d = e.getLocation().distanceSquared(player.getLocation());
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        if (best == null) {
            return false;
        }
        java.util.UUID other = linked(best);
        if (other != null && org.bukkit.Bukkit.getEntity(other) instanceof Entity partner) partner.remove();
        best.remove();
        return true;
    }
}
