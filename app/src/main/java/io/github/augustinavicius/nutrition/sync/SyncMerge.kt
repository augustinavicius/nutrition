package io.github.augustinavicius.nutrition.sync

/**
 * What a merge decided: the document to publish, and the changes the local database has yet
 * to catch up with.
 */
data class MergeResult(
    val merged: SyncDocument,
    val foodsToApply: List<SyncFood> = emptyList(),
    val recipesToApply: List<SyncRecipe> = emptyList(),
    val deletionsToApply: List<SyncDeletion> = emptyList(),
) {
    val hasLocalChanges: Boolean
        get() = foodsToApply.isNotEmpty() || recipesToApply.isNotEmpty() || deletionsToApply.isNotEmpty()
}

/**
 * Merges two libraries, record by record, newest edit winning.
 *
 * A deletion is just another timestamped fact: a tombstone newer than a record removes it, and
 * a record newer than a tombstone brings it back — which is what someone re-adding a food on
 * another device means. Ties go to the remote so that two devices merging the same pair of
 * documents always reach the same answer.
 */
fun merge(local: SyncDocument, remote: SyncDocument, now: Long): MergeResult {
    val foods = mergeRecords(
        local = local.foods.associateBy { it.uid },
        remote = remote.foods.associateBy { it.uid },
        localTombstones = tombstones(local, KIND_FOOD),
        remoteTombstones = tombstones(remote, KIND_FOOD),
        timestamp = SyncFood::updatedAt,
    )
    val recipes = mergeRecords(
        local = local.recipes.associateBy { it.uid },
        remote = remote.recipes.associateBy { it.uid },
        localTombstones = tombstones(local, KIND_RECIPE),
        remoteTombstones = tombstones(remote, KIND_RECIPE),
        timestamp = SyncRecipe::updatedAt,
    )

    val survivingDeletions = (foods.tombstones + recipes.tombstones).sortedBy { it.uid }

    return MergeResult(
        merged = SyncDocument(
            updatedAt = now,
            foods = foods.survivors.sortedBy { it.uid },
            recipes = recipes.survivors.sortedBy { it.uid },
            deletions = survivingDeletions,
        ),
        foodsToApply = foods.toApplyLocally,
        recipesToApply = recipes.toApplyLocally,
        deletionsToApply = foods.deletionsToApplyLocally + recipes.deletionsToApplyLocally,
    )
}

private const val KIND_FOOD = "FOOD"
private const val KIND_RECIPE = "RECIPE"

private fun tombstones(document: SyncDocument, kind: String): Map<String, SyncDeletion> =
    document.deletions.filter { it.kind == kind }.associateBy { it.uid }

private class RecordMerge<T>(
    val survivors: List<T>,
    val tombstones: List<SyncDeletion>,
    val toApplyLocally: List<T>,
    val deletionsToApplyLocally: List<SyncDeletion>,
)

private fun <T> mergeRecords(
    local: Map<String, T>,
    remote: Map<String, T>,
    localTombstones: Map<String, SyncDeletion>,
    remoteTombstones: Map<String, SyncDeletion>,
    timestamp: (T) -> Long,
): RecordMerge<T> {
    val survivors = mutableListOf<T>()
    val tombstones = mutableListOf<SyncDeletion>()
    val toApplyLocally = mutableListOf<T>()
    val deletionsToApplyLocally = mutableListOf<SyncDeletion>()

    val uids = local.keys + remote.keys + localTombstones.keys + remoteTombstones.keys
    for (uid in uids) {
        val localRecord = local[uid]
        val remoteRecord = remote[uid]
        val localGrave = localTombstones[uid]
        val remoteGrave = remoteTombstones[uid]

        // Ties favour the remote, so both sides of a sync reach the same answer.
        val newestRecord = listOfNotNull(remoteRecord, localRecord)
            .maxByOrNull { timestamp(it) }
        val newestGrave = listOfNotNull(remoteGrave, localGrave)
            .maxByOrNull { it.deletedAt }

        val recordAt = newestRecord?.let(timestamp) ?: Long.MIN_VALUE
        val graveAt = newestGrave?.deletedAt ?: Long.MIN_VALUE

        if (newestGrave != null && graveAt >= recordAt) {
            tombstones += newestGrave
            // Locally still present, or not yet known to be gone: catch up.
            if (localRecord != null || localGrave == null) deletionsToApplyLocally += newestGrave
            continue
        }

        val winner = newestRecord ?: continue
        survivors += winner
        val localAt = localRecord?.let(timestamp)
        if (localAt == null || timestamp(winner) > localAt) toApplyLocally += winner
    }

    return RecordMerge(survivors, tombstones, toApplyLocally, deletionsToApplyLocally)
}
