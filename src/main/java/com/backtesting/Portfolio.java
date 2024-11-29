package com.backtesting;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import java.util.*;

public class Portfolio {

    private final List<Trade> trades = new ArrayList<>();
    private final List<Double> dailyReturns = new ArrayList<>();
    private final List<Double> marketReturns = new ArrayList<>();
    private double startingCapital = 100000; // Starting capital for the portfolio
    private double currentCapital = startingCapital;
    private double riskFreeRate = 0.02; // Assume 2% annual risk-free rate
    private double volatility = 0.01; // Initial guess for volatility

    /**
     * Executes trade signals for a stock and calculates returns with stop-loss.
     */
    public void executeSignals(String symbol, List<TradeSignal> signals, List<StockData> stockData) {
        if (signals.isEmpty()) return;

        double position = 0.0; // Current position (long or short)
        double entryPrice = 0.0; // Price at which the current position was opened
        double stopLossThreshold = 0.05; // 5% stop-loss per trade

        for (int i = 0; i < stockData.size(); i++) {
            StockData data = stockData.get(i);

            // Check if a signal exists for this date
            Optional<TradeSignal> signal = signals.stream()
                    .filter(s -> s.getDate().equals(data.getDate()))
                    .findFirst();

            if (signal.isPresent()) {
                String action = signal.get().getSignal();

                if (action.equals(TradeSignal.BUY) && position <= 0) {
                    // Close any short position
                    if (position < 0) {
                        double pnl = (entryPrice - data.getAdjustedClose()) * Math.abs(position);
                        currentCapital += pnl;
                        dailyReturns.add(pnl / startingCapital);
                        trades.add(new Trade(symbol, data.getDate(), pnl));
                    }

                    // Open a new long position
                    position = calculatePositionSize(data.getAdjustedClose());
                    entryPrice = data.getAdjustedClose();
                } else if (action.equals(TradeSignal.SELL) && position >= 0) {
                    // Close any long position
                    if (position > 0) {
                        double pnl = (data.getAdjustedClose() - entryPrice) * position;
                        currentCapital += pnl;
                        dailyReturns.add(pnl / startingCapital);
                        trades.add(new Trade(symbol, data.getDate(), pnl));
                    }

                    // Open a new short position
                    position = -calculatePositionSize(data.getAdjustedClose());
                    entryPrice = data.getAdjustedClose();
                }
            }

            // Apply stop-loss
            if (position > 0 && (entryPrice - data.getAdjustedClose()) / entryPrice >= stopLossThreshold) {
                double pnl = (data.getAdjustedClose() - entryPrice) * position;
                currentCapital += pnl;
                dailyReturns.add(pnl / startingCapital);
                trades.add(new Trade(symbol, data.getDate(), pnl));
                position = 0;
            } else if (position < 0 && (data.getAdjustedClose() - entryPrice) / entryPrice >= stopLossThreshold) {
                double pnl = (entryPrice - data.getAdjustedClose()) * Math.abs(position);
                currentCapital += pnl;
                dailyReturns.add(pnl / startingCapital);
                trades.add(new Trade(symbol, data.getDate(), pnl));
                position = 0;
            }

            // Track daily portfolio value
            if (position != 0) {
                double dailyPnl = (data.getAdjustedClose() - entryPrice) * position;
                dailyReturns.add(dailyPnl / startingCapital);
            }
        }

        // Close any open position at the end of the period
        if (position != 0) {
            double pnl = position > 0
                    ? (stockData.get(stockData.size() - 1).getAdjustedClose() - entryPrice) * position
                    : (entryPrice - stockData.get(stockData.size() - 1).getAdjustedClose()) * Math.abs(position);
            currentCapital += pnl;
            trades.add(new Trade(symbol, stockData.get(stockData.size() - 1).getDate(), pnl));
        }
    }

    /**
     * Calculates position size based on portfolio volatility.
     */
    private double calculatePositionSize(double price) {
        double volatilityFactor = Math.min(1, 1 / (volatility * 100));
        return (currentCapital * volatilityFactor) / price;
    }

    // Other methods (addMarketReturns, performRegressionAnalysis, calculateSignalAccuracy, etc.)

public void addMarketReturns(List<Double> marketReturns) {
        this.marketReturns.addAll(marketReturns);
    }

    /**
     * Regresses portfolio returns on common risk factors (e.g., market returns).
     */
    public void performRegressionAnalysis() {
        if (dailyReturns.size() != marketReturns.size()) {
            System.err.println("Mismatch between portfolio returns and market returns.");
            return;
        }

        // Prepare data for regression
        double[] y = dailyReturns.stream().mapToDouble(Double::doubleValue).toArray();
        double[][] x = new double[marketReturns.size()][1];

        for (int i = 0; i < marketReturns.size(); i++) {
            x[i][0] = marketReturns.get(i);
        }

        // Perform regression
        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.newSampleData(y, x);

        double[] coefficients = regression.estimateRegressionParameters();
        double rSquared = regression.calculateRSquared();

        // Print results
        System.out.println("\nRegression Analysis:");
        System.out.println("Alpha (Intercept): " + coefficients[0]);
        System.out.println("Beta (Market Sensitivity): " + coefficients[1]);
        System.out.println("R-Squared: " + rSquared);
    }

    /**
     * Calculates the accuracy of trading signals.
     */
    public void calculateSignalAccuracy() {
        int totalTrades = trades.size();
        int profitableTrades = (int) trades.stream().filter(trade -> trade.getReturnValue() > 0).count();
        int lossMakingTrades = totalTrades - profitableTrades;

        double accuracyRate = (double) profitableTrades / totalTrades * 100;

        // Print results
        System.out.println("\nTrading Signal Accuracy:");
        System.out.println("Total Trades: " + totalTrades);
        System.out.println("Profitable Trades: " + profitableTrades);
        System.out.println("Loss-Making Trades: " + lossMakingTrades);
        System.out.println("Signal Accuracy: " + accuracyRate + "%");
    }

    /**
     * Analyzes periods where the strategy worked and did not work.
     */
    public void analyzePeriods() {
        List<Double> cumulativeReturns = new ArrayList<>();
        double runningTotal = 0.0;

        for (double dailyReturn : dailyReturns) {
            runningTotal += dailyReturn;
            cumulativeReturns.add(runningTotal);
        }

        double peak = Double.MIN_VALUE;
        double trough = Double.MAX_VALUE;
        String peakDate = null;
        String troughDate = null;

        // Identify the periods of success and failure
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            double cumulativeReturn = cumulativeReturns.get(i);

            if (cumulativeReturn > peak) {
                peak = cumulativeReturn;
                peakDate = trade.getDate();
            }
            if (cumulativeReturn < trough) {
                trough = cumulativeReturn;
                troughDate = trade.getDate();
            }
        }

        // Print analysis
        System.out.println("\nPeriod Analysis:");
        System.out.println("Highest Cumulative Return: " + peak + " (on " + peakDate + ")");
        System.out.println("Lowest Cumulative Return: " + trough + " (on " + troughDate + ")");
    }

    /**
     * Prints a summary of trades and portfolio performance.
     */
    public void printMetrics() {
        System.out.println("\nTrade History:");
        for (Trade trade : trades) {
            System.out.printf("Symbol: %s, Date: %s, PnL: %.2f%n",
                    trade.getSymbol(), trade.getDate(), trade.getReturnValue());
        }

        calculatePerformanceMetrics();
    }

    /**
     * Calculates performance metrics for the portfolio.
     */
    public void calculatePerformanceMetrics() {
        double averageReturn = dailyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double adjustedReturn = averageReturn - riskFreeRate / 252;  // Risk-free rate adjusted for daily returns
        double volatility = Math.sqrt(
                dailyReturns.stream().mapToDouble(r -> Math.pow(r - averageReturn, 2)).sum() / dailyReturns.size()
        );
        double sharpeRatio = adjustedReturn / volatility;

        // Calculate drawdown
        double peak = startingCapital;
        double maxDrawdown = 0.0;

        for (double capital : getCumulativeCapital()) {
            if (capital > peak) peak = capital;
            double drawdown = (peak - capital) / peak;
            if (drawdown > maxDrawdown) maxDrawdown = drawdown;
        }

        // Print results
        System.out.println("Portfolio Performance:");
        System.out.println("Average Return: " + (averageReturn * 100) + "%");
        System.out.println("Adjusted Return: " + (adjustedReturn * 100) + "%");
        System.out.println("Volatility: " + (volatility * 100) + "%");
        System.out.println("Sharpe Ratio: " + sharpeRatio);
        System.out.println("Maximum Drawdown: " + (maxDrawdown * 100) + "%");
    }

    /**
     * Calculates the cumulative portfolio value over time.
     */
    private List<Double> getCumulativeCapital() {
        List<Double> cumulativeCapital = new ArrayList<>();
        double runningTotal = startingCapital;

        for (double dailyReturn : dailyReturns) {
            runningTotal += dailyReturn * startingCapital;
            cumulativeCapital.add(runningTotal);
        }

        return cumulativeCapital;
    }





}
