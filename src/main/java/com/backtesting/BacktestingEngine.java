package com.backtesting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class BacktestingEngine {

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
        Map<String, BigDecimal> marketReturns = fetcher.calculateMarketReturns();
        // Run moving average strategy and generate a portfolio
        Portfolio portfolio = strategy.EWAStrategy(stockDataMap);

        // Print initial portfolio performance
        System.out.println("\n=== Initial Portfolio Performance ===");

        //portfolio.printMetrics();

        // Add market returns for regression analysis

        if (marketReturns != null && !marketReturns.isEmpty()) {


            portfolio.addMarketReturns(marketReturns);
            portfolio.alignReturnsForRegression();
            // Perform regression analysis
            portfolio.performRegressionAnalysis();
        } else {
            System.err.println("Market returns data is unavailable for regression analysis.");
        }
        portfolio.calculatePerformanceMetrics();
        // Calculate signal accuracy
        portfolio.calculateSignalAccuracy();

        // Analyze periods where the strategy worked and failed
        portfolio.analyzePeriods();
    }



}
