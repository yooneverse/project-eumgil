package com.ssafy.e102.eumgil.data.local.datasource

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.RecentDestination
import com.ssafy.e102.eumgil.core.model.RecentSearch
import com.ssafy.e102.eumgil.core.model.SearchQuery
import com.ssafy.e102.eumgil.core.model.SearchResult
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class SearchLocalDataSource(
    private val dataStore: DataStore<Preferences>? = null,
    private val currentUserScopeProvider: suspend () -> String? = { null },
) {
    private val cachedResultsByQuery = ConcurrentHashMap<String, List<SearchResult>>()
    private val recentSearchesByScope = LinkedHashMap<String, LinkedHashMap<String, RecentSearch>>()
    private val recentDestinationsByScope = LinkedHashMap<String, LinkedHashMap<String, RecentDestination>>()
    private val recentSearchesMutex = Mutex()
    private val recentDestinationsMutex = Mutex()

    suspend fun getCachedResults(query: SearchQuery): List<SearchResult> =
        cachedResultsByQuery[query.normalizedKey()].orEmpty()

    suspend fun updateCachedResults(
        query: SearchQuery,
        results: List<SearchResult>,
    ) {
        cachedResultsByQuery[query.normalizedKey()] = results
    }

    suspend fun getRecentSearches(): List<RecentSearch> =
        recentSearchesMutex.withLock {
            val storageScopeKey = resolveStorageScopeKey()
            loadRecentSearches(storageScopeKey).values.sortedByDescending(RecentSearch::searchedAtMillis)
        }

    suspend fun saveRecentSearch(keyword: String) {
        val normalizedKeyword = keyword.normalizedKeyword()
        if (normalizedKeyword.isEmpty()) return

        recentSearchesMutex.withLock {
            val storageScopeKey = resolveStorageScopeKey()
            val recentSearches = loadRecentSearches(storageScopeKey)
            recentSearches.remove(normalizedKeyword)
            recentSearches[normalizedKeyword] = RecentSearch(keyword = keyword.trim())

            while (recentSearches.size > MAX_RECENT_SEARCHES) {
                val oldestKey =
                    recentSearches
                        .entries
                        .minByOrNull { entry -> entry.value.searchedAtMillis }
                        ?.key
                        ?: break
                recentSearches.remove(oldestKey)
            }

            persistRecentSearches(storageScopeKey, recentSearches)
        }
    }

    suspend fun deleteRecentSearch(keyword: String) {
        val normalizedKeyword = keyword.normalizedKeyword()
        if (normalizedKeyword.isEmpty()) return

        recentSearchesMutex.withLock {
            val storageScopeKey = resolveStorageScopeKey()
            val recentSearches = loadRecentSearches(storageScopeKey)
            recentSearches.remove(normalizedKeyword)
            persistRecentSearches(storageScopeKey, recentSearches)
        }
    }

    suspend fun clearRecentSearches() {
        recentSearchesMutex.withLock {
            val storageScopeKey = resolveStorageScopeKey()
            val recentSearches = loadRecentSearches(storageScopeKey)
            recentSearches.clear()
            persistRecentSearches(storageScopeKey, recentSearches)
        }
    }

    suspend fun getRecentDestinations(): List<RecentDestination> =
        recentDestinationsMutex.withLock {
            val storageScopeKey = resolveStorageScopeKey()
            loadRecentDestinations(storageScopeKey).values.sortedByDescending(RecentDestination::searchedAtMillis)
        }

    suspend fun saveRecentDestination(destination: RecentDestination) {
        val sanitizedDestination = destination.sanitized()
        val normalizedKey = sanitizedDestination.normalizedKey()
        if (normalizedKey.isEmpty()) return

        recentDestinationsMutex.withLock {
            val storageScopeKey = resolveStorageScopeKey()
            val recentDestinations = loadRecentDestinations(storageScopeKey)
            recentDestinations.remove(normalizedKey)
            recentDestinations[normalizedKey] = sanitizedDestination

            while (recentDestinations.size > MAX_RECENT_DESTINATIONS) {
                val oldestKey =
                    recentDestinations
                        .entries
                        .minByOrNull { entry -> entry.value.searchedAtMillis }
                        ?.key
                        ?: break
                recentDestinations.remove(oldestKey)
            }

            persistRecentDestinations(storageScopeKey, recentDestinations)
        }
    }

    private suspend fun loadRecentSearches(storageScopeKey: String): LinkedHashMap<String, RecentSearch> =
        if (dataStore == null) {
            recentSearchesByScope.getOrPut(storageScopeKey) { LinkedHashMap() }
        } else {
            decodeRecentSearches(
                encoded = dataStore.data.first()[SearchPreferenceKeys.recentSearches(storageScopeKey)].orEmpty(),
            )
        }

    private suspend fun loadRecentDestinations(storageScopeKey: String): LinkedHashMap<String, RecentDestination> =
        if (dataStore == null) {
            recentDestinationsByScope.getOrPut(storageScopeKey) { LinkedHashMap() }
        } else {
            decodeRecentDestinations(
                encoded = dataStore.data.first()[SearchPreferenceKeys.recentDestinations(storageScopeKey)].orEmpty(),
            )
        }

    private suspend fun persistRecentSearches(
        storageScopeKey: String,
        recentSearches: LinkedHashMap<String, RecentSearch>,
    ) {
        if (dataStore == null) return

        dataStore.edit { preferences ->
            if (recentSearches.isEmpty()) {
                preferences.remove(SearchPreferenceKeys.recentSearches(storageScopeKey))
            } else {
                preferences[SearchPreferenceKeys.recentSearches(storageScopeKey)] =
                    encodeRecentSearches(recentSearches.values)
            }
        }
    }

    private suspend fun persistRecentDestinations(
        storageScopeKey: String,
        recentDestinations: LinkedHashMap<String, RecentDestination>,
    ) {
        if (dataStore == null) return

        dataStore.edit { preferences ->
            if (recentDestinations.isEmpty()) {
                preferences.remove(SearchPreferenceKeys.recentDestinations(storageScopeKey))
            } else {
                preferences[SearchPreferenceKeys.recentDestinations(storageScopeKey)] =
                    encodeRecentDestinations(recentDestinations.values)
            }
        }
    }

    private suspend fun resolveStorageScopeKey(): String =
        currentUserScopeProvider()
            .orEmpty()
            .trim()
            .takeIf(String::isNotEmpty)
            ?: DEFAULT_STORAGE_SCOPE

    private fun decodeRecentSearches(encoded: String): LinkedHashMap<String, RecentSearch> =
        runCatching {
            val recentSearches = LinkedHashMap<String, RecentSearch>()
            val jsonArray = JSONArray(encoded)
            for (index in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.optJSONObject(index) ?: continue
                val keyword = jsonObject.optString(JsonFields.KEYWORD, "").trim()
                if (keyword.isEmpty()) continue

                val recentSearch =
                    RecentSearch(
                        keyword = keyword,
                        searchedAtMillis =
                            jsonObject.optLong(
                                JsonFields.SEARCHED_AT_MILLIS,
                                System.currentTimeMillis(),
                            ),
                    )
                recentSearches.remove(keyword.normalizedKeyword())
                recentSearches[keyword.normalizedKeyword()] = recentSearch
            }
            recentSearches
        }.getOrDefault(LinkedHashMap())

    private fun decodeRecentDestinations(encoded: String): LinkedHashMap<String, RecentDestination> =
        runCatching {
            val recentDestinations = LinkedHashMap<String, RecentDestination>()
            val jsonArray = JSONArray(encoded)
            for (index in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.optJSONObject(index) ?: continue
                val name = jsonObject.optString(JsonFields.NAME, "").trim()
                val latitude = jsonObject.optDouble(JsonFields.LATITUDE, Double.NaN)
                val longitude = jsonObject.optDouble(JsonFields.LONGITUDE, Double.NaN)
                if (name.isEmpty() || !latitude.isFinite() || !longitude.isFinite()) continue

                val recentDestination =
                    RecentDestination(
                        placeId = jsonObject.optString(JsonFields.PLACE_ID, "").trim(),
                        name = name,
                        address =
                            jsonObject
                                .optString(JsonFields.ADDRESS, "")
                                .trim()
                                .takeIf(String::isNotEmpty),
                        latitude = latitude,
                        longitude = longitude,
                        category =
                            jsonObject
                                .optString(JsonFields.CATEGORY, "")
                                .takeIf(String::isNotBlank)
                                ?.let(::decodePlaceCategory),
                        accessibilityTagKeys =
                            jsonObject
                                .optJSONArray(JsonFields.ACCESSIBILITY_TAG_KEYS)
                                ?.toStringList()
                                .orEmpty(),
                        searchedAtMillis =
                            jsonObject.optLong(
                                JsonFields.SEARCHED_AT_MILLIS,
                                System.currentTimeMillis(),
                            ),
                    ).sanitized()
                val normalizedKey = recentDestination.normalizedKey()
                if (normalizedKey.isEmpty()) continue

                recentDestinations.remove(normalizedKey)
                recentDestinations[normalizedKey] = recentDestination
            }
            recentDestinations
        }.getOrDefault(LinkedHashMap())

    private fun encodeRecentSearches(recentSearches: Collection<RecentSearch>): String =
        JSONArray().apply {
            recentSearches
                .sortedByDescending(RecentSearch::searchedAtMillis)
                .forEach { recentSearch ->
                    put(
                        JSONObject().apply {
                            put(JsonFields.KEYWORD, recentSearch.keyword)
                            put(JsonFields.SEARCHED_AT_MILLIS, recentSearch.searchedAtMillis)
                        },
                    )
                }
        }.toString()

    private fun encodeRecentDestinations(recentDestinations: Collection<RecentDestination>): String =
        JSONArray().apply {
            recentDestinations
                .sortedByDescending(RecentDestination::searchedAtMillis)
                .forEach { recentDestination ->
                    put(
                        JSONObject().apply {
                            put(JsonFields.PLACE_ID, recentDestination.placeId)
                            put(JsonFields.NAME, recentDestination.name)
                            put(JsonFields.ADDRESS, recentDestination.address)
                            put(JsonFields.LATITUDE, recentDestination.latitude)
                            put(JsonFields.LONGITUDE, recentDestination.longitude)
                            put(JsonFields.CATEGORY, recentDestination.category?.name)
                            put(JsonFields.SEARCHED_AT_MILLIS, recentDestination.searchedAtMillis)
                            put(
                                JsonFields.ACCESSIBILITY_TAG_KEYS,
                                JSONArray().apply {
                                    recentDestination.accessibilityTagKeys.forEach(::put)
                                },
                            )
                        },
                    )
                }
        }.toString()

    private fun JSONArray.toStringList(): List<String> =
        buildList {
            for (index in 0 until length()) {
                optString(index, "").trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }.distinct()

    private fun decodePlaceCategory(raw: String): PlaceCategory? =
        runCatching { PlaceCategory.valueOf(raw) }.getOrNull()

    private fun SearchQuery.normalizedKey(): String =
        buildList {
            add(keyword.normalizedKeyword())
            add("size=$limit")
            latitude?.let { latitude -> add("lat=$latitude") }
            longitude?.let { longitude -> add("lng=$longitude") }
            radiusMeters?.let { radiusMeters -> add("radius=$radiusMeters") }
            cursor?.trim()?.takeIf(String::isNotEmpty)?.let { cursor -> add("cursor=$cursor") }
        }.joinToString(separator = "|")

    private fun String.normalizedKeyword(): String = trim().lowercase()

    private fun RecentDestination.normalizedKey(): String =
        placeId.trim().ifBlank {
            listOf(
                name.trim(),
                address.orEmpty().trim(),
                latitude.toString(),
                longitude.toString(),
            ).joinToString(separator = "|").lowercase()
        }

    private fun RecentDestination.sanitized(): RecentDestination =
        copy(
            placeId = placeId.trim(),
            name = name.trim(),
            address = address?.trim()?.takeIf(String::isNotEmpty),
            accessibilityTagKeys = accessibilityTagKeys.map(String::trim).filter(String::isNotEmpty).distinct(),
        )

    private object JsonFields {
        const val PLACE_ID: String = "placeId"
        const val NAME: String = "name"
        const val ADDRESS: String = "address"
        const val LATITUDE: String = "latitude"
        const val LONGITUDE: String = "longitude"
        const val CATEGORY: String = "category"
        const val ACCESSIBILITY_TAG_KEYS: String = "accessibilityTagKeys"
        const val SEARCHED_AT_MILLIS: String = "searchedAtMillis"
        const val KEYWORD: String = "keyword"
    }

    companion object {
        private const val MAX_RECENT_SEARCHES: Int = 10
        private const val MAX_RECENT_DESTINATIONS: Int = 10
        private const val DEFAULT_STORAGE_SCOPE: String = "guest"
    }
}

private object SearchPreferenceKeys {
    fun recentSearches(storageScopeKey: String) =
        stringPreferencesKey("search_recent_searches::$storageScopeKey")

    fun recentDestinations(storageScopeKey: String) =
        stringPreferencesKey("search_recent_destinations::$storageScopeKey")
}
