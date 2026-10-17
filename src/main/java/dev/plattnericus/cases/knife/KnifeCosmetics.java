package dev.plattnericus.cases.knife;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.plattnericus.cases.catalog.SkinDefinition;
import dev.plattnericus.cases.config.Messages;
import dev.plattnericus.cases.config.PluginSettings;
import dev.plattnericus.cases.inspect.HeldRig;
import dev.plattnericus.cases.items.PluginKeys;
import dev.plattnericus.cases.items.SkinFormatter;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Applies the equipped knife's identity to a sword and restores it later. Only name, lore, glint,
 * item model and the first-person arm data (holder profile and custom model data) are touched;
 * damage, enchantments, durability and every other component stay exactly as they were. The
 * original cosmetic values are stored on the item itself.
 */
public final class KnifeCosmetics {

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    private final PluginKeys keys;

    public KnifeCosmetics(PluginKeys keys) {
        this.keys = keys;
    }

    public UUID instanceOf(ItemStack item) {
        String v = item == null || item.isEmpty() ? null
                : item.getPersistentDataContainer().get(keys.knifeInstance, PersistentDataType.STRING);
        return parse(v);
    }

    public UUID ownerOf(ItemStack item) {
        String v = item == null || item.isEmpty() ? null
                : item.getPersistentDataContainer().get(keys.knifeOwner, PersistentDataType.STRING);
        return parse(v);
    }

    public boolean isCosmetic(ItemStack item) {
        return instanceOf(item) != null;
    }

    /**
     * Applies (or refreshes) the knife look and returns the item. If the item already shows exactly
     * this state, it is returned untouched so no needless slot update reaches the client.
     */
    public ItemStack apply(ItemStack item, SkinDefinition def, SkinInstance inst, SkinFormatter formatter,
                           Messages messages, PluginSettings settings, long catalogVersion) {
        return apply(item, def, inst, formatter, messages, settings, catalogVersion, null);
    }

    /**
     * As above; with a holder profile and an active pack the item also carries that profile, so the
     * first-person view draws the holder's own skin arms, plus the arm width for the pack's rig.
     */
    public ItemStack apply(ItemStack item, SkinDefinition def, SkinInstance inst, SkinFormatter formatter,
                           Messages messages, PluginSettings settings, long catalogVersion, PlayerProfile holder) {
        boolean arms = holder != null && settings.resourcePack().enabled();
        String armState = arms ? (PlayerArms.slim(holder) ? HeldRig.SLIM_STATE : HeldRig.WIDE_STATE) : "";
        String state = inst.id() + ":" + inst.kills() + ":" + inst.statTrak() + ":" + inst.floatValue() + ":"
                + inst.pattern() + ":" + def.id() + ":" + catalogVersion + ":" + settings.resourcePack().enabled()
                + ":" + settings.knives().glint() + ":" + messages.language()
                + ":" + armState + ":" + (arms ? PlayerArms.textureKey(holder) : 0);
        // A client-side inspect copy written back by a creative inventory must not keep its hidden state.
        if (state.equals(item.getPersistentDataContainer().get(keys.knifeState, PersistentDataType.STRING))
                && (!arms || armState.equals(firstModelString(item)))) {
            return item;
        }
        ItemStack target = isCosmetic(item) ? strip(item) : item;
        ItemMeta meta = target.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.origName, PersistentDataType.STRING, meta.hasDisplayName() ? GSON.serialize(meta.displayName()) : "");
        List<Component> originalLore = meta.hasLore() ? meta.lore() : List.of();
        pdc.set(keys.origLore, PersistentDataType.LIST.strings(), originalLore.stream().map(GSON::serialize).toList());
        byte glint = meta.hasEnchantmentGlintOverride() ? (byte) (Boolean.TRUE.equals(meta.getEnchantmentGlintOverride()) ? 1 : 2) : 0;
        pdc.set(keys.origGlint, PersistentDataType.BYTE, glint);
        pdc.set(keys.origModel, PersistentDataType.STRING, meta.hasItemModel() ? meta.getItemModel().asString() : "");
        pdc.set(keys.origModelData, PersistentDataType.STRING, encode(target.getData(DataComponentTypes.CUSTOM_MODEL_DATA)));
        boolean addProfile = arms && !target.hasData(DataComponentTypes.PROFILE);
        if (addProfile) pdc.set(keys.addedProfile, PersistentDataType.BYTE, (byte) 1);

        meta.displayName(formatter.name(def, inst));
        List<Component> lore = new ArrayList<>(formatter.lore(def, inst, false));
        lore.add(messages.item("knife.sword-footer", Text.component("base", Text.materialName(target.getType()))));
        lore.addAll(originalLore);
        meta.lore(lore);
        // The glint marks skinned weapons without the pack; with it, the skin itself is the marker
        // and the glint would tint the artwork and the holder's arms.
        if (settings.knives().glint() && !settings.resourcePack().enabled()) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (settings.resourcePack().enabled()) {
            meta.setItemModel(new NamespacedKey(settings.resourcePack().namespace(), "skin/" + def.id()));
        }
        pdc.set(keys.knifeInstance, PersistentDataType.STRING, inst.id().toString());
        pdc.set(keys.knifeOwner, PersistentDataType.STRING, inst.owner().toString());
        pdc.set(keys.knifeState, PersistentDataType.STRING, state);
        target.setItemMeta(meta);
        if (arms) {
            target.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(armState).build());
            if (addProfile) target.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(holder));
        }
        return target;
    }

    /** Client-only copy for the inspect owner: same hotbar icon, no first-person model. */
    public ItemStack inspecting(ItemStack item) {
        return inspecting(item, false);
    }

    /**
     * Client-only copy shown while an inspect plays: same hotbar icon, no hand model. With {@code aim}
     * it is a loaded crossbow, so player models raise both arms into Minecraft's crossbow hold and the
     * inspected weapon sits in their hands for everyone, including the owner's own third-person view.
     */
    public ItemStack inspecting(ItemStack item, boolean aim) {
        ItemStack copy = aim ? new ItemStack(org.bukkit.Material.CROSSBOW) : item.clone();
        if (aim) {
            var model = item.getData(DataComponentTypes.ITEM_MODEL);
            if (model != null) copy.setData(DataComponentTypes.ITEM_MODEL, model);
            var name = item.getData(DataComponentTypes.CUSTOM_NAME);
            if (name != null) copy.setData(DataComponentTypes.CUSTOM_NAME, name);
            copy.setData(DataComponentTypes.CHARGED_PROJECTILES, io.papermc.paper.datacomponent.item.ChargedProjectiles
                    .chargedProjectiles(List.of(new ItemStack(org.bukkit.Material.ARROW))));
        }
        copy.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(HeldRig.INSPECT_STATE).build());
        return ownCooldown(copy);
    }

    /**
     * Client-only copy that holds and looks exactly like {@code item} (same model, arm data and
     * loaded state) on an item without a use action, so the client does not start drawing a bow or
     * charging a crossbow on its own while an inspect starts.
     */
    public ItemStack ready(ItemStack item) {
        return ownCooldown(item.withType(org.bukkit.Material.STICK));
    }

    /**
     * Inspect copies keep their own cooldown group: the cooldown that blocks the held weapon's use on
     * the server during an inspect does not draw its sweep over the copy's hotbar icon.
     */
    private static final net.kyori.adventure.key.Key INSPECT_COOLDOWN = net.kyori.adventure.key.Key.key("mccases", "inspect");

    private ItemStack ownCooldown(ItemStack copy) {
        copy.setData(DataComponentTypes.USE_COOLDOWN, io.papermc.paper.datacomponent.item.UseCooldown.useCooldown(.05f)
                .cooldownGroup(INSPECT_COOLDOWN).build());
        return copy;
    }

    private static String firstModelString(ItemStack item) {
        CustomModelData data = item.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        return data == null || data.strings().isEmpty() ? "" : data.strings().getFirst();
    }

    /** Lossless text form of custom model data; empty when the item had none. */
    private static String encode(CustomModelData data) {
        if (data == null) return "";
        JsonObject json = new JsonObject();
        JsonArray floats = new JsonArray(), flags = new JsonArray(), strings = new JsonArray(), colors = new JsonArray();
        data.floats().forEach(floats::add);
        data.flags().forEach(flags::add);
        data.strings().forEach(strings::add);
        data.colors().forEach(color -> colors.add(color.asARGB()));
        json.add("floats", floats); json.add("flags", flags); json.add("strings", strings); json.add("colors", colors);
        return json.toString();
    }

    private static CustomModelData decode(String text) {
        JsonObject json = JsonParser.parseString(text).getAsJsonObject();
        CustomModelData.Builder builder = CustomModelData.customModelData();
        json.getAsJsonArray("floats").forEach(v -> builder.addFloat(v.getAsFloat()));
        json.getAsJsonArray("flags").forEach(v -> builder.addFlag(v.getAsBoolean()));
        json.getAsJsonArray("strings").forEach(v -> builder.addString(v.getAsString()));
        json.getAsJsonArray("colors").forEach(v -> builder.addColor(org.bukkit.Color.fromARGB(v.getAsInt())));
        return builder.build();
    }

    /** Restores the original name, lore, glint and model. */
    public ItemStack strip(ItemStack item) {
        if (!isCosmetic(item)) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String name = pdc.get(keys.origName, PersistentDataType.STRING);
        meta.displayName(name == null || name.isEmpty() ? null : GSON.deserialize(name));
        List<String> lore = pdc.get(keys.origLore, PersistentDataType.LIST.strings());
        meta.lore(lore == null || lore.isEmpty() ? null : lore.stream().map(GSON::deserialize).toList());
        Byte glint = pdc.get(keys.origGlint, PersistentDataType.BYTE);
        meta.setEnchantmentGlintOverride(glint == null || glint == 0 ? null : glint == 1);
        String model = pdc.get(keys.origModel, PersistentDataType.STRING);
        meta.setItemModel(model == null || model.isEmpty() ? null : NamespacedKey.fromString(model));
        String modelData = pdc.get(keys.origModelData, PersistentDataType.STRING);
        boolean addedProfile = pdc.has(keys.addedProfile, PersistentDataType.BYTE);
        for (NamespacedKey k : new NamespacedKey[]{keys.knifeInstance, keys.knifeOwner, keys.origName, keys.origLore,
                keys.origGlint, keys.origModel, keys.knifeState, keys.origModelData, keys.addedProfile}) {
            pdc.remove(k);
        }
        item.setItemMeta(meta);
        // Items from older versions have no stored model data: theirs was never replaced.
        if (modelData != null) {
            if (modelData.isEmpty()) item.resetData(DataComponentTypes.CUSTOM_MODEL_DATA);
            else item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, decode(modelData));
        }
        if (addedProfile) item.resetData(DataComponentTypes.PROFILE);
        return item;
    }

    private static UUID parse(String v) {
        if (v == null) {
            return null;
        }
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
