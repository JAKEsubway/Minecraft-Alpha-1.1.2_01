package mcshim.lwjgl.opengl;

/** Lets GLU reach the package-private matrix code in GL11. */
public final class GLUBridge {
    private GLUBridge() {}
    public static void perspective(float fovy, float aspect, float n, float f) { GL11.perspective(fovy, aspect, n, f); }
}
