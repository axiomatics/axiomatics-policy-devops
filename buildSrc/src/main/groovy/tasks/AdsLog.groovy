package tasks

import org.gradle.api.logging.Logger

/** Follows the output file of a spawned ADS (see {@link RunSpawnedAds}). */
class AdsLog {

    private static final long POLL_MILLIS = 200

    /**
     * Reads the lines ADS writes to {@code logFile}, logging each at info level, until {@code stop} returns true for
     * a line (returns true), ADS exits (false), or {@code timeoutMillis} passes (false; 0 = no timeout).
     */
    static boolean follow(File logFile, Process process, Logger logger, long timeoutMillis, Closure<Boolean> stop) {
        long deadline = timeoutMillis > 0 ? System.currentTimeMillis() + timeoutMillis : Long.MAX_VALUE
        logFile.withReader("UTF-8") { BufferedReader reader ->
            while (System.currentTimeMillis() < deadline) {
                String line = reader.readLine()
                if (line != null) {
                    logger.info("   ADS : " + line)
                    if (stop(line)) {
                        return true
                    }
                } else if (!process.alive) {
                    // ADS has exited: read what it wrote last, then give up
                    while ((line = reader.readLine()) != null) {
                        logger.info("   ADS : " + line)
                        if (stop(line)) {
                            return true
                        }
                    }
                    return false
                } else {
                    Thread.sleep(POLL_MILLIS)
                }
            }
            return false
        }
    }

    /** The last {@code count} lines of the ADS output, for error messages. */
    static String tail(File logFile, int count) {
        if (!logFile.exists()) {
            return ""
        }
        List<String> lines = logFile.readLines("UTF-8")
        lines.subList(Math.max(0, lines.size() - count), lines.size()).join(System.lineSeparator())
    }
}
