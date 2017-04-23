package org.orienteer.core.boot.loader.util;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author Vitaliy Gonchar
 * Class for work with jar contents.
 */
class JarUtils {
    private static final Logger LOG = LoggerFactory.getLogger(JarUtils.class);
    private static final String POM_FOLDER_NAME = "pom/";
    private Path pomFolder;
    private final Path modulesFolder;

    JarUtils(InitUtils initUtils) {
        this.modulesFolder = initUtils.getPathToModulesFolder();
    }

    public Optional<Path> getPomFromJar(String path) {
        return getPomFromJar(Paths.get(path));
    }

    public Optional<Path> getPomFromJar(Path path) {
        Optional<Path> result = Optional.absent();
        try {
            JarFile jarFile = new JarFile(path.toFile());
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                if (jarEntry.getName().endsWith("pom.xml")) {
                    result = unarchivePomFile(jarFile, jarEntry);
                    break;
                }
            }
            jarFile.close();
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.error("Cannot open file to read. File: " + path.toAbsolutePath());
                e.printStackTrace();
            }
        }
        return result;
    }

    private Optional<Path> unarchivePomFile(JarFile jarFile, JarEntry jarEntry) throws IOException {
        Optional<Path> resultOptional = Optional.absent();

        int pointer = jarEntry.getName().lastIndexOf("/") + 1;
        String fileName = jarEntry.getName().substring(pointer);
        pointer = jarFile.getName().lastIndexOf("/") + 1;
        if (pomFolder == null) {
            String folder = jarFile.getName().substring(0, pointer);
            pomFolder = Paths.get(folder + POM_FOLDER_NAME);
        }
        if (!Files.exists(pomFolder))
            Files.createDirectory(pomFolder);
        Path path = pomFolder.resolve(jarFile.getName().substring(pointer).replace("jar", fileName));

        try (BufferedInputStream in = new BufferedInputStream(jarFile.getInputStream(jarEntry));
             BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(path))){
            int readBytes;
            byte [] buff = new byte[4096];
            while ((readBytes = in.read(buff)) != -1) {
                out.write(buff, 0, readBytes);
            }
            resultOptional = Optional.of(path);
        } catch (IOException e) {
            Files.deleteIfExists(path);
            if (LOG.isDebugEnabled()) {
                LOG.error("Cannot unarchive " + jarEntry.getName());
                e.printStackTrace();
            }
        }
        return resultOptional;
    }

    public List<Path> readJarsInArtifactsFolder() {
        return readJarsInFolder(modulesFolder);
    }

    public List<Path> readNewJarsInFolder(String folder, List<Path> oldJars) {
        return readNewJarsInFolder(Paths.get(folder), oldJars);
    }

    public List<Path> readJarsInFolder(Path folder) {
        return readJarsInFolder(folder, new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });
    }

    public List<Path> readJarsInFolder(String folder) {
        return readJarsInFolder(Paths.get(folder), new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });
    }

    private List<Path> readJarsInFolder(Path folder, FilenameFilter filter) {
        if (!Files.isDirectory(folder)) return Lists.newArrayList();
        List<Path> jars = Lists.newArrayList();
        File file = folder.toFile();
        File[] list = file.listFiles(filter);
        for (File f : list) {
            jars.add(f.toPath().toAbsolutePath());
        }
        return jars;
    }


    public List<Path> readNewJarsInFolder(Path folder, final List<Path> jars) {
        List<Path> jarsInFolder = readJarsInFolder(folder, new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar") && !jars.contains(dir.toPath().resolve(name));
            }
        });
        if (jarsInFolder != null) jars.addAll(jarsInFolder);
        return jarsInFolder != null ? jarsInFolder : Lists.<Path>newArrayList();
    }

    public Optional<String> searchOrienteerInitModule(Path pathToJar) {
        Optional<String> fullClassName = Optional.absent();
        if (!pathToJar.toString().endsWith(".jar")) return fullClassName;

        final String initModuleStart = "org.orienteer";
        final String initModuleEnd   = "Initializer.class";
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(pathToJar.toFile());

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                String entryName = jarEntry.getName().replace('/', '.');
                if (!entryName.contains("org."))
                    continue;
                entryName = entryName.substring(entryName.indexOf("org."));
                if (entryName.startsWith(initModuleStart) && entryName.endsWith(initModuleEnd)) {
                    fullClassName = Optional.of(entryName.substring(0, entryName.indexOf(".class")));
                    break;
                }
            }
            jarFile.close();
        } catch (IOException e) {
            LOG.error("Cannot read jar file: " + pathToJar.toAbsolutePath());
            if (LOG.isDebugEnabled()) e.printStackTrace();
        }
        return fullClassName;
    }

    public Set<String> getAllClassNamesInModule(Path pathToJar) throws IOException {
        if (!pathToJar.toString().endsWith(".jar")) return Collections.emptySet();
        Set<String> classNames = new HashSet<>();
        JarFile jarFile = new JarFile(pathToJar.toFile());
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            String entryName = jarEntry.getName().replace('/', '.');
            if (!entryName.contains("org."))
                continue;
            entryName = entryName.substring(entryName.indexOf("org."));
            if (entryName.endsWith(".class")) {
                classNames.add(entryName.substring(0, entryName.indexOf(".class")));
            }
        }

        return classNames;
    }

    public void deletePomFolder() {
        if (pomFolder == null) return;
        try {
            DirectoryStream<Path> paths = Files.newDirectoryStream(pomFolder);
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
            Files.delete(pomFolder);
        } catch (IOException e) {
            LOG.error("Cannot delete pom.xml files in folder: " + pomFolder.toAbsolutePath());
            if (LOG.isDebugEnabled()) e.printStackTrace();
        }
    }
}