package pearlmart;

import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

/** Replays the real JavaFX controls. Start from the app directory; pass an evidence directory. */
public final class FxEvidence {
    final Path out;
    final Stage stage = new Stage();
    final AtomicBoolean failNext = new AtomicBoolean();
    final PearlMartApp app = new PearlMartApp(new Inventory(() -> {
        if (failNext.getAndSet(false)) throw new IllegalStateException("Save failed. No changes were committed.");
    }));
    FxEvidence(Path out) { this.out=out; }
    void set(String id,String value) { app.fields.get(id).setText(value); }
    void click(String id) {
        var b=app.buttons.get(id);
        check(!b.isDisabled() && b.isVisible(),"Unavailable button: " + id); b.fire();
    }
    static void check(boolean valid,String detail) { if (!valid) throw new AssertionError(detail); }
    void capture(String id,String expected,int coffee,int tea,int sales,int movements) throws Exception {
        String msg=app.message.getText();
        check(msg.contains(expected),msg);
        check(app.inventory.find("P100").quantity()==coffee && app.inventory.find("P200").quantity()==tea,"Inventory");
        check(app.inventory.sales().size()==sales && app.inventory.movements().size()==movements,"Ledger");
        var scene=stage.getScene(); scene.getRoot().applyCss();scene.getRoot().layout();
        WritableImage pixels=scene.snapshot(null);
        BufferedImage output=new BufferedImage((int)pixels.getWidth(),(int)pixels.getHeight(),BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<output.getHeight();y++) for(int x=0;x<output.getWidth();x++)
            output.setRGB(x,y,pixels.getPixelReader().getArgb(x,y));
        ImageIO.write(output,"png",out.resolve(id+".png").toFile());
        String log=id+" | PASS | "+msg+" | P100="+coffee+" P200="+tea+" sale records="+sales+" movements="+movements;
        Files.writeString(out.resolve("gui-results.txt"),log+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        System.out.println(log);
    }
    void run() throws Exception {
        Files.createDirectories(out); Files.deleteIfExists(out.resolve("gui-results.txt")); app.start(stage);
        set("id","P300");set("barcode","8300");set("name","Dates");set("price","6.00");set("maker","Pearl Foods");
        set("origin","Qatar");set("goodsQty","4");click("register");
        check(app.inventory.find("P300").quantity()==4,"New goods");capture("G01","Registered",10,5,0,3);
        set("key","8100");set("restockQty","2");click("restock");capture("G02","Stock updated",12,5,0,4);
        click("register");capture("G03","already in use",12,5,0,4);
        set("id","P400");set("barcode","8400");set("name","");click("register");capture("G04","Name is required",12,5,0,4);
        set("restockQty","0");click("restock");capture("G05","positive whole",12,5,0,4);
        set("restockQty","2");failNext.set(true);click("restock");capture("G06","Save failed",12,5,0,4);
        app.tabs.getSelectionModel().select(1);app.rules.setValue("Percentage");click("start");
        set("saleKey","P100");set("saleQty","2");click("add");set("saleKey","8200");set("saleQty","1");click("add");click("quote");
        check(app.cart.getText().contains("Discount 3.30") && app.cart.getText().contains("Tax 1.49"),"Pricing");
        capture("S01a","Payable 31.19",12,5,0,4);click("confirm");
        check(app.cart.getText().contains("SALES RECEIPT") && app.buttons.get("confirm").isDisabled(),"Receipt");
        capture("S01b","Receipt issued",10,4,1,6);
        app.rules.setValue("Standard");click("start");set("saleKey","P100");set("saleQty","11");click("add");
        capture("S02","Available: 10",10,4,1,6);set("saleKey","X404");set("saleQty","1");click("add");
        capture("S03","Unknown item",10,4,1,6);set("saleKey","P100");set("saleQty","0");click("add");
        capture("S04","positive whole",10,4,1,6);set("saleQty","-1");click("add");capture("S05","positive whole",10,4,1,6);
        check(app.billing.lines().isEmpty() && app.buttons.get("confirm").isDisabled(),"Rejected lines");
        set("saleQty","2");click("add");click("cancel");click("no");check(app.billing.pending(),"Kept draft");
        click("cancel");click("yes");check(app.inventory.sales().getLast().receipt()==null,"Cancellation");
        capture("S06","Cancellation recorded",10,4,2,6);
        click("start");set("saleKey","P100");set("saleQty","2");click("add");
        set("saleKey","8200");set("saleQty","1");click("add");click("quote");
        check(app.cart.getText().contains("Discount 0.00") && app.cart.getText().contains("Tax 0.00"),"Standard policy");
        capture("D01","Payable 33.00",10,4,2,6);
        stage.close();
    }
    public static void main(String[] args) throws Exception {
        var done=new CompletableFuture<Void>();
        Platform.startup(() -> {});
        Platform.runLater(() -> {
            try { new FxEvidence(Path.of(args[0])).run();done.complete(null); }
            catch(Throwable t) {done.completeExceptionally(t);}
            finally {Platform.exit();}
        });
        done.get(90,TimeUnit.SECONDS);
    }
}
