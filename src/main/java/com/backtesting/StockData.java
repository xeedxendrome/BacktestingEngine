package com.backtesting;

public class StockData {
    private String date;
    private double adjustedClose;
    private int volume; // Added volume field

    public StockData(String date, double adjustedClose, int volume) {
        this.date = date;
        this.adjustedClose = adjustedClose;
        this.volume = volume;
    }

    public String getDate() {
        return date;
    }

    public double getAdjustedClose() {
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
