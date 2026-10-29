package org.ccdev.footballscores.ui

import org.ccdev.footballscores.model.Match
import org.ccdev.footballscores.model.MatchState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.ccdev.footballscores.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(vm: MatchesViewModel = viewModel()) {
    // Lifecycle-aware: stops collecting (and therefore polling) when the app is in the background
    val state by vm.state.collectAsStateWithLifecycle()
    val matchDetails by vm.matchDetails.collectAsStateWithLifecycle()
    var showCalendar by remember { mutableStateOf(false) }

    if (matchDetails != null) {
        MatchDetailsScreen(matchDetails!!, onBack = vm::closeMatch)
    } else {
        Scaffold(topBar = { TopAppBar(title = { Text("Football Scores") }) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                DateBar(
                    label = state.date.format(dateFmt),
                    onPrev = vm::previousDay,
                    onNext = vm::nextDay,
                    onToday = vm::today,
                    onPickDate = { showCalendar = true },
                )
                state.error?.let {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(it, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                    }
                }
                when {
                    state.loading -> LoadingContent()
                    state.matches.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No matches on this day") }
                    else -> MatchList(state.matches, onMatchClick = vm::openMatch)
                }
                state.updatedAt?.let {
                    Text(
                        "Updated ${it.format(timeFmt)}",
                        Modifier.fillMaxWidth().padding(4.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }

    if (showCalendar) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        vm.selectDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showCalendar = false
                }) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun LoadingContent() {
    val transition = rememberInfiniteTransition(label = "football-loader")
    val scale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "football-loader-pulse",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(76.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        )
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Text(
            "Fetching the latest scores",
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DateBar(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onPickDate: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPrev) { Text("‹") }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = onPickDate) { Text("$label  ▦", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onToday) { Text("Today") }
        }
        TextButton(onClick = onNext) { Text("›") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatchList(matches: List<Match>, onMatchClick: (Match) -> Unit) {
    val grouped = matches.groupBy { it.competition }
    LazyColumn(Modifier.fillMaxSize()) {
        grouped.forEach { (competition, list) ->
            stickyHeader(key = "h_$competition") {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(list.first().competitionEmblem, null, Modifier.size(18.dp))
                        Text(competition, Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            items(list, key = { it.id }) { match -> MatchRow(match) { onMatchClick(match) } }
        }
    }
}

@Composable
private fun MatchRow(m: Match, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status / kickoff time
        val (statusText, statusColor) = when (m.state) {
            MatchState.LIVE -> "LIVE" to Color(0xFFD32F2F)
            MatchState.FINISHED -> "FT" to MaterialTheme.colorScheme.onSurface
            MatchState.UPCOMING -> m.kickoff.format(timeFmt) to MaterialTheme.colorScheme.onSurface
            MatchState.OTHER -> m.rawStatus.take(4) to MaterialTheme.colorScheme.outline
        }
        Text(statusText, Modifier.width(48.dp), color = statusColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)

        // Home
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(m.home, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            AsyncImage(m.homeCrest, null, Modifier.padding(start = 6.dp).size(24.dp))
        }

        // Score
        val score = if (m.homeScore != null && m.awayScore != null) "${m.homeScore} - ${m.awayScore}" else "–"
        Text(score, Modifier.width(64.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 16.sp)

        // Away
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(m.awayCrest, null, Modifier.padding(end = 6.dp).size(24.dp))
            Text(m.away, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchDetailsScreen(details: MatchDetailsUiState, onBack: () -> Unit) {
        val match = details.match
        val apiMatch = details.details
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Match details") },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                )
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(match.competition, style = MaterialTheme.typography.titleMedium)
                Text(match.kickoff.format(dateFmt) + " · " + match.kickoff.format(timeFmt), color = MaterialTheme.colorScheme.outline)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeamHeader(match.home, match.homeCrest, Modifier.weight(1f))
                    val score = if (match.homeScore != null && match.awayScore != null) {
                        "${match.homeScore} - ${match.awayScore}"
                    } else {
                        "vs"
                    }
                    Text(score, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    TeamHeader(match.away, match.awayCrest, Modifier.weight(1f))
                }
                Text(match.rawStatus.replace('_', ' '), style = MaterialTheme.typography.titleSmall)

                if (details.loading) {
                    CircularProgressIndicator(Modifier.padding(20.dp))
                }
                details.error?.let {
                    Text(it, Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.error)
                }
                apiMatch?.let { data ->
                    val info = listOfNotNull(
                        data.matchday?.let { "Matchday $it" },
                        data.stage?.replace('_', ' '),
                        data.venue,
                        data.referees.takeIf { it.isNotEmpty() }?.joinToString { referee -> referee.name },
                    )
                    info.forEach { Text(it, Modifier.fillMaxWidth().padding(vertical = 3.dp)) }
                    data.score.halfTime.home?.let { home ->
                        data.score.halfTime.away?.let { away ->
                            Text("Half-time: $home - $away", Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                    }

                    Text("Lineups", Modifier.fillMaxWidth().padding(top = 20.dp), style = MaterialTheme.typography.titleLarge)
                    if (data.homeTeam.lineup.isEmpty() && data.awayTeam.lineup.isEmpty()) {
                        Text("Lineups aren't available for this match.", Modifier.fillMaxWidth().padding(top = 8.dp))
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LineupColumn(data.homeTeam.shortName ?: match.home, data.homeTeam.lineup, data.homeTeam.bench, Modifier.weight(1f))
                            LineupColumn(data.awayTeam.shortName ?: match.away, data.awayTeam.lineup, data.awayTeam.bench, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

@Composable
private fun TeamHeader(name: String, crest: String?, modifier: Modifier) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(crest, contentDescription = "$name crest", Modifier.size(44.dp))
            Text(name, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
}

@Composable
private fun LineupColumn(
    team: String,
    starters: List<org.ccdev.footballscores.model.LineupPlayerDto>,
    bench: List<org.ccdev.footballscores.model.LineupPlayerDto>,
    modifier: Modifier,
) {
        Column(modifier.padding(top = 12.dp)) {
            Text(team, style = MaterialTheme.typography.titleMedium)
            Text("Starting XI", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            starters.forEach { PlayerLine(it) }
            if (bench.isNotEmpty()) {
                Text("Bench", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                bench.forEach { PlayerLine(it) }
            }
        }
}

@Composable
private fun PlayerLine(player: org.ccdev.footballscores.model.LineupPlayerDto) {
    val number = player.shirtNumber?.let { "$it " }.orEmpty()
    Text(
        "$number${player.name}${player.position?.let { " · $it" }.orEmpty()}",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
