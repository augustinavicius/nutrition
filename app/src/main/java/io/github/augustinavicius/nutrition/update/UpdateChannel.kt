package io.github.augustinavicius.nutrition.update

/**
 * Which stream of releases this install follows.
 *
 * Both are built by the same workflow from the same commit history; the difference is only
 * which branch the commit landed on. Stable releases come from `master` and are published as
 * full releases; development releases come from `development` and are published as
 * pre-releases, so GitHub's own "Latest release" keeps pointing at the stable one.
 */
enum class UpdateChannel(val id: String, val label: String) {
    STABLE("stable", "Stable"),
    DEVELOPMENT("development", "Development"),
    ;

    companion object {
        val DEFAULT = STABLE

        fun fromId(id: String?): UpdateChannel =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}
