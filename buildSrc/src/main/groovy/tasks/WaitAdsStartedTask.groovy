package tasks

import extensions.AdmExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Waits until the ADS started by {@link RunSpawnedAds} is ready, or fails (and stops it) when ADS exits or does not
 * start in time. No cached artifact, so never up-to-date.
 */
abstract class WaitAdsStartedTask extends DefaultTask {

    static final String START_CONDITION_REGEXP = '^.*Started AdsApplication.*$'
    static final long START_TIMEOUT_MILLIS = 120_000

    @Internal
    AdmExtension adm

    /** The ADS output, see {@link RunSpawnedAds#getAdsLogFile()}. */
    @Internal
    abstract RegularFileProperty getAdsLogFile()

    WaitAdsStartedTask() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void waitForAds() {
        Process process = adm.process
        if (process == null) {
            throw new GradleException("ADS for ${adm.environment} has not been started")
        }
        File logFile = adsLogFile.get().asFile
        logger.info("Waiting for ADS to start, output in ${logFile}")
        boolean started = AdsLog.follow(logFile, process, logger, START_TIMEOUT_MILLIS) { String line ->
            line.matches(START_CONDITION_REGEXP)
        }
        if (started) {
            logger.lifecycle("ADS started on port ${adm.adsHttpPort}")
            return
        }
        String reason = process.alive ? "did not start within ${START_TIMEOUT_MILLIS / 1000} s" : "exited with ${process.exitValue()}"
        process.destroyForcibly()
        throw new GradleException("ADS for ${adm.environment} ${reason}. Last output (all of it in ${logFile}):" +
                System.lineSeparator() + AdsLog.tail(logFile, 50))
    }
}
