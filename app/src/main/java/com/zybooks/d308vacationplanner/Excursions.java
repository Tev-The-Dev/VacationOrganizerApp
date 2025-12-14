package com.zybooks.d308vacationplanner;

import java.util.Date;

public class Excursions {
    private Long mId;
    private String mTitle;
    private Date mExcursionDate;

    public Excursions(String title, Date excursionDate) {
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

    public Date getExcursionDate() {
        return mExcursionDate;
    }

    public void setExcursionDate(Date excursionDate) {
        this.mExcursionDate = excursionDate;
    }
}
