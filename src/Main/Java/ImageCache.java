import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ImageCache {

    private final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();

    public ImageIcon getIcon(String url, int size) {
        if (url == null || url.trim().isEmpty()) return null;

        String key = url + "|" + size;
        ImageIcon existing = cache.get(key);
        if (existing != null) return existing;

        try {
            BufferedImage src = ImageIO.read(new URL(url));
            if (src == null) return null;

            BufferedImage scaled = scalePreserveAlpha(src, size, size);
            ImageIcon icon = new ImageIcon(scaled);
            cache.put(key, icon);
            return icon;
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage scalePreserveAlpha(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }
}
