package pearlmart;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;

class WorkflowTest {
    final AtomicBoolean fail = new AtomicBoolean(false);
    final Inventory inventory = new Inventory(() -> {
        if (fail.get()) throw new IllegalStateException("Simulated save failure.");
    });
    final InventoryController goods = new InventoryController(inventory);
    final BillingController sales = new BillingController(inventory);
    final PricingStrategy strategy = new PricingStrategy.Percentage(new BigDecimal("0.10"),new BigDecimal("0.05"));

    void register(String id, String barcode,String name,String qty) {
        goods.register(id,barcode,name,"12.50","Pearl Foods","Qatar","","",qty);
    }
    @BeforeEach void setUp() { register("P100","8100","Coffee","10"); sales.startSale(strategy); }
    @Test void newAndRestockUseOneMovementEach() {
        assertEquals(1,inventory.movements().size());
        goods.restock("8100","2");
        assertEquals(12,goods.lookup("P100").quantity());
        assertEquals(2,inventory.movements().size());
        assertEquals(2,inventory.movements().getLast().change());
        sales.addLine("P100","12");
        assertEquals(12,sales.lines().getFirst().quantity());
    }
    @Test void duplicatesRejectedAcrossIdAndBarcode() {
        for (String[] pair : new String[][]{{"P100","999"},{"X","8100"},{"8100","999"},{"X","P100"}})
            assertThrows(IllegalArgumentException.class,() -> register(pair[0],pair[1],"Coffee","3"));
        assertEquals(1,inventory.items().size()); assertEquals(1,inventory.movements().size());
    }
    @Test void missingNameRejected() {
        assertThrows(IllegalArgumentException.class,() -> register("P101","8101"," ","3"));
        assertNull(goods.lookup("P101")); assertEquals(1,inventory.movements().size());
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.5",""})
    void badAdditionRejected(String qty) {
        assertThrows(IllegalArgumentException.class,() -> goods.restock("P100",qty));
        assertEquals(10,goods.lookup("P100").quantity()); assertEquals(1,inventory.movements().size());
    }
    @Test void saveFailureDiscardsItemAndMovement() {
        fail.set(true);
        assertThrows(IllegalStateException.class,() -> goods.restock("P100","2"));
        assertThrows(IllegalStateException.class,() -> register("P101","8101","Tea","2"));
        assertNull(goods.lookup("P101")); assertEquals(10,goods.lookup("P100").quantity());
        assertEquals(1,inventory.movements().size());
    }
    @Test void twoLineSaleCommitsExactlyOnce() {
        goods.register("P200","8200","Tea","8.00","Pearl Foods","Qatar","","","5");
        sales.addLine("8100","2"); sales.addLine("P200","1");
        assertEquals(10,inventory.find("P100").quantity());
        var receipt=sales.confirm();
        assertEquals("31.19",receipt.totals().payable().toPlainString());
        assertEquals(2,receipt.lines().size()); assertEquals(8,inventory.find("P100").quantity());
        assertEquals(4,inventory.find("P200").quantity()); assertEquals(4,inventory.movements().size());
        assertThrows(IllegalStateException.class,sales::confirm); assertEquals(1,inventory.sales().size());
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","11","x"})
    void invalidSaleLineDoesNotChangeStock(String qty) {
        assertThrows(IllegalArgumentException.class,() -> sales.addLine("P100",qty));
        assertTrue(sales.lines().isEmpty()); assertEquals(10,inventory.find("P100").quantity());
    }
    @Test void unknownAndExpiredItemsRejected() {
        assertThrows(IllegalArgumentException.class,() -> sales.addLine("MISSING","1"));
        goods.register("OLD","001","Old coffee","1.00","Pearl Foods","Qatar","2000-01-01","","2");
        assertThrows(IllegalArgumentException.class,() -> sales.addLine("OLD","1"));
        assertTrue(sales.lines().isEmpty()); assertTrue(inventory.sales().isEmpty());
    }
    @Test void repeatedLineUsesAggregateAndCanBeRemoved() {
        sales.addLine("P100","7"); assertThrows(IllegalArgumentException.class,() -> sales.addLine("8100","4"));
        assertEquals(7,sales.lines().getFirst().quantity()); sales.removeLine("8100");
        assertThrows(IllegalArgumentException.class,sales::quote);
    }
    @Test void confirmRechecksSharedStock() {
        sales.addLine("P100","9");
        var other=new BillingController(inventory); other.startSale(new PricingStrategy.Standard());
        other.addLine("P100","2"); other.confirm();
        assertThrows(IllegalArgumentException.class,sales::confirm); assertTrue(sales.pending());
        assertEquals(8,inventory.find("P100").quantity()); assertEquals(1,inventory.sales().size());
    }
    @Test void cancelDoesNotIssueReceipt() {
        sales.addLine("P100","2"); sales.cancel();
        assertFalse(sales.pending()); assertNull(inventory.sales().getFirst().receipt());
        assertEquals("CANCELLED",inventory.sales().getFirst().status());
        assertEquals(10,inventory.find("P100").quantity()); assertEquals(1,inventory.movements().size());
    }
    @Test void failedCheckoutAndCancellationCanBeRetried() {
        sales.addLine("P100","2"); fail.set(true);
        assertThrows(IllegalStateException.class,sales::confirm); assertThrows(IllegalStateException.class,sales::cancel);
        assertTrue(sales.pending()); assertTrue(inventory.sales().isEmpty());
        assertEquals(1,inventory.movements().size()); assertEquals(10,inventory.find("P100").quantity());
        fail.set(false); sales.confirm(); assertEquals(8,inventory.find("P100").quantity());
    }
    @Test void pricingRulesAndRounding() {
        var subtotal=new BigDecimal("33.00");
        assertEquals(subtotal,new PricingStrategy.Standard().apply(subtotal).payable());
        assertEquals(new BigDecimal("1.49"),strategy.apply(subtotal).tax());
        assertThrows(IllegalArgumentException.class,() -> new PricingStrategy.Percentage(BigDecimal.TEN,BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,() -> new PricingStrategy.Percentage(null,BigDecimal.ZERO));
    }
}
