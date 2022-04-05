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

    AutoServiceRegisterTask() {
//        classpath = getProject().files()
//        Logger.d("TaskInit:${getProject().files().asPath}")
    }

    void setTargetDir(File dir) {
        targetDir = dir
    }

    void setClasspath(FileCollection classpath) {
        this.classpath = classpath
    }


    @TaskAction
    void run() {
        didWork(new AutoServiceRegisterAction(classpath, targetDir).execute())
    }
}
