import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.tree.*;

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
        {"javax/swing/", "mcshim/swing/"},
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
                    if (op == Opcodes.INVOKEVIRTUAL && owner.equals("java/net/URL") && name.equals("openConnection") && desc.equals("()Ljava/net/URLConnection;")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "mcshim/Net", "openConnection", "(Ljava/net/URL;)Ljava/net/URLConnection;", false);
                        return;
                    }
                    if (op == Opcodes.INVOKEVIRTUAL && owner.equals("java/net/URL") && name.equals("openStream")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "mcshim/Net", "openStream", "(Ljava/net/URL;)Ljava/io/InputStream;", false);
                        return;
                    }
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

    /**
     * The game object the shim hooks: net/minecraft/client/Minecraft when the
     * jar has it (Alpha), otherwise the Runnable in net/minecraft/client that
     * the applet constructs (Indev, where everything is obfuscated).
     */
    static String findMainClass(String jar) throws Exception {
        try (ZipFile in = new ZipFile(jar)) {
            if (in.getEntry("net/minecraft/client/Minecraft.class") != null) return "net/minecraft/client/Minecraft";
            for (Enumeration<? extends ZipEntry> e = in.entries(); e.hasMoreElements();) {
                ZipEntry en = e.nextElement();
                String n = en.getName();
                if (!n.startsWith("net/minecraft/client/") || !n.endsWith(".class")) continue;
                ClassReader cr = new ClassReader(in.getInputStream(en).readAllBytes());
                if (!java.util.Arrays.asList(cr.getInterfaces()).contains("java/lang/Runnable")) continue;
                final boolean[] hit = {false};
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    public MethodVisitor visitMethod(int acc, String mn, String d, String sig, String[] ex) {
                        if (mn.equals("<init>") && d.contains("Lnet/minecraft/client/MinecraftApplet;")) hit[0] = true;
                        return null;
                    }
                }, ClassReader.SKIP_CODE);
                if (hit[0]) return n.substring(0, n.length() - 6);
            }
        }
        throw new IllegalStateException("no Minecraft main class found in " + jar);
    }

    /**
     * The version's name, from the window title the game sets ("Minecraft Minecraft
     * Alpha v1.1.2_01" -> "Alpha v1.1.2_01"). Indev's title has no date, so a
     * yyyymmdd in the jar's file name is added ("Indev 20100223").
     */
    static String versionLabel(String jar, String mainClass) throws Exception {
        String label = null;
        try (ZipFile in = new ZipFile(jar)) {
            ClassNode cn = new ClassNode();
            new ClassReader(in.getInputStream(in.getEntry(mainClass + ".class")).readAllBytes()).accept(cn, 0);
            for (MethodNode m : cn.methods)
                for (AbstractInsnNode i : m.instructions)
                    if (i instanceof LdcInsnNode && ((LdcInsnNode) i).cst instanceof String
                            && ((String) ((LdcInsnNode) i).cst).startsWith("Minecraft Minecraft "))
                        label = ((String) ((LdcInsnNode) i).cst).substring("Minecraft Minecraft ".length());
        }
        if (label == null) label = new File(jar).getName().replaceAll("\\.jar$", "");
        java.util.regex.Matcher d = java.util.regex.Pattern.compile("(\\d{8})").matcher(new File(jar).getName());
        if (!label.matches(".*\\d.*") && d.find()) label += " " + d.group(1);
        return label.trim();
    }

    public static void main(String[] a) throws Exception {
        Remapper r = new Remapper() { public String map(String n) { return Remap.map(n); } };
        int classes = 0, res = 0;
        String hook = findMainClass(a[0]);
        System.out.println("main game class: " + hook);
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
                    if (name.equals(hook + ".class")) cv = new HookMinecraft(cv);
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
        if (a.length > 3) {
            String label = versionLabel(a[0], hook);
            try (Writer w = new OutputStreamWriter(new FileOutputStream(a[3]), "UTF-8")) { w.write(label); }
            System.out.println("version: " + label);
        }
    }
}
