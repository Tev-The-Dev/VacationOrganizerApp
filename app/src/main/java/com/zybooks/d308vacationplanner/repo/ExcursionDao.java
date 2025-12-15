package com.zybooks.d308vacationplanner.repo;

import androidx.room.*;

import com.zybooks.d308vacationplanner.model.Excursions;

import java.util.List;

@Dao
public interface ExcursionDao {
    @Query("SELECT * FROM Excursions WHERE excursion_id = :excursionId")
    Excursions getExcursion(long excursionId);
    @Query("SELECT * FROM Excursions")
    List<Excursions> getExcursions();

    @Insert
    long insertExcursion(Excursions excursion);

    @Update
    void updateExcursion(Excursions excursion);
    @Delete
    void deleteExcursion(Excursions excursion);


}
