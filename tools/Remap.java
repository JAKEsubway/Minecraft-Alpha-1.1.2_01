import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Rewrites a desktop Minecraft jar so its references to LWJGL / AWT / ImageIO /
 * javax.sound / applets point at the mcshim package instead.
 *   java Remap in.jar out-classes.jar out-resources.zip
 */
public class Remap {
    static final String[][] MAP = {
        {"org/lwjgl/", "mcshim/lwjgl/"},
        {"java/awt/", "mcshim/awt/"},
        {"java/applet/", "mcshim/applet/"},
        {"javax/imageio/", "mcshim/imageio/"},
        {"javax/sound/", "mcshim/sound/"},
    };

    public static String map(String n) {
        for (String[] m : MAP) if (n.startsWith(m[0])) return m[1] + n.substring(m[0].length());
        return n;
    }

    /** Redirect Collections.sort(List, Comparator) / Arrays.sort(Object[], Comparator) to mcshim.SafeSort. */
    static final class SafeSorts extends ClassVisitor {
        SafeSorts(ClassVisitor cv) { super(Opcodes.ASM9, cv); }
        public MethodVisitor visitMethod(int acc, String n, String d, String sig, String[] ex) {
            return new MethodVisitor(Opcodes.ASM9, super.visitMethod(acc, n, d, sig, ex)) {
                public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                    if (op == Opcodes.INVOKESTATIC && name.equals("sort")
                            && ((owner.equals("java/util/Collections") && desc.equals("(Ljava/util/List;Ljava/util/Comparator;)V"))
                            || (owner.equals("java/util/Arrays") && desc.equals("([Ljava/lang/Object;Ljava/util/Comparator;)V")))) {
                        super.visitMethodInsn(op, "mcshim/SafeSort", "sort", desc, false);
                        return;
                    }
                    super.visitMethodInsn(op, owner, name, desc, itf);
                }
            };
        }
    }

    /** At the end of every Minecraft constructor: mcshim.Hooks.minecraft = this; */
    static final class HookMinecraft extends ClassVisitor {
        HookMinecraft(ClassVisitor cv) { super(Opcodes.ASM9, cv); }
        public MethodVisitor visitMethod(int acc, String n, String d, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(acc, n, d, sig, ex);
            if (!n.equals("<init>")) return mv;
            return new MethodVisitor(Opcodes.ASM9, mv) {
                public void visitInsn(int op) {
                    if (op == Opcodes.RETURN) {
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitFieldInsn(Opcodes.PUTSTATIC, "mcshim/Hooks", "minecraft", "Ljava/lang/Object;");
                    }
                    super.visitInsn(op);
                }
                public void visitMaxs(int stack, int locals) { super.visitMaxs(Math.max(stack, 1), locals); }
            };
        }
    }

    public static void main(String[] a) throws Exception {
        Remapper r = new Remapper() { public String map(String n) { return Remap.map(n); } };
        int classes = 0, res = 0;
        try (ZipFile in = new ZipFile(a[0]);
             ZipOutputStream outC = new ZipOutputStream(new FileOutputStream(a[1]));
             ZipOutputStream outR = new ZipOutputStream(new FileOutputStream(a[2]))) {
            for (Enumeration<? extends ZipEntry> e = in.entries(); e.hasMoreElements();) {
                ZipEntry en = e.nextElement();
                String name = en.getName();
                if (en.isDirectory() || name.startsWith("META-INF/")) continue;
                byte[] data = in.getInputStream(en).readAllBytes();
                if (name.endsWith(".class")) {
                    ClassReader cr = new ClassReader(data);
                    ClassWriter cw = new ClassWriter(0);
                    ClassVisitor cv = new ClassRemapper(cw, r);
                    if (name.equals("net/minecraft/client/Minecraft.class")) cv = new HookMinecraft(cv);
                    cv = new SafeSorts(cv);
                    cr.accept(cv, 0);
                    outC.putNextEntry(new ZipEntry(name));
                    outC.write(cw.toByteArray());
                    classes++;
                } else {
                    outR.putNextEntry(new ZipEntry(name));
                    outR.write(data);
                    res++;
                }
            }
        }
        System.out.println("remapped " + classes + " classes, " + res + " resources");
    }
}
