import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Verifies every class/method/field the remapped game touches under mcshim/
 * exists in the shim (walking superclasses of both game and shim classes).
 *   java Check game-remapped.jar shim-classes-dir
 */
public class Check {
    static Map<String, ClassNode> game = new HashMap<>(), shim = new HashMap<>();

    static ClassNode node(byte[] b) { ClassNode n = new ClassNode(); new ClassReader(b).accept(n, ClassReader.SKIP_CODE); return n; }

    static ClassNode find(String n) { ClassNode c = shim.get(n); return c != null ? c : game.get(n); }

    static boolean hasMember(String owner, String name, String desc, boolean method, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return false;
        if (owner.equals("java/lang/Object")) // the usual super class: knows only its own few members
            return method && Arrays.asList("equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait", "clone", "finalize").contains(name);
        if (owner.startsWith("java/") || owner.startsWith("javax/")) return true; // assume platform provides
        ClassNode c = find(owner);
        if (c == null) return false;
        if (method) { for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return true; }
        else { for (FieldNode f : c.fields) if (f.name.equals(name) && f.desc.equals(desc)) return true; }
        if (hasMember(c.superName, name, desc, method, seen)) return true;
        for (String i : c.interfaces) if (hasMember(i, name, desc, method, seen)) return true;
        return false;
    }

    static boolean touchesShim(String owner, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return false;
        if (owner.startsWith("mcshim/")) return true;
        ClassNode c = game.get(owner);
        if (c == null) return false;
        if (touchesShim(c.superName, seen)) return true;
        for (String i : c.interfaces) if (touchesShim(i, seen)) return true;
        return false;
    }

    public static void main(String[] a) throws Exception {
        List<byte[]> gameBytes = new ArrayList<>();
        try (ZipFile z = new ZipFile(a[0])) {
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements();) {
                ZipEntry en = e.nextElement();
                if (!en.getName().endsWith(".class")) continue;
                byte[] b = z.getInputStream(en).readAllBytes();
                gameBytes.add(b);
                ClassNode n = node(b); game.put(n.name, n);
            }
        }
        java.nio.file.Files.walk(new File(a[1]).toPath()).filter(p -> p.toString().endsWith(".class")).forEach(p -> {
            try { ClassNode n = node(java.nio.file.Files.readAllBytes(p)); shim.put(n.name, n); } catch (IOException ex) { throw new UncheckedIOException(ex); }
        });
        TreeSet<String> missing = new TreeSet<>();
        for (byte[] b : gameBytes) {
            ClassNode cn = new ClassNode();
            new ClassReader(b).accept(cn, 0);
            Set<String> types = new HashSet<>();
            types.add(cn.superName); types.addAll(cn.interfaces);
            for (FieldNode f : cn.fields) addDesc(types, f.desc);
            for (MethodNode m : cn.methods) {
                addDesc(types, m.desc);
                if (m.exceptions != null) types.addAll(m.exceptions);
                for (TryCatchBlockNode t : m.tryCatchBlocks) if (t.type != null) types.add(t.type);
                for (AbstractInsnNode in : m.instructions) {
                    if (in instanceof MethodInsnNode) {
                        MethodInsnNode mi = (MethodInsnNode) in;
                        addDesc(types, mi.desc);
                        String o = mi.owner.startsWith("[") ? null : mi.owner;
                        if (o != null && touchesShim(o, new HashSet<>()) && !hasMember(o, mi.name, mi.desc, true, new HashSet<>()))
                            missing.add("METHOD " + o + "." + mi.name + mi.desc + "   (from " + cn.name + ")");
                        if (o != null) types.add(o);
                    } else if (in instanceof FieldInsnNode) {
                        FieldInsnNode fi = (FieldInsnNode) in;
                        addDesc(types, fi.desc);
                        if (touchesShim(fi.owner, new HashSet<>()) && !hasMember(fi.owner, fi.name, fi.desc, false, new HashSet<>()))
                            missing.add("FIELD  " + fi.owner + "." + fi.name + ":" + fi.desc + "   (from " + cn.name + ")");
                        types.add(fi.owner);
                    } else if (in instanceof TypeInsnNode) {
                        addDesc(types, ((TypeInsnNode) in).desc.startsWith("[") ? ((TypeInsnNode) in).desc : "L" + ((TypeInsnNode) in).desc + ";");
                    } else if (in instanceof LdcInsnNode && ((LdcInsnNode) in).cst instanceof Type) {
                        addDesc(types, ((Type) ((LdcInsnNode) in).cst).getDescriptor());
                    }
                }
            }
            for (String t : types) if (t != null && t.startsWith("mcshim/") && !shim.containsKey(t)) missing.add("CLASS  " + t + "   (from " + cn.name + ")");
        }
        // abstract methods of shim interfaces implemented by game classes are fine; report count
        for (String m : missing) System.out.println(m);
        System.out.println(missing.isEmpty() ? "OK: all shim references resolve" : missing.size() + " missing");
    }

    static void addDesc(Set<String> s, String d) {
        Type t = Type.getType(d);
        if (t.getSort() == Type.METHOD) { addDesc(s, t.getReturnType().getDescriptor()); for (Type x : t.getArgumentTypes()) addDesc(s, x.getDescriptor()); return; }
        while (t.getSort() == Type.ARRAY) t = t.getElementType();
        if (t.getSort() == Type.OBJECT) s.add(t.getInternalName());
    }
}
