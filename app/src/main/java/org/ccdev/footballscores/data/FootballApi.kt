package org.ccdev.footballscores.data

import org.ccdev.footballscores.BuildConfig
import org.ccdev.footballscores.model.Match
import org.ccdev.footballscores.model.MatchDto
import org.ccdev.footballscores.model.MatchesResponse
import org.ccdev.footballscores.model.toDomain
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDate
import java.time.ZoneId

interface FootballApi {
    /**
     * One call returns every match for all competitions your plan covers,
     * so a single request refreshes the whole screen (cheap for the 10/min limit).
     * dateFrom/dateTo are UTC dates, format yyyy-MM-dd.
     */
    @GET("matches")
    suspend fun getMatches(
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String,
        @Query("competitions") competitions: String? = null, // e.g. "SA,BSA"; null = all allowed
    ): MatchesResponse

    @GET("matches/{id}")
    suspend fun getMatch(@retrofit2.http.Path("id") id: Int): MatchDto
}

object ApiFactory {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val api: FootballApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("X-Auth-Token", BuildConfig.FOOTBALL_API_KEY)
                        .build()
                )
            }
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.football-data.org/v4/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
            .create(FootballApi::class.java)
    }
}

class MatchRepository(
    private val api: FootballApi = ApiFactory.api,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /**
     * API dates are UTC, but the user thinks in local days (a 00:30Z kickoff is
     * already "tomorrow" in some time zones). So we ask for day-1 .. day+1 and
     * keep only the matches that fall on [date] in the device time zone.
     */
    suspend fun matchesFor(date: LocalDate): List<Match> =
        api.getMatches(date.minusDays(1).toString(), date.plusDays(1).toString())
            .matches
            .map { it.toDomain(zone) }
            .filter { it.kickoff.toLocalDate() == date }
            .sortedBy { it.kickoff }

    suspend fun matchDetails(id: Int): MatchDto = api.getMatch(id)
}
