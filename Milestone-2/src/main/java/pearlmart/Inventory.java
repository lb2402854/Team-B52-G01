package pearlmart;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

/** One shared store. Records are immutable; a command publishes its staged changes together. */
public final class Inventory {
    public record Item(String id, String barcode, String name, BigDecimal price, String manufacturer,
                       String origin, LocalDate expiry, String comments, int quantity) {
        Item withQuantity(int value) {
            return new Item(id, barcode, name, price, manufacturer, origin, expiry, comments, value);
        }
    }
    public record InventoryMovement(String itemId, int change, String type, String reference, Instant time) {}
    public record SaleLine(String itemId, String name, BigDecimal price, int quantity) {
        public BigDecimal amount() { return price.multiply(BigDecimal.valueOf(quantity)); }
    }
    public record SalesReceipt(String saleId, List<SaleLine> lines, PricingStrategy.Totals totals) {
        public SalesReceipt { lines = List.copyOf(lines); }
    }
    public record SalesTransaction(String id, String status, List<SaleLine> lines, SalesReceipt receipt) {
        public SalesTransaction { lines = List.copyOf(lines); }
    }
    static final class State {
        final Map<String,Item> items = new LinkedHashMap<>();
        final List<InventoryMovement> movements = new ArrayList<>();
        final Map<String,SalesTransaction> sales = new LinkedHashMap<>();
        State() {}
        State(State before) {
            items.putAll(before.items);
            movements.addAll(before.movements);
            sales.putAll(before.sales);
        }
        Item find(String key) {
            String id = key == null ? "" : key.strip();
            return items.values().stream()
                .filter(i -> i.id().equals(id) || i.barcode().equals(id)).findFirst().orElse(null);
        }
        Item require(String key) {
            Item item = find(key);
            if (item == null) throw new IllegalArgumentException("Unknown item: " + key);
            return item;
        }
    }
    private State current = new State();
    private final Runnable beforePublish;
    public Inventory() { this(() -> {}); }
    Inventory(Runnable beforePublish) { this.beforePublish = beforePublish; }

    synchronized void commit(Consumer<State> changes) {
        State staged = new State(current);
        changes.accept(staged);
        beforePublish.run();
        current = staged;
    }
    public synchronized Item find(String key) { return current.find(key); }
    public synchronized List<Item> items() { return List.copyOf(current.items.values()); }
    public synchronized List<InventoryMovement> movements() { return List.copyOf(current.movements); }
    public synchronized List<SalesTransaction> sales() { return List.copyOf(current.sales.values()); }
}
