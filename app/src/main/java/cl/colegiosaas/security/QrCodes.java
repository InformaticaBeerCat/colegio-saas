package cl.colegiosaas.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Genera el QR como SVG en una data URI ({@code <img src="data:image/svg+xml;base64,…">}):
 * sin JavaScript ni servicios externos, compatible con la CSP del sitio.
 */
public final class QrCodes {

    private QrCodes() {
    }

    public static String svgDataUri(String text) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.MARGIN, 2, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            String svg = toSvg(matrix);
            return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el código QR", e);
        }
    }

    private static String toSvg(BitMatrix matrix) {
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x).append(',').append(y).append("h1v1h-1z");
                }
            }
        }
        return """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" shape-rendering="crispEdges">\
                <rect width="100%%" height="100%%" fill="#fff"/><path d="%s" fill="#000"/></svg>"""
                .formatted(matrix.getWidth(), matrix.getHeight(), path);
    }
}
