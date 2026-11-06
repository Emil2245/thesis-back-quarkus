package ec.uce.propuestas.cronograma.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Fingerprint canónico, determinista e independiente de identidades del presupuesto. */
public final class PresupuestoFingerprint {

    private PresupuestoFingerprint() {}

    public record RubroSnapshot(String capituloItem, String rubroItem, String codigo, BigDecimal precioTotal) {}

    private static final Comparator<RubroSnapshot> ORDEN = Comparator.comparing(RubroSnapshot::capituloItem)
            .thenComparing(RubroSnapshot::rubroItem)
            .thenComparing(RubroSnapshot::codigo)
            .thenComparing(r -> precioNormalizado(r.precioTotal()));

    /**
     * SHA-256 lowercase de cuatro campos UTF-8 por fila. Cada campo lleva un prefijo
     * binario de longitud de 4 bytes, por lo que valores con delimitadores no colisionan.
     */
    public static String calcular(List<RubroSnapshot> rubros) {
        MessageDigest digest = sha256();
        List<RubroSnapshot> ordenados = new ArrayList<>(rubros);
        ordenados.sort(ORDEN);
        for (RubroSnapshot rubro : ordenados) {
            agregar(digest, Objects.requireNonNull(rubro.capituloItem()));
            agregar(digest, Objects.requireNonNull(rubro.rubroItem()));
            agregar(digest, Objects.requireNonNull(rubro.codigo()));
            agregar(digest, precioNormalizado(rubro.precioTotal()));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String precioNormalizado(BigDecimal precio) {
        return Objects.requireNonNull(precio).setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    private static void agregar(MessageDigest digest, String campo) {
        byte[] bytes = campo.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
