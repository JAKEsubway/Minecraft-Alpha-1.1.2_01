package mcport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;

import android.content.res.AssetManager;

/**
 * Copies the sound files packed into the APK (assets/mcres/, listed in
 * assets/mcres/index.txt as "size path") to .minecraft/resources/, where the
 * game picks them up. Only missing or changed files are copied.
 */
final class SoundInstaller {
    interface Progress { void update(int done, int total); }

    private SoundInstaller() {}

    static int install(AssetManager assets, File resourcesDir, Progress progress) {
        ArrayList<String> paths = new ArrayList<String>();
        ArrayList<Long> sizes = new ArrayList<Long>();
        try {
            InputStream in = assets.open("mcres/index.txt");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                int sp = line.indexOf(' ');
                if (sp <= 0) continue;
                sizes.add(Long.parseLong(line.substring(0, sp)));
                paths.add(line.substring(sp + 1));
            }
            r.close();
        } catch (Exception e) {
            return 0; // this APK was built without sounds
        }
        int copied = 0;
        byte[] buf = new byte[64 * 1024];
        for (int i = 0; i < paths.size(); i++) {
            String p = paths.get(i);
            File dst = new File(resourcesDir, p);
            if (!dst.exists() || dst.length() != sizes.get(i)) {
                try {
                    dst.getParentFile().mkdirs();
                    File tmp = new File(dst.getPath() + ".part");
                    InputStream in = assets.open("mcres/" + p);
                    OutputStream out = new FileOutputStream(tmp);
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.close();
                    in.close();
                    if (!tmp.renameTo(dst)) tmp.delete();
                    copied++;
                } catch (Exception ignored) {}
            }
            if (progress != null && (i % 8 == 0 || i == paths.size() - 1)) progress.update(i + 1, paths.size());
        }
        return copied;
    }
}
