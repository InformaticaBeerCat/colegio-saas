package cl.colegiosaas.platform.license;

import cl.colegiosaas.platform.Feature;
import cl.colegiosaas.platform.Plan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Herramienta del proveedor para emitir licencias (OPS-05). No corre dentro del sitio: se usa desde la línea de
 * comandos con el mismo jar.
 *
 * <pre>
 * java -cp colegio-saas.jar -Dloader.main=cl.colegiosaas.platform.license.LicenseTool \
 *      org.springframework.boot.loader.launch.PropertiesLauncher keys ./llaves
 * java -cp colegio-saas.jar -Dloader.main=cl.colegiosaas.platform.license.LicenseTool \
 *      org.springframework.boot.loader.launch.PropertiesLauncher issue --key ./llaves/proveedor.key \
 *      --school "Colegio San José" --domain colegiosanjose.cl --plan COMMUNITY --addons WHATSAPP_SMS --expires 2027-12-31
 * </pre>
 *
 * La llave privada nunca sale del proveedor. La pública va en {@code src/main/resources/license/proveedor.pub}
 * antes de construir la imagen.
 */
public final class LicenseTool {

    private LicenseTool() {
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        if (args.length >= 2 && args[0].equals("keys")) {
            Path dir = Path.of(args[1]);
            Files.createDirectories(dir);
            KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            Files.writeString(dir.resolve("proveedor.key"), Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n");
            Files.writeString(dir.resolve("proveedor.pub"), Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n");
            System.out.println("Llaves en " + dir.toAbsolutePath() + ". Guarda proveedor.key fuera del repositorio.");
            return;
        }
        if (args.length >= 1 && args[0].equals("issue")) {
            Map<String, String> options = options(Arrays.copyOfRange(args, 1, args.length));
            EnumSet<Feature> addons = EnumSet.noneOf(Feature.class);
            String addonList = options.getOrDefault("addons", "");
            if (!addonList.isBlank()) {
                Arrays.stream(addonList.split(",")).map(String::strip).map(Feature::valueOf).forEach(addons::add);
            }
            License license = new License(UUID.randomUUID().toString(), required(options, "school"),
                    required(options, "domain").toLowerCase().replaceFirst("^www\\.", ""),
                    Plan.valueOf(required(options, "plan")), addons, LocalDate.now(),
                    LocalDate.parse(required(options, "expires")));
            String key = Files.readString(Path.of(required(options, "key")));
            System.out.println(LicenseCodec.sign(license, LicenseCodec.privateKey(key)));
            return;
        }
        System.err.println("Uso: keys <carpeta> | issue --key <archivo> --school <nombre> --domain <dominio> --plan <BASE|COMMUNITY|ADMISSIONS_PRO> [--addons A,B] --expires <aaaa-mm-dd>");
        System.exit(2);
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta --" + name);
        }
        return value;
    }
}
