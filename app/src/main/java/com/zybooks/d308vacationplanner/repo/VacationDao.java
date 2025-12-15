// Java
package com.zybooks.d308vacationplanner.repo;

import androidx.room.*;
import com.zybooks.d308vacationplanner.model.Vacations;
import java.util.List;

@Dao
public interface VacationDao {
    @Query("SELECT * FROM Vacations WHERE vacation_id = :vacationId")
    Vacations getVacation(long vacationId);

    @Query("SELECT * FROM Vacations")
    List<Vacations> getVacations();

    @Insert
    long insertVacation(Vacations vacation);

    @Update
    void updateVacation(Vacations vacation);

    @Delete
    void deleteVacation(Vacations vacation);
}
