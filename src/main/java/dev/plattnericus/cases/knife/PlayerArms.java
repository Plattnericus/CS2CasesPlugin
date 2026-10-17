package dev.plattnericus.cases.knife;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Arm width of a player's Minecraft skin. Signed textures carry {@code metadata.model = slim};
 * players without a custom skin use the client's default skin for their UUID.
 */
public final class PlayerArms {

    /** Minecraft 26.3 lists nine slim default skins before the nine wide ones. */
    private static final int DEFAULT_SKINS = 18, SLIM_DEFAULT_SKINS = 9;

    private PlayerArms() {
    }

    public static boolean slim(PlayerProfile profile) {
        for (ProfileProperty property : profile.getProperties()) {
            if (!property.getName().equals("textures")) continue;
            try {
                JsonObject textures = JsonParser.parseString(new String(Base64.getDecoder().decode(property.getValue()),
                        StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("textures");
                JsonObject skin = textures == null ? null : textures.getAsJsonObject("SKIN");
                if (skin != null) {
                    JsonObject metadata = skin.getAsJsonObject("metadata");
                    return metadata != null && metadata.has("model") && "slim".equals(metadata.get("model").getAsString());
                }
            } catch (RuntimeException malformed) {
                // Unreadable textures fall back to the default skin, exactly like the client.
            }
        }
        return profile.getId() != null && defaultSlim(profile.getId());
    }

    static boolean defaultSlim(UUID id) {
        return Math.floorMod(id.hashCode(), DEFAULT_SKINS) < SLIM_DEFAULT_SKINS;
    }

    /** Changes whenever the skin texture changes, so held items refresh their arms. */
    public static int textureKey(PlayerProfile profile) {
        int key = profile.getId() == null ? 0 : profile.getId().hashCode();
        for (ProfileProperty property : profile.getProperties()) {
            if (property.getName().equals("textures")) key = 31 * key + property.getValue().hashCode();
        }
        return key;
    }
}
