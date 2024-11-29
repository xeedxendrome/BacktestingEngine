package com.backtesting;

public class Trade {
    private String symbol;
    private String date;
    private double returnValue;

    public Trade(String symbol, String date, double returnValue) {
        this.symbol = symbol;
        this.date = date;
        this.returnValue = returnValue;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDate() {
        return date;
    }

    public double getReturnValue() {
        return returnValue;
    }
}
