package tasks

import extensions.AdmExtension
import extensions.AlfaExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

// Stops a live ADS process (started by RunSpawnedAds) — no sensible cached artifact here, so this
// task is never up-to-date by design.
class StopAdmAdsTask extends  DefaultTask {

    @Internal
    AdmExtension adm;
    @Internal
    AlfaExtension config

    StopAdmAdsTask() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    stop() {
        logger.info("Stopping ADS ${adm.environment} on port ${adm.adsHttpPort}")
        if (adm.process == null) {
            logger.info("No process to stop")
            return
        }
        adm.process.waitForOrKill(2000)
        logger.info("ADS is stopped!")
        if (adm.process.isAlive()) {
            logger.info("Could not stop ADS ${adm.environment} on port ${adm.adsHttpPort}!")
        }
    }
}
