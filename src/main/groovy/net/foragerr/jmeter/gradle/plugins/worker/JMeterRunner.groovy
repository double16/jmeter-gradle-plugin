package net.foragerr.jmeter.gradle.plugins.worker

import groovy.io.FileType
import groovy.transform.CompileDynamic
import net.foragerr.jmeter.gradle.plugins.JMSpecs
import org.gradle.internal.os.OperatingSystem
import org.gradle.workers.ClassLoaderWorkerSpec
import org.gradle.workers.ProcessWorkerSpec
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor

import java.util.regex.Pattern

@CompileDynamic
class JMeterRunner {

    final WorkerExecutor workerExecutor
    final Iterable<File> jmeterConfiguration

    JMeterRunner(WorkerExecutor workerExecutor, Iterable<File> jmeterConfiguration) {
        this.workerExecutor = workerExecutor
        this.jmeterConfiguration = jmeterConfiguration
    }

    void executeJmeterCommand(JMSpecs specs, String workingDirectory) {
        WorkQueue workQueue = workerExecutor.processIsolation { ProcessWorkerSpec spec ->
            populateClassLoader(spec, workingDirectory)
            populateProcessSpecs(spec, specs, workingDirectory)
        }

        workQueue.submit(JMeterAction) {
            List<String> cliArgs = []
            cliArgs << "-Jjmeter.exit.check.pause=-1" // prevents system exit check which won't happen with a daemon
            cliArgs.addAll(specs.jmeterProperties)
            specs.userSystemProperties.each { userSysProp ->
                cliArgs << "-J${userSysProp}".toString()
            }

            getArgs().set(cliArgs as String[])
        }

        workQueue.await()
    }

    private void populateClassLoader(ClassLoaderWorkerSpec spec, String workDir) {
        // openjfx for non-Oracle JDK
        final Pattern openjfxPattern = ~/\/javafx-.*\.jar/
        final Pattern openjfxOSPattern = ~/\/javafx-.*-${operatingSystemClassifier()}\.jar/

        jmeterConfiguration
                .findAll { File it -> !it.name.find(openjfxPattern) || it.name.find(openjfxOSPattern) }
                .each { spec.classpath.from(it) }

        spec.classpath.with {
            File lib = new File(workDir, 'lib')
            File ext = new File(lib, 'ext')
            from(lib)
            from(ext)

            lib.eachFileRecurse(FileType.FILES) { file ->
                from(file)
            }
            ext.eachFileRecurse(FileType.FILES) { file ->
                from(file)
            }
        }
    }

    private void populateProcessSpecs(ProcessWorkerSpec spec, JMSpecs specs, String workingDirectory) {
        spec.forkOptions {
//            workingDir(workingDirectory)
            minHeapSize = specs.minHeapSize
            maxHeapSize = specs.maxHeapSize
            specs.systemProperties.each { k, v ->
                spec.forkOptions.systemProperty(k, v)
            }
        }
    }

    private String operatingSystemClassifier() {
        String platform = 'unsupported'
        int javaMajorVersion = System.properties['java.runtime.version'].split('[^0-9]+')[0] as int
        if (javaMajorVersion < 11) {
            return platform
        }
        OperatingSystem currentOS = OperatingSystem.current()
        if (currentOS.isWindows()) {
            platform = 'win'
        } else if (currentOS.isLinux()) {
            platform = 'linux'
        } else if (currentOS.isMacOsX()) {
            platform = 'mac'
        }
        platform
    }
}
