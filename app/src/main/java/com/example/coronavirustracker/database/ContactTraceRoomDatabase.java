package com.example.coronavirustracker.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {ContactTrace.class}, version = 1, exportSchema = false)
public abstract class ContactTraceRoomDatabase extends RoomDatabase {
    public abstract ContactTraceDao ContactTraceDao();


    private static volatile ContactTraceRoomDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static ContactTraceRoomDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (ContactTraceRoomDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            ContactTraceRoomDatabase.class, "word_database")
                            .allowMainThreadQueries() //TODO: don't allow main thread querries for better preformance
                            .build();
                }
            }
        }
        return INSTANCE;
    }



}
