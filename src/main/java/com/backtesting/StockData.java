package com.backtesting;

import java.math.BigDecimal;

public class StockData {
    private String date;
    private BigDecimal adjustedClose;
    private int volume; // Added volume field

    public StockData(String date, BigDecimal adjustedClose, int volume) {
        this.date = date;
        this.adjustedClose = adjustedClose;
        this.volume = volume;
    }

    public String getDate() {
        return date;
    }

    public BigDecimal getAdjustedClose() {
        return adjustedClose;
    }

    public int getVolume() {
        return volume; // Getter for volume
    }

    @Override
    public String toString() {
        return "Date: " + date + ", Adjusted Close: " + adjustedClose + ", Volume: " + volume;
    }
}
