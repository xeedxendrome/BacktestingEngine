package com.backtesting;
import java.util.*;
import java.math.*;
import java.util.stream.Collectors;

public class TradingStrategy {

    private static final int SHORT_TERM_WINDOW = 20;
    private static final int LONG_TERM_WINDOW = 100;
    private static final BigDecimal DEFAULT_SIGNAL_THRESHOLD = BigDecimal.valueOf(0.05);
    private static final int MIN_VOLUME = 200000;
    private static final int RSI_PERIOD = 21;
    private static final BigDecimal RSI_OVERBOUGHT = BigDecimal.valueOf(75.0);
    private static final BigDecimal RSI_OVERSOLD = BigDecimal.valueOf(25.0);

    private static final BigDecimal BASE_STOP_LOSS = BigDecimal.valueOf(0.02); // 2% default loss
    private static final BigDecimal BASE_TAKE_PROFIT = BigDecimal.valueOf(0.04); // 4% default profit

    public Portfolio EWAStrategy(Map<String, List<StockData>> stockDataMap) {
        Portfolio portfolio = new Portfolio();

        // Filter liquid stocks
        List<String> liquidStocks = filterLiquidStocks(stockDataMap, MIN_VOLUME);

        for (String symbol : liquidStocks) {
            List<StockData> stockData = stockDataMap.get(symbol);

            if (stockData.size() < LONG_TERM_WINDOW) continue;

            List<TradeSignal> signals = generateSignals(stockData);
            portfolio.executeSignals(symbol, signals, stockData);
        }

        return portfolio;
    }

    private List<TradeSignal> generateSignals(List<StockData> stockData) {
        List<TradeSignal> signals = new ArrayList<>();
        BigDecimal[] shortTermEWA = calculateEWA(stockData, SHORT_TERM_WINDOW);
        BigDecimal[] longTermEWA = calculateEWA(stockData, LONG_TERM_WINDOW);
        BigDecimal[] rsi = calculateRSI(stockData, RSI_PERIOD);

        for (int i = 1; i < stockData.size(); i++) {
            BigDecimal diff = shortTermEWA[i].subtract(longTermEWA[i]);
            BigDecimal volatilityFactor = calculateVolatility(stockData, i);

            // Dynamic threshold based on volatility
            BigDecimal dynamicThreshold = DEFAULT_SIGNAL_THRESHOLD
                    .multiply(volatilityFactor)
                    .max(BigDecimal.valueOf(0.01))
                    .min(BigDecimal.valueOf(0.05));

            if (diff.compareTo(dynamicThreshold) > 0 &&
                    shortTermEWA[i - 1].subtract(longTermEWA[i - 1]).compareTo(dynamicThreshold) <= 0 &&
                    rsi[i].compareTo(RSI_OVERSOLD) < 0 &&
                    isTrendFavorable(shortTermEWA, longTermEWA, i)) {
                signals.add(new TradeSignal(stockData.get(i).getDate(), TradeSignal.BUY));
            } else if (diff.compareTo(dynamicThreshold.negate()) < 0 &&
                    shortTermEWA[i - 1].subtract(longTermEWA[i - 1]).compareTo(dynamicThreshold.negate()) >= 0 &&
                    rsi[i].compareTo(RSI_OVERBOUGHT) > 0 &&
                    isTrendFavorable(shortTermEWA, longTermEWA, i)) {
                signals.add(new TradeSignal(stockData.get(i).getDate(), TradeSignal.SELL));
            }
        }

        return signals;
    }

    private boolean isTrendFavorable(BigDecimal[] shortTermEWA, BigDecimal[] longTermEWA, int i) {
        return shortTermEWA[i].compareTo(longTermEWA[i]) > 0;
    }

    private BigDecimal[] calculateRSI(List<StockData> stockData, int period) {
        BigDecimal[] rsi = new BigDecimal[stockData.size()];
        Arrays.fill(rsi, BigDecimal.ZERO); // Initialize all elements to zero

        // Ensure we have enough data to calculate RSI
        if (stockData.size() <= period) {
            return rsi;
        }

        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;

        // Initial calculation for the first period
        for (int i = 1; i <= period; i++) {
            BigDecimal change = stockData.get(i).getAdjustedClose()
                    .subtract(stockData.get(i - 1).getAdjustedClose());
            if (change.compareTo(BigDecimal.ZERO) > 0)
                avgGain = avgGain.add(change);
            else
                avgLoss = avgLoss.add(change.abs());
        }

        avgGain = avgGain.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);

        // Calculate initial RSI
        rsi[period] = calculateRSIValue(avgGain, avgLoss);

        // Continue calculating RSI for subsequent periods
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

    private BigDecimal calculateRSIValue(BigDecimal avgGain, BigDecimal avgLoss) {
        // Prevent division by zero
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }

        return BigDecimal.valueOf(100).subtract(
                BigDecimal.valueOf(100).divide(
                        BigDecimal.ONE.add(avgGain.divide(
                                avgLoss,
                                4,
                                RoundingMode.HALF_UP
                        )),
                        4,
                        RoundingMode.HALF_UP
                )
        );
    }

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

    private List<String> filterLiquidStocks(Map<String, List<StockData>> stockDataMap, int minVolume) {
        return stockDataMap.entrySet().stream()
                .filter(entry -> {
                    List<StockData> stockDataList = entry.getValue();
                    if (stockDataList.isEmpty()) {
                        return false; // Skip empty lists to avoid division by zero
                    }

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