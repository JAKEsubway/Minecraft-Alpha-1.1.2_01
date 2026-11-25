package mcdesk;

import java.nio.*;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import mcshim.gl.GLBackend;

/** GLBackend on desktop OpenGL via real LWJGL 2, restricted to the GLES 1.1 subset. */
public class DesktopGL implements GLBackend {
    private final FloatBuffer f16 = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer f4 = BufferUtils.createFloatBuffer(4);
    private final IntBuffer i1 = BufferUtils.createIntBuffer(1);

    private FloatBuffer f4(float[] v) { f4.clear(); f4.put(v, 0, 4); f4.flip(); return f4; }

    public void glEnable(int c) { GL11.glEnable(c); }
    public void glDisable(int c) { GL11.glDisable(c); }
    public void glEnableClientState(int a) { GL11.glEnableClientState(a); }
    public void glDisableClientState(int a) { GL11.glDisableClientState(a); }
    public void glBindTexture(int t, int id) { GL11.glBindTexture(t, id); }
    public void glTexParameteri(int t, int p, int v) { GL11.glTexParameteri(t, p, v); }
    public void glTexImage2D(int t, int l, int i, int w, int h, int b, int f, int ty, Buffer px) { GL11.glTexImage2D(t, l, i, w, h, b, f, ty, (ByteBuffer) px); }
    public void glTexSubImage2D(int t, int l, int x, int y, int w, int h, int f, int ty, Buffer px) { GL11.glTexSubImage2D(t, l, x, y, w, h, f, ty, (ByteBuffer) px); }
    public void glGenTextures(int n, int[] out) { IntBuffer b = BufferUtils.createIntBuffer(n); GL11.glGenTextures(b); b.get(out, 0, n); }
    public void glDeleteTextures(int n, int[] ids) { IntBuffer b = BufferUtils.createIntBuffer(n); b.put(ids, 0, n).flip(); GL11.glDeleteTextures(b); }
    public void glBlendFunc(int s, int d) { GL11.glBlendFunc(s, d); }
    public void glAlphaFunc(int f, float r) { GL11.glAlphaFunc(f, r); }
    public void glClear(int m) { GL11.glClear(m); }
    public void glClearColor(float r, float g, float b, float a) { GL11.glClearColor(r, g, b, a); }
    public void glClearDepthf(float d) { GL11.glClearDepth(d); }
    public void glColor4f(float r, float g, float b, float a) { GL11.glColor4f(r, g, b, a); }
    public void glNormal3f(float x, float y, float z) { GL11.glNormal3f(x, y, z); }
    public void glColorMask(boolean r, boolean g, boolean b, boolean a) { GL11.glColorMask(r, g, b, a); }
    public void glCullFace(int m) { GL11.glCullFace(m); }
    public void glDepthFunc(int f) { GL11.glDepthFunc(f); }
    public void glDepthMask(boolean f) { GL11.glDepthMask(f); }
    public void glFogf(int p, float v) { GL11.glFogf(p, v); }
    public void glFogfv(int p, float[] v) { GL11.glFog(p, f4(v)); }
    public void glLightfv(int l, int p, float[] v) { GL11.glLight(l, p, f4(v)); }
    public void glLightModelfv(int p, float[] v) { GL11.glLightModel(p, f4(v)); }
    public void glLineWidth(float w) { GL11.glLineWidth(w); }
    public void glPolygonOffset(float f, float u) { GL11.glPolygonOffset(f, u); }
    public void glShadeModel(int m) { GL11.glShadeModel(m); }
    public void glViewport(int x, int y, int w, int h) { GL11.glViewport(x, y, w, h); }
    public void glMatrixMode(int m) { GL11.glMatrixMode(m); }
    public void glLoadMatrixf(float[] m) { f16.clear(); f16.put(m).flip(); GL11.glLoadMatrix(f16); }

    public void glVertexPointer(int size, int type, int stride, Buffer b) {
        if (b instanceof FloatBuffer) GL11.glVertexPointer(size, stride, (FloatBuffer) b);
        else GL11.glVertexPointer(size, type, stride, (ByteBuffer) b);
    }
    public void glTexCoordPointer(int size, int type, int stride, Buffer b) {
        if (b instanceof FloatBuffer) GL11.glTexCoordPointer(size, stride, (FloatBuffer) b);
        else GL11.glTexCoordPointer(size, type, stride, (ByteBuffer) b);
    }
    public void glColorPointer(int size, int type, int stride, Buffer b) {
        if (b instanceof FloatBuffer) GL11.glColorPointer(size, stride, (FloatBuffer) b);
        else GL11.glColorPointer(size, type, stride, (ByteBuffer) b);
    }
    public void glNormalPointer(int type, int stride, Buffer b) {
        if (b instanceof FloatBuffer) GL11.glNormalPointer(stride, (FloatBuffer) b);
        else GL11.glNormalPointer(type, stride, (ByteBuffer) b);
    }
    public void glVertexPointer(int size, int type, int stride, int off) { GL11.glVertexPointer(size, type, stride, off); }
    public void glTexCoordPointer(int size, int type, int stride, int off) { GL11.glTexCoordPointer(size, type, stride, off); }
    public void glColorPointer(int size, int type, int stride, int off) { GL11.glColorPointer(size, type, stride, off); }
    public void glNormalPointer(int type, int stride, int off) { GL11.glNormalPointer(type, stride, off); }

    public int glGenBuffer() { return GL15.glGenBuffers(); }
    public void glDeleteBuffer(int id) { GL15.glDeleteBuffers(id); }
    public void glBindBuffer(int t, int id) { GL15.glBindBuffer(t, id); }
    public void glBufferData(int t, int size, Buffer d, int usage) {
        if (d instanceof ShortBuffer) { ShortBuffer s = ((ShortBuffer) d).duplicate(); s.position(0).limit(size / 2); GL15.glBufferData(t, s, usage); }
        else { ByteBuffer b = ((ByteBuffer) d).duplicate(); b.position(0).limit(size); GL15.glBufferData(t, b, usage); }
    }

    public void glDrawArrays(int m, int f, int c) { GL11.glDrawArrays(m, f, c); }
    public void glDrawElements(int m, int c, int t, Buffer idx) {
        ShortBuffer s = ((ShortBuffer) idx).duplicate(); s.position(0).limit(c);
        GL11.glDrawElements(m, s);
    }
    public void glDrawElements(int m, int c, int t, int off) { GL11.glDrawElements(m, c, t, off); }

    public String glGetString(int n) { return GL11.glGetString(n); }
    public int glGetError() { return GL11.glGetError(); }
    public void glReadPixels(int x, int y, int w, int h, int f, int t, Buffer out) { GL11.glReadPixels(x, y, w, h, f, t, (ByteBuffer) out); }
}
