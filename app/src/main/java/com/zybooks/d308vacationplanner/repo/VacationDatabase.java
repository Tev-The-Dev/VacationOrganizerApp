package com.zybooks.d308vacationplanner.repo;

import androidx.room.*;
import com.zybooks.d308vacationplanner.model.Vacations;
import com.zybooks.d308vacationplanner.model.Excursions;
import com.zybooks.d308vacationplanner.repo.ExcursionDao;
import com.zybooks.d308vacationplanner.repo.VacationDao;

@Database(entities = {Vacations.class, Excursions.class}, version = 1)
public abstract class VacationDatabase extends RoomDatabase {
    public abstract VacationDao vacationDao();
    public abstract ExcursionDao excursionDao();
}