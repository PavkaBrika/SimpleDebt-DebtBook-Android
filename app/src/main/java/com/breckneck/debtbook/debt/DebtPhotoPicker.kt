package com.breckneck.debtbook.debt

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.breckneck.debtbook.auth.DebtSystemPicker
import com.breckneck.debtbook.core.MainActivity

@Composable
internal fun rememberDebtPhotoPicker(onFailure: () -> Unit, onSelected: (Uri) -> Unit): () -> Unit {
    val host = LocalActivity.current as? MainActivity
    val context = LocalContext.current
    val picker = DebtSystemPicker.PERSON_PHOTO
    val contract = remember { ActivityResultContracts.PickVisualMedia() }
    val launch = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = contract.parseResult(result.resultCode, result.data)?.takeIf { isGrantedPhoto(context, it) }
        host?.completeDebtPicker(picker, uri != null)
        if (uri != null) onSelected(uri)
        else if (result.resultCode == Activity.RESULT_OK) onFailure()
    }

    return {
        try {
            val intent = contract.createIntent(context, PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            launch.launch(host?.prepareDebtPickerIntent(picker, intent) ?: intent)
        } catch (_: ActivityNotFoundException) {
            host?.completeDebtPicker(picker, false)
            onFailure()
        } catch (_: SecurityException) {
            host?.completeDebtPicker(picker, false)
            onFailure()
        }
    }
}

private fun isGrantedPhoto(context: Context, uri: Uri): Boolean {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT ||
        context.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) !=
        PackageManager.PERMISSION_GRANTED
    ) return false
    return try {
        context.contentResolver.getType(uri)?.startsWith("image/") == true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}
