package pearlmart;

import java.math.BigDecimal;
import java.math.RoundingMode;

public interface PricingStrategy {
    record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal payable) {}
    Totals apply(BigDecimal subtotal);

    final class Standard implements PricingStrategy {
        @Override public Totals apply(BigDecimal subtotal) {
            BigDecimal zero = new BigDecimal("0.00");
            return new Totals(subtotal, zero, zero, subtotal);
        }
        @Override public String toString() { return "Standard"; }
    }
    record Percentage(BigDecimal discountRate, BigDecimal taxRate) implements PricingStrategy {
        public Percentage {
            validateRate(discountRate);
            validateRate(taxRate);
        }
        private static void validateRate(BigDecimal rate) {
            if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("Pricing rates must be between 0 and 1.");
        }
        @Override public Totals apply(BigDecimal subtotal) {
            BigDecimal discount = subtotal.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = subtotal.subtract(discount);
            BigDecimal tax = net.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
            return new Totals(subtotal, discount, tax, net.add(tax));
        }
        @Override public String toString() {
            return "Percentage (" + discountRate.movePointRight(2).stripTrailingZeros().toPlainString() +
                "% discount / " + taxRate.movePointRight(2).stripTrailingZeros().toPlainString() + "% tax)";
        }
    }
}
