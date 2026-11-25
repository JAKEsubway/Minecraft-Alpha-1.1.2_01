package mcport;

import java.nio.Buffer;

import android.opengl.GLES10;
import android.opengl.GLES11;

import mcshim.gl.GLBackend;

/** GLBackend on Android's OpenGL ES 1.1. */
final class AndroidGL implements GLBackend {
    private final int[] one = new int[1];

    public void glEnable(int c) { GLES10.glEnable(c); }
    public void glDisable(int c) { GLES10.glDisable(c); }
    public void glEnableClientState(int a) { GLES10.glEnableClientState(a); }
    public void glDisableClientState(int a) { GLES10.glDisableClientState(a); }
    public void glBindTexture(int t, int id) { GLES10.glBindTexture(t, id); }
    public void glTexParameteri(int t, int p, int v) { GLES11.glTexParameteri(t, p, v); }
    public void glTexImage2D(int t, int l, int i, int w, int h, int b, int f, int ty, Buffer px) { GLES10.glTexImage2D(t, l, i, w, h, b, f, ty, px); }
    public void glTexSubImage2D(int t, int l, int x, int y, int w, int h, int f, int ty, Buffer px) { GLES10.glTexSubImage2D(t, l, x, y, w, h, f, ty, px); }
    public void glGenTextures(int n, int[] out) { GLES10.glGenTextures(n, out, 0); }
    public void glDeleteTextures(int n, int[] ids) { GLES10.glDeleteTextures(n, ids, 0); }
    public void glBlendFunc(int s, int d) { GLES10.glBlendFunc(s, d); }
    public void glAlphaFunc(int f, float r) { GLES10.glAlphaFunc(f, r); }
    public void glClear(int m) { GLES10.glClear(m); }
    public void glClearColor(float r, float g, float b, float a) { GLES10.glClearColor(r, g, b, a); }
    public void glClearDepthf(float d) { GLES10.glClearDepthf(d); }
    public void glColor4f(float r, float g, float b, float a) { GLES10.glColor4f(r, g, b, a); }
    public void glNormal3f(float x, float y, float z) { GLES10.glNormal3f(x, y, z); }
    public void glColorMask(boolean r, boolean g, boolean b, boolean a) { GLES10.glColorMask(r, g, b, a); }
    public void glCullFace(int m) { GLES10.glCullFace(m); }
    public void glDepthFunc(int f) { GLES10.glDepthFunc(f); }
    public void glDepthMask(boolean f) { GLES10.glDepthMask(f); }
    public void glFogf(int p, float v) { GLES10.glFogf(p, v); }
    public void glFogfv(int p, float[] v) { GLES10.glFogfv(p, v, 0); }
    public void glLightfv(int l, int p, float[] v) { GLES10.glLightfv(l, p, v, 0); }
    public void glLightModelfv(int p, float[] v) { GLES10.glLightModelfv(p, v, 0); }
    public void glLineWidth(float w) { GLES10.glLineWidth(w); }
    public void glPolygonOffset(float f, float u) { GLES10.glPolygonOffset(f, u); }
    public void glShadeModel(int m) { GLES10.glShadeModel(m); }
    public void glViewport(int x, int y, int w, int h) { GLES10.glViewport(x, y, w, h); }
    public void glMatrixMode(int m) { GLES10.glMatrixMode(m); }
    public void glLoadMatrixf(float[] m) { GLES10.glLoadMatrixf(m, 0); }

    public void glVertexPointer(int size, int type, int stride, Buffer b) { GLES10.glVertexPointer(size, type, stride, b); }
    public void glTexCoordPointer(int size, int type, int stride, Buffer b) { GLES10.glTexCoordPointer(size, type, stride, b); }
    public void glColorPointer(int size, int type, int stride, Buffer b) { GLES10.glColorPointer(size, type, stride, b); }
    public void glNormalPointer(int type, int stride, Buffer b) { GLES10.glNormalPointer(type, stride, b); }
    public void glVertexPointer(int size, int type, int stride, int off) { GLES11.glVertexPointer(size, type, stride, off); }
    public void glTexCoordPointer(int size, int type, int stride, int off) { GLES11.glTexCoordPointer(size, type, stride, off); }
    public void glColorPointer(int size, int type, int stride, int off) { GLES11.glColorPointer(size, type, stride, off); }
    public void glNormalPointer(int type, int stride, int off) { GLES11.glNormalPointer(type, stride, off); }

    public int glGenBuffer() { GLES11.glGenBuffers(1, one, 0); return one[0]; }
    public void glDeleteBuffer(int id) { one[0] = id; GLES11.glDeleteBuffers(1, one, 0); }
    public void glBindBuffer(int t, int id) { GLES11.glBindBuffer(t, id); }
    public void glBufferData(int t, int size, Buffer d, int usage) { d.position(0); GLES11.glBufferData(t, size, d, usage); }

    public void glDrawArrays(int m, int f, int c) { GLES10.glDrawArrays(m, f, c); }
    public void glDrawElements(int m, int c, int t, Buffer idx) { GLES10.glDrawElements(m, c, t, idx); }
    public void glDrawElements(int m, int c, int t, int off) { GLES11.glDrawElements(m, c, t, off); }

    public String glGetString(int n) { return GLES10.glGetString(n); }
    public int glGetError() { return GLES10.glGetError(); }
    public void glReadPixels(int x, int y, int w, int h, int f, int t, Buffer out) { GLES10.glReadPixels(x, y, w, h, f, t, out); }
}
