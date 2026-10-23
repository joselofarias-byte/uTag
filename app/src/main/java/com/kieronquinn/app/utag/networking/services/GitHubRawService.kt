package com.kieronquinn.app.utag.networking.services

import android.content.Context
import com.kieronquinn.app.utag.networking.model.github.ModRelease
import com.kieronquinn.app.utag.utils.extensions.withCache
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET

interface GitHubRawService {

    companion object {
        /**
         *  Fork raw content. The app reads SmartThings.json from here, not from
         *  upstream, so a version stamp published on KieronQuinn/uTag cannot
         *  force an update loop on this build. The download URL inside the JSON
         *  can still point at the upstream APK until this fork publishes its own.
         */
        private const val BASE_URL =
            "https://raw.githubusercontent.com/joselofarias-byte/uTag/refs/heads/main/"

        fun createService(retrofit: Retrofit, context: Context): GitHubRawService {
            return retrofit.newBuilder()
                .baseUrl(BASE_URL)
                .withCache(context)
                .build()
                .create(GitHubRawService::class.java)
        }
    }

    @GET("SmartThings.json")
    fun getRelease(): Call<ModRelease>

}