package com.kieronquinn.app.utag.model.database.petwalk

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kieronquinn.app.utag.utils.extensions.wrapAsApplicationContext

/**
 * Separate from [com.kieronquinn.app.utag.model.database.UTagDatabase] so existing installs
 * keep their data. That database is version 1 with no migrations.
 */
@Database(
    entities = [
        PetWalkEntity::class,
        PetWalkSampleEntity::class,
        PetWalkEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PetWalkDatabase: RoomDatabase() {

    companion object {
        fun getDatabase(context: Context): PetWalkDatabase {
            return Room.databaseBuilder(
                context.wrapAsApplicationContext(),
                PetWalkDatabase::class.java,
                "pet_walk.db"
            ).enableMultiInstanceInvalidation().build()
        }
    }

    abstract fun petWalkDao(): PetWalkDao

}
