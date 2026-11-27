package pearlmart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.UUID;
import static pearlmart.Inventory.*;

public final class InventoryController {
    private final Inventory inventory;
    public InventoryController(Inventory inventory) { this.inventory = inventory; }

    public Item lookup(String key) { return inventory.find(key); }

    static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return value.strip();
    }
    static int positiveQuantity(String raw) {
        try {
            int value = Integer.parseInt(raw.strip());
            if (value > 0) return value;
        } catch (NumberFormatException | NullPointerException ignored) { }
        throw new IllegalArgumentException("Quantity must be a positive whole number.");
    }
    private static BigDecimal price(String raw) {
        try {
            BigDecimal value = new BigDecimal(required(raw, "Price")).setScale(2, RoundingMode.UNNECESSARY);
            if (value.signum() > 0) return value;
        } catch (NumberFormatException | ArithmeticException ignored) { }
        throw new IllegalArgumentException("Enter a positive price with at most two decimal places.");
    }
    public void register(String id, String barcode, String name, String rawPrice, String manufacturer,
                         String origin, String expiry, String comments, String rawQuantity) {
        LocalDate date = null;
        if (expiry != null && !expiry.isBlank()) {
            try { date = LocalDate.parse(expiry.strip()); }
            catch (java.time.format.DateTimeParseException ex) {
                throw new IllegalArgumentException("Use YYYY-MM-DD for expiry.");
            }
        }
        Item item = new Item(required(id, "Item ID"), required(barcode, "Barcode"), required(name, "Name"),
            price(rawPrice), required(manufacturer, "Manufacturer"), required(origin, "Origin"), date,
            comments == null ? "" : comments.strip(), positiveQuantity(rawQuantity));
        inventory.commit(state -> {
            if (state.find(item.id()) != null || state.find(item.barcode()) != null)
                throw new IllegalArgumentException("Item ID or barcode is already in use.");
            state.items.put(item.id(), item);
            logAddition(state, item.id(), item.quantity());
        });
    }
    public void restock(String key, String rawQuantity) {
        int added = positiveQuantity(rawQuantity);
        inventory.commit(state -> {
            Item item = state.require(required(key, "Item ID or barcode"));
            int total = Math.addExact(item.quantity(), added);
            state.items.put(item.id(), item.withQuantity(total));
            logAddition(state, item.id(), added);
        });
    }
    private static void logAddition(Inventory.State state, String id, int qty) {
        state.movements.add(new InventoryMovement(id, qty, "ADD", UUID.randomUUID().toString(), Instant.now()));
    }
}
