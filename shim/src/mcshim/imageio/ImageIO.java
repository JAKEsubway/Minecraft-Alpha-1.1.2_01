package mcshim.imageio;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import mcshim.Platform;
import mcshim.awt.image.BufferedImage;

public final class ImageIO {
    private ImageIO() {}

    public static BufferedImage read(InputStream in) throws IOException {
        if (in instanceof mcshim.Skins.ImageStream) return ((mcshim.Skins.ImageStream) in).image;
        if (in == null) throw new IllegalArgumentException("input == null!");
        try {
            return Platform.get().decodeImage(in);
        } finally {
            in.close();
        }
    }

    public static BufferedImage read(URL url) throws IOException {
        java.net.URLConnection c = url.openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(10000);
        return read(c.getInputStream());
    }

    public static BufferedImage read(java.io.File f) throws IOException {
        return read(new java.io.FileInputStream(f));
    }

    public static void setUseCache(boolean b) {}
    /** Screenshots are not saved on this port. */
    public static boolean write(mcshim.awt.image.RenderedImage im, String fmt, java.io.OutputStream out) { return false; }
    public static boolean write(mcshim.awt.image.RenderedImage im, String fmt, java.io.File out) { return false; }
}
