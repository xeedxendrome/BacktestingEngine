package com.backtesting;

import java.util.*;

public class TradingStrategy {

    private static final int SHORT_TERM_WINDOW = 50;
    private static final int LONG_TERM_WINDOW = 200;
    private static final double DEFAULT_SIGNAL_THRESHOLD = 0.02;
    private static final int MIN_VOLUME = 100000;
    private static final int RSI_PERIOD = 14;
    private static final double RSI_OVERBOUGHT = 70.0;
    private static final double RSI_OVERSOLD = 30.0;

    private static final double BASE_STOP_LOSS = 0.02; // 2% default loss
    private static final double BASE_TAKE_PROFIT = 0.04; // 4% default profit

    public Portfolio runImprovedStrategy(Map<String, List<StockData>> stockDataMap, List<Double> marketReturns) {
        Portfolio portfolio = new Portfolio();

        // Filter liquid stocks
        List<String> liquidStocks = filterLiquidStocks(stockDataMap, MIN_VOLUME);

        for (String symbol : liquidStocks) {
            List<StockData> stockData = stockDataMap.get(symbol);

            if (stockData.size() < LONG_TERM_WINDOW) continue;

            List<TradeSignal> signals = generateSignals(stockData);
            portfolio.executeSignals(symbol, signals, stockData);
        }

        portfolio.addMarketReturns(marketReturns);
        return portfolio;
    }

    private List<TradeSignal> generateSignals(List<StockData> stockData) {
        List<TradeSignal> signals = new ArrayList<>();
        double[] shortTermEWA = calculateEWA(stockData, SHORT_TERM_WINDOW);
        double[] longTermEWA = calculateEWA(stockData, LONG_TERM_WINDOW);
        double[] rsi = calculateRSI(stockData, RSI_PERIOD);

        for (int i = 1; i < stockData.size(); i++) {
            double diff = shortTermEWA[i] - longTermEWA[i];
            double volatilityFactor = calculateVolatility(stockData, i);

            // Dynamic threshold based on volatility
            double dynamicThreshold = DEFAULT_SIGNAL_THRESHOLD * volatilityFactor;

            if (diff > dynamicThreshold &&
                    (shortTermEWA[i - 1] - longTermEWA[i - 1] <= dynamicThreshold) &&
                    rsi[i] < RSI_OVERSOLD) {

                signals.add(new TradeSignal(stockData.get(i).getDate(), TradeSignal.BUY));
            } else if (diff < -dynamicThreshold &&
                    (shortTermEWA[i - 1] - longTermEWA[i - 1] >= -dynamicThreshold) &&
                    rsi[i] > RSI_OVERBOUGHT) {

                signals.add(new TradeSignal(stockData.get(i).getDate(), TradeSignal.SELL));
            }
        }

        return signals;
    }

    private double[] calculateRSI(List<StockData> stockData, int period) {
        double[] rsi = new double[stockData.size()];
        double avgGain = 0, avgLoss = 0;

        for (int i = 1; i <= period; i++) {
            double change = stockData.get(i).getAdjustedClose() - stockData.get(i - 1).getAdjustedClose();
            if (change > 0) avgGain += change;
            else avgLoss -= change;
        }

        avgGain /= period;
        avgLoss /= period;
        rsi[period] = 100 - (100 / (1 + avgGain / avgLoss));

        for (int i = period + 1; i < stockData.size(); i++) {
            double change = stockData.get(i).getAdjustedClose() - stockData.get(i - 1).getAdjustedClose();
            avgGain = (avgGain * (period - 1) + Math.max(change, 0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-change, 0)) / period;

            rsi[i] = 100 - (100 / (1 + avgGain / avgLoss));
        }

        return rsi;
    }

    private double calculateVolatility(List<StockData> stockData, int currentIndex) {
        int lookback = 20;
        if (currentIndex < lookback) return 1.0;

        double[] returns = new double[lookback];
        for (int i = currentIndex - lookback; i < currentIndex; i++) {
            returns[i - (currentIndex - lookback)] = (stockData.get(i + 1).getAdjustedClose() - stockData.get(i).getAdjustedClose()) /
                    stockData.get(i).getAdjustedClose();
        }

        double meanReturn = Arrays.stream(returns).average().orElse(0);
        double variance = Arrays.stream(returns).map(r -> Math.pow(r - meanReturn, 2)).sum() / lookback;

        return Math.sqrt(variance);
    }

    private double[] calculateEWA(List<StockData> stockData, int window) {
        double[] ewa = new double[stockData.size()];
        double alpha = 2.0 / (window + 1);
        ewa[0] = stockData.get(0).getAdjustedClose();

        for (int i = 1; i < stockData.size(); i++) {
            ewa[i] = alpha * stockData.get(i).getAdjustedClose() + (1 - alpha) * ewa[i - 1];
        }
        return ewa;
    }

    private List<String> filterLiquidStocks(Map<String, List<StockData>> stockDataMap, int minVolume) {
        List<String> liquidStocks = new ArrayList<>();
        for (Map.Entry<String, List<StockData>> entry : stockDataMap.entrySet()) {
            List<StockData> data = entry.getValue();
            double avgVolume = data.stream().mapToDouble(StockData::getVolume).average().orElse(0);
            if (avgVolume > minVolume) liquidStocks.add(entry.getKey());
        }
        return liquidStocks;
    }
}
