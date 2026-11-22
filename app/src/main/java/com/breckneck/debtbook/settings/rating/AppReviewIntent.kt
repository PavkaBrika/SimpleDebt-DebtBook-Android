package com.breckneck.debtbook.settings.rating

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.breckneck.debtbook.BuildConfig
import com.breckneck.debtbook.R

internal fun appReviewIntent(context: Context, rating: Int, review: String): Intent {
    val subject = context.getString(R.string.rating_email_subject, BuildConfig.VERSION_NAME)
    val score = context.getString(R.string.rating_score, rating)
    val body = if (review.isEmpty()) score else "$score\n\n$review"
    // Mail clients can prefill the message from the URI even when they ignore intent extras.
    val encodedBody = Uri.encode(body.lineSequence().joinToString("\r\n"))
    val mailto = "mailto:pavlikbrichkin@yandex.ru?subject=${Uri.encode(subject)}&body=$encodedBody"
    return Intent(Intent.ACTION_SENDTO, mailto.toUri())
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
}
