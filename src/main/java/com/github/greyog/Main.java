package com.github.greyog;

import com.google.common.math.Quantiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;

public class Main {

    private static final int SCALE = 6;

    public static void main(String[] args) {
        var fee = BigDecimal.valueOf(0.001);
        var qty = BigDecimal.valueOf(0.00304);
        var h = BigDecimal.valueOf(5);
        var defPrice = BigDecimal.valueOf(1800);
        var defBaseBalance = BigDecimal.valueOf(0.065).setScale(SCALE, RoundingMode.HALF_UP);
        var defQuoteBalance = BigDecimal.valueOf(117).setScale(SCALE, RoundingMode.HALF_UP);
        printStats("Initial", defPrice, defBaseBalance, defQuoteBalance);
        var epochCount = 1000;
        var tradeCount = 1000;
        var results = new ArrayList<TradeResult>();
        for (int j = 0; j < epochCount; j++) {
            var price = defPrice;
            var baseBalance = defBaseBalance;
            var quoteBalance = defQuoteBalance;
            var sellCount = 0;
            var buyCount = 0;
            for (int i = 0; i < tradeCount; i++) {
                var side = Math.random() >= 0.5 ? Side.BUY : Side.SELL;
                price = switch (side) {
                    case BUY -> {
                        buyCount++;
                        yield price.add(h);
                    }
                    case SELL -> {
                        sellCount++;
                        yield price.subtract(h);
                    }
                };
                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                var nextState = trade(side, baseBalance, quoteBalance, qty, price, fee);
                baseBalance = nextState.baseBalance;
                quoteBalance = nextState.quoteBalance;
//                if (baseBalance.compareTo(BigDecimal.ZERO) <= 0) {
//                    System.out.println("Zero baseBalance");
//                    printStats(buyCount, sellCount, price, baseBalance, quoteBalance);
//                    return;
//                } else if (quoteBalance.compareTo(BigDecimal.ZERO) <= 0) {
//                    System.out.println("Zero quoteBalance");
//                    printStats(buyCount, sellCount, price, baseBalance, quoteBalance);
//                    return;
//                }
                results.add(new TradeResult(price, baseBalance, quoteBalance));
            }
        }
//        results.stream()
//                .max(Comparator.comparing(TradeResult::baseBalance))
//                .ifPresent(tradeResult -> printStats("Best baseBalance", tradeResult.lastPrice, tradeResult.baseBalance, tradeResult.quoteBalance));
//        results.stream()
//                .max(Comparator.comparing(TradeResult::quoteBalance))
//                .ifPresent(tradeResult -> printStats("Best quoteBalance", tradeResult.lastPrice, tradeResult.baseBalance, tradeResult.quoteBalance));
//        results.stream()
//                .min(Comparator.comparing(TradeResult::baseBalance))
//                .ifPresent(tradeResult -> printStats("Worst baseBalance", tradeResult.lastPrice, tradeResult.baseBalance, tradeResult.quoteBalance));
//        results.stream()
//                .min(Comparator.comparing(TradeResult::quoteBalance))
//                .ifPresent(tradeResult -> printStats("Worst quoteBalance", tradeResult.lastPrice, tradeResult.baseBalance, tradeResult.quoteBalance));
        results.stream()
                .max(Comparator.comparing(tr -> {
                    TotalBalance totalBalance = getTotalBalance(tr.baseBalance, tr.lastPrice, tr.quoteBalance);
                    return totalBalance.totalBalanceInBase.multiply(totalBalance.totalBalanceInQuote);
                }))
                .ifPresent(tradeResult -> printStats("Best square", tradeResult.lastPrice, tradeResult.baseBalance, tradeResult.quoteBalance));
        results.stream()
                .min(Comparator.comparing(tr -> {
                    TotalBalance totalBalance = getTotalBalance(tr.baseBalance, tr.lastPrice, tr.quoteBalance);
                    return totalBalance.totalBalanceInBase.multiply(totalBalance.totalBalanceInQuote);
                }))
                .ifPresent(tradeResult -> printStats("Worst square", tradeResult.lastPrice, tradeResult.baseBalance, tradeResult.quoteBalance));

        System.out.println("\"--------------------------------------------\" = " + "--------------------------------------------");
        var squares = results.stream()
                .map(tr -> {
                    TotalBalance totalBalance = getTotalBalance(tr.baseBalance, tr.lastPrice, tr.quoteBalance);
                    return totalBalance.totalBalanceInBase.multiply(totalBalance.totalBalanceInQuote);
                })
                .map(BigDecimal::doubleValue)
                .toList();
        double median = Quantiles.median().compute(squares);
        System.out.println("median square = " + median);
    }

    record TradeResult(BigDecimal lastPrice, BigDecimal baseBalance, BigDecimal quoteBalance) {}

    private static void printStats(String prefix, BigDecimal price, BigDecimal baseBalance, BigDecimal quoteBalance) {
        TotalBalance totalBal;
        System.out.println("\"--------------------------------------------\" = " + "--------------------------------------------");
        System.out.println("prefix = " + prefix);
        System.out.println("lastPrice = " + price + ", baseBalance = " + baseBalance + ", quoteBalance = " + quoteBalance);
        totalBal = getTotalBalance(baseBalance, price, quoteBalance);
        System.out.println("totalBalanceInBase = " + totalBal.totalBalanceInBase() + ", totalBalanceInQuote = " + totalBal.totalBalanceInQuote());
        System.out.println("square = " + totalBal.totalBalanceInBase().multiply(totalBal.totalBalanceInQuote()));
    }

    private static TotalBalance getTotalBalance(BigDecimal baseBalance, BigDecimal price, BigDecimal quoteBalance) {
        var totalBalanceInQuote = baseBalance.multiply(price).add(quoteBalance);
        var totalBalanceInBase = quoteBalance.divide(price, SCALE, RoundingMode.HALF_UP).add(baseBalance);
        TotalBalance totalBal = new TotalBalance(totalBalanceInQuote, totalBalanceInBase);
        return totalBal;
    }

    private record TotalBalance(BigDecimal totalBalanceInQuote, BigDecimal totalBalanceInBase) {
    }

    enum Side {
        SELL, BUY
    }

    record BaseQuoteBalances(BigDecimal baseBalance, BigDecimal quoteBalance) {}

    private static BaseQuoteBalances trade(Side side, BigDecimal baseBalance, BigDecimal quoteBalance,
                                           BigDecimal qty, BigDecimal price, BigDecimal fee) {
        var feeFactor = BigDecimal.ONE.subtract(fee);
        BaseQuoteBalances result = null;
        switch (side) {
            case BUY -> {
                var nextBaseBalance = baseBalance.add(qty.multiply(feeFactor));
                var nextQuoteBalance = quoteBalance.subtract(qty.multiply(price));
                result = new BaseQuoteBalances(nextBaseBalance, nextQuoteBalance);
            }
            case SELL -> {
                var nextBaseBalance = baseBalance.subtract(qty);
                var nextQuoteBalance = quoteBalance.add(qty.multiply(price).multiply(feeFactor));
                result = new BaseQuoteBalances(nextBaseBalance, nextQuoteBalance);
            }
        }
        if (result.baseBalance.compareTo(BigDecimal.ZERO) <= 0) {
//            System.out.println("Zero baseBalance, rebalancing");
            var halfQuote = quoteBalance.divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
            var rebalanceQty = halfQuote.divide(price, SCALE, RoundingMode.FLOOR);
            result = trade(Side.BUY, baseBalance, quoteBalance, rebalanceQty, price, fee);
//            result = new BaseQuoteBalances(rebalanceState.baseBalance, halfQuote);
        } else if (result.quoteBalance.compareTo(BigDecimal.ZERO) <= 0) {
//            System.out.println("Zero quoteBalance, rebalancing");
            var halfBase = baseBalance.divide(BigDecimal.valueOf(2), SCALE, RoundingMode.FLOOR);
            result = trade(Side.SELL, baseBalance, quoteBalance, halfBase, price, fee);
//            result = new BaseQuoteBalances(rebalanceState.baseBalance, halfQuote);
        }
        return result;
    }
}