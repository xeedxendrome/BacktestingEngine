package com.backtesting;

public class TradeSignal {
    public static final String BUY = "BUY";
    public static final String SELL = "SELL";

    private String date;
    private String signal;

    public TradeSignal(String date, String signal) {
        this.date = date;
        this.signal = signal;
    }

    public String getDate() {
        return date;
    }

    public String getSignal() {
        return signal;
    }
}
