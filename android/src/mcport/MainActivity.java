package mcport;

import java.io.File;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import mcshim.Platform;
import mcshim.lwjgl.input.Keyboard;
import mcshim.lwjgl.input.Mouse;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private static boolean gameStarted;
    private static AndroidPlatform platform;
    private static Games.Game chosen;      // the version being played (one per process)
    private boolean surfaceReady;
    private View picker;

    private FrameLayout root;
    private TouchOverlay overlay;
    private TextInput textInput;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            try { // layoutInDisplayCutoutMode = SHORT_EDGES (API 28; we compile against 23)
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                WindowManager.LayoutParams.class.getField("layoutInDisplayCutoutMode").setInt(lp, 1);
                getWindow().setAttributes(lp);
            } catch (Exception ignored) {}
        }

        root = new FrameLayout(this);
        SurfaceView sv = new SurfaceView(this);
        sv.getHolder().addCallback(this);
        overlay = new TouchOverlay(this);
        textInput = new TextInput(this);
        root.addView(textInput, new FrameLayout.LayoutParams(1, 1));
        root.addView(sv);
        root.addView(overlay);
        mcshim.Hooks.setFrameListener(new mcshim.Hooks.FrameListener() {
            int lastKind = -1;
            String lastClose = "";
            public void onFrame() {
                mcshim.ui.GuiTouch.dedupeButtons();
                mcshim.ui.GuiTouch.nudgeFirstMusic();
                final int[] close = mcshim.ui.GuiTouch.closeButton(overlay.getWidth(), overlay.getHeight());
                String ck = close == null ? "" : close[0] + "," + close[1] + "," + close[2] + "," + close[3];
                if (!ck.equals(lastClose)) {
                    lastClose = ck;
                    runOnUiThread(new Runnable() {
                        public void run() { overlay.setCloseButton(close); }
                    });
                }
                final int kind = mcshim.ui.GuiTouch.kind();
                if (kind == lastKind) return;
                lastKind = kind;
                runOnUiThread(new Runnable() {
                    public void run() {
                        overlay.setScreenKind(kind);
                        textInput.setShown(kind == mcshim.ui.GuiTouch.TEXT);   // soft keyboard for chat / signs / server address
                        if (kind != mcshim.ui.GuiTouch.TEXT) hideSystemUi();
                    }
                });
            }
        });
        setContentView(root);
        hideSystemUi();

        if (platform == null) platform = new AndroidPlatform(this);
        else platform.setActivity(this);
        overlay.setGrabbed(Mouse.isGrabbed());

        if (chosen == null) {
            java.util.List<Games.Game> games = Games.list(this);
            if (games.size() == 1) chosen = games.get(0);
            else if (games.isEmpty()) showCrash("This APK contains no game.");
            else showPicker(games);
        }
    }

    /** Version picker, shown at start when the APK holds more than one version. */
    private void showPicker(final java.util.List<Games.Game> games) {
        final android.content.SharedPreferences prefs = getSharedPreferences("mcport", MODE_PRIVATE);
        String last = prefs.getString("version", null);
        float dp = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout col = new android.widget.LinearLayout(this);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setGravity(android.view.Gravity.CENTER);
        col.setBackgroundColor(0xFF2B2118);
        TextView title = new TextView(this);
        title.setText("選擇版本  Select version");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        title.setShadowLayer(0, 2 * dp, 2 * dp, 0xFF3F3F3F);
        col.addView(title, new android.widget.LinearLayout.LayoutParams(-2, -2));
        for (final Games.Game g : games) {
            TextView b = new TextView(this);
            boolean wasLast = g.id.equals(last);
            b.setText(g.label + (wasLast ? "   （上次）" : ""));
            b.setTextColor(wasLast ? 0xFFFFFFA0 : Color.WHITE);
            b.setTextSize(17);
            b.setTypeface(Typeface.MONOSPACE);
            b.setShadowLayer(0, 2 * dp, 2 * dp, 0xFF3F3F3F);
            b.setGravity(android.view.Gravity.CENTER);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFF6F6F6F);
            bg.setStroke((int) (2 * dp), Color.BLACK);
            b.setBackgroundDrawable(bg);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    prefs.edit().putString("version", g.id).apply();
                    chosen = g;
                    root.removeView(picker);
                    picker = null;
                    hideSystemUi();
                    if (surfaceReady) startGame();
                }
            });
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams((int) (300 * dp), (int) (48 * dp));
            lp.topMargin = (int) (12 * dp);
            col.addView(b, lp);
        }
        TextView hint = new TextView(this);
        hint.setText("要換版本：關閉 App 再開啟");
        hint.setTextColor(0xFFA0A0A0);
        hint.setTextSize(13);
        android.widget.LinearLayout.LayoutParams hl = new android.widget.LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = (int) (18 * dp);
        col.addView(hint, hl);
        col.setClickable(true); // keep touches away from the game underneath
        picker = col;
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void startGame() {
        if (gameStarted || chosen == null) return;
        gameStarted = true;
        final Games.Game game = chosen;
        File root = getExternalFilesDir(null);
        if (root == null) root = getFilesDir();
        final File home = Games.home(root, game);
        home.mkdirs();
        System.setProperty("user.home", home.getAbsolutePath());
        Platform.INSTANCE = platform;
        final File resources = new File(root, ".minecraft/resources");
        final File ownResources = new File(home, ".minecraft/resources");
        new Thread(new Runnable() {
            public void run() {
                // copy sounds packed into this APK (if any) to where the game looks for them
                SoundInstaller.install(getAssets(), resources, new SoundInstaller.Progress() {
                    public void update(final int done, final int total) {
                        runOnUiThread(new Runnable() {
                            public void run() { showStatus(done < total ? "Installing sounds... " + (done * 100 / total) + "%" : null); }
                        });
                    }
                });
                if (!ownResources.equals(resources)) Games.linkResources(resources, ownResources);
                try {
                    runOnUiThread(new Runnable() { public void run() { showStatus("Loading " + game.label + "..."); } });
                    ClassLoader cl = Games.load(MainActivity.this, game);
                    runOnUiThread(new Runnable() { public void run() { showStatus(null); } });
                    mcshim.Launch.start(cl, "Player");
                } catch (Throwable t) {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    t.printStackTrace(new java.io.PrintWriter(sw));
                    showCrash("Could not start the game:\n\n" + sw);
                }
            }
        }, "launcher").start();
    }

    // ---- SurfaceHolder.Callback ----
    public void surfaceCreated(SurfaceHolder h) {}

    public void surfaceChanged(SurfaceHolder h, int format, int w, int hgt) {
        platform.setSurface(h.getSurface(), w, hgt);
        surfaceReady = true;
        startGame();
    }

    public void surfaceDestroyed(SurfaceHolder h) {
        surfaceReady = false;
        platform.clearSurface();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (platform != null) platform.setResumed(true);
    }

    @Override
    protected void onPause() {
        if (platform != null) platform.setResumed(false);
        super.onPause();
    }

    private TextView status;

    /** Small centred status line over the game surface (null hides it). */
    void showStatus(String text) {
        if (text == null) {
            if (status != null) { root.removeView(status); status = null; }
            return;
        }
        if (status == null) {
            status = new TextView(this);
            status.setTextColor(Color.WHITE);
            status.setTextSize(16);
            status.setGravity(android.view.Gravity.CENTER);
            root.addView(status, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        status.setText(text);
    }

    // ---- callbacks from the game thread ----
    void onGrabChanged(final boolean g) {
        runOnUiThread(new Runnable() {
            public void run() { overlay.setGrabbed(g); }
        });
    }

    void showCrash(final String text) {
        runOnUiThread(new Runnable() {
            public void run() {
                TextView tv = new TextView(MainActivity.this);
                tv.setText(text);
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(Typeface.MONOSPACE);
                tv.setTextSize(11);
                tv.setTextIsSelectable(true);
                int pad = (int) (16 * getResources().getDisplayMetrics().density);
                tv.setPadding(pad, pad, pad, pad);
                ScrollView sv = new ScrollView(MainActivity.this);
                sv.setBackgroundColor(0xEE202020);
                sv.addView(tv);
                root.addView(sv);
            }
        });
    }

    /**
     * Indev's "Load file..." / "Save file...": a list of the levels in dir, or a
     * name to save under. Called from a game worker thread; blocks until answered.
     */
    String chooseFile(final boolean save, final String dir, final java.io.FilenameFilter filter) {
        final String[] result = {null};
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        final File d = new File(dir == null ? System.getProperty("user.home") : dir);
        runOnUiThread(new Runnable() {
            public void run() {
                android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(MainActivity.this);
                final File[] files = d.listFiles(filter);
                if (files != null) java.util.Arrays.sort(files, new java.util.Comparator<File>() {
                    public int compare(File x, File y) { return Long.compare(y.lastModified(), x.lastModified()); }
                });
                if (save) {
                    final android.widget.EditText name = new android.widget.EditText(MainActivity.this);
                    name.setSingleLine();
                    name.setText("level");
                    name.selectAll();
                    b.setTitle("儲存世界 Save level").setMessage("檔名 File name:").setView(name)
                     .setPositiveButton("儲存 Save", new android.content.DialogInterface.OnClickListener() {
                         public void onClick(android.content.DialogInterface di, int w) {
                             String n = name.getText().toString().trim().replaceAll("[\\\\/:*?\"<>|]", "_");
                             if (n.length() > 0) result[0] = new File(d, n).getPath();
                         }
                     });
                } else if (files == null || files.length == 0) {
                    b.setTitle("載入世界 Load level").setMessage("還沒有存檔。\nNo saved levels yet.");
                } else {
                    String[] names = new String[files.length];
                    for (int i = 0; i < files.length; i++) names[i] = files[i].getName();
                    b.setTitle("載入世界 Load level").setItems(names, new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface di, int w) { result[0] = files[w].getPath(); }
                    });
                }
                b.setNegativeButton("取消 Cancel", null);
                android.app.AlertDialog dlg = b.create();
                dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    public void onDismiss(android.content.DialogInterface di) { hideSystemUi(); done.countDown(); }
                });
                dlg.show();
            }
        });
        try { done.await(); } catch (InterruptedException e) { return null; }
        return result[0];
    }

    // ---- hardware keys (back button, Bluetooth / USB keyboards) ----
    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getRepeatCount() == 0) Keyboard.tap(1, (char) 27);
            return true;
        }
        int k = KeyMap.toLwjgl(code);
        if (k == 0) return super.onKeyDown(code, e);
        if (e.getRepeatCount() == 0 || k == 14) Keyboard.inject(k, true, (char) e.getUnicodeChar());
        return true;
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) return true;
        int k = KeyMap.toLwjgl(code);
        if (k == 0) return super.onKeyUp(code, e);
        Keyboard.inject(k, false, (char) 0);
        return true;
    }
}
