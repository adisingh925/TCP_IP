package com.adreal.tcp_ip.Database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.adreal.tcp_ip.Dao.Dao
import com.adreal.tcp_ip.DataClass.ConnectionData

@androidx.room.Database(entities = [ConnectionData::class], version = 1, exportSchema = false)
abstract class Database : RoomDatabase() {

    abstract fun connectionDao(): Dao

    companion object{

        @Volatile
        private var INSTANCE : Database? = null

        fun getDatabase(context: Context): Database {
            val tempInstance = INSTANCE
            if(tempInstance!=null) {
                return tempInstance
            }
            synchronized(this)
            {
                val instance = Room.databaseBuilder(context.applicationContext,
                    Database::class.java,
                    "ConnectionDatabase").build()

                INSTANCE = instance
                return instance
            }
        }
    }
}