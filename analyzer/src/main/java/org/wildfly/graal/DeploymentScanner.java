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
import java.util.Set;
import java.util.regex.Pattern;

public class DeploymentScanner implements AutoCloseable {

    private final Path binary;
    private final Path tempDirectory;
    private boolean verbose;
    private final Set<Pattern> excludeArchivesFromScan;
    private ArchiveType archiveType;
    private DeploymentScanner parent;
    private final boolean isArchive;

    public DeploymentScanner(Path binary, boolean verbose, Set<Pattern> excludeArchivesFromScan) throws IOException {
        this(null, binary, verbose, excludeArchivesFromScan);
    }

    private DeploymentScanner(DeploymentScanner parent, Path binary, boolean verbose, Set<Pattern> excludeArchivesFromScan) throws IOException {
        this.parent = parent;
        this.tempDirectory = parent == null ? Files.createTempDirectory("analyzer") : parent.tempDirectory;
        this.verbose = verbose;
        this.excludeArchivesFromScan = excludeArchivesFromScan;

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
        if(parent == null) {
            IoUtils.recursiveDelete(tempDirectory);
        }
    }

    public void scan(Set<String> classes) throws Exception {
        DeploymentScanContext ctx = new DeploymentScanContext(classes);
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

    private void scanClasses(DeploymentScanContext ctx) throws IOException {
        Indexer indexer = new Indexer();
        Index index = isArchive ? JarIndexer.createJarIndex(binary.toFile(),
                indexer, false, true, false).getIndex()
                : DirectoryIndexer.indexDirectory(binary.toFile(), indexer);
        for (ClassInfo ci : index.getKnownClasses()) {
            ctx.classes.add(ci.name().toString());
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

        try (DeploymentScanner nestedScanner = new DeploymentScanner(DeploymentScanner.this, file, verbose, excludeArchivesFromScan)) {
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
        ctx.classes.add(clazz.this_class.replaceAll("/", "."));
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

        private DeploymentScanContext(Set<String> classes) {
            this.classes = classes;
        }
    }
}
