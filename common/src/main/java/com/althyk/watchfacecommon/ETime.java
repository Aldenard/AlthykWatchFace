package com.althyk.watchfacecommon;

public class ETime {
    private static final String TAG = "ETime";

    public static final double L_E_TIME_RATE = 3600.0 / 175;

    public static final long SECOND_IN_MILLIS = 1000;
    public static final long MINUTE_IN_SECOND = 60;
    public static final long HOUR_IN_MINUTE   = 60;
    public static final long DAY_IN_HOUR      = 24;
    public static final long MONTH_IN_DAY     = 32;
    public static final long YEAR_IN_MONTH    = 12;
    public static final long MINUTE_IN_MILLIS = SECOND_IN_MILLIS * MINUTE_IN_SECOND;
    public static final long HOUR_IN_MILLIS   = MINUTE_IN_MILLIS * HOUR_IN_MINUTE;
    public static final long DAY_IN_MILLIS    = HOUR_IN_MILLIS * DAY_IN_HOUR;
    public static final long MONTH_IN_MILLIS  = DAY_IN_MILLIS * MONTH_IN_DAY;
    public static final long YEAR_IN_MILLIS   = MONTH_IN_MILLIS * YEAR_IN_MONTH;

    public int year;
    public int month;
    public int day;
    public int hour;
    public int minute;
    public int second;
    public double time;

    public ETime() {
        this.year = 0;
        this.month = 0;
        this.day = 0;
        this.hour = 0;
        this.minute = 0;
        this.second = 0;
        this.time = 0.0;
    }

    public ETime setEtMillis(double etMillis) {
        this.time   = etMillis;
        this.year   = (int) (Math.floor(this.time / YEAR_IN_MILLIS));
        this.month  = (int) (Math.floor(this.time / MONTH_IN_MILLIS)  % YEAR_IN_MONTH);
        this.day    = (int) (Math.floor(this.time / DAY_IN_MILLIS)    % MONTH_IN_DAY);
        this.hour   = (int) (Math.floor(this.time / HOUR_IN_MILLIS)   % DAY_IN_HOUR);
        this.minute = (int) (Math.floor(this.time / MINUTE_IN_MILLIS) % HOUR_IN_MINUTE);
        this.second = (int) (Math.floor(this.time / SECOND_IN_MILLIS) % MINUTE_IN_SECOND);
        return this;
    }

    public ETime setLtMillis(long ltMillis) {
        return setEtMillis(ltMillis * L_E_TIME_RATE);
    }

    public ETime setToNow() {
        return this.setLtMillis(System.currentTimeMillis());
    }

    public ETime generateStartET() {
        double diffMillis = this.time % SECOND_IN_MILLIS +
                SECOND_IN_MILLIS * this.second +
                MINUTE_IN_MILLIS * this.minute +
                HOUR_IN_MILLIS * (this.hour % 8);
        return new ETime().setEtMillis(this.time - diffMillis);
    }

    // year-month-day-hour
    public String getTimeId() {
        return ETime.getTimeId(this.year, this.month, this.day, this.hour);
    }

    public static final String getTimeId(int year, int month, int day, int hour) {
        return String.format("%d-%02d-%02d-%02d", year, month, day, hour);
    }

}
