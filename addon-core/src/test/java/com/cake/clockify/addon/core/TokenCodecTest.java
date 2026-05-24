package com.cake.clockify.addon.core;

import com.cake.clockify.addon.core.crypto.TokenCodec;
import com.cake.clockify.addon.core.crypto.TokenCodecException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TokenCodecTest {

    private static final String KEY_HEX = "00".repeat(32);
    private static final Map<String, String> KEYS = Map.of("k1", KEY_HEX);

    @Test
    void encryptThenDecryptRoundTrips() {
        TokenCodec codec = new TokenCodec(KEYS, "k1");
        String plaintext = "my-secret-installation-token";
        TokenCodec.Encrypted enc = codec.encrypt(plaintext);
        assertNotNull(enc.keyId());
        assertNotNull(enc.cipher());
        String decrypted = codec.decrypt(enc.keyId(), enc.cipher());
        assertEquals(plaintext, decrypted);
    }

    @Test
    void differentEncryptCallsProduceDifferentCiphertexts() {
        TokenCodec codec = new TokenCodec(KEYS, "k1");
        TokenCodec.Encrypted enc1 = codec.encrypt("token");
        TokenCodec.Encrypted enc2 = codec.encrypt("token");
        assertNotEquals(enc1.cipher(), enc2.cipher(), "IV must differ per encryption");
    }

    @Test
    void decryptWithWrongKeyIdThrows() {
        TokenCodec codec = new TokenCodec(KEYS, "k1");
        TokenCodec.Encrypted enc = codec.encrypt("token");
        assertThrows(TokenCodecException.class, () -> codec.decrypt("unknown-key", enc.cipher()));
    }

    @Test
    void decryptTamperedCiphertextThrows() {
        TokenCodec codec = new TokenCodec(KEYS, "k1");
        TokenCodec.Encrypted enc = codec.encrypt("token");
        byte[] tampered = enc.cipher().clone();
        tampered[tampered.length - 1] ^= 0xFF;
        assertThrows(TokenCodecException.class, () -> codec.decrypt(enc.keyId(), tampered));
    }

    @Test
    void blankActiveKeyIdThrows() {
        assertThrows(TokenCodecException.class, () -> new TokenCodec(KEYS, ""));
    }

    @Test
    void invalidHexKeyThrows() {
        assertThrows(TokenCodecException.class,
                () -> new TokenCodec(Map.of("bad", "not-hex"), "bad"));
    }

    @Test
    void shortKeyThrows() {
        assertThrows(TokenCodecException.class,
                () -> new TokenCodec(Map.of("short", "deadbeef"), "short"));
    }

    @Test
    void plaintextNotLeakedInEncryptedForm() {
        TokenCodec codec = new TokenCodec(KEYS, "k1");
        String secret = "super-secret-token-12345";
        TokenCodec.Encrypted enc = codec.encrypt(secret);
        // Verify the plaintext bytes don't appear in the ciphertext
        String cipherHex = java.util.HexFormat.of().formatHex(enc.cipher());
        assertFalse(cipherHex.contains(java.util.HexFormat.of().formatHex(secret.getBytes())));
    }
}
