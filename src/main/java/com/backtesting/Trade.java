package com.backtesting;

import java.math.BigDecimal;

public class Trade {
    private String symbol;
    private String date;
    private BigDecimal returnValue;

    public Trade(String symbol, String date, BigDecimal returnValue) {
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

    public BigDecimal getReturnValue() {
        return returnValue;
    }
}
