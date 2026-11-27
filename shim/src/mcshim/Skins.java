package mcshim;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mcshim.awt.image.BufferedImage;

/**
 * Player skins. The game asks the long-gone www.minecraft.net/skin/<name>.png;
 * the answer comes from (1) the skin the player picked on this device, for the
 * local player, or (2) the account's real skin through today's Mojang API.
 * Modern 64x64 skins are cut to the 64x32 layout these versions draw.
 */
public final class Skins {
    private Skins() {}

    /** The local player's name, as the game was started with (or renamed to). */
    public static volatile String localName = "Player";

    private static final Map<String, BufferedImage> cache = new HashMap<String, BufferedImage>();

    /** An InputStream that already holds the decoded image (ImageIO.read takes it as is). */
    public static final class ImageStream extends ByteArrayInputStream {
        public final BufferedImage image;
        ImageStream(BufferedImage im) { super(new byte[0]); image = im; }
    }

    public static boolean isSkinUrl(String url) {
        return url.contains("minecraft.net/skin/") && url.endsWith(".png");
    }

    public static java.net.URLConnection connection(final URL url) {
        String s = url.toString();
        final String name = s.substring(s.lastIndexOf('/') + 1, s.length() - 4);
        final BufferedImage[] img = {null};
        final boolean[] done = {false};
        return new HttpURLConnection(url) {
            private void load() {
                if (!done[0]) { done[0] = true; img[0] = skin(name); }
            }
            public void connect() { load(); }
            public void disconnect() {}
            public boolean usingProxy() { return false; }
            public int getResponseCode() { load(); return img[0] != null ? 200 : 404; }
            public InputStream getInputStream() throws IOException {
                load();
                if (img[0] == null) throw new java.io.FileNotFoundException(name);
                return new ImageStream(img[0]);
            }
        };
    }

    /** The skin for this player name, already in 64x32 layout, or null for the default. */
    static BufferedImage skin(String name) {
        synchronized (cache) {
            if (cache.containsKey(name)) return cache.get(name);
        }
        BufferedImage im = null;
        try {
            if (name.equals(localName)) {
                File f = Platform.get().customSkin();
                if (f != null && f.isFile()) {
                    InputStream in = new FileInputStream(f);
                    try { im = Platform.get().decodeImage(in); } finally { in.close(); }
                }
            }
            if (im == null && Platform.get().onlineSkin(name)) im = fetch(name);
            if (im != null) im = legacy(im);
        } catch (Throwable t) {
            Platform.get().log("skin " + name + ": " + t);
            im = null;
        }
        synchronized (cache) { cache.put(name, im); }
        return im;
    }

    /** Forget cached skins (after the player picks another one). */
    public static void reset() {
        synchronized (cache) { cache.clear(); }
    }

    private static BufferedImage fetch(String name) throws IOException {
        String p = text("https://api.mojang.com/users/profiles/minecraft/" + name);
        Matcher id = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"").matcher(p);
        if (!id.find()) return null;
        String prof = text("https://sessionserver.mojang.com/session/minecraft/profile/" + id.group(1));
        Matcher v = Pattern.compile("\"value\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"").matcher(prof);
        if (!v.find()) return null;
        String textures = new String(Net.base64(v.group(1)), "UTF-8");
        Matcher u = Pattern.compile("\"SKIN\"\\s*:\\s*\\{\\s*\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(textures);
        if (!u.find()) return null;
        InputStream in = new URL(u.group(1)).openStream();
        try { return Platform.get().decodeImage(in); } finally { in.close(); }
    }

    private static String text(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        if (c.getResponseCode() != 200) { c.disconnect(); return ""; }
        InputStream in = c.getInputStream();
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return new String(b.toByteArray(), "UTF-8");
    }

    /**
     * 64x64 (1.8+) skin -> the 64x32 layout. The top half is the same in both;
     * the bottom half (separate left limbs, second layers) has no place in it.
     * Other sizes (HD skins) are scaled the same way by their width.
     */
    static BufferedImage legacy(BufferedImage im) {
        int w = im.getWidth(), h = im.getHeight();
        if (w == 64 && h == 32) return im;
        if (w < 64 || (h != w && h * 2 != w)) return null; // not a skin
        int scale = w / 64;
        BufferedImage out = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 64; x++)
                out.setRGB(x, y, im.getRGB(x * scale, y * scale));
        return out;
    }
}
