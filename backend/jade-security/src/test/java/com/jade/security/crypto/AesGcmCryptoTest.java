package com.jade.security.crypto;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AesGcmCryptoTest {

    @Inject
    AesGcmCrypto crypto;

    @Test
    void encryptAndDecrypt() {
        String plain = "13800138000";
        String cipher = crypto.encrypt(plain);
        assertNotNull(cipher);
        assertNotEquals(plain, cipher);

        String decrypted = crypto.decrypt(cipher);
        assertEquals(plain, decrypted);
    }

    @Test
    void encryptDifferentEachTime() {
        // AES-GCM 每次用新 IV，所以同明文应产生不同密文
        String a = crypto.encrypt("hello");
        String b = crypto.encrypt("hello");
        assertNotEquals(a, b);
        assertEquals("hello", crypto.decrypt(a));
        assertEquals("hello", crypto.decrypt(b));
    }

    @Test
    void nullSafe() {
        assertNull(crypto.encrypt(null));
        assertNull(crypto.decrypt(null));
    }

    @Test
    void decryptInvalidFails() {
        assertThrows(Exception.class, () -> crypto.decrypt("not-a-valid-cipher"));
    }
}
