package com.anymore.auto.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction

/**
 * Created by anymore on 2022/4/3.
 */
class AutoServiceRegisterTask extends DefaultTask {

    private FileCollection classpath
    private File targetDir
    private Map<String, Set<String>> requiredServices

    AutoServiceRegisterTask() {
        requiredServices = new HashMap<>()
    }

    void setTargetDir(File dir) {
        targetDir = dir
    }

    void setClasspath(FileCollection classpath) {
        this.classpath = classpath
    }

    void setRequiredServices(Map<String, Set<String>> requiredServices) {
        this.requiredServices = requiredServices
    }


    @TaskAction
    void run() {
        didWork(new AutoServiceRegisterAction(classpath, targetDir,requiredServices).execute())
    }
}
