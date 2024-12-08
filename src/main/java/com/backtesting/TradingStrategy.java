package com.backtesting;

import java.util.*;
import java.math.*;
import java.util.stream.Collectors;

public class TradingStrategy {

    // Constants for strategy parameters
    private static final int SHORT_TERM_WINDOW = 20; // Window size for short-term EWA
    private static final int LONG_TERM_WINDOW = 100; // Window size for long-term EWA
    private static final BigDecimal DEFAULT_SIGNAL_THRESHOLD = BigDecimal.valueOf(0.05); // Default threshold for trade signals
    private static final int MIN_VOLUME = 200000; // Minimum average trading volume for liquidity filter
    private static final int RSI_PERIOD = 21; // Period for calculating RSI
    private static final BigDecimal RSI_OVERBOUGHT = BigDecimal.valueOf(75.0); // RSI overbought threshold
    private static final BigDecimal RSI_OVERSOLD = BigDecimal.valueOf(25.0); // RSI oversold threshold

    /**
     * Executes the EWA trading strategy on a set of stock data.
     *
     * @param stockDataMap A map of stock symbols to their historical data
     * @return A portfolio with executed trade signals
     */
    public Portfolio EWAStrategy(Map<String, List<StockData>> stockDataMap) {
        Portfolio portfolio = new Portfolio();

        // Filter out illiquid stocks based on average trading volume
        List<String> liquidStocks = filterLiquidStocks(stockDataMap, MIN_VOLUME);

        // Generate and execute trade signals for each liquid stock
        for (String symbol : liquidStocks) {
            List<StockData> stockData = stockDataMap.get(symbol);

            // Skip stocks with insufficient data for long-term calculations
            if (stockData.size() < LONG_TERM_WINDOW) continue;

            List<TradeSignal> signals = generateSignals(stockData);
            portfolio.executeSignals(symbol, signals, stockData);
        }

        return portfolio;
    }

    /**
     * Generates buy/sell trade signals based on EWA, RSI, and volatility.
     *
     * @param stockData Historical data for a single stock
     * @return A list of trade signals
     */
    private List<TradeSignal> generateSignals(List<StockData> stockData) {
        List<TradeSignal> signals = new ArrayList<>();

        // Calculate short-term and long-term EWAs and RSI
        BigDecimal[] shortTermEWA = calculateEWA(stockData, SHORT_TERM_WINDOW);
        BigDecimal[] longTermEWA = calculateEWA(stockData, LONG_TERM_WINDOW);
        BigDecimal[] rsi = calculateRSI(stockData, RSI_PERIOD);

        // Iterate through stock data to generate trade signals
        for (int i = 1; i < stockData.size(); i++) {
            BigDecimal diff = shortTermEWA[i].subtract(longTermEWA[i]);
            BigDecimal volatilityFactor = calculateVolatility(stockData, i);

            // Adjust the signal threshold dynamically based on volatility
            BigDecimal dynamicThreshold = DEFAULT_SIGNAL_THRESHOLD
                    .multiply(volatilityFactor)
                    .max(BigDecimal.valueOf(0.01))
                    .min(BigDecimal.valueOf(0.05));

            // Generate BUY signal
            if (diff.compareTo(dynamicThreshold) > 0 &&
                    shortTermEWA[i - 1].subtract(longTermEWA[i - 1]).compareTo(dynamicThreshold) <= 0 &&
                    rsi[i].compareTo(RSI_OVERSOLD) < 0 &&
                    isTrendFavorable(shortTermEWA, longTermEWA, i)) {
                signals.add(new TradeSignal(stockData.get(i).getDate(), TradeSignal.BUY));
            }
            // Generate SELL signal
            else if (diff.compareTo(dynamicThreshold.negate()) < 0 &&
                    shortTermEWA[i - 1].subtract(longTermEWA[i - 1]).compareTo(dynamicThreshold.negate()) >= 0 &&
                    rsi[i].compareTo(RSI_OVERBOUGHT) > 0 &&
                    isTrendFavorable(shortTermEWA, longTermEWA, i)) {
                signals.add(new TradeSignal(stockData.get(i).getDate(), TradeSignal.SELL));
            }
        }

        return signals;
    }

    /**
     * Determines if the trend is favorable for a trade.
     *
     * @param shortTermEWA Short-term EWA array
     * @param longTermEWA Long-term EWA array
     * @param i Current index
     * @return True if the trend is favorable, false otherwise
     */
    private boolean isTrendFavorable(BigDecimal[] shortTermEWA, BigDecimal[] longTermEWA, int i) {
        return shortTermEWA[i].compareTo(longTermEWA[i]) > 0;
    }

    /**
     * Calculates the Relative Strength Index (RSI) for stock data.
     *
     * @param stockData Historical stock data
     * @param period The period for RSI calculation
     * @return An array of RSI values
     */
    private BigDecimal[] calculateRSI(List<StockData> stockData, int period) {
        BigDecimal[] rsi = new BigDecimal[stockData.size()];
        Arrays.fill(rsi, BigDecimal.ZERO);

        if (stockData.size() <= period) return rsi; // Not enough data for RSI calculation

        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;

        // Calculate average gain and loss for the first period
        for (int i = 1; i <= period; i++) {
            BigDecimal change = stockData.get(i).getAdjustedClose()
                    .subtract(stockData.get(i - 1).getAdjustedClose());
            if (change.compareTo(BigDecimal.ZERO) > 0) avgGain = avgGain.add(change);
            else avgLoss = avgLoss.add(change.abs());
        }

        avgGain = avgGain.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

        // Initial RSI calculation
        rsi[period] = calculateRSIValue(avgGain, avgLoss);

        // RSI for subsequent periods
        for (int i = period + 1; i < stockData.size(); i++) {
            BigDecimal change = stockData.get(i).getAdjustedClose()
                    .subtract(stockData.get(i - 1).getAdjustedClose());

            BigDecimal gain = change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO;
            BigDecimal loss = change.compareTo(BigDecimal.ZERO) < 0 ? change.abs() : BigDecimal.ZERO;

            avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1))
                    .add(gain)
                    .divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

            avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1))
                    .add(loss)
                    .divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

            rsi[i] = calculateRSIValue(avgGain, avgLoss);
        }

        return rsi;
    }

    /**
     * Calculates the RSI value given average gain and loss.
     */
    private BigDecimal calculateRSIValue(BigDecimal avgGain, BigDecimal avgLoss) {
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.valueOf(100); // No losses
        return BigDecimal.valueOf(100).subtract(
                BigDecimal.valueOf(100).divide(
                        BigDecimal.ONE.add(avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP)),
                        4, RoundingMode.HALF_UP));
    }

    /**
     * Calculates the volatility based on historical returns.
     */
    private BigDecimal calculateVolatility(List<StockData> stockData, int currentIndex) {
        int lookback = 20;
        if (currentIndex < lookback) return BigDecimal.ONE;

        BigDecimal[] returns = new BigDecimal[lookback];
        for (int i = currentIndex - lookback; i < currentIndex; i++) {
            returns[i - (currentIndex - lookback)] = stockData.get(i + 1).getAdjustedClose()
                    .subtract(stockData.get(i).getAdjustedClose())
                    .divide(stockData.get(i).getAdjustedClose(), 4, RoundingMode.HALF_UP);
        }

        BigDecimal meanReturn = Arrays.stream(returns)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(lookback), 4, RoundingMode.HALF_UP);

        BigDecimal variance = Arrays.stream(returns)
                .map(r -> r.subtract(meanReturn).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(lookback), 4, RoundingMode.HALF_UP);

        return variance.sqrt(new MathContext(4, RoundingMode.HALF_UP));
    }

    /**
     * Calculates the Exponential Weighted Average (EWA).
     */
    private BigDecimal[] calculateEWA(List<StockData> stockData, int window) {
        BigDecimal[] ewa = new BigDecimal[stockData.size()];
        BigDecimal alpha = BigDecimal.valueOf(2.0).divide(BigDecimal.valueOf(window + 1), 4, RoundingMode.HALF_UP);
        ewa[0] = stockData.get(0).getAdjustedClose();

        for (int i = 1; i < stockData.size(); i++) {
            ewa[i] = stockData.get(i).getAdjustedClose()
                    .multiply(alpha)
                    .add(ewa[i - 1].multiply(BigDecimal.ONE.subtract(alpha)));
        }
        return ewa;
    }

    /**
     * Filters out illiquid stocks based on average trading volume.
     */
    private List<String> filterLiquidStocks(Map<String, List<StockData>> stockDataMap, int minVolume) {
        return stockDataMap.entrySet().stream()
                .filter(entry -> {
                    List<StockData> stockDataList = entry.getValue();
                    if (stockDataList.isEmpty()) return false;

                    BigDecimal avgVolume = stockDataList.stream()
                            .map(StockData::getVolume)
                            .map(BigDecimal::valueOf)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(stockDataList.size()), 2, RoundingMode.HALF_UP);
                    return avgVolume.compareTo(BigDecimal.valueOf(minVolume)) > 0;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
