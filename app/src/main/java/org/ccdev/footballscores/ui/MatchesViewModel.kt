package org.ccdev.footballscores.ui

import org.ccdev.footballscores.data.MatchRepository
import org.ccdev.footballscores.model.Match
import org.ccdev.footballscores.model.MatchState
import org.ccdev.footballscores.model.MatchDto
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

data class UiState(
    val date: LocalDate,
    val matches: List<Match> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,          // shown as a banner; last good data stays visible
    val updatedAt: LocalTime? = null,
)

data class MatchDetailsUiState(
    val match: Match,
    val details: MatchDto? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MatchesViewModel(
    private val repo: MatchRepository = MatchRepository(),
) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now())
    private val mutableMatchDetails = MutableStateFlow<MatchDetailsUiState?>(null)
    private var detailsJob: Job? = null

    /**
     * Polling lives inside the flow. With WhileSubscribed + collectAsStateWithLifecycle in the UI,
     * polling stops 5s after the app goes to the background and resumes when it returns,
     * so you don't burn your quota while nobody is looking.
     */
    val state: StateFlow<UiState> = date
        .flatMapLatest { poll(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(date.value))
    val matchDetails: StateFlow<MatchDetailsUiState?> = mutableMatchDetails

    fun previousDay() = date.update { it.minusDays(1) }
    fun nextDay() = date.update { it.plusDays(1) }
    fun today() { date.value = LocalDate.now() }
    fun selectDate(selectedDate: LocalDate) { date.value = selectedDate }

    fun openMatch(match: Match) {
        detailsJob?.cancel()
        mutableMatchDetails.value = MatchDetailsUiState(match)
        detailsJob = viewModelScope.launch {
            try {
                val details = repo.matchDetails(match.id)
                mutableMatchDetails.value = MatchDetailsUiState(match, details, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                mutableMatchDetails.value = MatchDetailsUiState(
                    match,
                    loading = false,
                    error = when (e.code()) {
                        403 -> "Match details aren't included in your plan"
                        else -> "Unable to load match details (${e.code()})"
                    },
                )
            } catch (e: IOException) {
                mutableMatchDetails.value = MatchDetailsUiState(
                    match,
                    loading = false,
                    error = "No connection",
                )
            }
        }
    }

    fun closeMatch() {
        detailsJob?.cancel()
        mutableMatchDetails.value = null
    }

    private fun poll(day: LocalDate): Flow<UiState> = flow {
        var current = UiState(day)
        emit(current)

        while (true) {
            var wait = 60_000L
            try {
                val matches = repo.matchesFor(day)
                current = current.copy(
                    matches = matches, loading = false, error = null, updatedAt = LocalTime.now(),
                )
                wait = nextDelayMillis(day, matches)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                wait = when (e.code()) {
                    // Rate limited: wait until the counter resets (header is in seconds)
                    429 -> (e.response()?.headers()?.get("X-RequestCounter-Reset")
                        ?.toLongOrNull()?.plus(1)?.times(1_000)) ?: 60_000L
                    else -> 60_000L
                }
                current = current.copy(
                    loading = false,
                    error = when (e.code()) {
                        429 -> "Rate limit reached, retrying shortly"
                        403 -> "This data isn't included in your plan"
                        400 -> "Invalid request (check the date range)"
                        else -> "Server error (${e.code()})"
                    },
                )
            } catch (e: IOException) {
                wait = 30_000L
                current = current.copy(loading = false, error = "No connection")
            }
            emit(current)
            delay(wait)
        }
    }

    /**
     * Adaptive polling, all comfortably under 10 req/min (worst case here = 2 req/min):
     *  - live match, or kickoff within 10 min -> every 30s
     *  - otherwise today                      -> every 2 min
     *  - other days (nothing changes)         -> every 10 min
     */
    private fun nextDelayMillis(day: LocalDate, matches: List<Match>): Long {
        if (day != LocalDate.now()) return 600_000L
        val now = ZonedDateTime.now()
        val hot = matches.any {
            it.state == MatchState.LIVE ||
                (it.state == MatchState.UPCOMING && Duration.between(now, it.kickoff).abs().toMinutes() <= 10)
        }
        return if (hot) 30_000L else 120_000L
    }
}
