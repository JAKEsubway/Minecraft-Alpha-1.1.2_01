package mcshim.lwjgl.opengl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import mcshim.Platform;
import mcshim.gl.GLBackend;

/**
 * Desktop OpenGL 1.1 (as used by Minecraft Alpha) emulated on OpenGL ES 1.1.
 *
 * Handled here: matrix stacks (kept in Java so glGetFloat works everywhere),
 * display lists (recorded commands + vertex snapshots, big ones in VBOs),
 * GL_QUADS / GL_QUAD_STRIP / GL_POLYGON, GL_CLAMP, double-precision entry points.
 */
public final class GL11 {
    private GL11() {}

    // ---- a few constants (the game inlines them, kept for our own use) ----
    public static final int GL_MODELVIEW = 5888, GL_PROJECTION = 5889, GL_TEXTURE = 5890;
    public static final int GL_MODELVIEW_MATRIX = 2982, GL_PROJECTION_MATRIX = 2983, GL_TEXTURE_MATRIX = 2984;
    public static final int GL_VERTEX_ARRAY = 32884, GL_NORMAL_ARRAY = 32885, GL_COLOR_ARRAY = 32886, GL_TEXTURE_COORD_ARRAY = 32888;
    public static final int GL_BYTE = 5120, GL_UNSIGNED_BYTE = 5121, GL_SHORT = 5122, GL_UNSIGNED_SHORT = 5123, GL_INT = 5124, GL_FLOAT = 5126;
    public static final int GL_LINES = 1, GL_LINE_LOOP = 2, GL_LINE_STRIP = 3, GL_TRIANGLES = 4, GL_TRIANGLE_STRIP = 5,
            GL_TRIANGLE_FAN = 6, GL_QUADS = 7, GL_QUAD_STRIP = 8, GL_POLYGON = 9;
    public static final int GL_COMPILE = 4864, GL_COMPILE_AND_EXECUTE = 4865;
    public static final int GL_TEXTURE_2D = 3553, GL_RGBA = 6408, GL_RGB = 6407;
    static final int GL_ARRAY_BUFFER = 34962, GL_ELEMENT_ARRAY_BUFFER = 34963, GL_STATIC_DRAW = 35044;
    static final int GL_POSITION = 4611, GL_SPOT_DIRECTION = 4612;
    static final int GL_CLAMP = 10496, GL_CLAMP_TO_EDGE = 33071;
    static final int GL_FOG_DISTANCE_MODE_NV = 34139;

    static GLBackend gl;

    // ======================================================================
    // init
    // ======================================================================
    private static final int MAX_QUAD_VERTS = 65536;
    private static ShortBuffer quadIdx;
    private static int quadEbo;

    /** Called by Display.create() once the context is current. */
    static void init() {
        gl = Platform.get().gl();
        if (quadIdx == null) {
            int quads = MAX_QUAD_VERTS / 4;
            ByteBuffer bb = ByteBuffer.allocateDirect(quads * 6 * 2).order(ByteOrder.nativeOrder());
            quadIdx = bb.asShortBuffer();
            for (int q = 0; q < quads; q++) {
                int v = q * 4;
                quadIdx.put((short) v).put((short) (v + 1)).put((short) (v + 2));
                quadIdx.put((short) v).put((short) (v + 2)).put((short) (v + 3));
            }
            quadIdx.flip();
        }
        quadEbo = gl.glGenBuffer();
        gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, quadEbo);
        gl.glBufferData(GL_ELEMENT_ARRAY_BUFFER, quadIdx.capacity() * 2, quadIdx, GL_STATIC_DRAW);
        gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        for (int i = 0; i < 3; i++) dirty[i] = true;
        for (int i = 0; i < 4; i++) glClientOn[i] = false;
    }

    // ======================================================================
    // matrices
    // ======================================================================
    private static final int STACK = 64;
    private static final float[][][] stacks = new float[3][STACK][16];
    private static final int[] depth = new int[3];
    private static final boolean[] dirty = {true, true, true};
    private static int curMat = 0; // 0 modelview, 1 projection, 2 texture
    private static final float[] tmp = new float[16];
    private static final float[] tmp2 = new float[16];

    static {
        for (int s = 0; s < 3; s++) identity(stacks[s][0]);
    }

    private static float[] top() { return stacks[curMat][depth[curMat]]; }

    private static void identity(float[] m) {
        for (int i = 0; i < 16; i++) m[i] = 0;
        m[0] = m[5] = m[10] = m[15] = 1;
    }

    /** m = m * b (column-major). */
    private static void mulRight(float[] m, float[] b) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                tmp2[c * 4 + r] = m[r] * b[c * 4] + m[4 + r] * b[c * 4 + 1] + m[8 + r] * b[c * 4 + 2] + m[12 + r] * b[c * 4 + 3];
            }
        }
        System.arraycopy(tmp2, 0, m, 0, 16);
    }

    private static void matChanged() { dirty[curMat] = true; }

    /** Upload any changed matrix before a draw or light-position call. */
    static void flushMatrices() {
        for (int i = 0; i < 3; i++) {
            if (dirty[i]) {
                gl.glMatrixMode(GL_MODELVIEW + i);
                gl.glLoadMatrixf(stacks[i][depth[i]]);
                dirty[i] = false;
            }
        }
    }

    public static void glMatrixMode(final int mode) {
        if (R() && rec(new Cmd() { void run() { glMatrixMode(mode); } })) return;
        int i = mode - GL_MODELVIEW;
        if (i >= 0 && i < 3) curMat = i;
    }

    public static void glLoadIdentity() {
        if (R() && rec(new Cmd() { void run() { glLoadIdentity(); } })) return;
        identity(top());
        matChanged();
    }

    public static void glPushMatrix() {
        if (R() && rec(new Cmd() { void run() { glPushMatrix(); } })) return;
        int d = depth[curMat];
        if (d + 1 >= STACK) return;
        System.arraycopy(stacks[curMat][d], 0, stacks[curMat][d + 1], 0, 16);
        depth[curMat] = d + 1;
    }

    public static void glPopMatrix() {
        if (R() && rec(new Cmd() { void run() { glPopMatrix(); } })) return;
        if (depth[curMat] > 0) {
            depth[curMat]--;
            matChanged();
        }
    }

    public static void glTranslatef(final float x, final float y, final float z) {
        if (R() && rec(new Cmd() { void run() { glTranslatef(x, y, z); } })) return;
        float[] m = top();
        m[12] += m[0] * x + m[4] * y + m[8] * z;
        m[13] += m[1] * x + m[5] * y + m[9] * z;
        m[14] += m[2] * x + m[6] * y + m[10] * z;
        m[15] += m[3] * x + m[7] * y + m[11] * z;
        matChanged();
    }

    public static void glScalef(final float x, final float y, final float z) {
        if (R() && rec(new Cmd() { void run() { glScalef(x, y, z); } })) return;
        float[] m = top();
        for (int i = 0; i < 4; i++) { m[i] *= x; m[4 + i] *= y; m[8 + i] *= z; }
        matChanged();
    }

    public static void glRotatef(final float angle, final float x, final float y, final float z) {
        if (R() && rec(new Cmd() { void run() { glRotatef(angle, x, y, z); } })) return;
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len == 0) return;
        float ax = x / len, ay = y / len, az = z / len;
        double rad = Math.toRadians(angle);
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad), t = 1 - c;
        float[] r = tmp;
        r[0] = t * ax * ax + c;      r[4] = t * ax * ay - s * az; r[8] = t * ax * az + s * ay;  r[12] = 0;
        r[1] = t * ax * ay + s * az; r[5] = t * ay * ay + c;      r[9] = t * ay * az - s * ax;  r[13] = 0;
        r[2] = t * ax * az - s * ay; r[6] = t * ay * az + s * ax; r[10] = t * az * az + c;      r[14] = 0;
        r[3] = 0; r[7] = 0; r[11] = 0; r[15] = 1;
        mulRight(top(), r);
        matChanged();
    }

    public static void glOrtho(final double l, final double r, final double b, final double t, final double n, final double f) {
        if (R() && rec(new Cmd() { void run() { glOrtho(l, r, b, t, n, f); } })) return;
        float[] m = tmp;
        identity(m);
        m[0] = (float) (2 / (r - l));
        m[5] = (float) (2 / (t - b));
        m[10] = (float) (-2 / (f - n));
        m[12] = (float) (-(r + l) / (r - l));
        m[13] = (float) (-(t + b) / (t - b));
        m[14] = (float) (-(f + n) / (f - n));
        mulRight(top(), m);
        matChanged();
    }

    static void perspective(float fovy, float aspect, float zNear, float zFar) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovy) / 2.0));
        float[] m = tmp;
        for (int i = 0; i < 16; i++) m[i] = 0;
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (zFar + zNear) / (zNear - zFar);
        m[11] = -1;
        m[14] = 2 * zFar * zNear / (zNear - zFar);
        mulRight(top(), m);
        matChanged();
    }

    public static void glGetFloat(int pname, FloatBuffer out) {
        int i = pname - GL_MODELVIEW_MATRIX;
        int p = out.position();
        if (i >= 0 && i < 3) {
            float[] m = stacks[i][depth[i]];
            for (int k = 0; k < 16; k++) out.put(p + k, m[k]);
        }
    }

    // ======================================================================
    // display lists
    // ======================================================================
    abstract static class Cmd {
        abstract void run();
        void free() {}
    }

    static final class DList {
        final ArrayList<Cmd> cmds = new ArrayList<Cmd>();
        void run() { for (int i = 0, n = cmds.size(); i < n; i++) cmds.get(i).run(); }
        void free() { for (int i = 0, n = cmds.size(); i < n; i++) cmds.get(i).free(); cmds.clear(); }
    }

    private static final HashMap<Integer, DList> lists = new HashMap<Integer, DList>();
    private static int nextList = 1;
    private static DList compiling;
    private static int compilingId;
    private static boolean compileExec;
    private static int callDepth;

    private static boolean R() { return compiling != null && callDepth == 0; }

    /** Record cmd if a list is being compiled. Returns true if the caller must NOT execute now. */
    private static boolean rec(Cmd c) {
        if (compiling == null || callDepth > 0) return false;
        compiling.cmds.add(c);
        return !compileExec;
    }

    public static int glGenLists(int range) {
        int first = nextList;
        nextList += range;
        return first;
    }

    public static void glNewList(int list, int mode) {
        DList old = lists.remove(list);
        if (old != null) old.free();
        compiling = new DList();
        compilingId = list;
        compileExec = mode == GL_COMPILE_AND_EXECUTE;
    }

    public static void glEndList() {
        if (compiling != null) lists.put(compilingId, compiling);
        compiling = null;
    }

    public static void glDeleteLists(int list, int range) {
        for (int i = 0; i < range; i++) {
            DList d = lists.remove(list + i);
            if (d != null) d.free();
        }
    }

    public static void glCallList(final int list) {
        if (R() && rec(new Cmd() { void run() { glCallList(list); } })) return;
        DList d = lists.get(list);
        if (d == null) return;
        callDepth++;
        try { d.run(); } finally { callDepth--; }
    }

    public static void glCallLists(IntBuffer ids) {
        int p = ids.position(), lim = ids.limit();
        if (compiling != null && callDepth == 0) {
            final int[] copy = new int[lim - p];
            for (int i = 0; i < copy.length; i++) copy[i] = ids.get(p + i);
            if (R() && rec(new Cmd() { void run() { for (int id : copy) glCallList(id); } })) return;
        }
        for (int i = p; i < lim; i++) glCallList(ids.get(i));
    }

    // ======================================================================
    // client arrays
    // ======================================================================
    static final class Ptr {
        boolean enabled;
        int size, type, stride;
        Buffer buf;      // positioned duplicate, or null when VBO offset is used
        int vboOffset = -1;
    }

    private static final Ptr vtx = new Ptr(), tex = new Ptr(), col = new Ptr(), nrm = new Ptr();
    private static final Ptr[] ptrs = {vtx, tex, col, nrm};
    private static final int[] ARRAY_ENUM = {GL_VERTEX_ARRAY, GL_TEXTURE_COORD_ARRAY, GL_COLOR_ARRAY, GL_NORMAL_ARRAY};
    private static final boolean[] glClientOn = new boolean[4];

    private static Ptr ptrFor(int array) {
        switch (array) {
            case GL_VERTEX_ARRAY: return vtx;
            case GL_TEXTURE_COORD_ARRAY: return tex;
            case GL_COLOR_ARRAY: return col;
            case GL_NORMAL_ARRAY: return nrm;
        }
        return null;
    }

    public static void glEnableClientState(int array) { Ptr p = ptrFor(array); if (p != null) p.enabled = true; }

    public static void glDisableClientState(int array) { Ptr p = ptrFor(array); if (p != null) p.enabled = false; }

    private static Buffer snap(Buffer b) {
        if (b instanceof ByteBuffer) return ((ByteBuffer) b).duplicate().order(ByteOrder.nativeOrder()).position(b.position());
        if (b instanceof FloatBuffer) return ((FloatBuffer) b).duplicate().position(b.position());
        if (b instanceof IntBuffer) return ((IntBuffer) b).duplicate().position(b.position());
        if (b instanceof ShortBuffer) return ((ShortBuffer) b).duplicate().position(b.position());
        return b;
    }

    private static void setPtr(Ptr p, int size, int type, int stride, Buffer b) {
        p.size = size; p.type = type; p.stride = stride; p.buf = snap(b); p.vboOffset = -1;
    }

    private static void setPtr(Ptr p, int size, int type, int stride, long offset) {
        p.size = size; p.type = type; p.stride = stride; p.buf = null; p.vboOffset = (int) offset;
    }

    public static void glVertexPointer(int size, int stride, FloatBuffer b) { setPtr(vtx, size, GL_FLOAT, stride, b); }
    public static void glVertexPointer(int size, int type, int stride, long off) { setPtr(vtx, size, type, stride, off); }
    public static void glTexCoordPointer(int size, int stride, FloatBuffer b) { setPtr(tex, size, GL_FLOAT, stride, b); }
    public static void glTexCoordPointer(int size, int type, int stride, long off) { setPtr(tex, size, type, stride, off); }
    public static void glColorPointer(int size, boolean unsigned, int stride, ByteBuffer b) {
        setPtr(col, size, unsigned ? GL_UNSIGNED_BYTE : GL_BYTE, stride, b);
    }
    public static void glColorPointer(int size, int stride, FloatBuffer b) { setPtr(col, size, GL_FLOAT, stride, b); }
    public static void glColorPointer(int size, int type, int stride, long off) { setPtr(col, size, type, stride, off); }
    public static void glNormalPointer(int stride, ByteBuffer b) { setPtr(nrm, 3, GL_BYTE, stride, b); }
    public static void glNormalPointer(int stride, FloatBuffer b) { setPtr(nrm, 3, GL_FLOAT, stride, b); }
    public static void glNormalPointer(int type, int stride, long off) { setPtr(nrm, 3, type, stride, off); }

    private static int typeBytes(int type) {
        switch (type) {
            case GL_BYTE: case GL_UNSIGNED_BYTE: return 1;
            case GL_SHORT: case GL_UNSIGNED_SHORT: return 2;
            default: return 4;
        }
    }

    /** Distance between two vertices, in units of the buffer's element type. */
    private static int stepElems(Ptr p) {
        int strideBytes = p.stride != 0 ? p.stride : p.size * typeBytes(p.type);
        Buffer b = p.buf;
        if (b instanceof ByteBuffer) return strideBytes;
        if (b instanceof ShortBuffer) return strideBytes / 2;
        return strideBytes / 4;
    }

    private static Buffer offsetBuf(Ptr p, int vertices) {
        if (vertices == 0) return p.buf;
        int np = p.buf.position() + vertices * stepElems(p);
        if (p.buf instanceof ByteBuffer) return ((ByteBuffer) p.buf).duplicate().position(np);
        if (p.buf instanceof FloatBuffer) return ((FloatBuffer) p.buf).duplicate().position(np);
        if (p.buf instanceof IntBuffer) return ((IntBuffer) p.buf).duplicate().position(np);
        return ((ShortBuffer) p.buf).duplicate().position(np);
    }

    private static void clientOn(int i, boolean on) {
        if (glClientOn[i] != on) {
            if (on) gl.glEnableClientState(ARRAY_ENUM[i]);
            else gl.glDisableClientState(ARRAY_ENUM[i]);
            glClientOn[i] = on;
        }
    }

    /** Push the game's client-array state to GL, starting at vertex 'first'. */
    private static void applyClient(int first) {
        for (int i = 0; i < 4; i++) {
            Ptr p = ptrs[i];
            boolean on = p.enabled && (p.buf != null || p.vboOffset >= 0);
            clientOn(i, on);
            if (!on) continue;
            if (p.buf != null) {
                Buffer b = offsetBuf(p, first);
                switch (i) {
                    case 0: gl.glVertexPointer(p.size, p.type, p.stride, b); break;
                    case 1: gl.glTexCoordPointer(p.size, p.type, p.stride, b); break;
                    case 2: gl.glColorPointer(p.size, p.type, p.stride, b); break;
                    default: gl.glNormalPointer(p.type, p.stride, b); break;
                }
            } else {
                int strideBytes = p.stride != 0 ? p.stride : p.size * typeBytes(p.type);
                int off = p.vboOffset + first * strideBytes;
                switch (i) {
                    case 0: gl.glVertexPointer(p.size, p.type, p.stride, off); break;
                    case 1: gl.glTexCoordPointer(p.size, p.type, p.stride, off); break;
                    case 2: gl.glColorPointer(p.size, p.type, p.stride, off); break;
                    default: gl.glNormalPointer(p.type, p.stride, off); break;
                }
            }
        }
    }

    private static int esMode(int mode) {
        if (mode == GL_QUAD_STRIP) return GL_TRIANGLE_STRIP;
        if (mode == GL_POLYGON) return GL_TRIANGLE_FAN;
        return mode;
    }

    public static void glDrawArrays(int mode, int first, int count) {
        if (compiling != null && callDepth == 0) {
            recordDraw(mode, first, count);
            if (!compileExec) return;
        }
        if (count <= 0 || !vtx.enabled) return;
        flushMatrices();
        int m = esMode(mode);
        if (m == GL_QUADS) {
            int end = first + count;
            for (int s = first; s + 3 < end; s += MAX_QUAD_VERTS) {
                int n = Math.min(MAX_QUAD_VERTS, end - s) & ~3;
                applyClient(s);
                quadIdx.position(0);
                gl.glDrawElements(GL_TRIANGLES, n / 4 * 6, GL_UNSIGNED_SHORT, quadIdx);
            }
        } else {
            applyClient(0);
            gl.glDrawArrays(m, first, count);
        }
    }

    // ---- recorded geometry ------------------------------------------------
    private static float readF(Ptr p, int v, int c) {
        Buffer b = p.buf;
        int step = stepElems(p);
        int base = b.position() + v * step;
        if (b instanceof FloatBuffer) return ((FloatBuffer) b).get(base + c);
        if (b instanceof ByteBuffer) {
            ByteBuffer bb = (ByteBuffer) b;
            switch (p.type) {
                case GL_FLOAT: return bb.getFloat(base + c * 4);
                case GL_SHORT: return bb.getShort(base + c * 2);
                case GL_INT: return bb.getInt(base + c * 4);
                case GL_UNSIGNED_BYTE: return bb.get(base + c) & 0xFF;
                default: return bb.get(base + c);
            }
        }
        if (b instanceof IntBuffer) return ((IntBuffer) b).get(base + c);
        if (b instanceof ShortBuffer) return ((ShortBuffer) b).get(base + c);
        return 0;
    }

    private static byte readColorByte(Ptr p, int v, int c) {
        if (c >= p.size) return (byte) 255;
        if (p.buf instanceof ByteBuffer && (p.type == GL_UNSIGNED_BYTE || p.type == GL_BYTE)) {
            ByteBuffer bb = (ByteBuffer) p.buf;
            return bb.get(bb.position() + v * stepElems(p) + c);
        }
        float f = readF(p, v, c);
        return (byte) Math.max(0, Math.min(255, Math.round(f * 255f)));
    }

    private static byte readNormalByte(Ptr p, int v, int c) {
        if (p.buf instanceof ByteBuffer && p.type == GL_BYTE) {
            ByteBuffer bb = (ByteBuffer) p.buf;
            return bb.get(bb.position() + v * stepElems(p) + c);
        }
        float f = readF(p, v, c);
        return (byte) Math.max(-127, Math.min(127, Math.round(f * 127f)));
    }

    private static final int VBO_MIN_BYTES = 2048;

    static final class DrawCmd extends Cmd {
        final int mode, count, stride;
        final boolean hasT, hasC, hasN;
        int vbo;
        ByteBuffer data;
        Buffer pv, pt, pc, pn;

        DrawCmd(int mode, int count, boolean hasT, boolean hasC, boolean hasN, ByteBuffer data) {
            this.mode = mode; this.count = count; this.hasT = hasT; this.hasC = hasC; this.hasN = hasN;
            this.stride = 12 + (hasT ? 8 : 0) + (hasC ? 4 : 0) + (hasN ? 4 : 0);
            int bytes = count * stride;
            if (bytes >= VBO_MIN_BYTES) {
                vbo = gl.glGenBuffer();
                gl.glBindBuffer(GL_ARRAY_BUFFER, vbo);
                data.position(0);
                gl.glBufferData(GL_ARRAY_BUFFER, bytes, data, GL_STATIC_DRAW);
                gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
            } else {
                this.data = data;
                int off = 12;
                pv = data.duplicate().position(0);
                if (hasT) { pt = data.duplicate().position(off); off += 8; }
                if (hasC) { pc = data.duplicate().position(off); off += 4; }
                if (hasN) { pn = data.duplicate().position(off); }
            }
        }

        void run() {
            flushMatrices();
            clientOn(0, true);
            clientOn(1, hasT);
            clientOn(2, hasC);
            clientOn(3, hasN);
            if (vbo != 0) {
                gl.glBindBuffer(GL_ARRAY_BUFFER, vbo);
                int off = 12;
                gl.glVertexPointer(3, GL_FLOAT, stride, 0);
                if (hasT) { gl.glTexCoordPointer(2, GL_FLOAT, stride, off); off += 8; }
                if (hasC) { gl.glColorPointer(4, GL_UNSIGNED_BYTE, stride, off); off += 4; }
                if (hasN) { gl.glNormalPointer(GL_BYTE, stride, off); }
                if (mode == GL_QUADS) {
                    gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, quadEbo);
                    gl.glDrawElements(GL_TRIANGLES, count / 4 * 6, GL_UNSIGNED_SHORT, 0);
                    gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
                } else {
                    gl.glDrawArrays(mode, 0, count);
                }
                gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
            } else {
                gl.glVertexPointer(3, GL_FLOAT, stride, pv);
                if (hasT) gl.glTexCoordPointer(2, GL_FLOAT, stride, pt);
                if (hasC) gl.glColorPointer(4, GL_UNSIGNED_BYTE, stride, pc);
                if (hasN) gl.glNormalPointer(GL_BYTE, stride, pn);
                if (mode == GL_QUADS) {
                    quadIdx.position(0);
                    gl.glDrawElements(GL_TRIANGLES, count / 4 * 6, GL_UNSIGNED_SHORT, quadIdx);
                } else {
                    gl.glDrawArrays(mode, 0, count);
                }
            }
        }

        void free() {
            if (vbo != 0) { gl.glDeleteBuffer(vbo); vbo = 0; }
            data = null;
        }
    }

    private static void recordDraw(int mode, int first, int count) {
        if (count <= 0 || !vtx.enabled || vtx.buf == null) return;
        int m = esMode(mode);
        boolean hasT = tex.enabled && tex.buf != null;
        boolean hasC = col.enabled && col.buf != null;
        boolean hasN = nrm.enabled && nrm.buf != null;
        int stride = 12 + (hasT ? 8 : 0) + (hasC ? 4 : 0) + (hasN ? 4 : 0);
        int chunk = m == GL_QUADS ? MAX_QUAD_VERTS : count;
        for (int s = 0; s < count; s += chunk) {
            int n = Math.min(chunk, count - s);
            if (m == GL_QUADS) n &= ~3;
            if (n <= 0) break;
            ByteBuffer d = ByteBuffer.allocateDirect(n * stride).order(ByteOrder.nativeOrder());
            for (int v = 0; v < n; v++) {
                int sv = first + s + v;
                d.putFloat(readF(vtx, sv, 0));
                d.putFloat(readF(vtx, sv, 1));
                d.putFloat(vtx.size > 2 ? readF(vtx, sv, 2) : 0f);
                if (hasT) { d.putFloat(readF(tex, sv, 0)); d.putFloat(tex.size > 1 ? readF(tex, sv, 1) : 0f); }
                if (hasC) {
                    d.put(readColorByte(col, sv, 0)); d.put(readColorByte(col, sv, 1));
                    d.put(readColorByte(col, sv, 2)); d.put(readColorByte(col, sv, 3));
                }
                if (hasN) {
                    d.put(readNormalByte(nrm, sv, 0)); d.put(readNormalByte(nrm, sv, 1));
                    d.put(readNormalByte(nrm, sv, 2)); d.put((byte) 0);
                }
            }
            d.flip();
            compiling.cmds.add(new DrawCmd(m, n, hasT, hasC, hasN, d));
        }
    }

    // ======================================================================
    // plain state (recorded into lists, otherwise passed through)
    // ======================================================================
    public static void glEnable(final int cap) {
        if (R() && rec(new Cmd() { void run() { glEnable(cap); } })) return;
        gl.glEnable(cap);
    }

    public static void glDisable(final int cap) {
        if (R() && rec(new Cmd() { void run() { glDisable(cap); } })) return;
        gl.glDisable(cap);
    }

    public static void glBindTexture(final int target, final int id) {
        if (R() && rec(new Cmd() { void run() { glBindTexture(target, id); } })) return;
        gl.glBindTexture(target, id);
    }

    public static void glBlendFunc(final int s, final int d) {
        if (R() && rec(new Cmd() { void run() { glBlendFunc(s, d); } })) return;
        gl.glBlendFunc(s, d);
    }

    public static void glAlphaFunc(final int f, final float ref) {
        if (R() && rec(new Cmd() { void run() { glAlphaFunc(f, ref); } })) return;
        gl.glAlphaFunc(f, ref);
    }

    public static void glColor3f(float r, float g, float b) { glColor4f(r, g, b, 1f); }

    public static void glColor4f(final float r, final float g, final float b, final float a) {
        if (R() && rec(new Cmd() { void run() { glColor4f(r, g, b, a); } })) return;
        gl.glColor4f(r, g, b, a);
    }

    public static void glNormal3f(final float x, final float y, final float z) {
        if (R() && rec(new Cmd() { void run() { glNormal3f(x, y, z); } })) return;
        gl.glNormal3f(x, y, z);
    }

    public static void glColorMask(final boolean r, final boolean g, final boolean b, final boolean a) {
        if (R() && rec(new Cmd() { void run() { glColorMask(r, g, b, a); } })) return;
        gl.glColorMask(r, g, b, a);
    }

    public static void glColorMaterial(int face, int mode) {
        // GLES 1.1 only has GL_AMBIENT_AND_DIFFUSE tracking, which is what the game asks for.
    }

    public static void glCullFace(final int mode) {
        if (R() && rec(new Cmd() { void run() { glCullFace(mode); } })) return;
        gl.glCullFace(mode);
    }

    public static void glDepthFunc(final int f) {
        if (R() && rec(new Cmd() { void run() { glDepthFunc(f); } })) return;
        gl.glDepthFunc(f);
    }

    public static void glDepthMask(final boolean flag) {
        if (R() && rec(new Cmd() { void run() { glDepthMask(flag); } })) return;
        gl.glDepthMask(flag);
    }

    public static void glShadeModel(final int mode) {
        if (R() && rec(new Cmd() { void run() { glShadeModel(mode); } })) return;
        gl.glShadeModel(mode);
    }

    public static void glLineWidth(final float w) {
        if (R() && rec(new Cmd() { void run() { glLineWidth(w); } })) return;
        gl.glLineWidth(w);
    }

    public static void glPolygonOffset(final float f, final float u) {
        if (R() && rec(new Cmd() { void run() { glPolygonOffset(f, u); } })) return;
        gl.glPolygonOffset(f, u);
    }

    private static float[] fv(FloatBuffer b) {
        int p = b.position();
        int n = Math.min(4, b.limit() - p);
        float[] out = new float[4];
        for (int i = 0; i < n; i++) out[i] = b.get(p + i);
        return out;
    }

    public static void glFog(int pname, FloatBuffer params) {
        final int pn = pname;
        final float[] v = fv(params);
        if (R() && rec(new Cmd() { void run() { gl.glFogfv(pn, v); } })) return;
        gl.glFogfv(pn, v);
    }

    public static void glFogf(final int pname, final float param) {
        if (R() && rec(new Cmd() { void run() { glFogf(pname, param); } })) return;
        gl.glFogf(pname, param);
    }

    public static void glFogi(final int pname, final int param) {
        if (pname == GL_FOG_DISTANCE_MODE_NV) return;
        if (R() && rec(new Cmd() { void run() { glFogi(pname, param); } })) return;
        gl.glFogf(pname, (float) param);
    }

    private static void lightNow(int light, int pname, float[] v) {
        if (pname == GL_POSITION || pname == GL_SPOT_DIRECTION) flushMatrices();
        gl.glLightfv(light, pname, v);
    }

    public static void glLight(int light, int pname, FloatBuffer params) {
        final int l = light, pn = pname;
        final float[] v = fv(params);
        if (R() && rec(new Cmd() { void run() { lightNow(l, pn, v); } })) return;
        lightNow(l, pn, v);
    }

    public static void glLightModel(int pname, FloatBuffer params) {
        final int pn = pname;
        final float[] v = fv(params);
        if (R() && rec(new Cmd() { void run() { gl.glLightModelfv(pn, v); } })) return;
        gl.glLightModelfv(pn, v);
    }

    public static void glTexParameteri(int target, int pname, int param) {
        if (param == GL_CLAMP) param = GL_CLAMP_TO_EDGE;
        gl.glTexParameteri(target, pname, param);
    }

    private static int fixFormat(int internal, int format) {
        if (internal == 4) return GL_RGBA;
        if (internal == 3) return GL_RGB;
        return format; // GLES requires internalformat == format
    }

    public static void glTexImage2D(int target, int level, int internal, int w, int h, int border, int format, int type, ByteBuffer px) {
        gl.glTexImage2D(target, level, fixFormat(internal, format), w, h, border, format, type, px);
    }

    public static void glTexSubImage2D(int target, int level, int x, int y, int w, int h, int format, int type, ByteBuffer px) {
        gl.glTexSubImage2D(target, level, x, y, w, h, format, type, px);
    }

    public static void glGenTextures(IntBuffer out) {
        int p = out.position(), n = out.remaining();
        int[] ids = new int[n];
        gl.glGenTextures(n, ids);
        for (int i = 0; i < n; i++) out.put(p + i, ids[i]);
    }

    public static void glDeleteTextures(IntBuffer in) {
        int p = in.position(), n = in.remaining();
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) ids[i] = in.get(p + i);
        gl.glDeleteTextures(n, ids);
    }

    public static void glClear(int mask) { gl.glClear(mask); }

    public static void glClearColor(float r, float g, float b, float a) { gl.glClearColor(r, g, b, a); }

    public static void glClearDepth(double d) { gl.glClearDepthf((float) d); }

    public static void glViewport(int x, int y, int w, int h) { gl.glViewport(x, y, w, h); }

    public static int glGetError() { return 0; }

    public static String glGetString(int name) {
        String s = gl.glGetString(name);
        return s == null ? "" : s;
    }

    public static void glReadPixels(int x, int y, int w, int h, int format, int type, ByteBuffer out) {
        gl.glReadPixels(x, y, w, h, format, type, out);
    }

    public static void glFlush() {}

    public static void glFinish() {}
}
