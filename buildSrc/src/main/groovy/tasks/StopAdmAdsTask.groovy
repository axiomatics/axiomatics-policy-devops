package tasks

import extensions.AdmExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

import java.util.concurrent.TimeUnit

/** Stops the ADS started by {@link RunSpawnedAds}. No cached artifact, so never up-to-date. */
class StopAdmAdsTask extends DefaultTask {

    @Internal
    AdmExtension adm

    StopAdmAdsTask() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void stop() {
        Process process = adm.process
        if (process == null || !process.alive) {
            logger.info("No ADS running for ${adm.environment}")
            return
        }
        logger.info("Stopping ADS ${adm.environment} on port ${adm.adsHttpPort}")
        process.destroy()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
        }
        logger.info(process.alive ? "Could not stop ADS ${adm.environment} on port ${adm.adsHttpPort}!" : "ADS is stopped")
    }
}
