package com.saif.ai.builder.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Download ko browser me kholta hai.
 * AD_GATE_URL set ho to us par bhejta hai (ad landing page → auto download),
 * warna seedha apk/Drive link khol deta hai.
 */
object AdGate {

    fun resolve(downloadUrl: String, appName: String, adGateUrl: String): String {
        if (adGateUrl.isBlank()) return downloadUrl
        return Uri.parse(adGateUrl).buildUpon()
            .appendQueryParameter("url", downloadUrl)
            .appendQueryParameter("name", appName)
            .build()
            .toString()
    }

    fun open(context: Context, url: String): Boolean {
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
