package mcport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.system.Os;
import dalvik.system.DexClassLoader;

/**
 * The game versions packed into this APK (assets/games/index.txt, one
 * "id<TAB>label" per line, each with assets/games/<id>.jar holding its dex and
 * resources). Each version runs in its own class loader and its own folder.
 */
final class Games {
    static final class Game {
        final String id, label;
        Game(String id, String label) { this.id = id; this.label = label; }
    }

    private Games() {}

    static List<Game> list(Context c) {
        ArrayList<Game> out = new ArrayList<Game>();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("games/index.txt"), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                int t = line.indexOf('\t');
                if (t > 0) out.add(new Game(line.substring(0, t), line.substring(t + 1)));
            }
            r.close();
        } catch (Exception ignored) {}
        return out;
    }

    /**
     * Where the version keeps .minecraft. Alpha stays in the app's folder, as
     * before versions existed (so existing worlds are kept); others get
     * versions/<id>/ so their options and saves do not mix.
     */
    static File home(File root, Game g) {
        return g.id.startsWith("alpha") ? root : new File(root, "versions/" + g.id);
    }

    /** Copy the version's jar out of the APK (once per install / update) and load it. */
    static ClassLoader load(Context c, Game g) throws Exception {
        File dir = new File(c.getFilesDir(), "games");
        dir.mkdirs();
        long stamp = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime;
        File jar = new File(dir, g.id + "-" + stamp + ".jar");
        if (!jar.exists()) {
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) if (f.getName().startsWith(g.id + "-")) f.delete();
            File tmp = new File(dir, g.id + ".part");
            InputStream in = c.getAssets().open("games/" + g.id + ".jar");
            OutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            tmp.setReadOnly(); // newer Android refuses writable code files
            if (!tmp.renameTo(jar)) throw new java.io.IOException("cannot store " + jar);
        }
        File opt = new File(c.getCodeCacheDir(), "dex");
        opt.mkdirs();
        return new DexClassLoader(jar.getPath(), opt.getPath(), null, Games.class.getClassLoader());
    }

    /**
     * Give a version with its own folder the sounds installed in the shared
     * one, by links: resources/<dir> -> shared/<dir>. music/ is a real folder
     * of links to each track, so a version may add its own files there.
     */
    static void linkResources(File shared, File mine) {
        String[] tops = shared.list();
        if (tops == null) return;
        mine.mkdirs();
        for (String t : tops) {
            File s = new File(shared, t), m = new File(mine, t);
            if (t.equals("music") && s.isDirectory()) {
                m.mkdirs();
                String[] tracks = s.list();
                if (tracks != null) for (String f : tracks) link(new File(s, f), new File(m, f));
            } else {
                link(s, m);
            }
        }
    }

    private static void link(File target, File link) {
        try {
            Os.lstat(link.getPath());
            return; // already there
        } catch (Exception notThere) {}
        try {
            Os.symlink(target.getPath(), link.getPath());
        } catch (Exception ignored) {}
    }
}
