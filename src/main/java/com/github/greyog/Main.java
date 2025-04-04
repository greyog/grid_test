package com.github.greyog;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Main {

    private static final int SCALE = 6;

    public static void main(String[] args) {
        var fee = BigDecimal.valueOf(0.001);
        var qty = BigDecimal.valueOf(0.003);
        var h = BigDecimal.valueOf(10);
        var price = BigDecimal.valueOf(1800);
        var baseBalance = BigDecimal.valueOf(0.065).setScale(SCALE, RoundingMode.HALF_UP);
        var quoteBalance = BigDecimal.valueOf(117).setScale(SCALE, RoundingMode.HALF_UP);
        TotalBalance totalBal = getTotalBalance(baseBalance, price, quoteBalance);
        System.out.println("totalBalanceInBase = " + totalBal.totalBalanceInBase() + ", totalBalanceInQuote = " + totalBal.totalBalanceInQuote());

        var tradeCount = 1000;
        var sellCount = 0;
        var buyCount = 0;
        for (int i = 0; i < tradeCount; i++) {
            var side = Math.random() >= 0.5 ? Side.BUY : Side.SEll;
            price = switch (side) {
                case BUY -> {
                    buyCount++;
                    yield price.subtract(h);
                }
                case SEll -> {
                    sellCount++;
                    yield price.add(h);
                }
            };
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            var nextState = trade(side, baseBalance, quoteBalance, qty, price, fee);
            baseBalance = nextState.baseBalance;
            quoteBalance = nextState.quoteBalance;
            if (baseBalance.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("Zero baseBalance");
                printStats(buyCount, sellCount, price, baseBalance, quoteBalance);
                return;
            } else if (quoteBalance.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("Zero quoteBalance");
                printStats(buyCount, sellCount, price, baseBalance, quoteBalance);
                return;
            }
        }
        printStats(buyCount, sellCount, price, baseBalance, quoteBalance);

    }

    private static void printStats(int buyCount, int sellCount, BigDecimal price, BigDecimal baseBalance, BigDecimal quoteBalance) {
        TotalBalance totalBal;
        System.out.println("buyCount = " + buyCount + ", sellCount = " + sellCount);
        System.out.println("lastPrice = " + price + ", baseBalance = " + baseBalance + ", quoteBalance = " + quoteBalance);
        totalBal = getTotalBalance(baseBalance, price, quoteBalance);
        System.out.println("totalBalanceInBase = " + totalBal.totalBalanceInBase() + ", totalBalanceInQuote = " + totalBal.totalBalanceInQuote());
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
        SEll, BUY
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
            case SEll -> {
                var nextBaseBalance = baseBalance.subtract(qty);
                var nextQuoteBalance = quoteBalance.add(qty.multiply(price).multiply(feeFactor));
                result = new BaseQuoteBalances(nextBaseBalance, nextQuoteBalance);
            }
        }
        if (result.baseBalance.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("Zero baseBalance, rebalancing");
            var halfQuote = quoteBalance.divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
            var rebalanceQty = halfQuote.divide(price, SCALE, RoundingMode.FLOOR);
            result = trade(Side.BUY, baseBalance, quoteBalance, rebalanceQty, price, fee);
//            result = new BaseQuoteBalances(rebalanceState.baseBalance, halfQuote);
        } else if (result.quoteBalance.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("Zero quoteBalance, rebalancing");
            var halfBase = baseBalance.divide(BigDecimal.valueOf(2), SCALE, RoundingMode.FLOOR);
            result = trade(Side.SEll, baseBalance, quoteBalance, halfBase, price, fee);
//            result = new BaseQuoteBalances(rebalanceState.baseBalance, halfQuote);
        }
        return result;
    }
}