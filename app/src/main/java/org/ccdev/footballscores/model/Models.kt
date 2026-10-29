package org.ccdev.footballscores.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

// ---------- DTOs (mirror the API JSON; unknown fields are ignored) ----------

@Serializable
data class MatchesResponse(
    val resultSet: ResultSetDto? = null,
    val matches: List<MatchDto> = emptyList(),
)

@Serializable
data class ResultSetDto(
    val count: Int = 0,
    val competitions: String? = null,
    val first: String? = null,
    val last: String? = null,
    val played: Int? = null,
)

@Serializable
data class MatchDto(
    val id: Int,
    val utcDate: String,
    val status: String,
    val matchday: Int? = null,
    val stage: String? = null,
    val venue: String? = null,
    val lastUpdated: String? = null,
    val competition: CompetitionDto,
    val homeTeam: TeamDto,
    val awayTeam: TeamDto,
    val score: ScoreDto,
    val referees: List<RefereeDto> = emptyList(),
)

@Serializable
data class RefereeDto(
    val id: Int? = null,
    val name: String,
    val type: String? = null,
    val nationality: String? = null,
)

@Serializable
data class CompetitionDto(
    val id: Int,
    val name: String,
    val code: String? = null,
    val emblem: String? = null,
)

@Serializable
data class TeamDto(          // fields can be null for undecided knockout fixtures
    val id: Int? = null,
    val name: String? = null,
    val shortName: String? = null,
    val tla: String? = null,
    val crest: String? = null,
    val lineup: List<LineupPlayerDto> = emptyList(),
    val bench: List<LineupPlayerDto> = emptyList(),
)

@Serializable
data class LineupPlayerDto(
    val id: Int? = null,
    val name: String,
    val position: String? = null,
    val shirtNumber: Int? = null,
)

@Serializable
data class ScoreDto(
    val winner: String? = null,
    val duration: String? = null,
    val fullTime: ScoreTimeDto = ScoreTimeDto(),
    val halfTime: ScoreTimeDto = ScoreTimeDto(),
)

@Serializable
data class ScoreTimeDto(val home: Int? = null, val away: Int? = null)

// ---------- UI-friendly domain model ----------

enum class MatchState { UPCOMING, LIVE, FINISHED, OTHER }

data class Match(
    val id: Int,
    val competition: String,
    val competitionEmblem: String?,
    val home: String,
    val away: String,
    val homeCrest: String?,
    val awayCrest: String?,
    val state: MatchState,
    val rawStatus: String,
    val kickoff: ZonedDateTime,   // already converted to the device time zone
    val homeScore: Int?,
    val awayScore: Int?,
)

private fun TeamDto.displayName() = shortName ?: name ?: tla ?: "TBD"

private fun statusToState(status: String) = when (status) {
    "SCHEDULED", "TIMED" -> MatchState.UPCOMING
    "IN_PLAY", "PAUSED", "LIVE" -> MatchState.LIVE
    "FINISHED", "AWARDED" -> MatchState.FINISHED
    else -> MatchState.OTHER      // POSTPONED, SUSPENDED, CANCELLED
}

fun MatchDto.toDomain(zone: ZoneId) = Match(
    id = id,
    competition = competition.name,
    competitionEmblem = competition.emblem,
    home = homeTeam.displayName(),
    away = awayTeam.displayName(),
    homeCrest = homeTeam.crest,
    awayCrest = awayTeam.crest,
    state = statusToState(status),
    rawStatus = status,
    kickoff = Instant.parse(utcDate).atZone(zone),   // API times are UTC
    homeScore = score.fullTime.home,
    awayScore = score.fullTime.away,
)
