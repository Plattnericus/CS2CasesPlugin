package dev.plattnericus.cases.items;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * HMAC signature for case and key items. Items built with commands or data packs that merely copy
 * the persistent data keys are rejected because they cannot produce a valid signature without the
 * server secret ({@code secret.key}, generated once).
 */
public final class ItemSigner {

    private final byte[] secret;

    public ItemSigner(File dataFolder) throws IOException {
        File file = new File(dataFolder, "secret.key");
        if (file.isFile()) {
            secret = Base64.getDecoder().decode(Files.readString(file.toPath(), StandardCharsets.UTF_8).trim());
        } else {
            byte[] fresh = new byte[32];
            new SecureRandom().nextBytes(fresh);
            Files.writeString(file.toPath(), Base64.getEncoder().encodeToString(fresh), StandardCharsets.UTF_8);
            secret = fresh;
        }
    }

    public String sign(String type, String id, boolean test) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] out = mac.doFinal((type + ':' + id + ':' + (test ? 1 : 0)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out, 0, 12);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    public boolean verify(String type, String id, boolean test, String signature) {
        if (signature == null) {
            return false;
        }
        return MessageDigest.isEqual(sign(type, id, test).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
