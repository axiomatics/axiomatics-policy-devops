package tasks

import org.ajoberstar.grgit.Grgit
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

import java.time.format.DateTimeFormatter

abstract class GitInfoValueSource implements ValueSource<Map<String, String>, GitInfoValueSource.Params> {

    interface Params extends ValueSourceParameters {
        DirectoryProperty getRootDir()
    }

    @Override
    Map<String, String> obtain() {
        File rootDir = parameters.rootDir.get().asFile
        def grgit = null
        try {
            grgit = Grgit.open(currentDir: rootDir)
            def head = grgit.head()
            String repoName = grgit.repository.rootDir.parentFile.name
            return [
                    repoAndCommit: "${repoName}-${head.abbreviatedId}".toString(),
                    commitRef    : head.abbreviatedId,
                    commitDate   : head.dateTime.format(DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss Z')),
                    author       : "${head.author.name} <${head.author.email}>".toString(),
                    message      : (head.shortMessage ?: '').toString(),
            ]
        } catch (Exception ignored) {
            // not a git repo or git unavailable — allow
            return [repoAndCommit: 'unknown', commitRef: 'unknown', commitDate: 'unknown', author: '', message: '']
        } finally {
            grgit?.close()
        }
    }
}
