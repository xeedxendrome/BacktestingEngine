package com.backtesting;

import java.util.*;

public class TradingStrategy {

    private static final int SHORT_TERM_WINDOW = 50;
    private static final int LONG_TERM_WINDOW = 200;
    private static final double SIGNAL_THRESHOLD = 0.02;
    private static final int MIN_VOLUME = 100000;

    private static final double STOP_LOSS = 0.02; // 2% loss
    private static final double TAKE_PROFIT = 0.04; // 4% profit

    /**
     * Runs the improved moving average strategy with risk management and dynamic thresholds.
     */
    public Portfolio runImprovedStrategy(Map<String, List<StockData>> stockDataMap, List<Double> marketReturns) {
        Portfolio portfolio = new Portfolio();

        // Filter liquid stocks based on average volume
        List<String> liquidStocks = filterLiquidStocks(stockDataMap, MIN_VOLUME);

        for (String symbol : liquidStocks) {
            List<StockData> stockData = stockDataMap.get(symbol);

            if (stockData.size() < LONG_TERM_WINDOW) continue; // Skip stocks with insufficient data

            List<TradeSignal> signals = generateSignals(stockData);
            portfolio.executeSignals(symbol, signals, stockData);
        }

        // Add market returns for regression analysis
        portfolio.addMarketReturns(marketReturns);

        return portfolio;
    }

    /**
     * Generates trade signals using exponential moving averages (EWA).
     */
    private List<TradeSignal> generateSignals(List<StockData> stockData) {
        List<TradeSignal> signals = new ArrayList<>();
        double[] shortTermEWA = calculateEWA(stockData, SHORT_TERM_WINDOW);
        double[] longTermEWA = calculateEWA(stockData, LONG_TERM_WINDOW);

        for (int i = 1; i < stockData.size(); i++) {
            double diff = shortTermEWA[i] - longTermEWA[i];

            if (diff > SIGNAL_THRESHOLD && (shortTermEWA[i - 1] - longTermEWA[i - 1] <= SIGNAL_THRESHOLD)) {
                signals.add(new TradeSignal(stockData.get(i).getDate(), TradeSignal.BUY));
            } else if (diff < -SIGNAL_THRESHOLD && (shortTermEWA[i - 1] - longTermEWA[i - 1] >= -SIGNAL_THRESHOLD)) {
                signals.add(new TradeSignal(stockData.get(i).getDate(), TradeSignal.SELL));
            }
        }

        return signals;
    }


    /**
     * Calculates the exponential moving average (EWA).
     */
    private double[] calculateEWA(List<StockData> stockData, int window) {
        double[] ewa = new double[stockData.size()];
        double alpha = 2.0 / (window + 1);
        ewa[0] = stockData.get(0).getAdjustedClose();

        for (int i = 1; i < stockData.size(); i++) {
            ewa[i] = alpha * stockData.get(i).getAdjustedClose() + (1 - alpha) * ewa[i - 1];
        }
        return ewa;
    }

    /**
     * Checks if momentum is positive over a short-term window.
     */
    private boolean isMomentumPositive(List<StockData> stockData, int index) {
        int momentumWindow = 5;
        if (index < momentumWindow) return false;

        for (int i = index - momentumWindow; i < index; i++) {
            if (stockData.get(i).getAdjustedClose() >= stockData.get(i + 1).getAdjustedClose()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if momentum is negative over a short-term window.
     */
    private boolean isMomentumNegative(List<StockData> stockData, int index) {
        int momentumWindow = 5;
        if (index < momentumWindow) return false;

        for (int i = index - momentumWindow; i < index; i++) {
            if (stockData.get(i).getAdjustedClose() <= stockData.get(i + 1).getAdjustedClose()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Filters stocks based on their liquidity.
     */
    private List<String> filterLiquidStocks(Map<String, List<StockData>> stockDataMap, int minVolume) {
        List<String> liquidStocks = new ArrayList<>();
        for (Map.Entry<String, List<StockData>> entry : stockDataMap.entrySet()) {
            List<StockData> data = entry.getValue();
            double avgVolume = data.stream().mapToDouble(StockData::getVolume).average().orElse(0);
            if (avgVolume > minVolume) {
                liquidStocks.add(entry.getKey());
            }
        }
        return liquidStocks;
    }

    /**
     * Calculates historical volatility as a dynamic factor for thresholds.
     */
    private double calculateVolatility(List<StockData> stockData) {
        double[] returns = new double[stockData.size() - 1];
        for (int i = 1; i < stockData.size(); i++) {
            double dailyReturn = (stockData.get(i).getAdjustedClose() - stockData.get(i - 1).getAdjustedClose()) /
                    stockData.get(i - 1).getAdjustedClose();
            returns[i - 1] = dailyReturn;
        }

        double meanReturn = Arrays.stream(returns).average().orElse(0);
        double variance = Arrays.stream(returns).map(r -> Math.pow(r - meanReturn, 2)).sum() / returns.length;

        return Math.sqrt(variance);
    }
}
