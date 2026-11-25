package mcshim.lwjgl;

public final class Sys {
    private Sys() {}
    public static String getVersion() { return "2.4.2 (mcshim GLES)"; }
    public static long getTime() { return System.nanoTime() / 1000000L; }
    public static long getTimerResolution() { return 1000L; }
}
