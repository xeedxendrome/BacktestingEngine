package com.backtesting;

import java.util.List;
import java.util.Map;

public class MovingAverageStrategy {

    public static void main(String[] args) {
        // Initialize data fetcher
        TiingoDataFetcher fetcher = new TiingoDataFetcher();

        // Fetch stock data for all symbols
        Map<String, List<StockData>> stockDataMap = fetcher.fetchAllStockData();

        if (stockDataMap == null || stockDataMap.isEmpty()) {
            System.err.println("No stock data available. Exiting.");
            return;
        }

        // Initialize trading strategy
        TradingStrategy strategy = new TradingStrategy();
        List<Double> marketReturns = fetcher.calculateMarketReturns();
        // Run moving average strategy and generate a portfolio
        Portfolio portfolio = strategy.runImprovedStrategy(stockDataMap, marketReturns);

        // Print initial portfolio performance
        System.out.println("\n=== Initial Portfolio Performance ===");
        portfolio.calculatePerformanceMetrics();
        //portfolio.printMetrics();

        // Add market returns for regression analysis

        if (marketReturns != null && !marketReturns.isEmpty()) {
            portfolio.addMarketReturns(marketReturns);

            // Perform regression analysis
            portfolio.performRegressionAnalysis();
        } else {
            System.err.println("Market returns data is unavailable for regression analysis.");
        }

        // Calculate signal accuracy
        portfolio.calculateSignalAccuracy();

        // Analyze periods where the strategy worked and failed
        portfolio.analyzePeriods();
    }



}
