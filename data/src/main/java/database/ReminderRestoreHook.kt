package database

/** The Android app synchronously replaces OS schedules after a committed restore/undo. */
object ReminderRestoreHook {
    @Volatile var afterRestore: (() -> Unit)? = null
}
