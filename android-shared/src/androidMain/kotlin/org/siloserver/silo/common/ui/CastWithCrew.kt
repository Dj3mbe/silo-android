package org.siloserver.silo.common.ui

import org.siloserver.silo.model.catalog.CastMember
import org.siloserver.silo.model.catalog.CrewMember
import org.siloserver.silo.model.catalog.ItemDetail

private val CREATOR_JOBS = setOf("creator", "series creator", "created by")
private val DIRECTOR_JOBS = setOf("director")
private val WRITER_JOBS = setOf("writer", "screenplay", "story", "teleplay")

/**
 * BK fork: the "Cast & Crew" rail with the key crew (directors, then writers)
 * placed ahead of the cast (creator, directors, then writers), as the web app does. Each crew member is shown as
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
    add(CREATOR_JOBS, "Creator")
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

private const val CREW_ORDER_LIMIT = -50

private fun CastMember.railGroup(): String = when {
    order >= CREW_ORDER_LIMIT -> "cast"
    character?.startsWith("Creator") == true -> "creator"
    character?.startsWith("Director") == true -> "director"
    else -> "writer"
}

/**
 * BK fork: the small vertical label shown in front of the first card of each
 * group in the Cast & Crew rail (the web app's "WRITERS" / "CAST" dividers).
 * Returns null for the first card and for cards that continue a group.
 */
fun castGroupLabelBefore(list: List<CastMember>, index: Int): String? {
    if (index <= 0 || index >= list.size) return null
    val current = list[index].railGroup()
    if (current == list[index - 1].railGroup()) return null
    return when (current) {
        "writer" -> "WRITERS"
        "creator" -> "CREATORS"
        "cast" -> "CAST"
        else -> "DIRECTORS"
    }
}
