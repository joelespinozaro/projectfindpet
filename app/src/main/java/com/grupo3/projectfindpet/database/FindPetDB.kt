package com.grupo3.projectfindpet.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.grupo3.projectfindpet.modelsRoom.PetRoom
import com.grupo3.projectfindpet.modelsRoom.MessageRoom
import com.grupo3.projectfindpet.modelsRoom.UserRoom
import kotlinx.coroutines.InternalCoroutinesApi

@Database(entities = [UserRoom::class, PetRoom::class, MessageRoom::class,
], version = 22, exportSchema = false)
@TypeConverters(Converters::class)

abstract class FindPetDB : RoomDatabase() {
    abstract  val findPetDAO: FindPetDAO

    companion object {
        @Volatile
        private var INSTANCE: FindPetDB? = null

        @InternalCoroutinesApi
        fun getInstance(context: Context?): FindPetDB {
            synchronized(this) {
                var instance = INSTANCE
                if(instance == null) {
                    instance = Room.databaseBuilder(
                        context!!.applicationContext,
                        FindPetDB::class.java,
                        "findPet_db"
                    )
                        .fallbackToDestructiveMigration()
                        .build()

                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}