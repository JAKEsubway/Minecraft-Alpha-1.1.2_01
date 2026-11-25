package mcshim;

import java.io.IOException;
import java.io.InputStream;

import mcshim.awt.image.BufferedImage;
import mcshim.gl.GLBackend;

/**
 * Everything the shim layer needs from the host OS. One implementation for
 * Android (EGL + GLES 1.1 + Bitmap) and one for desktop testing (LWJGL 2).
 */
public abstract class Platform {
    public static Platform INSTANCE;

    public static Platform get() {
        if (INSTANCE == null) throw new IllegalStateException("Platform not initialised");
        return INSTANCE;
    }

    /** GL entry points of the host. Only valid on the thread that called createDisplay(). */
    public abstract GLBackend gl();

    /** Create a GL context and make it current on the calling (game) thread. */
    public abstract void createDisplay() throws Exception;

    /** Present the frame. May block while the app is in the background. */
    public abstract void swapBuffers();

    public void destroyDisplay() {}

    public abstract int width();

    public abstract int height();

    public boolean isActive() { return true; }

    public boolean closeRequested() { return false; }

    public abstract BufferedImage decodeImage(InputStream in) throws IOException;

    public String getClipboard() { return ""; }

    /** Called with the full crash-report text when the game shows its crash panel. */
    public void crash(String report) { System.err.println(report); }

    public void mouseGrabbed(boolean grabbed) {}

    /** Open a 16-bit stereo output at the given rate, or null for no sound. */
    public mcshim.audio.AudioOut openAudio(int rate) { return null; }

    public void log(String s) { System.out.println("[mcshim] " + s); }
}
