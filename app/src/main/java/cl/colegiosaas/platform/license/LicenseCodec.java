package cl.colegiosaas.platform.license;

import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.Plan;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formato de la licencia: {@code <datos en JSON, base64url>.<firma Ed25519, base64url>}. Cabe en una variable de
 * entorno ({@code APP_LICENSE}) y cualquier cambio en los datos invalida la firma.
 */
public final class LicenseCodec {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private LicenseCodec() {
    }

    public static class InvalidLicense extends RuntimeException {
        InvalidLicense(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static String sign(License license, PrivateKey key) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", license.id());
        data.put("school", license.school());
        data.put("domain", license.domain());
        data.put("plan", license.plan().name());
        data.put("addons", license.addons().stream().map(Enum::name).sorted().toList());
        data.put("issuedOn", license.issuedOn().toString());
        data.put("expiresOn", license.expiresOn().toString());
        byte[] payload = JSON.writeValueAsString(data).getBytes(StandardCharsets.UTF_8);
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(key);
            signer.update(payload);
            return ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar la licencia", e);
        }
    }

    public static License verify(String token, PublicKey key) {
        if (token == null || token.isBlank()) {
            throw new InvalidLicense("No hay licencia", null);
        }
        String[] parts = token.strip().split("\\.");
        if (parts.length != 2) {
            throw new InvalidLicense("La licencia no tiene el formato esperado", null);
        }
        try {
            byte[] payload = DECODER.decode(parts[0]);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(payload);
            if (!verifier.verify(DECODER.decode(parts[1]))) {
                throw new InvalidLicense("La firma de la licencia no es válida", null);
            }
            JsonNode data = JSON.readTree(new String(payload, StandardCharsets.UTF_8));
            EnumSet<Feature> addons = EnumSet.noneOf(Feature.class);
            data.path("addons").forEach(a -> addons.add(Feature.valueOf(a.asString())));
            return new License(data.path("id").asString(), data.path("school").asString(), data.path("domain").asString(),
                    Plan.valueOf(data.path("plan").asString()), addons, LocalDate.parse(data.path("issuedOn").asString()),
                    LocalDate.parse(data.path("expiresOn").asString()));
        } catch (InvalidLicense e) {
            throw e;
        } catch (RuntimeException | GeneralSecurityException e) {
            throw new InvalidLicense("La licencia está dañada", e);
        }
    }

    public static PublicKey publicKey(String base64) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64.strip())));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("La llave pública de licencias no es válida", e);
        }
    }

    public static PrivateKey privateKey(String base64) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64.strip())));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("La llave privada de licencias no es válida", e);
        }
    }

    static String describe(License license) {
        return license.plan() + (license.addons().isEmpty() ? "" : " + "
                + license.addons().stream().map(Enum::name).sorted().collect(Collectors.joining(", ")));
    }
}
