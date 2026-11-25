package mcport;

import java.io.IOException;
import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Log;
import android.view.Surface;

import mcshim.Platform;
import mcshim.awt.image.BufferedImage;
import mcshim.gl.GLBackend;

/**
 * EGL + GLES 1.1 host. The game thread owns the context; the UI thread only
 * hands over / takes back the window Surface. While there is no Surface the
 * game thread parks inside swapBuffers(), which pauses the game.
 */
final class AndroidPlatform extends Platform {
    private static final String TAG = "MCPort";

    private volatile MainActivity act;
    private final AndroidGL gl = new AndroidGL();
    private final Object lock = new Object();

    private Surface surface;       // what the UI thread offers
    private Surface bound;         // what the EGL window surface was made from
    private volatile int sw = 854, sh = 480;
    private volatile boolean resumed = true;

    private EGLDisplay dpy;
    private EGLContext ctx;
    private EGLConfig cfg;
    private EGLSurface win = EGL14.EGL_NO_SURFACE, pbuf = EGL14.EGL_NO_SURFACE;

    AndroidPlatform(MainActivity act) { this.act = act; }

    void setActivity(MainActivity a) { act = a; }

    // ---------------- UI thread side ----------------
    void setSurface(Surface s, int w, int h) {
        synchronized (lock) {
            surface = s;
            sw = w;
            sh = h;
            lock.notifyAll();
        }
    }

    /** Take the surface away; waits briefly until the game thread has let go of it. */
    void clearSurface() {
        synchronized (lock) {
            surface = null;
            lock.notifyAll();
            long end = System.currentTimeMillis() + 1500;
            while (bound != null && System.currentTimeMillis() < end) {
                try { lock.wait(100); } catch (InterruptedException e) { break; }
            }
        }
    }

    void setResumed(boolean r) { resumed = r; }

    // ---------------- game thread side ----------------
    public GLBackend gl() { return gl; }

    public void createDisplay() throws Exception {
        synchronized (lock) {
            while (surface == null) lock.wait();
        }
        dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] ver = new int[2];
        if (!EGL14.eglInitialize(dpy, ver, 0, ver, 1)) throw new RuntimeException("eglInitialize failed");
        cfg = chooseConfig(24);
        if (cfg == null) cfg = chooseConfig(16);
        if (cfg == null) throw new RuntimeException("No GLES 1.x EGL config");
        ctx = EGL14.eglCreateContext(dpy, cfg, EGL14.EGL_NO_CONTEXT,
                new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 1, EGL14.EGL_NONE}, 0);
        if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT) throw new RuntimeException("eglCreateContext failed");
        pbuf = EGL14.eglCreatePbufferSurface(dpy, cfg, new int[] {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
        synchronized (lock) { bindWindow(); }
        log("GL: " + gl.glGetString(0x1F00) + " / " + gl.glGetString(0x1F01) + " / " + gl.glGetString(0x1F02));
    }

    private EGLConfig chooseConfig(int depth) {
        int[] attrs = {
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, depth,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        };
        EGLConfig[] out = new EGLConfig[1];
        int[] n = new int[1];
        if (!EGL14.eglChooseConfig(dpy, attrs, 0, out, 0, 1, n, 0) || n[0] == 0) return null;
        return out[0];
    }

    /** Called with lock held and surface != null. */
    private void bindWindow() {
        win = EGL14.eglCreateWindowSurface(dpy, cfg, surface, new int[] {EGL14.EGL_NONE}, 0);
        if (win == null || win == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreateWindowSurface failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
            win = EGL14.EGL_NO_SURFACE;
            EGL14.eglMakeCurrent(dpy, pbuf, pbuf, ctx);
            return;
        }
        bound = surface;
        EGL14.eglMakeCurrent(dpy, win, win, ctx);
        EGL14.eglSwapInterval(dpy, 1);
    }

    private void unbindWindow() {
        EGL14.eglMakeCurrent(dpy, pbuf, pbuf, ctx); // keep the context (and all textures) alive
        if (win != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, win);
        win = EGL14.EGL_NO_SURFACE;
        bound = null;
        lock.notifyAll();
    }

    public void swapBuffers() {
        if (win != EGL14.EGL_NO_SURFACE) EGL14.eglSwapBuffers(dpy, win);
        synchronized (lock) {
            if (bound != null && surface != bound) unbindWindow();
            while (surface == null) {
                try { lock.wait(); } catch (InterruptedException e) { return; }
            }
            if (bound == null) bindWindow();
        }
    }

    public void destroyDisplay() {
        synchronized (lock) { unbindWindow(); }
    }

    public int width() { return sw; }

    public int height() { return sh; }

    public boolean isActive() { return resumed; }

    public BufferedImage decodeImage(InputStream in) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        o.inScaled = false;
        o.inPremultiplied = false;
        Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
        if (bmp == null) throw new IOException("Could not decode image");
        int w = bmp.getWidth(), h = bmp.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        bmp.getPixels(img.pixels(), 0, w, 0, 0, w, h);
        bmp.recycle();
        return img;
    }

    public void crash(String report) {
        Log.e(TAG, report);
        act.showCrash(report);
    }

    public void mouseGrabbed(boolean g) { act.onGrabChanged(g); }

    public mcshim.audio.AudioOut openAudio(int rate) {
        try {
            int min = android.media.AudioTrack.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_OUT_STEREO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT);
            final android.media.AudioTrack t = new android.media.AudioTrack(android.media.AudioManager.STREAM_MUSIC, rate,
                    android.media.AudioFormat.CHANNEL_OUT_STEREO, android.media.AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min * 2, 4096 * 4), android.media.AudioTrack.MODE_STREAM);
            if (t.getState() != android.media.AudioTrack.STATE_INITIALIZED) { t.release(); return null; }
            t.play();
            return new mcshim.audio.AudioOut() {
                public void write(short[] pcm, int frames) { t.write(pcm, 0, frames * 2); }
                public void pause() { t.pause(); }
                public void resume() { t.play(); }
                public void close() { try { t.stop(); } catch (Exception ignored) {} t.release(); }
            };
        } catch (Exception e) {
            Log.w(TAG, "No audio: " + e);
            return null;
        }
    }

    public void log(String s) { Log.i(TAG, s); }
}
