package com.example.examprep.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.examprep.data.model.CostRecord;

import java.util.List;

@Dao
public interface CostRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CostRecord costRecord);

    @Query("SELECT * FROM cost_records WHERE date = :date LIMIT 1")
    CostRecord getCostRecordForDate(String date);

    @Query("SELECT SUM(costUsd) FROM cost_records WHERE date = :date")
    Double getTotalCostForDate(String date);

    @Query("SELECT * FROM cost_records ORDER BY date DESC LIMIT 30")
    List<CostRecord> getRecentCostRecords();
}
