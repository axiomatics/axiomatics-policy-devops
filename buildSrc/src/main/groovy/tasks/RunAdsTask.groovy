package tasks

import extensions.AdmExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Keeps the ADS started by {@link RunSpawnedAds} running until it exits or the build is stopped (Ctrl-C), showing its
 * output with --info (runAdsWith...). Never up-to-date.
 */
abstract class RunAdsTask extends DefaultTask {

    @Internal
    AdmExtension adm

    /** The ADS output, see {@link RunSpawnedAds#getAdsLogFile()}. */
    @Internal
    abstract RegularFileProperty getAdsLogFile()

    RunAdsTask() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void runAds() {
        Process process = adm.process
        File logFile = adsLogFile.get().asFile
        logger.lifecycle("ADS is running on port ${adm.adsHttpPort}, output in ${logFile}. " +
                "${logger.infoEnabled ? "" : "To see ADS output run gradle with --info. "}Stop it with Ctrl-C!")
        try {
            AdsLog.follow(logFile, process, logger, 0) { false }
            logger.lifecycle("ADS exited with ${process.exitValue()}")
        } finally {
            if (process.alive) {
                logger.info("Stopping ADS process")
                process.destroyForcibly()
            }
        }
    }
}
