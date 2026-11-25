package mcshim.lwjgl.opengl;

public final class GLContext {
    private static final ContextCapabilities CAPS = new ContextCapabilities();
    private GLContext() {}
    public static ContextCapabilities getCapabilities() { return CAPS; }
}
