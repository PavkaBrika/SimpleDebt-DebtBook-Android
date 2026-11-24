package com.breckneck.debtbook.debt

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import com.breckneck.deptbook.domain.backup.BackupCodec
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PersonAvatar(id: Int, photo: String?, size: Dp = 38.dp) {
    val bytes = remember(photo) { photo?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() } }
    Box(Modifier.size(size).clip(CircleShape)) {
        PersonMotif(id, Modifier.matchParentSize())
        if (bytes != null) {
            AsyncImage(bytes, contentDescription = null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}

internal suspend fun encodePersonPhoto(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val request = ImageRequest.Builder(context)
        .data(uri).size(192).scale(Scale.FIT).precision(Precision.EXACT).allowHardware(false)
        .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED)
        .build()
    val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image
        ?: error("Could not read photo")
    val bitmap = image.toBitmap()
    val output = ByteArrayOutputStream()
    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output))
    val encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    require(encoded.length <= BackupCodec.MAX_PERSON_PHOTO_CHARACTERS) { "Person photo is too large" }
    encoded
}
