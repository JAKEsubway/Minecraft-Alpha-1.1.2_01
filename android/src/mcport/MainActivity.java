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
        if (gameStarted) return;
        gameStarted = true;
        File home = getExternalFilesDir(null);
        if (home == null) home = getFilesDir();
        System.setProperty("user.home", home.getAbsolutePath());
        Platform.INSTANCE = platform;
        final File resources = new File(home, ".minecraft/resources");
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
                try {
                    Class.forName("net.minecraft.client.Minecraft")
                            .getMethod("main", String[].class)
                            .invoke(null, (Object) new String[] {"Player", "-"});
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
        startGame();
    }

    public void surfaceDestroyed(SurfaceHolder h) {
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
