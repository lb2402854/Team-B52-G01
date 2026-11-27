package pearlmart;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static pearlmart.Inventory.*;

public final class BillingController {
    private final Inventory inventory;
    private final Map<String, SaleLine> draft = new LinkedHashMap<>();
    private String id;
    private PricingStrategy policy;
    private boolean pending;
    public BillingController(Inventory inventory) { this.inventory = inventory; }
    public boolean pending() { return pending; }
    public List<SaleLine> lines() { return List.copyOf(draft.values()); }
    public void startSale(PricingStrategy strategy) {
        if (pending) throw new IllegalStateException("Confirm or cancel the current sale first.");
        if (strategy == null) throw new IllegalArgumentException("Choose a valid pricing policy.");
        draft.clear();
        policy = strategy;
        id = UUID.randomUUID().toString();
        pending = true;
    }
    private void requirePending() {
        if (!pending) throw new IllegalStateException("Start a new sale first.");
    }
    private static void available(Item item, int qty) {
        if (item.expiry() != null && item.expiry().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Item has expired: " + item.id());
        if (qty > item.quantity())
            throw new IllegalArgumentException("Insufficient stock for " + item.id() + ". Available: " + item.quantity());
    }
    public void addLine(String key, String rawQuantity) {
        requirePending();
        Item item = inventory.find(key);
        if (item == null) throw new IllegalArgumentException("Unknown item: " + key);
        int qty = InventoryController.positiveQuantity(rawQuantity);
        SaleLine previous = draft.get(item.id());
        int total = Math.addExact(qty, previous == null ? 0 : previous.quantity());
        available(item, total);
        BigDecimal price = previous == null ? item.price() : previous.price();
        draft.put(item.id(), new SaleLine(item.id(), item.name(), price, total));
    }
    public void removeLine(String key) {
        requirePending();
        Item item = inventory.find(key);
        if (item == null || draft.remove(item.id()) == null)
            throw new IllegalArgumentException("Select an item already in the sale.");
    }
    public PricingStrategy.Totals quote() {
        requirePending();
        if (draft.isEmpty()) throw new IllegalArgumentException("Add at least one valid line.");
        BigDecimal subtotal = draft.values().stream().map(SaleLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return policy.apply(subtotal);
    }
    public SalesReceipt confirm() {
        PricingStrategy.Totals totals = quote();
        SalesReceipt receipt = new SalesReceipt(id, lines(), totals);
        inventory.commit(state -> {
            for (SaleLine line : receipt.lines()) {
                Item item = state.require(line.itemId());
                available(item, line.quantity());
                state.items.put(item.id(), item.withQuantity(item.quantity() - line.quantity()));
                state.movements.add(new InventoryMovement(item.id(), -line.quantity(), "SALE", id, Instant.now()));
            }
            state.sales.put(id, new SalesTransaction(id, "COMPLETED", receipt.lines(), receipt));
        });
        pending = false;
        return receipt;
    }
    public void cancel() {
        requirePending();
        inventory.commit(state -> state.sales.put(id, new SalesTransaction(id, "CANCELLED", lines(), null)));
        pending = false;
    }
}
