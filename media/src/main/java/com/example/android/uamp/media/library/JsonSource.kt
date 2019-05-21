/*
 * Copyright 2017 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.uamp.media.library

import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat.STATUS_NOT_DOWNLOADED
import android.support.v4.media.MediaMetadataCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.android.uamp.media.R
import com.example.android.uamp.media.extensions.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import khttp.get
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Source of [MediaMetadataCompat] objects created from a basic JSON stream.
 *
 * The definition of the JSON is specified in the docs of [JsonMusic] in this file,
 * which is the object representation of it.
 */
class JsonSource(context: Context, source: Uri) : AbstractMusicSource() {
    private var catalog: List<MediaMetadataCompat> = emptyList()

    init {
        state = STATE_INITIALIZING

        UpdateCatalogTask(Glide.with(context)) { mediaItems ->
            catalog = mediaItems
            state = STATE_INITIALIZED
        }.execute(source)
    }

    override fun iterator(): Iterator<MediaMetadataCompat> = catalog.iterator()
}

/**
 * Task to connect to remote URIs and download/process JSON files that correspond to
 * [MediaMetadataCompat] objects.
 */
private class UpdateCatalogTask(val glide: RequestManager,
                                val listener: (List<MediaMetadataCompat>) -> Unit) :
        AsyncTask<Uri, Void, List<MediaMetadataCompat>>() {

    override fun doInBackground(vararg params: Uri): List<MediaMetadataCompat> {
        val mediaItems = ArrayList<MediaMetadataCompat>()

        params.forEach { catalogUri ->
            val musicCat = tryDownloadJson(catalogUri)

            // Get the base URI to fix up relative references later.
            val baseUri = catalogUri.toString().removeSuffix(catalogUri.lastPathSegment)

            mediaItems += musicCat.tracks.map { song ->
                // The JSON may have paths that are relative to the source of the JSON
                // itself. We need to fix them up here to turn them into absolute paths.
                if (!song.mp3.startsWith(catalogUri.scheme)) {
                    song.mp3 = baseUri + song.mp3
                }
                if (!song.image.startsWith(catalogUri.scheme)) {
                    song.image = baseUri + song.image
                }

                // Block on downloading artwork.
                /* val art = glide.applyDefaultRequestOptions(glideOptions)
                         .asBitmap()
                         .load(song.image)
                         .submit(NOTIFICATION_LARGE_ICON_SIZE, NOTIFICATION_LARGE_ICON_SIZE)
                         .get()*/

                MediaMetadataCompat.Builder()
                        .from(song)
                        .apply {
                            albumArt = null
                        }
                        .build()
            }.toList()
        }

        return mediaItems
    }

    override fun onPostExecute(mediaItems: List<MediaMetadataCompat>) {
        super.onPostExecute(mediaItems)
        listener(mediaItems)
    }

    /**
     * Attempts to download a catalog from a given Uri.
     *
     * @param catalogUri URI to attempt to download the catalog form.
     * @return The catalog downloaded, or an empty catalog if an error occurred.
     */
    private fun tryDownloadJson(catalogUri: Uri) =
            try {
                //val catalogConn = URL(catalogUri.toString())
                //val reader = BufferedReader(InputStreamReader(catalogConn.openStream()))
                val start = System.currentTimeMillis()
                val auth = mapOf("Authorization" to
                        "Bearer " +
                        "bb2286b37f9df4df7c33d79bd2479925c5ec35531feab05e" +
                        "4375a20fad4369f3fc5128194360d9296d39c7f6bde839f9")
                val theYear = get("$catalogUri", headers = auth)

                val gson = GsonBuilder().create()
                val shows = gson.fromJson(theYear.jsonObject.toString(), JsonPhishShowWrap::class.java)

                var showData = shows.data

                for (sh in showData) {
                    for (test in sh.tracks) {
                        test.venue_name = sh.venue_name
                    }
                }

                var flatYear: List<JsonPhishTracks> = showData.flatMap { it.tracks }
                val rootObj = JSONObject()
                var yearJson = Gson().toJson(flatYear)
                val tracksObj = JSONArray(yearJson)
                rootObj.put("tracks", tracksObj)

                val end = System.currentTimeMillis()
                println("TIME: ${end - start} ms")
                Gson().fromJson<JsonCatalog>(rootObj.toString(), JsonCatalog::class.java)
            } catch (ioEx: IOException) {
                JsonCatalog()
            }
}

/**
 * Extension method for [MediaMetadataCompat.Builder] to set the fields from
 * our JSON constructed object (to make the code a bit easier to see).
 */
fun MediaMetadataCompat.Builder.from(jsonMusic: JsonMusic): MediaMetadataCompat.Builder {
    // The duration from the JSON is given in seconds, but the rest of the code works in
    // milliseconds. Here's where we convert to the proper units.
    val durationMs = TimeUnit.SECONDS.toMillis(jsonMusic.duration)

    id = jsonMusic.id
    title = jsonMusic.title
    artist = jsonMusic.show_date
    album = jsonMusic.venue_name
    duration = durationMs
    genre = jsonMusic.genre
    mediaUri = jsonMusic.mp3
    albumArtUri = jsonMusic.image
    trackNumber = jsonMusic.position
    trackCount = jsonMusic.totalTrackCount
    flag = MediaItem.FLAG_PLAYABLE

    // To make things easier for *displaying* these, set the display properties as well.
    displayTitle = jsonMusic.title
    displaySubtitle = jsonMusic.set_name
    displayDescription = jsonMusic.show_date
    displayIconUri = jsonMusic.image

    // Add downloadStatus to force the creation of an "extras" bundle in the resulting
    // MediaMetadataCompat object. This is needed to send accurate metadata to the
    // media session during updates.
    downloadStatus = STATUS_NOT_DOWNLOADED

    // Allow it to be used in the typical builder style.
    return this
}

/**
 * Wrapper object for our JSON in order to be processed easily by GSON.
 */
class JsonCatalog {
    var tracks: List<JsonMusic> = ArrayList()
}

/**
 * An individual piece of music included in our JSON catalog.
 * The format from the server is as specified:
 * ```
 *     { "music" : [
 *     { "title" : // Title of the piece of music
 *     "album" : // Album title of the piece of music
 *     "artist" : // Artist of the piece of music
 *     "genre" : // Primary genre of the music
 *     "source" : // Path to the music, which may be relative
 *     "image" : // Path to the art for the music, which may be relative
 *     "trackNumber" : // Track number
 *     "totalTrackCount" : // Track count
 *     "duration" : // Duration of the music in seconds
 *     "site" : // Source of the music, if applicable
 *     }
 *     ]}
 * ```
 *
 * `source` and `image` can be provided in either relative or
 * absolute paths. For example:
 * ``
 *     "source" : "https://www.example.com/music/ode_to_joy.mp3",
 *     "image" : "ode_to_joy.jpg"
 * ``
 *
 * The `source` specifies the full URI to download the piece of music from, but
 * `image` will be fetched relative to the path of the JSON file itself. This means
 * that if the JSON was at "https://www.example.com/json/music.json" then the image would be found
 * at "https://www.example.com/json/ode_to_joy.jpg".
 */
class JsonMusic {
    var id: String = ""
    var title: String = ""
    var set_name: String = ""
    var show_date: String = ""
    var venue_name: String = ""
    var artist: String = "Phish"
    var genre: String = ""
    var mp3: String = ""
    var image: String = ""
    var position: Long = 0
    var totalTrackCount: Long = 0
    var duration: Long = -1
    var site: String = ""
}

class JsonPhishShowWrap {
    var data: List<JsonPhishShow> = emptyList()
}

class JsonPhishShow {
    var id: String = ""
    var date: String = ""
    var duration: String = ""
    var sbd: String = ""
    var tour_id: String = ""
    var venue_name: String = ""
    var tracks: List<JsonPhishTracks> = emptyList()
}

class JsonPhishTracks {
    var id: String = ""
    var title: String = ""
    var position: String = ""
    var venue_name: String = ""
    var show_date: String = ""
    var duration: Long = -1
    var set_name: String = ""
    var mp3: String = ""
}

private const val NOTIFICATION_LARGE_ICON_SIZE = 144 // px

private val glideOptions = RequestOptions()
        .fallback(R.drawable.default_art)
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
