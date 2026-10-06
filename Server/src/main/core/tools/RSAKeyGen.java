package core.tools;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.EnumSet;
import java.util.Set;

/**
 * Generates the 1024-bit RSA key pair the server uses to decrypt registration
 * tokens and login blocks (AIO-03).
 *
 * <p>Usage: {@code java core.tools.RSAKeyGen [output-dir]}
 * (default {@code data/rsa}, i.e. {@code Server/data/rsa} when the server runs
 * from {@code Server/}).
 *
 * <p>Writes {@code private.key} (modulus and private exponent, mode 600) and
 * {@code public.key} (modulus and 65537). Point {@code server.rsa_key_path} at
 * {@code private.key}; bake the public modulus into released clients.
 */
public class RSAKeyGen {

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args.length > 0 ? args[0] : "data/rsa");
        generate(dir);
        System.out.println("Wrote " + dir.resolve("private.key") + " and " + dir.resolve("public.key"));
    }

    /** Writes a fresh pair into {@code dir}, creating it if necessary. */
    public static void generate(Path dir) throws Exception {
        Files.createDirectories(dir);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(1024);
        KeyPair keypair = keyGen.genKeyPair();

        RSAPrivateKeySpec privSpec = factory.getKeySpec(keypair.getPrivate(), RSAPrivateKeySpec.class);
        writeKey(dir.resolve("private.key"), privSpec.getModulus(), privSpec.getPrivateExponent(), true);

        RSAPublicKeySpec pubSpec = factory.getKeySpec(keypair.getPublic(), RSAPublicKeySpec.class);
        writeKey(dir.resolve("public.key"), pubSpec.getModulus(), pubSpec.getPublicExponent(), false);
    }

    private static void writeKey(Path file, BigInteger modulus, BigInteger exponent, boolean secret) throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("# 2009Scape RSA ").append(secret ? "private" : "public").append(" key, 1024-bit.\n");
        if (secret) {
            text.append("# Keep this file private: never commit it and never log the exponent.\n");
        }
        text.append("modulus=").append(modulus).append('\n');
        text.append("exponent=").append(exponent).append('\n');
        Files.write(file, text.toString().getBytes(StandardCharsets.UTF_8));
        restrictToOwner(file);
    }

    private static void restrictToOwner(Path file) {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystem; the data directory's permissions apply instead.
        }
    }
}
