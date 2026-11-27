package pearlmart;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
import static pearlmart.Inventory.*;

public final class PearlMartApp extends Application {
    final Inventory inventory;
    final InventoryController goods;
    final BillingController billing;
    final Map<String,TextField> fields = new LinkedHashMap<>();
    final Map<String,Button> buttons = new LinkedHashMap<>();
    final TabPane tabs = new TabPane();
    final ComboBox<String> rules = new ComboBox<>();
    final Label message = new Label();
    final Label saleStatus = new Label("No active sale");
    final TextArea cart = display(8);
    final TextArea stock = display(9);
    private final Label history = new Label();
    private boolean awaitingCancellation;
    private SalesReceipt lastReceipt;

    public PearlMartApp() { this(new Inventory()); }
    PearlMartApp(Inventory inventory) {
        this.inventory = inventory;
        goods = new InventoryController(inventory);
        billing = new BillingController(inventory);
    }
    @Override public void start(Stage stage) {
        goods.register("P100","8100","Coffee","12.50","Pearl Foods","Qatar","","","10");
        goods.register("P200","8200","Tea","8.00","Pearl Foods","Qatar","","","5");
        Label title = new Label("PearlMart");
        title.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #123e41;");
        Label subtitle = new Label("Inventory and sales");
        HBox header = new HBox(18, title, subtitle);
        header.setAlignment(Pos.BASELINE_LEFT);
        Tab add = new Tab("Add Goods", goodsPane());
        Tab sell = new Tab("Process Sale", salePane());
        tabs.getTabs().addAll(add,sell);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox ledger = new VBox(10, heading("Shared inventory"),stock,history);
        ledger.setPrefWidth(295); ledger.setMinWidth(275);
        HBox body = new HBox(16,tabs,ledger);
        HBox.setHgrow(tabs,Priority.ALWAYS);
        VBox root = new VBox(14,header,body,message);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-font-family: 'DejaVu Sans'; -fx-font-size: 13px; -fx-background-color: #f5f8f7;");
        message.setWrapText(true);
        refresh();
        feedback("Ready.",false);
        stage.setTitle("PearlMart | Inventory and sales");
        stage.setScene(new Scene(root,1010,650));
        stage.show();
    }
    private static TextArea display(int rows) {
        TextArea area = new TextArea(); area.setEditable(false); area.setPrefRowCount(rows);
        area.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;"); return area;
    }
    private static Label heading(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #155954; -fx-font-size: 18px; -fx-font-weight: bold;");
        return label;
    }
    private TextField field(String key, String hint) {
        TextField f = new TextField(); f.setPromptText(hint); f.setId(key); fields.put(key,f); return f;
    }
    private String value(String key) { return fields.get(key).getText(); }
    private Button button(String id, String label, Supplier<String> action) {
        Button b = new Button(label); b.setId(id); buttons.put(id,b);
        b.setOnAction(event -> {
            try { feedback(action.get(),false); }
            catch (RuntimeException ex) { feedback(ex.getMessage(),true); }
            refresh();
        });
        return b;
    }
    private void feedback(String text, boolean error) {
        message.setText(text);
        message.setStyle("-fx-padding: 9 12; -fx-background-color: " + (error ? "#fbe7e3" : "#e1f1eb") +
            "; -fx-text-fill: " + (error ? "#832f20" : "#124d40") + "; -fx-font-weight: bold;");
    }
    private VBox goodsPane() {
        GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(8);
        String[][] descriptions = {{"id","Item ID"},{"barcode","Barcode"},{"name","Name"},{"price","Selling price"},
            {"maker","Manufacturer"},{"origin","Origin"},{"expiry","Expiry date"},{"comments","Comments"}};
        for (int i=0;i<descriptions.length;i++) {
            String[] f = descriptions[i]; int col=(i%2)*2, row=i/2;
            grid.add(new Label(f[1]),col,row); String hint = switch (f[0]) { case "expiry" -> "Optional: YYYY-MM-DD"; case "comments" -> "Optional"; default -> ""; };
            grid.add(field(f[0],hint),col+1,row);
        }
        TextField qty = field("goodsQty","Positive whole number");
        Button register = button("register","Confirm new item",() -> {
            goods.register(value("id"),value("barcode"),value("name"),value("price"),value("maker"),value("origin"),
                value("expiry"),value("comments"),value("goodsQty"));
            return "Registered " + value("id") + ". One addition recorded.";
        });
        Button lookup = button("lookup","Find item",() -> {
            Item item = goods.lookup(value("key"));
            if (item == null) throw new IllegalArgumentException("No item found. Use the new item form to register it.");
            return item.id() + " • " + item.name() + " • available " + item.quantity();
        });
        Button restock = button("restock","Confirm restock",() -> {
            goods.restock(value("key"),value("restockQty")); return "Stock updated. One addition recorded.";
        });
        return pane(heading("Register new goods"),grid,new HBox(10,new Label("Quantity"),qty,register),
            new Separator(),heading("Increase existing stock"),new HBox(8,field("key","Item ID or barcode"),lookup),
            new HBox(8,field("restockQty","Quantity to add"),restock));
    }
    private PricingStrategy selectedPolicy() {
        if ("Standard".equals(rules.getValue())) return new PricingStrategy.Standard();
        Properties p = new Properties();
        try (var input = Files.newBufferedReader(Path.of("pricing.properties"))) {
            p.load(input);
            return new PricingStrategy.Percentage(new BigDecimal(p.getProperty("discountRate","")),
                new BigDecimal(p.getProperty("taxRate","")));
        } catch (IOException | NumberFormatException ex) {
            throw new IllegalArgumentException("Set valid discountRate and taxRate in pricing.properties.");
        }
    }
    private VBox salePane() {
        rules.getItems().addAll("Standard","Percentage"); rules.setValue("Standard");
        Button start = button("start","Start sale",() -> {
            PricingStrategy policy = selectedPolicy(); billing.startSale(policy);
            awaitingCancellation=false; lastReceipt=null; saleStatus.setText("PENDING  |  " + policy); return "Pending sale opened.";
        });
        Button add = button("add","Add line",() -> {
            billing.addLine(value("saleKey"),value("saleQty")); return "Line accepted. Stock changes only on confirmation.";
        });
        Button remove = button("remove","Remove line",() -> { billing.removeLine(value("saleKey")); return "Line removed."; });
        Button quote = button("quote","Calculate total",() -> { var totals=billing.quote(); return totalsText(totals); });
        Button confirm = button("confirm","Confirm sale",() -> {
            SalesReceipt result=billing.confirm(); saleStatus.setText("COMPLETED");
            lastReceipt=result;
            return "Receipt issued. Stock updated once.";
        });
        Button cancel = button("cancel","Cancel sale",() -> {
            awaitingCancellation=true; return "Cancel this pending sale? Choose Yes, cancel or Keep editing.";
        });
        Button yes = button("yes","Yes, cancel",() -> { billing.cancel(); awaitingCancellation=false;
            lastReceipt=null; saleStatus.setText("CANCELLED"); return "Cancellation recorded. Stock unchanged."; });
        Button no = button("no","Keep editing",() -> { awaitingCancellation=false; return "Sale is still pending."; });
        return pane(heading("Process a sale"),new HBox(8,new Label("Pricing rule"),rules,start),saleStatus,
            new HBox(8,field("saleKey","Item ID or barcode"),field("saleQty","Quantity"),add,remove),
            cart,new HBox(8,quote,confirm,cancel), new HBox(8,yes,no));
    }
    private static VBox pane(Node... nodes) {
        VBox box = new VBox(13,nodes); box.setPadding(new Insets(14)); return box;
    }
    private static String totalsText(PricingStrategy.Totals t) {
        return "Subtotal " + t.subtotal() + "  |  Discount " + t.discount() +
            "  |  Tax " + t.tax() + "  |  Payable " + t.payable();
    }
    private void refresh() {
        StringBuilder listing = new StringBuilder("ITEM       PRICE      ON HAND\n");
        for (Item item: inventory.items()) listing.append(String.format(Locale.ROOT,"%-8s %7s %10d%n",
            item.id(),item.price(),item.quantity())).append("  ").append(item.name()).append('\n');
        stock.setText(listing.toString());
        long completed=inventory.sales().stream().filter(s -> "COMPLETED".equals(s.status())).count();
        history.setText("Movements  " + inventory.movements().size() + "\nCompleted sales  " + completed +
            "\nCancellations  " + (inventory.sales().size()-completed));
        StringBuilder lines=new StringBuilder();
        if (lastReceipt != null) lines.append("SALES RECEIPT  ").append(lastReceipt.saleId(),0,8).append('\n');
        if (billing.pending() || lastReceipt != null) {
            for (SaleLine line:billing.lines()) lines.append(String.format(Locale.ROOT,"%s  %-12s  %d × %s = %s%n",
                line.itemId(),line.name(),line.quantity(),line.price(),line.amount()));
            if (!billing.lines().isEmpty()) {
                var t = billing.pending() ? billing.quote() : lastReceipt.totals();
                lines.append("\n").append(totalsText(t));
            }
        }
        cart.setText(lines.isEmpty() ? "No sale lines." : lines.toString());
        rules.setDisable(billing.pending()); buttons.get("start").setDisable(billing.pending());
        for (String key : List.of("add","remove","cancel","quote","confirm"))
            buttons.get(key).setDisable(!billing.pending() || awaitingCancellation ||
                (List.of("confirm","quote").contains(key) && billing.lines().isEmpty()));
        for (String key:List.of("yes","no")) {
            buttons.get(key).setVisible(awaitingCancellation); buttons.get(key).setManaged(awaitingCancellation);
        }
    }
    public static void main(String[] args) { launch(args); }
}
