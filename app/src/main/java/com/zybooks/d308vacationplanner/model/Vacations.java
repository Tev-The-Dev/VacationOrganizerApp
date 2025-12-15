package com.zybooks.d308vacationplanner.model;

import java.util.Date;
import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName="Vacations")
public class Vacations {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "vacation_id")
    private Long mId;
    @NonNull
    @ColumnInfo(name = "vacation_title")
    private String mTitle;
    @NonNull
    @ColumnInfo(name = "accommodation")
    private String mAccommodation;
    @ColumnInfo(name = "start_date")
    private String mStartDate;
    @ColumnInfo(name = "end_date")
    private String mEndDate;

    public Vacations(String title, String accommodation, String startDate, String endDate) {
        this.mTitle = title;
        this.mAccommodation = accommodation;
        this.mStartDate = startDate;
        this.mEndDate = endDate;

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

    public String getAccommodation() {
        return mAccommodation;
    }

    public void setAccommodation(String accommodation) {
        this.mAccommodation = accommodation;
    }

    public String getStartDate() {
        return mStartDate;
    }

    public void setStartDate(String startDate) {
        this.mStartDate = startDate;
    }

    public String getEndDate() {
        return mEndDate;
    }

    public void setEndDate(String endDate) {
        this.mEndDate = endDate;
    }


}
