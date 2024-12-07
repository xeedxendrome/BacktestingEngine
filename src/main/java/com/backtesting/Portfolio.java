package com.backtesting;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;


public class Portfolio {

    private final List<Trade> trades = new ArrayList<>();
    private final Map<String, BigDecimal> dailyReturnsMap = new LinkedHashMap<>();
    private final List<BigDecimal> dailyReturns = new ArrayList<>();
    private final Map<String, BigDecimal> marketReturnsMap = new LinkedHashMap<>();
    private final List<BigDecimal> marketReturns = new ArrayList<>();

    private static final BigDecimal STARTING_CAPITAL = new BigDecimal("100000.00");
    private BigDecimal currentCapital = STARTING_CAPITAL;
    private static final BigDecimal RISK_FREE_RATE = new BigDecimal("0.02"); // 2% annual
    private BigDecimal volatility = new BigDecimal("0.01");

    public void updatepnl(String date, BigDecimal value) {
        dailyReturnsMap.merge(date, value, BigDecimal::add);
    }

    public void executeSignals(String symbol, List<TradeSignal> signals, List<StockData> stockData) {
        if (signals.isEmpty()) return;

        BigDecimal position = BigDecimal.ZERO;
        BigDecimal entryPrice = BigDecimal.ZERO;
        BigDecimal stopLossThreshold = volatility.multiply(new BigDecimal("2"));

        for (StockData data : stockData) {
            Optional<TradeSignal> signal = signals.stream()
                    .filter(s -> s.getDate().equals(data.getDate()))
                    .findFirst();

            if (signal.isPresent()) {
                String action = signal.get().getSignal();

                if (action.equals(TradeSignal.BUY) && position.compareTo(BigDecimal.ZERO) <= 0) {
                    // Close short position
                    if (position.compareTo(BigDecimal.ZERO) < 0) {
                        BigDecimal pnl = entryPrice.subtract(data.getAdjustedClose())
                                .multiply(position.abs())
                                .setScale(2, RoundingMode.HALF_UP);

                        updatepnl(data.getDate(), pnl.divide(currentCapital, 4, RoundingMode.HALF_UP));
                        currentCapital = currentCapital.add(pnl);
                        trades.add(new Trade(symbol, data.getDate(), pnl));
                        position = BigDecimal.ZERO;
                    }

                    // Open long position
                    position = calculatePositionSize(data.getAdjustedClose());
                    entryPrice = data.getAdjustedClose();
                }
                else if (action.equals(TradeSignal.SELL) && position.compareTo(BigDecimal.ZERO) >= 0) {
                    // Close long position
                    if (position.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal pnl = data.getAdjustedClose().subtract(entryPrice)
                                .multiply(position)
                                .setScale(2, RoundingMode.HALF_UP);

                        updatepnl(data.getDate(), pnl.divide(currentCapital, 4, RoundingMode.HALF_UP));
                        currentCapital = currentCapital.add(pnl);
                        trades.add(new Trade(symbol, data.getDate(), pnl));
                        position = BigDecimal.ZERO;
                    }

                    // Open short position
                    position = calculatePositionSize(data.getAdjustedClose()).negate();
                    entryPrice = data.getAdjustedClose();
                }
            }

            // Stop Loss Logic
            if (position.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal lossPct = entryPrice.subtract(data.getAdjustedClose())
                        .divide(entryPrice, 4, RoundingMode.HALF_UP);

                if (lossPct.abs().compareTo(stopLossThreshold) >= 0) {
                    BigDecimal pnl = data.getAdjustedClose().subtract(entryPrice)
                            .multiply(position)
                            .setScale(2, RoundingMode.HALF_UP);

                    updatepnl(data.getDate(), pnl.divide(currentCapital, 4, RoundingMode.HALF_UP));
                    currentCapital = currentCapital.add(pnl);
                    trades.add(new Trade(symbol, data.getDate(), pnl));
                    position = BigDecimal.ZERO;
                }
            }
            else if (position.compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal lossPct = data.getAdjustedClose().subtract(entryPrice)
                        .divide(entryPrice, 4, RoundingMode.HALF_UP);

                if (lossPct.abs().compareTo(stopLossThreshold) >= 0) {
                    BigDecimal pnl = entryPrice.subtract(data.getAdjustedClose())
                            .multiply(position.abs())
                            .setScale(2, RoundingMode.HALF_UP);

                    updatepnl(data.getDate(), pnl.divide(currentCapital, 4, RoundingMode.HALF_UP));
                    currentCapital = currentCapital.add(pnl);
                    trades.add(new Trade(symbol, data.getDate(), pnl));
                    position = BigDecimal.ZERO;
                }
            }
        }

        // Close final position if open
        if (position.compareTo(BigDecimal.ZERO) != 0) {
            StockData lastData = stockData.get(stockData.size() - 1);
            BigDecimal finalPnL = position.compareTo(BigDecimal.ZERO) > 0
                    ? lastData.getAdjustedClose().subtract(entryPrice).multiply(position)
                    : entryPrice.subtract(lastData.getAdjustedClose()).multiply(position.abs());

            finalPnL = finalPnL.setScale(2, RoundingMode.HALF_UP);
            updatepnl(lastData.getDate(), finalPnL.divide(currentCapital, 4, RoundingMode.HALF_UP));
            currentCapital = currentCapital.add(finalPnL);
            trades.add(new Trade(symbol, lastData.getDate(), finalPnL));
        }
    }

    private BigDecimal calculatePositionSize(BigDecimal price) {
        // Position sizing logic with BigDecimal
        final BigDecimal MAX_POSITION_FRACTION = new BigDecimal("0.1");
        final BigDecimal MAX_VOLATILITY = new BigDecimal("0.05");

        BigDecimal effectiveVolatility = volatility.min(MAX_VOLATILITY);

        BigDecimal positionMultiplier = BigDecimal.ONE.min(
                BigDecimal.ONE.divide(effectiveVolatility.multiply(new BigDecimal("100")), 4, RoundingMode.HALF_UP)
        );

        BigDecimal positionSize = currentCapital
                .multiply(positionMultiplier)
                .divide(price, 4, RoundingMode.HALF_UP);

        BigDecimal maxPosition = currentCapital
                .multiply(MAX_POSITION_FRACTION)
                .divide(price, 4, RoundingMode.HALF_UP);

        return positionSize.min(maxPosition);
    }

    public void addMarketReturns(Map<String, BigDecimal> marketReturns) {
        this.marketReturnsMap.putAll(marketReturns);
    }

    public void alignReturnsForRegression() {
        List<String> commonDates = new ArrayList<>(dailyReturnsMap.keySet());
        commonDates.retainAll(marketReturnsMap.keySet());

        dailyReturns.clear();
        marketReturns.clear();

        commonDates.forEach(date -> {
            dailyReturns.add(dailyReturnsMap.get(date));
            marketReturns.add(marketReturnsMap.get(date));
        });
        System.out.println(dailyReturns);
        System.out.println(marketReturns);
    }

    public void performRegressionAnalysis() {
        if (dailyReturns.size() != marketReturns.size()) {
            System.err.println("Mismatch in returns data");
            return;
        }

        // Convert BigDecimal to double for regression
        double[] y = dailyReturns.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .toArray();

        double[][] x = new double[marketReturns.size()][1];
        for (int i = 0; i < marketReturns.size(); i++) {
            x[i][0] = marketReturns.get(i).doubleValue();
        }

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.newSampleData(y, x);

        double[] coefficients = regression.estimateRegressionParameters();
        double rSquared = regression.calculateRSquared();

        System.out.println("\nRegression Analysis:");
        System.out.println("Alpha: " + new BigDecimal(coefficients[0]));
        System.out.println("Beta: " + new BigDecimal(coefficients[1]));
        System.out.println("R-Squared: " + new BigDecimal(rSquared));
    }

    public void calculatePerformanceMetrics() {
        // Average Return
        BigDecimal averageReturn = dailyReturns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(dailyReturns.size()), 4, RoundingMode.HALF_UP);

        // Adjusted Return (subtracting risk-free rate)
        BigDecimal adjustedReturn = averageReturn.subtract(
                RISK_FREE_RATE.divide(new BigDecimal("252"), 4, RoundingMode.HALF_UP)
        );


        // Volatility Calculation
        BigDecimal variance = dailyReturns.stream()
                .map(r -> r.subtract(averageReturn).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(dailyReturns.size()), 4, RoundingMode.HALF_UP);

        BigDecimal volatility = new BigDecimal(Math.sqrt(variance.doubleValue()));


        // Sharpe Ratio
        BigDecimal sharpeRatio = adjustedReturn.divide(volatility, 4, RoundingMode.HALF_UP);

        // Drawdown Calculation
        BigDecimal maxDrawdown = calculateMaxDrawdown();

        // Output Metrics
        System.out.println("Portfolio Performance:");
        System.out.println("Average Return: " + averageReturn.multiply(new BigDecimal("100")) + "%");
        System.out.println("Adjusted Return: " + adjustedReturn.multiply(new BigDecimal("100")) + "%");
        System.out.println("Volatility: " + volatility.multiply(new BigDecimal("100")) + "%");
        System.out.println("Sharpe Ratio: " + sharpeRatio);
        System.out.println("Maximum Drawdown: " + maxDrawdown.multiply(new BigDecimal("100")) + "%");
    }

    private BigDecimal calculateMaxDrawdown() {
        List<BigDecimal> cumulativeCapital = getCumulativeCapital();
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal peak = STARTING_CAPITAL;

        for (BigDecimal capital : cumulativeCapital) {
            if (capital.compareTo(peak) > 0) {
                peak = capital;
            }
            BigDecimal drawdown = peak.subtract(capital).divide(peak, 4, RoundingMode.HALF_UP);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }

        return maxDrawdown;
    }

    private List<BigDecimal> getCumulativeCapital() {
        List<BigDecimal> cumulativeCapital = new ArrayList<>();
        BigDecimal runningTotal = STARTING_CAPITAL;

        for (BigDecimal dailyReturn : dailyReturns) {
            runningTotal = runningTotal.multiply(BigDecimal.ONE.add(dailyReturn));
            cumulativeCapital.add(runningTotal);
        }

        return cumulativeCapital;
    }

    public void calculateSignalAccuracy() {
        int totalTrades = trades.size();
        long profitableTrades = trades.stream()
                .filter(trade -> trade.getReturnValue().compareTo(BigDecimal.ZERO) > 0)
                .count();

        long lossMakingTrades = totalTrades - profitableTrades;

        BigDecimal accuracyRate = new BigDecimal(profitableTrades)
                .divide(new BigDecimal(totalTrades), 2, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        System.out.println("\nTrading Signal Accuracy:");
        System.out.println("Total Trades: " + totalTrades);
        System.out.println("Profitable Trades: " + profitableTrades);
        System.out.println("Loss-Making Trades: " + lossMakingTrades);
        System.out.println("Signal Accuracy: " + accuracyRate + "%");
    }

    public void analyzePeriods() {
        List<BigDecimal> cumulativeReturns = new ArrayList<>();
        BigDecimal runningTotal = BigDecimal.ZERO;

        for (BigDecimal dailyReturn : dailyReturns) {
            runningTotal = runningTotal.add(dailyReturn);
            cumulativeReturns.add(runningTotal);
        }

        if (!trades.isEmpty() && !cumulativeReturns.isEmpty()) {
            BigDecimal peak = cumulativeReturns.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal trough = cumulativeReturns.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

            Trade peakTrade = trades.get(cumulativeReturns.indexOf(peak));
            Trade troughTrade = trades.get(cumulativeReturns.indexOf(trough));

            System.out.println("\nPeriod Analysis:");
            System.out.println("Highest Cumulative Return: " + peak + " (on " + peakTrade.getDate() + ")");
            System.out.println("Lowest Cumulative Return: " + trough + " (on " + troughTrade.getDate() + ")");
        }
    }

    public void printMetrics() {
        System.out.println("\nTrade History:");
        trades.forEach(trade ->
                System.out.printf("Symbol: %s, Date: %s, PnL: %s%n",
                        trade.getSymbol(),
                        trade.getDate(),
                        trade.getReturnValue().setScale(2, RoundingMode.HALF_UP))
        );

        calculatePerformanceMetrics();
    }
}