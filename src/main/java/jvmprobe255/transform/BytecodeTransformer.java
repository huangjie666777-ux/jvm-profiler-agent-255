package jvmprobe255.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * ASM tree-API transformer that injects entry/exit probes into Java 17 classes.
 *
 * <p>Each eligible method is wrapped with a single synthetic catch-all handler so
 * that exactly one settlement happens per invocation: every return opcode settles
 * as a normal exit, and only an exception escaping the whole body settles as an
 * exceptional exit. Exceptions caught by the method's own handlers stay on the
 * normal path and later reach whichever return/throw they ultimately do.</p>
 */
public final class BytecodeTransformer {

    private static final int ASM_API = Opcodes.ASM9;
    private static final int JAVA17_MAJOR = Opcodes.V17 & 0xFFFF;
    private static final String SDK_PACKAGE = "jvmprobe255/";
    private static final String RUNTIME_GUARD = "jvmprobe255/runtime/ExitGuard";
    private static final String MARKER_FIELD = "$jvmprobe255$probed";
    private static final String MARKER_DESC = "Z";

    public byte[] transform(byte[] classBytes, ClassLoader loader) {
        if (classBytes == null || classBytes.length == 0) {
            throw new IllegalArgumentException("null or empty class bytes");
        }
        if (classBytes.length < 4
                || (classBytes[0] & 0xFF) != 0xCA
                || (classBytes[1] & 0xFF) != 0xFE
                || (classBytes[2] & 0xFF) != 0xBA
                || (classBytes[3] & 0xFF) != 0xBE) {
            throw new IllegalArgumentException("invalid class file: bad magic number");
        }
        ClassReader reader;
        ClassNode clazz = new ClassNode(ASM_API);
        try {
            reader = new ClassReader(classBytes);
            reader.accept(clazz, 0);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid class file: " + e.getMessage(), e);
        }

        validateClass(clazz);

        // The SDK must never profile itself.
        if (clazz.name != null && clazz.name.startsWith(SDK_PACKAGE)) {
            return classBytes;
        }
        // Idempotency: re-transforming our own output must add no second probe set.
        if (hasMarker(clazz)) {
            return classBytes;
        }

        int instrumented = 0;
        for (MethodNode method : clazz.methods) {
            if (isEligible(method)) {
                instrumentMethod(clazz.name, method);
                instrumented++;
            }
        }
        if (instrumented == 0) {
            // No probes means no observable change and no need to mark the class.
            return classBytes;
        }

        clazz.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC
                        | Opcodes.ACC_DEPRECATED,
                MARKER_FIELD, MARKER_DESC, null, null));

        FrameResolvingWriter writer = new FrameResolvingWriter(loader);
        try {
            clazz.accept(writer);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("failed to write instrumented class "
                    + clazz.name + ": " + e.getMessage(), e);
        }
        return writer.toByteArray();
    }

    private static void validateClass(ClassNode clazz) {
        if (clazz.name == null || clazz.name.isEmpty()) {
            throw new IllegalArgumentException("class file has no name");
        }
        int major = clazz.version & 0xFFFF;
        if (major != JAVA17_MAJOR) {
            throw new IllegalArgumentException(
                    "unsupported class file version (major " + major + ") in " + clazz.name
                            + ": only Java 17 (major " + JAVA17_MAJOR + ") is supported");
        }
        if ((clazz.access & Opcodes.ACC_MODULE) != 0) {
            throw new IllegalArgumentException("module-info classes are not supported: " + clazz.name);
        }
        if ((clazz.access & Opcodes.ACC_ANNOTATION) != 0) {
            throw new IllegalArgumentException("annotation types are not supported: " + clazz.name);
        }
        if ((clazz.access & Opcodes.ACC_INTERFACE) != 0) {
            throw new IllegalArgumentException("interfaces are not supported: " + clazz.name);
        }
    }

    private static boolean hasMarker(ClassNode clazz) {
        for (FieldNode field : clazz.fields) {
            if (MARKER_FIELD.equals(field.name) && MARKER_DESC.equals(field.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEligible(MethodNode method) {
        if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) {
            return false;
        }
        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return false;
        }
        return true;
    }

    private static void instrumentMethod(String internalClassName, MethodNode method) {
        InsnList instructions = method.instructions;

        int tokenSlot = method.maxLocals;
        int exceptionSlot = method.maxLocals + 2;

        LabelNode rangeStart = new LabelNode();
        LabelNode rangeEnd = new LabelNode();
        LabelNode handler = new LabelNode();

        InsnList prologue = new InsnList();
        // Default token is 0 (probe disabled) so the exit chokepoint is safe even
        // if control somehow reached the handler without the store completing.
        prologue.add(new org.objectweb.asm.tree.InsnNode(Opcodes.LCONST_0));
        prologue.add(new VarInsnNode(Opcodes.LSTORE, tokenSlot));
        prologue.add(rangeStart);
        prologue.add(new LdcInsnNode(internalClassName));
        prologue.add(new LdcInsnNode(method.name));
        prologue.add(new LdcInsnNode(method.desc));
        prologue.add(new org.objectweb.asm.tree.MethodInsnNode(
                Opcodes.INVOKESTATIC, RUNTIME_GUARD, "safeEnter",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)J", false));
        prologue.add(new VarInsnNode(Opcodes.LSTORE, tokenSlot));
        instructions.insert(prologue);

        // Snapshot the original instruction stream; only the return opcodes need a
        // normal-exit chokepoint. Existing exception tables are kept untouched, so
        // user catch/finally semantics are preserved.
        java.util.List<AbstractInsnNode> originals = new java.util.ArrayList<>();
        for (AbstractInsnNode node = instructions.getFirst(); node != null; node = node.getNext()) {
            originals.add(node);
        }
        for (AbstractInsnNode node : originals) {
            int opcode = node.getOpcode();
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                InsnList settle = new InsnList();
                settle.add(new VarInsnNode(Opcodes.LLOAD, tokenSlot));
                settle.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_0));
                settle.add(new org.objectweb.asm.tree.MethodInsnNode(
                        Opcodes.INVOKESTATIC, RUNTIME_GUARD, "safeExit", "(JZ)V", false));
                instructions.insertBefore(node, settle);
            }
        }

        InsnList epilogue = new InsnList();
        epilogue.add(rangeEnd);
        epilogue.add(handler);
        // Stack at handler entry: the escaping throwable.
        epilogue.add(new VarInsnNode(Opcodes.ASTORE, exceptionSlot));
        epilogue.add(new VarInsnNode(Opcodes.LLOAD, tokenSlot));
        epilogue.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_1));
        epilogue.add(new org.objectweb.asm.tree.MethodInsnNode(
                Opcodes.INVOKESTATIC, RUNTIME_GUARD, "safeExit", "(JZ)V", false));
        epilogue.add(new VarInsnNode(Opcodes.ALOAD, exceptionSlot));
        epilogue.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ATHROW));
        instructions.add(epilogue);

        // Catch-all added last so it never shadows the method's own handlers.
        method.tryCatchBlocks.add(
                new TryCatchBlockNode(rangeStart, rangeEnd, handler, null));
    }

    /**
     * ClassWriter that computes stack map frames against the application class
     * loader. Types are resolved for hierarchy queries only; no business class is
     * initialized, and resolution failures conservatively collapse to Object.
     */
    private static final class FrameResolvingWriter extends ClassWriter {
        private final ClassLoader loader;

        FrameResolvingWriter(ClassLoader loader) {
            super(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            this.loader = loader;
        }

        @Override
        protected String getCommonSuperClass(final String type1, final String type2) {
            if (type1.equals(type2)) {
                return type1;
            }
            Class<?> class1;
            Class<?> class2;
            try {
                class1 = Class.forName(type1.replace('/', '.'), false, loader);
                class2 = Class.forName(type2.replace('/', '.'), false, loader);
            } catch (Exception e) {
                return "java/lang/Object";
            }
            if (class1.isAssignableFrom(class2)) {
                return type1;
            }
            if (class2.isAssignableFrom(class1)) {
                return type2;
            }
            if (class1.isInterface() || class2.isInterface()) {
                return "java/lang/Object";
            }
            Class<?> common = class1.getSuperclass();
            while (common != null && !common.isAssignableFrom(class2)) {
                common = common.getSuperclass();
            }
            return common == null ? "java/lang/Object" : common.getName().replace('.', '/');
        }
    }
}
