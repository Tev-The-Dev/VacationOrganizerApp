package com.zybooks.d308vacationplanner.model;

import java.util.Date;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "Excursions", foreignKeys = @ForeignKey(entity = Vacations.class, parentColumns = "vacation_id", childColumns = "vacation_id", onDelete = ForeignKey.RESTRICT))

public class Excursions {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "excursion_id")
    private Long mId;
    @ColumnInfo(name = "vacation_id")
    private Long mVacationId;
    @ColumnInfo(name = "excursion_title")
    private String mTitle;
    @ColumnInfo(name = "excursion_date")
    private String mExcursionDate;

    public Excursions(String title, String excursionDate) {
        this.mTitle = title;
        this.mExcursionDate = excursionDate;
    }

    public Long getId() {
        return mId;
    }

    public void setId(Long id) {
        this.mId = id;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public String getExcursionDate() {
        return mExcursionDate;
    }

    public void setExcursionDate(String excursionDate) {
        this.mExcursionDate = excursionDate;
    }
    public Long getVacationId() {
        return mVacationId;
    }

    public void setVacationId(Long vacationId) {
        this.mVacationId = vacationId;
    }
}
