package com.zybooks.d308vacationplanner;

import java.util.Date;

public class Vacations {
    private Long mId;
    private String mTitle;
    private String mAccommodation;
    private Date mStartDate;
    private Date mEndDate;

    public void Vacation(String title, String accommodation, Date startDate, Date endDate) {
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

    public Date getStartDate() {
        return mStartDate;
    }

    public void setStartDate(Date startDate) {
        this.mStartDate = startDate;
    }

    public Date getEndDate() {
        return mEndDate;
    }

    public void setEndDate(Date endDate) {
        this.mEndDate = endDate;
    }


}
