package mcshim.imageio;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import mcshim.Platform;
import mcshim.awt.image.BufferedImage;

public final class ImageIO {
    private ImageIO() {}

    public static BufferedImage read(InputStream in) throws IOException {
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
}
