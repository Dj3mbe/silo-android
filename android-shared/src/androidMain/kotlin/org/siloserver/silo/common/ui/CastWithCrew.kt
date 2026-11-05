package org.siloserver.silo.common.ui

import org.siloserver.silo.model.catalog.CastMember
import org.siloserver.silo.model.catalog.CrewMember
import org.siloserver.silo.model.catalog.ItemDetail

private val DIRECTOR_JOBS = setOf("director")
private val WRITER_JOBS = setOf("writer", "screenplay", "story", "teleplay")

/**
 * BK fork: the "Cast & Crew" rail with the key crew (directors, then writers)
 * placed ahead of the cast, as the web app does. Each crew member is shown as
 * a card whose caption is their job, and opens the same person page.
 */
fun ItemDetail.castAndKeyCrew(): List<CastMember> {
    val credits = LinkedHashMap<String, Pair<CrewMember, MutableList<String>>>()
    fun add(jobs: Set<String>, label: String) {
        crew.asSequence()
            .filter { member -> member.job?.trim()?.lowercase() in jobs }
            .filter { member -> member.name.isNotBlank() }
            .take(3)
            .forEach { member ->
                val key = member.personId ?: member.name.trim().lowercase()
                val entry = credits.getOrPut(key) { member to mutableListOf() }
                if (label !in entry.second) entry.second.add(label)
            }
    }
    add(DIRECTOR_JOBS, "Director")
    add(WRITER_JOBS, "Writer")
    if (credits.isEmpty()) return cast
    val crewCards = credits.values.mapIndexed { index, (member, labels) ->
        CastMember(
            name = member.name.trim(),
            character = labels.joinToString(", "),
            order = index - 100,
            personId = member.personId,
            tmdbId = member.tmdbId,
            tvdbId = member.tvdbId,
            imdbId = member.imdbId,
            plexGuid = member.plexGuid,
            photoUrl = member.photoUrl,
            photoThumbhash = member.photoThumbhash,
        )
    }
    return crewCards + cast
}
