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
        serviceRegistryFactory
    }


    private static DefaultServiceRegistry createDefaultRegistry() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry()
        registry.add(ClassPathRegistry, new TestingClassPathRegistry())
        registry.add(CommandLine2StartParameterConverter, new DefaultCommandLine2StartParameterConverter())
        registry.add(CacheFactory, new DefaultCacheFactory())
        registry.add(ClassLoaderFactory, new DefaultClassLoaderFactory(registry.get(ClassPathRegistry) as ClassPathRegistry))
        registry
    }


    private static File createTempDir() {
        File rootDir = File.createTempFile('GradleProject', 'test')
        rootDir.mkdir()
        rootDir.deleteOnExit()
        rootDir
    }
}



private class TestingClassPathRegistry implements ClassPathRegistry {
    private final ClassPathScanner pluginLibs
    private final ClassPathScanner runtimeLibs
    private final List<Pattern> all = Arrays.asList(Pattern.compile(".+"))
    private final Map<String, List<Pattern>> classPaths = [:]


    TestingClassPathRegistry() {
        File codeSource = findThisClass()
        runtimeLibs = new ClassPathScanner(codeSource)
        pluginLibs = runtimeLibs
    }


    private File findThisClass() {
        URI location = DefaultClassPathRegistry.class.protectionDomain.codeSource.location.toURI()
        if (location.scheme != "file") {
            throw new GradleException("Cannot determine Gradle home using codebase '$location'.")
        }
        new File(location.path)
    }


    URL[] getClassPathUrls(String name) {
        toURLArray(getClassPathFiles(name))
    }


    Set<URL> getClassPath(String name) {
        toUrlSet(getClassPathFiles(name))
    }


    public Set<File> getClassPathFiles(String name) {
        if (name.equals("GRADLE_PLUGINS")) {
            Set<File> matches = pluginLibs.find(all)
            return matches
        }
        throw new IllegalArgumentException(String.format("unknown classpath '%s' requested.", name))
    }


    private Set<URL> toUrlSet(Set<File> classPathFiles) {
        classPathFiles.collect(new LinkedHashSet<URL>(classPathFiles.size())) {File file ->
            file.toURI().toURL()
        } as Set<URL>
    }


    private URL[] toURLArray(Collection<File> files) {
        files.collect(new ArrayList<URL>(files.size())) {File file ->
            file.toURI().toURL()
        }.toArray() as URL[]
    }




    private static class ClassPathScanner {
        private final File classesDir
        private final Collection<URL> classpath


        private ClassPathScanner(File classesDir) {
            this.classesDir = classesDir
            this.classpath = ClasspathUtil.getClasspath(getClass().classLoader)
        }


        private static boolean matches(List<Pattern> patterns, String name) {
            patterns.any {Pattern pattern ->
                pattern.matcher(name).matches()
            }
        }


        public Set<File> find(List<Pattern> patterns) {
            Set<File> into = []
            if (matches(patterns, "gradle-core-version.jar")) {
                into.add(classesDir)
            }
            if (matches(patterns, "gradle-core-worker-version.jar")) {
                String path = System.getProperty("gradle.core.worker.jar")
                if (path) {
                    into.add(new File(path))
                }
            }
            classpath.each {URL url ->
                if (url.protocol == "file") {
                    File file = new File(url.toURI())
                    if (matches(patterns, file.name)) {
                        into.add(file)
                    }
                }
            }
            into
        }
    }

}
