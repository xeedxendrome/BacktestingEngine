package com.backtesting;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.google.gson.*;
import io.github.cdimascio.dotenv.Dotenv;

public class TiingoDataFetcher {

    // Tiingo API credentials and base URL
    private static final Dotenv dotenv = Dotenv.load(); // Load .env file
    private static final String API_KEY = dotenv.get("API_KEY"); // Fetch the variable
    private static final String BASE_URL = "https://api.tiingo.com/tiingo/daily";
    private static final String CACHE_DIRECTORY = "stock_cache"; // Directory for storing cached files
    private static final String START_DATE = "2018-01-01";
    private static final String END_DATE = "2023-12-31";

    // List to store S&P 500 company symbols
    private static List<String> STOCK_SYMBOLS;

    // In-memory cache to store stock data during runtime
    private static Map<String, List<StockData>> stockDataCache = new HashMap<>();

    // Static block to load the list of S&P 500 companies dynamically
    static {
        STOCK_SYMBOLS = fetchSP500Companies(); // Fetch S&P 500 symbols dynamically
    }

    /**
     * Fetches the list of S&P 500 companies dynamically from Wikipedia.
     *
     * @return A list of S&P 500 ticker symbols.
     */
    public static List<String> fetchSP500Companies() {
        List<String> sp500Symbols = new ArrayList<>();

        try {
            // Fetch and parse the Wikipedia page for S&P 500 companies
            Document doc = Jsoup.connect("https://en.wikipedia.org/wiki/List_of_S%26P_500_companies").get();
            Elements rows = doc.select("table.wikitable tbody tr");

            for (Element row : rows) {
                Elements cols = row.select("td");
                if (cols.size() > 0) {
                    String symbol = cols.get(0).text();
                    if (!symbol.matches(".*\\d.*")) { // Regex to check if the symbol contains digits
                        sp500Symbols.add(symbol);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error fetching S&P 500 companies: " + e.getMessage());
        }

        return sp500Symbols;
    }

    /**
     * Fetches stock data for all S&P 500 companies.
     *
     * @return A map where the key is the stock symbol and the value is a list of StockData objects.
     */
    public Map<String, List<StockData>> fetchAllStockData() {
        Map<String, List<StockData>> stockDataMap = new HashMap<>();

        // Fetch data for each S&P 500 stock symbol
        for (String symbol : STOCK_SYMBOLS) {
            List<StockData> stockData = getStockData(symbol);
            if (stockData != null) {
                stockDataMap.put(symbol, stockData);
            }
        }

        return stockDataMap;
    }

    /**
     * Fetches stock data for a specific symbol.
     *
     * @param symbol The stock symbol for which to fetch data.
     * @return A list of StockData objects.
     */
    public List<StockData> getStockData(String symbol) {
        // Check if the data is available in the in-memory cache
        if (stockDataCache.containsKey(symbol)) {
            return stockDataCache.get(symbol);
        }

        // Try fetching from the disk cache
        List<StockData> cachedData = loadFromCache(symbol);
        if (cachedData != null) {
            stockDataCache.put(symbol, cachedData);
            return cachedData;
        }

        // Fetch data from Tiingo API
        List<StockData> fetchedData = fetchStockDataFromApi(symbol);
        if (fetchedData != null) {
            // Store data in the in-memory cache and save to disk
            stockDataCache.put(symbol, fetchedData);
            saveToCache(symbol, fetchedData);
        }

        return fetchedData;
    }

    /**
     * Fetches stock data from the Tiingo API for a given symbol.
     *
     * @param symbol The stock symbol to fetch data for.
     * @return A list of StockData objects containing the stock data.
     */
    public List<StockData> fetchStockDataFromApi(String symbol) {
        String url = String.format("%s/%s/prices?startDate=%s&endDate=%s&token=%s",
                BASE_URL, symbol, START_DATE, END_DATE, API_KEY);

        try {
            // Create HTTP client and send request
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Parse the JSON response and return a list of StockData objects
                return parseStockData(response.body());
            }
        } catch (Exception e) {
            System.err.println("Exception fetching data for " + symbol + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Parses the stock data JSON response into a list of StockData objects.
     *
     * @param jsonResponse The JSON response from the Tiingo API.
     * @return A list of StockData objects.
     */
    public List<StockData> parseStockData(String jsonResponse) {
        JsonArray jsonArray = JsonParser.parseString(jsonResponse).getAsJsonArray();
        List<StockData> stockDataList = new ArrayList<>();

        for (JsonElement element : jsonArray) {
            JsonObject obj = element.getAsJsonObject();
            String date = obj.get("date").getAsString();
            BigDecimal adjustedClose = BigDecimal.valueOf(obj.get("adjClose").getAsDouble());
            int volume = obj.get("volume").getAsInt();

            // Create StockData object and add it to the list
            stockDataList.add(new StockData(date, adjustedClose, volume));
        }

        return stockDataList;
    }

    /**
     * Calculates the daily returns for the S&P 500 index.
     *
     * @return A map where the key is the date and the value is the daily return percentage.
     */
    public Map<String, BigDecimal> calculateMarketReturns() {
        // S&P 500 ETF symbol (common for Tiingo API)
        String sp500Symbol = "SPY"; // URL-encoded "^GSPC" (commonly used for APIs)

        // Fetch data for the S&P 500
        List<StockData> sp500Data = getStockData(sp500Symbol);
        Map<String, BigDecimal> marketReturns = new LinkedHashMap<>(); // Maintain insertion order

        // Ensure there is enough data to calculate returns
        if (sp500Data != null && sp500Data.size() > 1) {
            for (int i = 1; i < sp500Data.size(); i++) {
                StockData previousData = sp500Data.get(i - 1);
                StockData currentData = sp500Data.get(i);

                BigDecimal previousClose = previousData.getAdjustedClose();
                BigDecimal currentClose = currentData.getAdjustedClose();

                // Calculate the daily return: ((currentClose - previousClose) / previousClose) * 100
                BigDecimal dailyReturn = currentClose.subtract(previousClose)
                        .divide(previousClose, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

                // Add the date and return to the map
                marketReturns.put(currentData.getDate(), dailyReturn);
            }
        } else {
            System.err.println("Insufficient data to calculate market returns.");
        }

        return marketReturns;
    }

    /**
     * Saves stock data to the disk cache.
     *
     * @param symbol    The stock symbol for which the data is being saved.
     * @param stockData The list of StockData objects to save.
     */
    private void saveToCache(String symbol, List<StockData> stockData) {
        try {
            ensureCacheDirectoryExists();
            File file = new File(CACHE_DIRECTORY, symbol + ".json");
            Gson gson = new Gson();
            FileWriter writer = new FileWriter(file);
            gson.toJson(stockData, writer);
            writer.close();
        } catch (IOException e) {
            System.err.println("Error saving cache for " + symbol + ": " + e.getMessage());
        }
    }

    /**
     * Loads stock data from the disk cache.
     *
     * @param symbol The stock symbol for which to load the data.
     * @return A list of StockData objects, or null if not found.
     */
    private List<StockData> loadFromCache(String symbol) {
        File file = new File(CACHE_DIRECTORY, symbol + ".json");
        if (file.exists()) {
            try {
                Gson gson = new Gson();
                FileReader reader = new FileReader(file);
                List<StockData> stockData = Arrays.asList(gson.fromJson(reader, StockData[].class));
                reader.close();
                return stockData;
            } catch (IOException e) {
                System.err.println("Error loading cache for " + symbol + ": " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Ensures that the cache directory exists. If not, creates it.
     */
    private void ensureCacheDirectoryExists() {
        File directory = new File(CACHE_DIRECTORY);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }
}
