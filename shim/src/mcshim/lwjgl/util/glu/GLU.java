package mcshim.lwjgl.util.glu;

public final class GLU {
    private GLU() {}
    public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
        mcshim.lwjgl.opengl.GLUBridge.perspective(fovy, aspect, zNear, zFar);
    }
    public static String gluErrorString(int code) { return "GL error 0x" + Integer.toHexString(code); }
}
