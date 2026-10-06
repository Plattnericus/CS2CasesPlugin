package dev.plattnericus.cases.profile;

import dev.plattnericus.cases.skin.SkinInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** In-memory copy of a player's virtual skin inventory. Server thread only. */
public final class PlayerProfile {

    public static final String KNIFE_SLOT = "knife";

    private final UUID owner;
    private final Map<UUID, SkinInstance> skins = new LinkedHashMap<>();
    private final java.util.EnumMap<EquipSlot, UUID> equipped = new java.util.EnumMap<>(EquipSlot.class);

    public PlayerProfile(UUID owner) {
        this.owner = owner;
    }

    public UUID owner() {
        return owner;
    }

    public void put(SkinInstance instance) {
        skins.put(instance.id(), instance);
    }

    public SkinInstance get(UUID id) {
        return skins.get(id);
    }

    public SkinInstance remove(UUID id) {
        SkinInstance removed = skins.remove(id);
        equipped.values().removeIf(id::equals);
        return removed;
    }

    /** Owned instances only (pending rewards are hidden until revealed). */
    public List<SkinInstance> owned() {
        List<SkinInstance> out = new ArrayList<>();
        for (SkinInstance s : skins.values()) {
            if (s.status() == SkinInstance.Status.OWNED) {
                out.add(s);
            }
        }
        return out;
    }

    public Collection<SkinInstance> all() {
        return skins.values();
    }

    /** Finds an owned instance by full id or by its 8-character short id. */
    public SkinInstance find(String idOrPrefix) {
        String needle = idOrPrefix.toLowerCase(java.util.Locale.ROOT);
        for (SkinInstance s : skins.values()) {
            if (s.status() == SkinInstance.Status.OWNED && s.id().toString().startsWith(needle)) {
                return s;
            }
        }
        return null;
    }

    public UUID equipped(EquipSlot slot) {
        return equipped.get(slot);
    }

    /** Returns the instance in a slot only if this profile really owns it. */
    public SkinInstance equippedInstance(EquipSlot slot) {
        UUID id = equipped.get(slot);
        if (id == null) {
            return null;
        }
        SkinInstance s = skins.get(id);
        return s != null && s.status() == SkinInstance.Status.OWNED ? s : null;
    }

    public void setEquipped(EquipSlot slot, UUID instance) {
        if (instance == null) {
            equipped.remove(slot);
        } else {
            equipped.put(slot, instance);
        }
    }

    /** Slot the instance is equipped in, or null. */
    public EquipSlot slotOf(UUID instance) {
        for (var e : equipped.entrySet()) {
            if (e.getValue().equals(instance)) {
                return e.getKey();
            }
        }
        return null;
    }

    public boolean isEquipped(UUID instance) {
        return equipped.containsValue(instance);
    }

    public UUID equippedKnife() {
        return equipped(EquipSlot.KNIFE);
    }

    public SkinInstance equippedKnifeInstance() {
        return equippedInstance(EquipSlot.KNIFE);
    }

    public void setEquippedKnife(UUID knife) {
        setEquipped(EquipSlot.KNIFE, knife);
    }
}
