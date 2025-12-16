package com.zybooks.d308vacationplanner.repo;

import android.content.Context;

import androidx.room.Room;

import com.zybooks.d308vacationplanner.model.Excursions;
import com.zybooks.d308vacationplanner.model.Vacations;

import java.util.List;

public class VacationRepository {
    private static VacationRepository mVacationRepository;
    private final VacationDao mVacationDao;
    private final ExcursionDao mExcursionDao;

    public static VacationRepository getInstance(Context context) {
        if (mVacationRepository == null) {
            mVacationRepository = new VacationRepository(context);
        }
        return mVacationRepository;
    }

    private VacationRepository(Context context) {
        VacationDatabase database = Room.databaseBuilder(context, VacationDatabase.class, "vacation-database.db")
                .allowMainThreadQueries()
                .build();
        mVacationDao = database.vacationDao();
        mExcursionDao = database.excursionDao();

        if (mVacationDao.getVacations().isEmpty()) {
            addStarterData();
        }
    }

    private void addStarterData() {
        // Use ISO date strings yyyy-MM-dd
        Vacations vacation1 = new Vacations("Japan Vacation", "Hotel in Tokyo", "2027-02-01", "2027-02-28");
        long vacationId1 = mVacationDao.insertVacation(vacation1);

        Excursions excursion1 = new Excursions("Kinkaku-Ji Temple", "2027-02-02");
        excursion1.setVacationId(vacationId1);
        mExcursionDao.insertExcursion(excursion1);

        Excursions excursion2 = new Excursions("Mount Fuji", "2027-02-10");
        excursion2.setVacationId(vacationId1);
        mExcursionDao.insertExcursion(excursion2);

        Excursions excursion3 = new Excursions("Tokyo Skytree", "2027-02-15");
        excursion3.setVacationId(vacationId1);
        mExcursionDao.insertExcursion(excursion3);

        Vacations vacation2 = new Vacations("Canada Vacation", "Hotel in Montreal", "2027-03-01", "2027-03-28");
        long vacationId2 = mVacationDao.insertVacation(vacation2);

        Excursions excursion4 = new Excursions("Grand Canyon", "2027-03-02");
        excursion4.setVacationId(vacationId2);
        mExcursionDao.insertExcursion(excursion4);

        Excursions excursion5 = new Excursions("Montreal Museum", "2027-03-10");
        excursion5.setVacationId(vacationId2);
        mExcursionDao.insertExcursion(excursion5);

        Vacations vacation3 = new Vacations("Australia Vacation", "Hotel in Sydney", "2027-04-01", "2027-04-28");
        long vacationId3 = mVacationDao.insertVacation(vacation3);

        Excursions excursion6 = new Excursions("Sydney Opera House", "2027-04-02");
        excursion6.setVacationId(vacationId3);
        mExcursionDao.insertExcursion(excursion6);

        Excursions excursion7 = new Excursions("Sydney Harbour Bridge", "2027-04-10");
        excursion7.setVacationId(vacationId3);
        mExcursionDao.insertExcursion(excursion7);
    }

    // Vacation methods
    public void addVacation(Vacations vacation) {
        long vacationId = mVacationDao.insertVacation(vacation);
        vacation.setId(vacationId);
    }

    public Vacations getVacation(long vacationId) {
        return mVacationDao.getVacation(vacationId);
    }

    public List<Vacations> getVacations() {
        return mVacationDao.getVacations();
    }

    public void updateVacation(Vacations vacation) {
        mVacationDao.updateVacation(vacation);
    }

    public void deleteVacation(Vacations vacation) {
        mVacationDao.deleteVacation(vacation);
    }

    // Excursion methods
    public void addExcursion(Excursions excursion) {
        long excursionId = mExcursionDao.insertExcursion(excursion);
        excursion.setId(excursionId);
    }

    public Excursions getExcursion(long excursionId) {
        return mExcursionDao.getExcursion(excursionId);
    }

    public List<Excursions> getExcursions() {
        return mExcursionDao.getExcursions();
    }

    public void updateExcursion(Excursions excursion) {
        mExcursionDao.updateExcursion(excursion);
    }

    public void deleteExcursion(Excursions excursion) {
        mExcursionDao.deleteExcursion(excursion);
    }
}
