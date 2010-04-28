/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mooregreatsoftware.gradle.gitplugin

import java.util.regex.Pattern
import org.gradle.StartParameter
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskActionListener
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.DefaultClassPathRegistry
import org.gradle.api.internal.GroovySourceGenerationBackedClassGenerator
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.DefaultServiceRegistry
import org.gradle.api.internal.project.IProjectFactory
import org.gradle.api.internal.project.ProjectFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.TopLevelBuildServiceRegistry
import org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.api.internal.project.taskfactory.TaskFactory
import org.gradle.api.internal.tasks.DefaultTaskExecuter
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.invocation.Gradle
import org.gradle.cache.CacheFactory
import org.gradle.cache.DefaultCacheFactory
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.initialization.ClassLoaderFactory
import org.gradle.initialization.CommandLine2StartParameterConverter
import org.gradle.initialization.DefaultClassLoaderFactory
import org.gradle.initialization.DefaultCommandLine2StartParameterConverter
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultProjectDescriptorRegistry
import org.gradle.invocation.DefaultGradle
import org.gradle.util.ClasspathUtil
import org.gradle.util.GUtil
import static org.junit.Assert.assertTrue

/**
 * Most of this is copied from the testing framework Gradle uses.
 */
class GradleTestHelper {
    private static final ClassGenerator CLASS_GENERATOR = new GroovySourceGenerationBackedClassGenerator()
    private static final ITaskFactory TASK_FACTORY = new AnnotationProcessingTaskFactory(new TaskFactory(CLASS_GENERATOR))


    static <T extends GitTask> T createTask(Class<T> type) {
        createTask(type, createRootProject(), type.name)
    }


    static <T extends GitTask> T createTask(Class<T> type, Project project, String name) {
        Map taskMap = GUtil.map(Task.TASK_TYPE, type, Task.TASK_NAME, name)
        Task task = TASK_FACTORY.createTask((ProjectInternal) project, taskMap);
        assertTrue(type.isAssignableFrom(task.getClass()))
        return type.cast(task);
    }


    static DefaultProject createRootProject() {
        File rootDir = createTempDir()

        IProjectFactory projectFactory = createProjectFactory()
        ProjectDescriptor descriptor = createProjectDescriptor(rootDir)
        Gradle build = createBuild()
        Project project = projectFactory.createProject(descriptor, null, build)
        build.rootProject = project
        project
    }


    private static DefaultGradle createBuild() {
        DefaultServiceRegistry registry = createDefaultRegistry()
        StartParameter startParameter = new StartParameter()

        TopLevelBuildServiceRegistry serviceRegistryFactory = createServiceRegistry(registry, startParameter)

        new DefaultGradle(null, startParameter, serviceRegistryFactory)
    }


    private static DefaultProjectDescriptor createProjectDescriptor(File rootDir) {
        new DefaultProjectDescriptor(null, rootDir.name, rootDir, new DefaultProjectDescriptorRegistry())
    }


    private static IProjectFactory createProjectFactory() {
        StringScriptSource scriptSource = new StringScriptSource("embedded build file", "embedded")
        new ProjectFactory(scriptSource, CLASS_GENERATOR)
    }


    private static TopLevelBuildServiceRegistry createServiceRegistry(DefaultServiceRegistry registry, StartParameter startParameter) {
        TopLevelBuildServiceRegistry serviceRegistryFactory = new TopLevelBuildServiceRegistry(registry, startParameter)
        serviceRegistryFactory.add(TaskExecuter, new DefaultTaskExecuter({} as TaskActionListener))
        return serviceRegistryFactory
    }


    private static DefaultServiceRegistry createDefaultRegistry() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry()
        registry.add(ClassPathRegistry, new TestingClassPathRegistry())
        registry.add(CommandLine2StartParameterConverter, new DefaultCommandLine2StartParameterConverter())
        registry.add(CacheFactory, new DefaultCacheFactory())
        registry.add(ClassLoaderFactory, new DefaultClassLoaderFactory(registry.get(ClassPathRegistry) as ClassPathRegistry))
        return registry
    }


    private static File createTempDir() {
        File rootDir = File.createTempFile('GradleProject', 'test')
        rootDir.mkdir()
        rootDir.deleteOnExit()
        return rootDir
    }
}



public class TestingClassPathRegistry implements ClassPathRegistry {
    private final Scanner pluginLibs
    private final Scanner runtimeLibs
    private final List<Pattern> all = Arrays.asList(Pattern.compile(".+"))
    private final Map<String, List<Pattern>> classPaths = [:]

    private static abstract class Scanner {
        abstract void find(List<Pattern> patterns, Collection<File> into)
    }


    TestingClassPathRegistry() {
        File codeSource = findThisClass()
        runtimeLibs = new ClassPathScanner(codeSource)
        pluginLibs = runtimeLibs

        List<Pattern> groovyPatterns = toPatterns("groovy-all")

        classPaths.put("LOCAL_GROOVY", groovyPatterns)
        List<Pattern> gradleApiPatterns = toPatterns("gradle-\\w+", "ivy", "slf4j")
        gradleApiPatterns.addAll(groovyPatterns)
        classPaths.put("GRADLE_API", gradleApiPatterns)
        classPaths.put("GRADLE_CORE", toPatterns("gradle-core"))
        classPaths.put("ANT", toPatterns("ant", "ant-launcher"))
        classPaths.put("ANT_JUNIT", toPatterns("ant", "ant-launcher", "ant-junit"))
        classPaths.put("COMMONS_CLI", toPatterns("commons-cli"))
        classPaths.put("WORKER_PROCESS", toPatterns("gradle-core", "slf4j-api", "logback-classic", "logback-core", "jul-to-slf4j", "jansi", "jna", "jna-posix"))
        classPaths.put("WORKER_MAIN", toPatterns("gradle-core-worker"))
    }


    private File findThisClass() {
        URI location
        location = DefaultClassPathRegistry.class.getProtectionDomain().getCodeSource().getLocation().toURI()
        if (!location.getScheme().equals("file")) {
            throw new GradleException(String.format("Cannot determine Gradle home using codebase '%s'.", location))
        }
        new File(location.getPath())
    }


    private static List<Pattern> toPatterns(String... patternStrings) {
        List<Pattern> patterns = new ArrayList<Pattern>()
        for (String patternString: patternStrings) {
            patterns.add(Pattern.compile(patternString + "-.+"))
        }
        patterns
    }


    URL[] getClassPathUrls(String name) {
        toURLArray(getClassPathFiles(name))
    }


    Set<URL> getClassPath(String name) {
        toUrlSet(getClassPathFiles(name))
    }


    public Set<File> getClassPathFiles(String name) {
        Set<File> matches = new LinkedHashSet<File>()
        if (name.equals("GRADLE_PLUGINS")) {
            pluginLibs.find(all, matches)
            return matches
        }
        if (name.equals("GRADLE_RUNTIME")) {
            runtimeLibs.find(all, matches);
            return matches
        }
        List<Pattern> classPathPatterns = classPaths.get(name)
        if (classPathPatterns != null) {
            runtimeLibs.find(classPathPatterns, matches);
            pluginLibs.find(classPathPatterns, matches);
            return matches
        }
        throw new IllegalArgumentException(String.format("unknown classpath '%s' requested.", name))
    }


    private Set<URL> toUrlSet(Set<File> classPathFiles) {
        Set<URL> urls = new LinkedHashSet<URL>()
        for (File file: classPathFiles) {
            urls.add(file.toURI().toURL())
        }
        return urls;
    }


    private URL[] toURLArray(Collection<File> files) {
        List<URL> urls = new ArrayList<URL>(files.size())
        for (File file: files) {
            urls.add(file.toURI().toURL())
        }
        urls.toArray(new URL[urls.size()])
    }


    private static boolean matches(List<Pattern> patterns, String name) {
        for (Pattern pattern: patterns) {
            if (pattern.matcher(name).matches()) {
                return true
            }
        }
        return false
    }


    private static class ClassPathScanner extends Scanner {
        private final File classesDir
        private final Collection<URL> classpath


        private ClassPathScanner(File classesDir) {
            this.classesDir = classesDir
            this.classpath = ClasspathUtil.getClasspath(getClass().getClassLoader())
        }


        public void find(List<Pattern> patterns, Collection<File> into) {
            if (matches(patterns, "gradle-core-version.jar")) {
                into.add(classesDir)
            }
            if (matches(patterns, "gradle-core-worker-version.jar")) {
                String path = System.getProperty("gradle.core.worker.jar")
                if (path != null) {
                    into.add(new File(path))
                }
            }
            for (URL url: classpath) {
                if (url.getProtocol().equals("file")) {
                    File file = new File(url.toURI())
                    if (matches(patterns, file.getName())) {
                        into.add(file)
                    }
                }
            }
        }
    }
}
