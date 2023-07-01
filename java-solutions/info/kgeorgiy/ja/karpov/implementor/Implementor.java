package info.kgeorgiy.ja.karpov.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * Creates an implementation of a given class or interface.
 *
 * @author Denis Karpov
 * @version 1.0
 */
public class Implementor implements JarImpler {
    /**
     * Class consisting of {@link Method} name and parameter types, used for comparing methods.
     */
    private static class Signature {
        /**
         * Name of method.
         */
        public String name;

        /**
         * Method parameters.
         */
        public Class<?>[] parameterTypes;

        /**
         * Getter for {@code name}.
         *
         * @return {@code name}.
         */
        public String getName() {
            return name;
        }

        /**
         * Getter for {@code parameterTypes}.
         *
         * @return {@code parameterTypes}.
         */
        public Class<?>[] getParameterTypes() {
            return parameterTypes;
        }

        /**
         * Constructor from {@link Method} name and parameters.
         *
         * @param method {@link Method} to extract signature from.
         */
        public Signature(Method method) {
            this.name = method.getName();
            this.parameterTypes = method.getParameterTypes();
        }

        /**
         * Overrides equals, compares names and parameter types.
         *
         * @param cmp {@link Object} to compare to
         * @return the result of comparison
         */
        @Override
        public boolean equals(Object cmp) {
            if (cmp instanceof Signature signature) {
                return signature.getName().equals(name) && Arrays.equals(signature.getParameterTypes(), parameterTypes);
            }
            return false;
        }

        /**
         * Overrides {@code hashCode()} to enable {@link HashMap}.
         *
         * @return the hash code.
         */
        @Override
        public int hashCode() {
            return Objects.hash(name, Arrays.hashCode(parameterTypes));
        }
    }

    /**
     * Checks if token is a private class.
     * @param token - class to check.
     * @return the boolean result.
     */
    private boolean privateCheck(Class<?> token) {
        return Modifier.isPrivate(token.getModifiers());
    }

    /**
     * Null check method to reduce clutter.
     *
     * @param obj     {@link Object} to check for null.
     * @param message {@link String} containing exception message.
     * @throws ImplerException if {@code obj} is {@code null}.
     */
    private void checkNull(Object obj, String message) throws ImplerException {
        if (obj == null) {
            throw new ImplerException(message);
        }
    }

    /**
     * Generates a given number of line separators.
     *
     * @param size the number of times to repeat {@code System.lineSeparator()}.
     * @return a string containing the line separators.
     */
    private String genLineSeparation(int size) {
        return System.lineSeparator().repeat(size);
    }

    /**
     * Generates a given number of tabulation symbols.
     *
     * @param size the number of times to repeat tabulation (4 times whitespace).
     * @return a {@link String} containing the required tabulation.
     */
    private String genTabulation(int size) {
        return "    ".repeat(size);
    }

    /**
     * Generates the {@link String} form of {@code token}'s package name.
     * Package name is followed by two new line symbols.
     *
     * @param token {@link Class} token.
     * @return the {@link String} containing the package name.
     */
    private String genPackage(Class<?> token) {
        return token.getPackage() == null ? ""
                : "package " + token.getPackage().getName() + ";" + genLineSeparation(2);
    }

    /**
     * Generates the {@link String} form of {@code token}'s class header.
     *
     * @param token {@link Class} token.
     * @return the {@link String} containing the class header.
     */
    private String genClassHead(Class<?> token) {
        return genPackage(token) + "public class " + token.getSimpleName() + "Impl "
                + (token.isInterface() ? "implements " : "extends ")
                + token.getCanonicalName() + " {" + genLineSeparation(1);
    }

    /**
     * Generates the {@link String} form of {@code executable}'s return type.
     *
     * @param executable {@link Executable} for which the return type is generated.
     * @return the {@link String} containing the return type of the {@code executable}.
     * @throws ImplerException if the return type is private and nested.
     */
    private String genReturnType(Executable executable) throws ImplerException {
        if (executable instanceof Constructor<?>) {
            return "";
        }

        Method method = (Method) executable;
        if (privateCheck(method.getReturnType())) {
            throw new ImplerException("Cannot implement method with private nested return type.");
        }
        return method.getReturnType().getCanonicalName() + " ";
    }

    /**
     * Generates the {@link String} form of {@code executable}'s name.
     *
     * @param executable {@link Executable} for which the name is generated.
     * @return the {@link String} containing the name of the {@code executable}.
     */
    private String genExecutableName(Executable executable) {
        if (executable instanceof Method method) {
            return method.getName();
        }
        return ((Constructor<?>) executable).getDeclaringClass().getSimpleName() + "Impl";
    }

    /**
     * Generates the {@link String} of parameters either to declare {@code executable} or call it.
     * The two cases are differentiated by a boolean flag {@code appendTypes}.
     *
     * @param executable  {@link Executable} for which the parameters are generated.
     * @param appendTypes a flag determining whether to generate arguments with their types or not. If
     *                    {@code appendTypes} is true the types are generated and not otherwise.
     * @return the {@link String} containing the parameters.
     * @throws ImplerException if there is a private nested parameter type.
     */
    private String genParameters(Executable executable, boolean appendTypes) throws ImplerException {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        Class<?>[] parameterTypes = executable.getParameterTypes();
        for (Class<?> token : parameterTypes) {
            if (privateCheck(token)) {
                throw new ImplerException("Cannot implement method with private nested arguments.");
            }
        }
        String[] joinArray = IntStream.range(0, parameterTypes.length)
                .mapToObj(ind -> appendTypes ? parameterTypes[ind].getCanonicalName() + " arg" + ind : "arg" + ind)
                .toArray(String[]::new);
        sb.append(String.join(", ", joinArray));
        sb.append(") ");
        return sb.toString();
    }

    /**
     * Generates the {@link String} form of exceptions declaration for {@code executable}.
     *
     * @param executable {@link Executable} for which the exception part of declaration is generated.
     * @return the {@link String} containing the declaration of exceptions thrown by {@code executable}.
     */
    private String genExceptions(Executable executable) {
        StringBuilder sb = new StringBuilder();
        Class<?>[] exceptionTypes = executable.getExceptionTypes();
        if (exceptionTypes.length > 0) {
            sb.append("throws ");
            for (Class<?> exceptionType : exceptionTypes) {
                sb.append(exceptionType.getCanonicalName()).append(" ");
            }
        }
        sb.append("{").append(genLineSeparation(1));
        return sb.toString();
    }

    /**
     * Generates the {@link String} form of {@code executable}'s main body.
     * If {@code executable} is a {@link Method}, a return with default value
     * for it's return type is generated. Otherwise, if it is a
     * {@link Constructor}, a call to {@code super()} with corresponding parameters is made.
     *
     * @param executable {@link Executable} for which the default value is generated.
     * @return the {@link String} containing the default value for {@code executable}.
     * @throws ImplerException if {@link #genParameters(Executable, boolean)} throws it.
     */
    private String genDefaultValue(Executable executable) throws ImplerException {
        if (executable instanceof Constructor<?>) {
            return "super" + genParameters(executable, false) + ";" + genLineSeparation(1);
        }

        Method method = (Method) executable;
        Class<?> returnType = method.getReturnType();
        String res = "null";
        if (returnType.equals(boolean.class)) {
            res = "false";
        } else if (returnType.equals(void.class)) {
            res = "";
        } else if (returnType.isPrimitive()) {
            res = "0";
        }
        return "return " + res + ";" + genLineSeparation(1);
    }

    /**
     * Generates the {@link String} form of {@code executable}.
     * The generated code includes the return type ({@link #genReturnType(Executable)}),
     * name ({@link #genExecutableName(Executable)}), parameter list ({@link #genParameters(Executable, boolean)}),
     * list of throwable exceptions ({@link #genExceptions(Executable)})
     * and a main body ({@link #genDefaultValue(Executable)}).
     *
     * @param executable {@link Executable} for which to generate code.
     * @return the {@link String} containing the full code of {@code executable}.
     * @throws ImplerException if one of the auxiliary methods throws it.
     */
    private String genExecutable(Executable executable) throws ImplerException {
        StringBuilder sb = new StringBuilder();

        int modifiers = executable.getModifiers() & ~Modifier.ABSTRACT & ~Modifier.TRANSIENT;
        sb.append(genTabulation(1)).append(Modifier.toString(modifiers)).append(" ")
                .append(genReturnType(executable))
                .append(genExecutableName(executable))
                .append(genParameters(executable, true))
                .append(genExceptions(executable))
                .append(genTabulation(2)).append(genDefaultValue(executable))
                .append(genTabulation(1)).append('}').append(genLineSeparation(2));
        return sb.toString();
    }

    /**
     * Gets an array of non-private {@link Constructor} for the given token.
     *
     * @param token {@link Class} for which to get constructors.
     * @return the {@link Constructor} array for the {@code token}.
     */
    private Constructor<?>[] getConstructors(Class<?> token) {
        return Arrays.stream(token.getDeclaredConstructors())
                .filter(constructor -> !Modifier.isPrivate(constructor.getModifiers())).toArray(Constructor<?>[]::new);
    }

    /**
     * Generates the {@link String} form of {@code token}'s non-private constructors.
     * {@link #getConstructors(Class)} is used to provide constructors.
     *
     * @param token {@link Class} token for which to generate constructors.
     * @return the {@link String} containing the code for {@code token}'s non-private constructors.
     * @throws ImplerException if there are no non-private constructors in {@code token}.
     */
    private String genConstructors(Class<?> token) throws ImplerException {
        Constructor<?>[] constructors = getConstructors(token);

        if (constructors.length == 0) {
            throw new ImplerException("Cannot implement class with no constructors");
        }

        StringBuilder sb = new StringBuilder();
        for (Constructor<?> constructor : constructors) {
            sb.append(genExecutable(constructor));
        }

        return sb.toString();
    }

    /**
     * Collects the methods to implement for {@code token}.
     * The methods taken are {@link Class#getMethods()}, {@link Class#getDeclaredMethods()} and non-implemented
     * abstract methods from superclasses. Conflicts are resolved via {@link HashMap} and {@link Signature},
     * of two methods with the same signature, the one chosen has its return type {@link Class#isAssignableFrom(Class)}
     * from the other's.
     *
     * @param token {@link Class} for which to collect methods.
     * @return a {@link Method} array required to implement for {@code token}.
     */
    private Method[] getMethods(Class<?> token) {
        Map<Signature, Method> map = new HashMap<>();
        List<Method> methods = new ArrayList<>(Stream.of(token.getMethods(), token.getDeclaredMethods())
                .flatMap(Stream::of).filter(method -> !Modifier.isPrivate(method.getModifiers())).toList());
        while (token != null) {
            methods.addAll(Arrays.stream(token.getDeclaredMethods())
                    .filter(method -> Modifier.isAbstract(method.getModifiers())).toList());
            for (Method method : methods) {
                map.merge(new Signature(method), method, (old, now) -> {
                    if (now.getReturnType().isAssignableFrom(old.getReturnType())) {
                        return old;
                    }
                    return now;
                });
            }
            token = token.getSuperclass();
            methods.clear();
        }
        return map.values().toArray(Method[]::new);
    }

    /**
     * Generates the {@link String} form of {@code token}'s methods.
     * {@link #getMethods(Class)} is used to provide methods. Final, native and volatile methods are discarded.
     *
     * @param token {@link Class} token for which to generate methods.
     * @return the {@link String} containing the code for {@code token}'s methods.
     * @throws ImplerException if there was an error generating methods.
     */
    private String genMethods(Class<?> token) throws ImplerException {
        Method[] methods = getMethods(token);

        StringBuilder sb = new StringBuilder();
        for (Method method : methods) {
            if (!Modifier.isFinal(method.getModifiers())
                    && !Modifier.isNative(method.getModifiers())
                    && !Modifier.isVolatile(method.getModifiers())) {
                sb.append(genExecutable(method));
            }
        }

        return sb.toString();
    }

    /**
     * Generates the {@link String} form of the implementation of {@code token}.
     * Implementation consists of class header ({@link #genClassHead(Class)}), constructors if {@code token}
     * is a {@link Class} ({@link #genConstructors(Class)}) and methods ({@link #genMethods(Class)}).
     *
     * @param token {@link Class} token for which to generate code.
     * @return the {@link String} containing the full code of the implementation.
     * @throws ImplerException thrown by the generation methods.
     */
    private String genCode(Class<?> token) throws ImplerException {
        StringBuilder sb = new StringBuilder();
        sb.append(genClassHead(token));
        if (!token.isInterface()) {
            sb.append(genConstructors(token));
        }
        sb.append(genMethods(token));
        sb.append('}').append(genLineSeparation(1));
        return sb.toString();
    }

    /**
     * Creates parent directories for given {@link Path}.
     *
     * @param root {@link Path} where to attempt creating directories.
     * @throws ImplerException if the directories cannot be created.
     */
    private void createDirectories(Path root) throws ImplerException {
        if (root.getParent() != null) {
            try {
                Files.createDirectories(root.getParent());
            } catch (IOException e) {
                throw new ImplerException("Cannot create directory for the implementation", e);
            } catch (SecurityException e) {
                throw new ImplerException("No access to create directory for implementation", e);
            }
        }
    }

    /**
     * Generates {@link Path} to specified file with specified file extension, extending {@code root}.
     *
     * @param root  the {@link Path} used to generate new one.
     * @param token {@link Class} token for which to generate {@link Path}.
     * @param type  desired file extension.
     * @return the generated {@link Path}.
     */
    private Path genPath(Path root, Class<?> token, String type) {
        return Paths.get(root.toString(), token.getPackageName().replace(".", File.separator),
                token.getSimpleName() + "Impl." + type);
    }

    /**
     * Used to generate the implementation for {@code token} and write it to output.
     *
     * @param token {@link Class} token to create implementation for.
     * @param root  root directory.
     * @throws ImplerException if one of the following is true:
     *                         <ol>
     *                             <li>Token is null</li>
     *                             <li>Root is null</li>
     *                             <li>Token is a class or interface that cannot be implemented</li>
     *                             <li>Creation of implementation file fails</li>
     *                             <li>Output of the code to the created file fails</li>
     *                         </ol>
     * @see #genCode(Class)
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        checkNull(token, "Class token is null");
        checkNull(root, "Root path is null");

        if (token.isPrimitive()) {
            throw new ImplerException("Cannot implement primitive class");
        }
        if (Modifier.isFinal(token.getModifiers())) {
            throw new ImplerException("Cannot implement final class");
        }
        if (token == Enum.class) {
            throw new ImplerException("Cannot implement Enum class");
        }
        if (privateCheck(token)) {
            throw new ImplerException("Cannot implement private nested interface or class");
        }
        if (token.isArray()) {
            throw new ImplerException("Cannot implement array");
        }

        root = genPath(root, token, "java");
        createDirectories(root);
        try (BufferedWriter out = Files.newBufferedWriter(root, StandardCharsets.UTF_8)) {
            out.write(genCode(token));
        } catch (IOException e) {
            throw new ImplerException("Cannot write the generated implementation code", e);
        }
    }

    /**
     * Deletes the specified directory.
     *
     * @param root {@link Path} of directory to delete.
     * @throws IOException if file walk fails.
     */
    private void clean(Path root) throws IOException {
        if (Files.exists(root)) {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * Used to generate the implementation for {@code token}, convert it into jar and write it to output.
     *
     * @param token   {@link Class} token to create implementation for.
     * @param jarFile desired jar location.
     * @throws ImplerException if one of the following is true:
     *                         <ol>
     *                             <li>Token is null</li>
     *                             <li>JarFile is null</li>
     *                             <li>Token is a class or interface that cannot be implemented</li>
     *                             <li>Creation of implementation file fails</li>
     *                             <li>Generated implementation could not be compiled</li>
     *                             <li>Directory for compilation could not be created or deleted</li>
     *                             <li>Output to java implementation or jar file fails</li>
     *                         </ol>
     * @see #implement(Class, Path)
     */
    @SuppressWarnings("ThrowFromFinallyBlock")
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        checkNull(token, "Class token is null");
        checkNull(jarFile, "Class token is null");

        createDirectories(jarFile);
        Path tmpDirectory;
        String classPath;
        try {
            classPath = Path.of(token.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException e) {
            throw new ImplerException("Failed to convert URL to URI", e);
        }
        try {
            tmpDirectory = Files.createTempDirectory(jarFile.toAbsolutePath().getParent(), "temp");
        } catch (IOException e) {
            throw new ImplerException("Could not create directory for compiling classes", e);
        } catch (SecurityException e) {
            throw new ImplerException("No access to create compile directory", e);
        }

        try {
            implement(token, tmpDirectory);
            String[] args = new String[]{
                    "-encoding",
                    "utf8",
                    "-cp",
                    classPath,
                    genPath(tmpDirectory, token, "java").toString()
            };
            final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler.run(null, null, null, args) != 0) {
                throw new ImplerException("Could not compile files for jar");
            }
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            try (JarOutputStream writeJar = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
                ZipEntry zipEntry = new ZipEntry(
                        token.getPackageName().replace(".", "/") + "/" + token.getSimpleName() + "Impl.class");
                writeJar.putNextEntry(zipEntry);
                Files.copy(genPath(tmpDirectory, token, "class"), writeJar);
            } catch (IOException e) {
                throw new ImplerException("Could not write jar file", e);
            } catch (SecurityException e) {
                throw new ImplerException("Error with access to output", e);
            }
        } finally {
            try {
                clean(tmpDirectory);
            } catch (IOException e) {
                throw new ImplerException("Could not delete temporary directory", e);
            }
        }
    }

    /**
     * Main method used to call the class.
     *
     * @param args: <ol>
     *              <li>implement jar: -jar ClassName JarPath</li>
     *              <li>implement: Classname</li>
     *              </ol>
     */
    public static void main(String[] args) {
        if (args == null) {
            System.out.println("Null arguments provided");
            return;
        }
        for (String arg : args) {
            if (arg == null) {
                System.out.println("Non-null arguments required");
            }
        }
        if (args.length != 1 && args.length != 3) {
            System.out.println("Wrong number of arguments");
            return;
        }

        Implementor implementor = new Implementor();
        try {
            if (args.length == 1) {
                implementor.implement(Class.forName(args[0]), Paths.get(""));
            } else if (args[0].equals("-jar")) {
                implementor.implementJar(Class.forName(args[1]), Paths.get(args[2]));
            } else {
                System.out.println("Incorrect input");
            }
        } catch (ImplerException e) {
            System.out.println("Error implementing: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("Required class not found: " + e.getMessage());
        }
    }
}
