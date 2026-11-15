package repository

import android.content.Context
import com.breckneck.deptbook.domain.repository.DataTransaction
import database.AppDataBase
import java.util.concurrent.Callable

class RoomDataTransaction(context: Context) : DataTransaction {
    private val database = AppDataBase(context)
    override fun <T> run(block: () -> T): T = database.runInTransaction(Callable { block() })
}
