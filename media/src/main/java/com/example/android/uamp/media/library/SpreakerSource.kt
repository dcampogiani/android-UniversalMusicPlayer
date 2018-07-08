/*
 * Copyright 2018 Daniele Campogiani. All rights reserved.
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.TimeUnit

class SpreakerSource(context: Context, source: Uri) : AbstractMusicSource() {
    private var catalog: List<MediaMetadataCompat> = emptyList()

    init {
        state = STATE_INITIALIZING

        SpreakerUpdateCatalogTask(Glide.with(context)) { mediaItems ->
            catalog = mediaItems
            state = STATE_INITIALIZED
        }.execute(source)
    }

    override fun iterator(): Iterator<MediaMetadataCompat> = catalog.iterator()
}

private class SpreakerUpdateCatalogTask(val glide: RequestManager,
                                        val listener: (List<MediaMetadataCompat>) -> Unit) :
        AsyncTask<Uri, Void, List<MediaMetadataCompat>>() {

    override fun doInBackground(vararg params: Uri): List<MediaMetadataCompat> {
        val gson = Gson()
        val mediaItems = ArrayList<MediaMetadataCompat>()

        params.forEach { catalogUri ->
            val catalogConn = URL(catalogUri.toString())
            val reader = BufferedReader(InputStreamReader(catalogConn.openStream()))
            val musicCat = gson.fromJson<SpreakerCatalog>(reader, SpreakerCatalog::class.java)


            mediaItems += musicCat.response.items.map { song ->

                val art = glide.applyDefaultRequestOptions(glideOptions)
                        .asBitmap()
                        .load(song.image_url)
                        .submit(NOTIFICATION_LARGE_ICON_SIZE, NOTIFICATION_LARGE_ICON_SIZE)
                        .get()

                MediaMetadataCompat.Builder()
                        .from(song)
                        .apply {
                            albumArt = art
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
}

fun MediaMetadataCompat.Builder.from(spreakerItem: SpreakerItem): MediaMetadataCompat.Builder {
    val durationMs = TimeUnit.SECONDS.toMillis(spreakerItem.duration)

    val episodeId = spreakerItem.episode_id
    id = episodeId
    title = spreakerItem.title
    album = spreakerItem.show_id
    duration = durationMs
    mediaUri = "https://api.spreaker.com/v2/episodes/$episodeId/play"
    albumArtUri = spreakerItem.image_url
    flag = MediaItem.FLAG_PLAYABLE

    displayTitle = spreakerItem.title
    displayIconUri = spreakerItem.image_url

    downloadStatus = STATUS_NOT_DOWNLOADED
    return this
}

class SpreakerCatalog {
    var response: SpreakerResponse = SpreakerResponse()
}

class SpreakerResponse {
    var items: List<SpreakerItem> = ArrayList()
}

class SpreakerItem {
    var episode_id: String = ""
    var title: String = ""
    var show_id: String = ""
    var image_url: String = ""
    var duration: Long = -1
}

private const val NOTIFICATION_LARGE_ICON_SIZE = 144

private val glideOptions = RequestOptions()
        .fallback(R.drawable.default_art)
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
