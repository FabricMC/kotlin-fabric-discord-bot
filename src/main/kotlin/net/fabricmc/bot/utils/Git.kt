package net.fabricmc.bot.utils

import net.fabricmc.bot.conf.config
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

/**
 * Get a Git instance for a repo, ensuring that it's cloned if it doesn't exist.
 *
 * @param directoryName The name of the directory to clone the repo to, relative to the configured git root dir
 * @param url The URL pointing to the git repo
 * @param branch The name of the branch, or null to use the default for this repo
 */
fun ensureRepo(directoryName: String, url: String, branch: String?): Git {
    val gitDir = File(config.git.directory, directoryName)

    return if (!gitDir.exists()) {
        var command = Git.cloneRepository()
                .setURI(url)
                .setDirectory(gitDir)


        if (branch != null) {
            command = command.setBranch("refs/heads/$branch")
        }

        command.call()
    } else {
        Git(
                FileRepositoryBuilder()
                        .setGitDir(gitDir.toPath().resolve(".git").toFile())
                        .readEnvironment()
                        .build()
        )
    }
}
