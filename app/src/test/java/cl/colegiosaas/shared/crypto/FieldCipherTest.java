package cl.colegiosaas.shared.crypto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCipherTest {

    final FieldCipher cipher = new FieldCipher(key((byte) 1));

    @Test
    void encryptsAndDecryptsIncludingAccents() {
        String stored = cipher.encrypt("María José Ñúñez");

        assertThat(stored).startsWith("v1:").doesNotContain("María");
        assertThat(cipher.decrypt(stored)).isEqualTo("María José Ñúñez");
    }

    @Test
    void sameTextEncryptsDifferentlyEachTime() {
        assertThat(cipher.encrypt("ana@mail.cl")).isNotEqualTo(cipher.encrypt("ana@mail.cl"));
    }

    @Test
    void wrongKeyOrTamperedDataIsDetected() {
        String stored = cipher.encrypt("dato");

        assertThatThrownBy(() -> new FieldCipher(key((byte) 2)).decrypt(stored)).isInstanceOf(IllegalStateException.class);
        String tampered = stored.substring(0, stored.length() - 2) + (stored.endsWith("A") ? "BB" : "AA");
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullStaysNull() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void blindIndexIsStableAndIgnoresCaseAndSpaces() {
        BlindIndex index = new BlindIndex(key((byte) 3));

        assertThat(index.of(" Ana@Mail.CL ")).isEqualTo(index.of("ana@mail.cl")).hasSize(64);
        assertThat(index.of("ana@mail.cl")).isNotEqualTo(new BlindIndex(key((byte) 4)).of("ana@mail.cl"));
    }

    static byte[] key(byte fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, fill);
        return key;
    }
}
