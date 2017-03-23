package org.orienteer.core.boot.loader;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import org.eclipse.aether.artifact.Artifact;
import org.orienteer.core.boot.loader.util.MavenResolver;
import org.orienteer.core.boot.loader.util.OModuleMetadata;
import org.orienteer.core.boot.loader.util.OrienteerClassLoaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Vitaliy Gonchar
 */
public class OrienteerClassLoader extends URLClassLoader {
	
	private final MavenResolver resolver = MavenResolver.get();

    private static final Logger LOG = LoggerFactory.getLogger(OrienteerClassLoader.class);

    private static ClassLoader orienteerClassLoader;
    private static OrienteerClassLoader trustedClassLoader;
    private static OrienteerClassLoader untrustedClassLoader;
    private static boolean useUnTrusted = true;
    private static boolean useOrienteerClassLoader = false;

    /**
     * Create trusted and untrusted OrienteerClassLoader
     * @param parent - classloader for delegate loading classes
     */
    public static void create(ClassLoader parent) {
        trustedClassLoader = new OrienteerClassLoader(parent);
    }

    /**
     * Get Orienteer classloader.
     * @return - return trusted or untrusted Orienteer classloader
     */
    public static ClassLoader getClassLoader() {
        return useUnTrusted ? untrustedClassLoader : (useOrienteerClassLoader ? orienteerClassLoader : trustedClassLoader);
    }

    /**
     * Disable using untrusted classloader and start using trusted Orienteer classloader.
     */
    public static void useTrustedClassLoader() {
        useUnTrusted = false;
        useOrienteerClassLoader = false;
    }

    /**
     * Disable using trusted classloader and use custom Orienteer classloader.
     */
    public static void useOrienteerClassLoader() {
        useOrienteerClassLoader = true;
        useUnTrusted = false;
    }

    /**
     * Use default classloader properties. Orienteer runs with untrusted classloader.
     */
    public static void useDefaultClassLoaderProperties() {
        useUnTrusted = true;
        useOrienteerClassLoader = false;
    }

    public static void clear() {
        untrustedClassLoader = null;
        trustedClassLoader = null;
    }

    /**
     * Constructor for trusted Orienteer classloader.
     * Test modules and resolve modules dependencies.
     * Create untrusted Orienteer classloader.
     * @param parent - classloader for delegate loading classes
     */
	private OrienteerClassLoader(ClassLoader parent) {
		super(new URL[0], parent);
        orienteerClassLoader = parent;
        Map<Path, OModuleMetadata> modules = OrienteerClassLoaderUtil.getMetadataModulesInMap();
        List<Path> jars = OrienteerClassLoaderUtil.getJarsInModulesFolder();

        List<OModuleMetadata> modulesForLoad;
        if (modules.isEmpty()) {
            modulesForLoad = createModules(jars);
        } else {
            modulesForLoad = getUpdateModules(modules);
            resolver.setDependenciesInModules(modulesForLoad);
        }
        modulesForLoad = searchTrustyModules(modulesForLoad, parent);
        addModulesToClassLoaderResources(getTrustedModules(modulesForLoad));
        untrustedClassLoader = new OrienteerClassLoader(getUnTrustedModules(modulesForLoad), this);
    }

    /**
     * Constructor for  untrusted Orienteer classloader.
     * @param unTrustedModules - untrusted modules for load in untrusted classloader
     * @param parent - parent classloader
     */
    private OrienteerClassLoader(List<OModuleMetadata> unTrustedModules, ClassLoader parent) {
	    super(new URL[0], parent);
	    addModulesToClassLoaderResources(unTrustedModules);
    }

    /**
     * Test Orienteer modules - load it together.
     * @param unTrustedModules - untrusted modules
     * @param parent - parent classloader
     * @return list with tested Orienteer modules
     */
    private List<OModuleMetadata> searchTrustyModules(List<OModuleMetadata> unTrustedModules,
                                                      ClassLoader parent) {
        List<OModuleMetadata> trustyModules = Lists.newArrayList();
        OrienteerSandboxClassLoader sandboxClassLoader = new OrienteerSandboxClassLoader(parent);
        for (OModuleMetadata module : unTrustedModules) {
            boolean isTrusted = sandboxClassLoader.test(module);
            if (isTrusted) {
                trustyModules.add(module);
            } else {
                sandboxClassLoader = new OrienteerSandboxClassLoader(parent);
                sandboxClassLoader.loadResourcesInClassLoader(trustyModules);
            }
        }
        return trustyModules;
    }

    private void addModulesToClassLoaderResources(List<OModuleMetadata> modules) {
        for(OModuleMetadata metadata : modules) {
            try {
                addURL(metadata.getMainArtifact().getFile().toURI().toURL());
                for (Artifact artifact : metadata.getDependencies()) {
                    addURL(artifact.getFile().toURI().toURL());
                }
            } catch (MalformedURLException e) {
                LOG.error("Can't load dependency", e);
            }
        }
    }
	
	private List<OModuleMetadata> createModules(List<Path> jars) {
        List<OModuleMetadata> modulesForLoad = resolver.getResolvedModulesMetadata(jars);
        if (modulesForLoad.size() > 0) {
            OrienteerClassLoaderUtil.createMetadata(modulesForLoad);
        } else OrienteerClassLoaderUtil.deleteMetadataFile();

        return modulesForLoad;
    }
	
    private List<OModuleMetadata> getUpdateModules(Map<Path, OModuleMetadata> modules) {
        resolveModulesWithoutMainJar(modules);
	    List<Path> jars = OrienteerClassLoaderUtil.getJarsInModulesFolder();
	    List<OModuleMetadata> modulesForWrite = getModulesForAddToMetadata(jars, modules);

	    if (modulesForWrite.size() > 0) {
            OrienteerClassLoaderUtil.updateModulesInMetadata(modulesForWrite);
        }

        modules = OrienteerClassLoaderUtil.getMetadataModulesForLoadInMap();

        return getModulesForLoad(modules.values());
    }
    
    private List<OModuleMetadata> getModulesForAddToMetadata(List<Path> jars, Map<Path, OModuleMetadata> modules) {
        List<Path> modulesForWrite = Lists.newArrayList();
        Set<Path> jarsInMetadata = modules.keySet();
        for (Path pathToJar : jars) {
            if (!jarsInMetadata.contains(pathToJar)) {
                modulesForWrite.add(pathToJar);
            }
        }
        return resolver.getResolvedModulesMetadata(modulesForWrite);
    }

    private List<OModuleMetadata> getModulesForLoad(Collection<OModuleMetadata> modules) {
        List<OModuleMetadata> modulesForLoad = Lists.newArrayList();
        for (OModuleMetadata metadata : modules) {
            if (metadata.isLoad()) modulesForLoad.add(metadata);
        }
        return modulesForLoad;
    }

    private void resolveModulesWithoutMainJar(Map<Path, OModuleMetadata> modules) {
        List<OModuleMetadata> modulesWithoutMainJar = getModulesWithoutMainJar(modules.values());
        if (modulesWithoutMainJar.size() > 0) {
            resolver.downloadModules(modulesWithoutMainJar);
            List<Path> keysForDelete = Lists.newArrayList();
            for (Path key : modules.keySet()) {
                if (key.toString().contains(OrienteerClassLoaderUtil.WITHOUT_JAR)) {
                    keysForDelete.add(key);
                }
            }
            for (Path key : keysForDelete) {
                modules.remove(key);
            }
            for (OModuleMetadata module : modulesWithoutMainJar) {
                modules.put(module.getMainArtifact().getFile().toPath(), module);
            }
            OrienteerClassLoaderUtil.updateModulesJarsInMetadata(modulesWithoutMainJar);
        }
    }

    private List<OModuleMetadata> getModulesWithoutMainJar(Collection<OModuleMetadata> modules) {
        List<OModuleMetadata> result = Lists.newArrayList();
        for (OModuleMetadata module : modules) {
            File jar = module.getMainArtifact().getFile();
            if (jar == null || !jar.exists()) {
                result.add(module);
            }
        }
        return result;
    }

    private List<OModuleMetadata> getTrustedModules(List<OModuleMetadata> modules) {
	    List<OModuleMetadata> trustedModules = Lists.newArrayList();
	    for (OModuleMetadata module : modules) {
	        if (module.isTrusted()) trustedModules.add(module);
        }
	    return trustedModules;
    }

    private List<OModuleMetadata> getUnTrustedModules(List<OModuleMetadata> modules) {
        List<OModuleMetadata> unTrustedModules = Lists.newArrayList();
        for (OModuleMetadata module : modules) {
            if (!module.isTrusted()) unTrustedModules.add(module);
        }
        return unTrustedModules;
    }

    private static class OrienteerSandboxClassLoader extends URLClassLoader {

        public OrienteerSandboxClassLoader(ClassLoader parent) {
            super(new URL[]{}, parent);
        }

        public void loadResourcesInClassLoader(List<OModuleMetadata> modules) {
            for (OModuleMetadata module : modules) {
                loadResourceInClassLoader(module);
            }
        }

        private void loadResourceInClassLoader(OModuleMetadata module) {
            try {
                addURL(module.getMainArtifact().getFile().toURI().toURL());
                for (Artifact artifact : module.getDependencies()) {
                    addURL(artifact.getFile().toURI().toURL());
                }
            } catch (MalformedURLException e) {
                LOG.error("Cannot load dependency.", e);
            }
        }

        public boolean test(OModuleMetadata module) {
            boolean trusted = false;
            try {
                loadResourceInClassLoader(module);
                Path pathToJar = module.getMainArtifact().getFile().toPath();
                Optional<String> className = OrienteerClassLoaderUtil.searchOrienteerInitModule(pathToJar);
                if (className.isPresent()) {
                    loadClass(className.get());
                    trusted = true;
                }
            } catch (ClassNotFoundException e) {
                LOG.warn("Cannot load init class for module: " + module);
                if (LOG.isDebugEnabled()) e.printStackTrace();
            }
            return trusted;
        }
    }

    @Override
    public String toString() {
        String trusted = "trustedOrienteerClassLoader";
        String unTrusted = "unTrustedOrienteerClassLoader";
        String custom = "customOrienteerClassLoader";
        return useUnTrusted ? unTrusted : (useOrienteerClassLoader ? custom : trusted);
    }
}