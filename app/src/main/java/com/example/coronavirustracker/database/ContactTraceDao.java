package com.example.coronavirustracker.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ContactTraceDao {

    @Query("SELECT * FROM ContactTraceTable")
    List<ContactTrace> getAll();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(ContactTrace contactTrace);

    @Query("DELETE FROM ContactTraceTable")
    void deleteAll();

    @Query("SELECT macAddress FROM ContactTraceTable")
    List<String> getMacAddresses();
}
