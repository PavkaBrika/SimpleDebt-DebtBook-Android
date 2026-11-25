package com.breckneck.debtbook.settings

import androidx.lifecycle.ViewModelProvider
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.core.navigation.Backup
import com.breckneck.debtbook.settings.synchronization.SynchronizationViewModel

/** Install fixture providers on an already opened backup screen. */
internal fun MainActivity.backupFixtureModel(): SynchronizationViewModel {
    val entry = requireNotNull(navController).getBackStackEntry<Backup>()
    return ViewModelProvider(entry)[SynchronizationViewModel::class.java]
}
