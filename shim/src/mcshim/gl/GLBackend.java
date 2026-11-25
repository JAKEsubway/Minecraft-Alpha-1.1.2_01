package mcshim.gl;

import java.nio.Buffer;

/** The OpenGL ES 1.1 subset the emulation layer talks to. */
public interface GLBackend {
    void glEnable(int cap);
    void glDisable(int cap);
    void glEnableClientState(int array);
    void glDisableClientState(int array);

    void glBindTexture(int target, int tex);
    void glTexParameteri(int target, int pname, int param);
    void glTexImage2D(int target, int level, int internalFormat, int w, int h, int border, int format, int type, Buffer pixels);
    void glTexSubImage2D(int target, int level, int x, int y, int w, int h, int format, int type, Buffer pixels);
    void glGenTextures(int n, int[] out);
    void glDeleteTextures(int n, int[] ids);

    void glBlendFunc(int s, int d);
    void glAlphaFunc(int func, float ref);
    void glClear(int mask);
    void glClearColor(float r, float g, float b, float a);
    void glClearDepthf(float d);
    void glColor4f(float r, float g, float b, float a);
    void glNormal3f(float x, float y, float z);
    void glColorMask(boolean r, boolean g, boolean b, boolean a);
    void glCullFace(int mode);
    void glDepthFunc(int func);
    void glDepthMask(boolean flag);
    void glFogf(int pname, float param);
    void glFogfv(int pname, float[] params);
    void glLightfv(int light, int pname, float[] params);
    void glLightModelfv(int pname, float[] params);
    void glLineWidth(float w);
    void glPolygonOffset(float factor, float units);
    void glShadeModel(int mode);
    void glViewport(int x, int y, int w, int h);

    void glMatrixMode(int mode);
    void glLoadMatrixf(float[] m);

    /** Client-memory pointers; the buffer's position marks the first element. */
    void glVertexPointer(int size, int type, int stride, Buffer b);
    void glTexCoordPointer(int size, int type, int stride, Buffer b);
    void glColorPointer(int size, int type, int stride, Buffer b);
    void glNormalPointer(int type, int stride, Buffer b);

    /** Pointers into the currently bound GL_ARRAY_BUFFER. */
    void glVertexPointer(int size, int type, int stride, int offset);
    void glTexCoordPointer(int size, int type, int stride, int offset);
    void glColorPointer(int size, int type, int stride, int offset);
    void glNormalPointer(int type, int stride, int offset);

    int glGenBuffer();
    void glDeleteBuffer(int id);
    void glBindBuffer(int target, int id);
    void glBufferData(int target, int size, Buffer data, int usage);

    void glDrawArrays(int mode, int first, int count);
    void glDrawElements(int mode, int count, int type, Buffer indices);
    void glDrawElements(int mode, int count, int type, int offset);

    String glGetString(int name);
    int glGetError();
    void glReadPixels(int x, int y, int w, int h, int format, int type, Buffer out);
}
