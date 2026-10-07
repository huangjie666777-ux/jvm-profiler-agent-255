package jvmprobe255;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads test classes from its parent (the test classpath) and instruments every
 * class in the demo package before defining it. The SDK package itself is loaded
 * unchanged by the parent so runtime identity is shared.
 */
public class TransformingLoader extends ClassLoader {
    private final Profiler profiler = new Profiler();

    static {
        registerAsParallelCapable();
    }

    public TransformingLoader(ClassLoader parent) {
        super(parent);
    }

    public Class<?> loadTransformed(String name) throws ClassNotFoundException {
        return loadClass(name);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> existing = findLoadedClass(name);
            if (existing != null) {
                return existing;
            }
            if (name.startsWith("demo255.")) {
                byte[] bytes = readBytes(name);
                byte[] out = profiler.transform(bytes, this);
                Class<?> defined = defineClass(name, out, 0, out.length);
                if (resolve) {
                    resolveClass(defined);
                }
                return defined;
            }
            return super.loadClass(name, resolve);
        }
    }

    private byte[] readBytes(String name) throws ClassNotFoundException {
        String resource = name.replace('.', '/') + ".class";
        try (InputStream in = getParent().getResourceAsStream(resource)) {
            if (in == null) {
                throw new ClassNotFoundException(name);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}
