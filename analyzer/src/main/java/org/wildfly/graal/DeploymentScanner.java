/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.graal;

import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import aQute.bnd.classfile.ClassFile;
import aQute.lib.io.ByteBufferDataInput;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.JarIndexer;

import java.io.DataInput;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import java.util.List;

public class DeploymentScanner implements AutoCloseable {

    private static final String IGNORE_RESPONSE_PROPERTIES = "json.ignore.responses";
    private static final String ADDITIONL_JSON_CLASSES = "json.additional.classes";
    private final Path binary;
    private final Path tempDirectory;
    private boolean verbose;
    private final Set<Pattern> excludeArchivesFromScan;
    private ArchiveType archiveType;
    private DeploymentScanner parent;
    private final boolean isArchive;

    Set<String> ignoredJsonMethods = new HashSet<>();
    Set<String> additionalJsonClasses = new HashSet<>();
    private final Properties props;

    public DeploymentScanner(Path binary, boolean verbose, Set<Pattern> excludeArchivesFromScan, Properties props) throws IOException {
        this(null, binary, verbose, excludeArchivesFromScan, props);
    }

    private DeploymentScanner(DeploymentScanner parent, Path binary, boolean verbose, Set<Pattern> excludeArchivesFromScan, Properties props) throws IOException {
        this.parent = parent;
        this.props = props;
        this.tempDirectory = parent == null ? Files.createTempDirectory("analyzer") : parent.tempDirectory;
        this.verbose = verbose;
        this.excludeArchivesFromScan = excludeArchivesFromScan;
        String ignored = props.getProperty(IGNORE_RESPONSE_PROPERTIES);
        if (ignored != null) {
            String[] arr = ignored.split(",");
            for (String s : arr) {
                s = s.trim();
                if (!s.isEmpty()) {
                    ignoredJsonMethods.add(s);
                }
            }
        }
        String additional = props.getProperty(ADDITIONL_JSON_CLASSES);
        if (additional != null) {
            String[] arr = additional.split(",");
            for (String s : arr) {
                s = s.trim();
                if (!s.isEmpty()) {
                    additionalJsonClasses.add(s);
                }
            }
        }
        if (!Files.exists(binary)) {
            throw new IllegalArgumentException(binary.normalize().toAbsolutePath() + " is not an archive");
        }
        isArchive = !Files.isDirectory(binary);
        FileNameParts fileNameParts = FileNameParts.parse(binary);
        this.archiveType = fileNameParts.archiveType;

        if (parent == null) {
            this.binary = binary;
        } else {
            if (isArchive) {
                // We need to copy the nested archive out of the containing archive
                // The binary argument comes from the Jar filesystem, while the tempDirectory is in the default filesystem
                this.binary = Files.createTempFile(tempDirectory, fileNameParts.coreName, fileNameParts.archiveType.suffix);
                Files.delete(this.binary);
                Files.copy(binary, this.binary);
            } else {
                this.binary = binary;
            }
        }
    }

    @Override
    public void close() {
        if (parent != null && binary != null) {
            try {
                if (isArchive) {
                    Files.delete(binary);
                }
            } catch (IOException ignore) {
            }
        }
        if (parent == null) {
            IoUtils.recursiveDelete(tempDirectory);
        }
    }

    public void scan(Set<String> classes, Set<String> jsonBClasses) throws Exception {
        jsonBClasses.addAll(additionalJsonClasses);
        DeploymentScanContext ctx = new DeploymentScanContext(classes, jsonBClasses);
        scan(ctx);
    }

    private void scan(DeploymentScanContext ctx) throws Exception {
        scanClasses(ctx);
        FileSystem fs = isArchive ? ZipUtils.newFileSystem(binary) : binary.getFileSystem();
        try {
            Path rootPath = isArchive ? fs.getPath("/") : binary;
            scanTypesAndChildren(rootPath, ctx);
        } finally {
            if (isArchive) {
                fs.close();
            }
        }
    }

    private static String formatClassName(String name) {
        name = name.replace("/", ".");
        return name;
    }

    /**
     * Extracts all JSON-mappable types from a Jandex Type, unwrapping generic wrappers.
     * Handles CompletionStage, CompletableFuture, Uni, Multi, Collections, arrays, etc.
     *
     * @param type The Jandex type to extract from
     * @param types Set to collect discovered class names
     * @param processedTypes Set to track already processed types (prevents infinite recursion)
     */
    private void extractJsonTypes(Type type, Set<String> types, Set<String> processedTypes) {
        if (type == null) {
            return;
        }

        switch (type.kind()) {
            case CLASS:
                String className = formatClassName(type.asClassType().name().toString());

                // Skip if already processed
                if (processedTypes.contains(className)) {
                    return;
                }
                processedTypes.add(className);

                // Special handling for Response - cannot determine runtime type
                if (className.equals("jakarta.ws.rs.core.Response")) {
                    return; // Skip - runtime type unknown
                }

                types.add(className);
                break;

            case PARAMETERIZED_TYPE:
                ParameterizedType paramType = type.asParameterizedType();
                String ownerClassName = formatClassName(paramType.name().toString());

                // Unwrap common async/reactive wrappers
                if (isWrapperType(ownerClassName)) {
                    // Extract the wrapped type(s)
                    for (Type arg : paramType.arguments()) {
                        extractJsonTypes(arg, types, processedTypes);
                    }
                } else {
                    // For other generic types (e.g., custom generics), add the owner class
                    if (!processedTypes.contains(ownerClassName)) {
                        processedTypes.add(ownerClassName);
                        types.add(ownerClassName);
                    }

                    // Also process type arguments
                    for (Type arg : paramType.arguments()) {
                        extractJsonTypes(arg, types, processedTypes);
                    }
                }
                break;

            case ARRAY:
                // Extract the component type from arrays
                extractJsonTypes(type.asArrayType().componentType(), types, processedTypes);
                break;

            case WILDCARD_TYPE:
                // For wildcards like "? extends Foo", extract the bound
                Type bound = type.asWildcardType().extendsBound();
                if (bound != null) {
                    extractJsonTypes(bound, types, processedTypes);
                }
                break;

            case TYPE_VARIABLE:
                // Type variables (T, E, etc.) - can't resolve without more context
                // Skip for now
                break;

            case PRIMITIVE:
            case VOID:
                // Primitives don't need JSON mapping registration
                break;

            default:
                // Other kinds - log if verbose
                if (verbose) {
                    System.out.println("Unhandled type kind: " + type.kind() + " for type: " + type);
                }
                break;
        }
    }

    /**
     * Checks if a class is a wrapper type that should be unwrapped to find the actual payload.
     */
    private boolean isWrapperType(String className) {
        return className.equals("java.util.concurrent.CompletionStage")
                || className.equals("java.util.concurrent.CompletableFuture")
                || className.equals("io.smallrye.mutiny.Uni")
                || className.equals("io.smallrye.mutiny.Multi")
                || className.equals("java.util.List")
                || className.equals("java.util.Set")
                || className.equals("java.util.Collection")
                || className.equals("java.util.Map")
                || className.equals("java.util.Optional")
                || className.equals("jakarta.ws.rs.core.GenericEntity");
    }

    /**
     * Process @Produces or @Consumes annotation to extract JSON-mapped types.
     *
     * @param mi Method to analyze
     * @param ci Containing class
     * @param annotationName Annotation to look for (jakarta.ws.rs.Produces or jakarta.ws.rs.Consumes)
     * @param ctx Scan context to add discovered types
     * @param checkReturnType If true, extract from return type; if false, extract from parameters
     */
    private void processJsonAnnotation(MethodInfo mi, ClassInfo ci, String annotationName,
                                       DeploymentScanContext ctx, boolean checkReturnType) {
        AnnotationInstance instance = mi.annotation(annotationName);
        if (instance == null) {
            // Check class-level annotation
            instance = ci.annotation(annotationName);
        }

        if (instance != null && hasJsonMediaType(instance)) {
            Set<String> discoveredTypes = new HashSet<>();
            Set<String> processedTypes = new HashSet<>();

            if (checkReturnType) {
                // For @Produces - extract from return type
                Type returnType = mi.returnType();

                // Special handling for Response type
                if (isResponseType(returnType)) {
                    String methodName = ci.name() + "#" + mi.name();
                    if (!ignoredJsonMethods.contains(methodName)) {
                        throw new RuntimeException("Found unhandled JAXRS REST method Response return type. Add the method " + methodName
                                + " to the analyzer property " + IGNORE_RESPONSE_PROPERTIES + ". And check what java type it is hiding");
                    }
                    return; // Skip - cannot determine runtime type
                }

                if (verbose) {
                    System.out.println("Processing @Produces method: " + ci.name() + "#" + mi.name()
                            + " return type: " + returnType);
                }

                extractJsonTypes(returnType, discoveredTypes, processedTypes);
            } else {
                // For @Consumes - extract from method parameters
                List<Type> parameters = mi.parameterTypes();
                for (Type paramType : parameters) {
                    // Skip JAX-RS framework types (Context, PathParam, QueryParam, etc. are not JSON bodies)
                    if (!isJaxRsFrameworkType(paramType)) {
                        if (verbose) {
                            System.out.println("Processing @Consumes method: " + ci.name() + "#" + mi.name()
                                    + " parameter type: " + paramType);
                        }
                        extractJsonTypes(paramType, discoveredTypes, processedTypes);
                    }
                }
            }

            // Add all discovered types to the context
            ctx.jsonBClasses.addAll(discoveredTypes);

            if (verbose && !discoveredTypes.isEmpty()) {
                System.out.println("Discovered JSON types from " + ci.name() + "#" + mi.name() + ": " + discoveredTypes);
            }
        }
    }

    /**
     * Check if the annotation specifies application/json media type.
     */
    private boolean hasJsonMediaType(AnnotationInstance annotation) {
        for (AnnotationValue v : annotation.values()) {
            for (AnnotationValue vv : v.asArrayList()) {
                String[] mediaTypes = vv.asString().split(",");
                for (String mediaType : mediaTypes) {
                    if (mediaType.trim().equals("application/json")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if type is jakarta.ws.rs.core.Response (which hides the actual type).
     */
    private boolean isResponseType(Type type) {
        if (type.kind() == Type.Kind.CLASS) {
            return type.asClassType().name().equals(DotName.createSimple("jakarta.ws.rs.core.Response"));
        }
        return false;
    }

    /**
     * Check if type is a JAX-RS framework type that won't be JSON-serialized.
     */
    private boolean isJaxRsFrameworkType(Type type) {
        if (type.kind() == Type.Kind.CLASS) {
            String className = type.asClassType().name().toString();
            return className.startsWith("jakarta.ws.rs.core.")
                    || className.startsWith("jakarta.ws.rs.container.")
                    || className.startsWith("jakarta.servlet.");
        }
        return false;
    }

    private void scanClasses(DeploymentScanContext ctx) throws IOException {
        Indexer indexer = new Indexer();
        Index index = isArchive ? JarIndexer.createJarIndex(binary.toFile(),
                indexer, false, true, false).getIndex()
                : DirectoryIndexer.indexDirectory(binary.toFile(), indexer);
        for (ClassInfo ci : index.getKnownClasses()) {
            ctx.classes.add(formatClassName(ci.name().toString()));
            for (MethodInfo mi : ci.methods()) {
                // Process @Produces (response types)
                processJsonAnnotation(mi, ci, "jakarta.ws.rs.Produces", ctx, true);

                // Process @Consumes (request types)
                processJsonAnnotation(mi, ci, "jakarta.ws.rs.Consumes", ctx, false);
            }
        }
        int i = binary.toFile().getName().lastIndexOf(".");
        String ext = binary.toFile().getName().substring(i + 1);
        String name = binary.toFile().getName().substring(0, i) + "-jandex";
        Path parent = binary.getParent();
        Path jd = parent == null ? Paths.get(name + "." + ext) : parent.resolve(name + "." + ext);
        if (Files.exists(jd)) {
            Files.delete(jd);
        }
    }

    private void scanTypesAndChildren(Path archiveContentRoot, DeploymentScanContext ctx) throws Exception {
        Files.walkFileTree(archiveContentRoot, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new NestedWarOrExplodedArchiveFileVisitor(archiveContentRoot, isArchive) {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".class")) {
                    if (archiveType != ArchiveType.EAR) {
                        scanClass(file, ctx);
                    }
                } else if (ArchiveType.isArchiveName(file)) {
                    Path relativeFile = archiveContentRoot.relativize(file);
                    if (archiveType.isValidArchiveLocation(relativeFile)) {
                        scanWithNestedScanner(file, ctx);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                FileVisitResult result = super.preVisitDirectory(dir, attrs);
                if (result == FileVisitResult.CONTINUE) {
                    return FileVisitResult.CONTINUE;
                }
                Path relativeFile = archiveContentRoot.relativize(dir);
                if (archiveType.isValidArchiveLocation(relativeFile)) {
                    scanWithNestedScanner(dir, ctx);
                }
                return result;
            }
        });
    }

    private void scanWithNestedScanner(Path file, DeploymentScanContext ctx) throws IOException {
        // Check it is not an excluded archive
        for (Pattern exclude : excludeArchivesFromScan) {
            if (exclude.matcher(file.getFileName().toString()).matches()) {
                return;
            }
        }

        try (DeploymentScanner nestedScanner = new DeploymentScanner(DeploymentScanner.this, file, verbose, excludeArchivesFromScan, props)) {
            try {
                nestedScanner.scan(ctx);
            } catch (RuntimeException | IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void scanClass(Path file, DeploymentScanContext ctx) throws IOException {
        byte[] content = Files.readAllBytes(file);
        DataInput in = ByteBufferDataInput.wrap(content);
        ClassFile clazz = ClassFile.parseClassFile(in);
        ctx.classes.add(formatClassName(clazz.this_class));
    }

    private static class FileNameParts {

        private final String coreName;
        private final ArchiveType archiveType;

        public FileNameParts(String coreName, ArchiveType archiveType) {
            this.coreName = coreName;
            this.archiveType = archiveType;
        }

        static FileNameParts parse(Path binary) {
            String filename = binary.getFileName().toString();
            int index = filename.lastIndexOf(".");
            String suffix = filename.substring(index + 1);
            String core = filename.substring(0, index);
            return new FileNameParts(core, ArchiveType.parse(suffix));
        }
    }

    enum ArchiveType {
        EAR(".ear") {
            @Override
            public boolean isValidArchiveLocation(Path pathInArchive) {
                // Accept all war and jar files no matter the location
                return ArchiveType.isJar(pathInArchive) || ArchiveType.isWar(pathInArchive) || ArchiveType.isRar(pathInArchive) || ArchiveType.isSar(pathInArchive);
            }
        },
        WAR(".war") {
            @Override
            public boolean isValidArchiveLocation(Path pathInArchive) {

                // Only /WEB-INF/lib/*.jar is allowed
                if (!ArchiveType.isJar(pathInArchive)) {
                    return false;
                }
                if (pathInArchive.getNameCount() != 3) {
                    return false;
                }
                if (!pathInArchive.getName(0).toString().equals("WEB-INF") || !pathInArchive.getName(1).toString().equals("lib")) {
                    return false;
                }
                return true;
            }
        },
        JAR(".jar"),
        RAR(".rar") {
            @Override
            public boolean isValidArchiveLocation(Path pathInArchive) {
                return pathInArchive.getNameCount() == 1 && ArchiveType.isJar(pathInArchive);
            }
        },
        SAR(".sar");

        private final String suffix;

        ArchiveType(String suffix) {
            this.suffix = suffix;
        }

        public boolean isValidArchiveLocation(Path pathInArchive) {
            return false;
        }

        static ArchiveType parse(String s) {
            switch (s) {
                case "ear":
                    return EAR;
                case "war":
                    return WAR;
                case "jar":
                    return JAR;
                case "rar":
                    return RAR;
                case "sar":
                    return SAR;
                default:
                    throw new IllegalArgumentException(s);
            }
        }

        private static boolean isJar(Path pathInArchive) {
            return hasSuffix(pathInArchive, JAR.suffix);
        }

        private static boolean isWar(Path pathInArchive) {
            return hasSuffix(pathInArchive, WAR.suffix);
        }

        private static boolean isRar(Path pathInArchive) {
            return hasSuffix(pathInArchive, RAR.suffix);
        }

        private static boolean isSar(Path pathInArchive) {
            return hasSuffix(pathInArchive, SAR.suffix);
        }

        private static boolean hasSuffix(Path pathInArchive, String suffix) {
            return pathInArchive.getFileName().toString().endsWith(suffix);
        }

        static boolean isArchiveName(Path path) {
            for (ArchiveType type : ArchiveType.values()) {
                if (path.getFileName().toString().endsWith(type.suffix)) {
                    return true;
                }
            }
            return false;
        }
    }

    static class DeploymentScanContext {

        private final Set<String> classes;
        private final Set<String> jsonBClasses;

        private DeploymentScanContext(Set<String> classes, Set<String> jsonBClasses) {
            this.classes = classes;
            this.jsonBClasses = jsonBClasses;
        }
    }
}
