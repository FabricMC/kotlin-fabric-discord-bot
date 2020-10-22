package net.fabricmc.bot.conf.wrappers

import com.uchuhimo.konf.Config
import net.fabricmc.bot.conf.spec.GitSpec

/**
 * Wrapper object representing the Git configuration.
 *
 * @param config Loaded Konf Config object.
 */
data class GitConfig(private val config: Config) {
    /** The directory to store cloned Git repositories. **/
    val directory get() = config[GitSpec.directory]

    /** The branch name to checkout for the tags repo. **/
    val tagsRepoBranch get() = config[GitSpec.tagsRepoBranch]

    /** URL to the git repo containing tags. **/
    val tagsRepoUrl get() = config[GitSpec.tagsRepoUrl]

    /** Root directory (within the repository) containing tags. **/
    val tagsRepoPath get() = config[GitSpec.tagsRepoPath]
}
