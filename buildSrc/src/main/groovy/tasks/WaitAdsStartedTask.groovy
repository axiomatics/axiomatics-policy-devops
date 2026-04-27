package tasks

import extensions.AdmExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

import java.util.concurrent.atomic.AtomicBoolean

class WaitAdsStartedTask extends DefaultTask {

    @Internal
    String START_CONDITION_REGEXP = '^.*Started AdsApplication.*$'

    @Input
    AdmExtension adm

    @Input
    int admHash

    @TaskAction
    public execute() {

        def Process process = adm.process

        BufferedReader reader =  new BufferedReader(new InputStreamReader(process.getInputStream()));
        AtomicBoolean match = new AtomicBoolean(false)
        ArrayList<String> adsOutput = new ArrayList<>();

        Runnable waitForAdsStarted = () -> {

            logger.info("Waiting for ADS start... ")
            String line;
            line = reader.readLine();

            while (line != null) {
                 logger.info("   ADS : " + line);
                adsOutput.add(line);
                 if (line.matches(START_CONDITION_REGEXP)) {
                    logger.info("  Start condition met")
                    match.set(true)
                    break;
                 } else {
                       //logger.info("Start condition NOT met for line")
                    }
                    line = reader.readLine();
                }
        }


            Thread t = new Thread(waitForAdsStarted);
            t.start();


            int c = 0;
            while (c++ <   120 *2  && !match.get() && t.isAlive()) {
                Thread.sleep(500)

                logger.info("  Waiting for condition ADS have started, match=" + match.get() +", alive="+t.isAlive())
            }
            logger.info("Out of waiting loop, match=" + match.get() +", alive="+t.isAlive())
            boolean wasAlive = process.isAlive()
            t.interrupt();

            if (!match.get() && wasAlive) {
                process.destroy()
                adsOutput.forEach {it->logger.error(it)}
                throw new RuntimeException("ADS failed to start within time, giving up! See debug output.")
        } else if (!wasAlive) {
                adsOutput.forEach {it->logger.error(it)}
                throw new RuntimeException("ADS failed to start because of an error! See debug output.")
        } else {
                println "  ADS successfully started!"
        }
    }
}
