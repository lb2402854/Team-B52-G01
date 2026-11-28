package controller;

import java.math.BigDecimal;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import model.*;
import repository.Inventory;
import service.*;

// @author LOLWA BAALI

public class ProcessSaleController{

    private Inventory inventory;
    private SaleService saleService;

    private TextField itemField=new TextField();
    private TextField quantityField=new TextField();

    private ComboBox<String> discountChoice=new ComboBox<>();
    private TextField discountRateField=new TextField();
    private TextField taxRateField=new TextField("0");

    private ListView<String> saleItems=new ListView<>();

    private Label subtotal=new Label("-");
    private Label discount=new Label("-");
    private Label tax=new Label("-");
    private Label total=new Label("-");

    private TextArea receipt=new TextArea();
    private Label message=new Label();

    private boolean quoted=false;

    public ProcessSaleController(Inventory inventory){
        this.inventory=inventory;
    }

    public ScrollPane getView(){

        Label title=new Label("Process Sale");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        discountChoice.getItems().addAll("No Discount","Percentage Discount");
        discountChoice.setValue("No Discount");

        discountRateField.setPromptText("Discount %");
        discountRateField.setDisable(true);

        taxRateField.setPromptText("Tax %");

        discountRateField.setPrefWidth(100);
        taxRateField.setPrefWidth(100);

        discountChoice.setOnAction(e->{

            if(discountChoice.getValue().equals("Percentage Discount")){
                discountRateField.setDisable(false);
            }
            else{
                discountRateField.setDisable(true);
            }
        });

        HBox pricingBox=new HBox(10,
                new Label("Discount Policy:"),
                discountChoice,
                discountRateField,
                new Label("Tax %:"),
                taxRateField);

        itemField.setPromptText("Item ID or barcode");
        quantityField.setPromptText("Quantity");

        Button newSaleButton=new Button("New Sale");
        Button addButton=new Button("Add Item");
        Button clearButton=new Button("Clear Entry");
        Button removeButton=new Button("Remove Selected Item");

        Button calculateButton=new Button("Calculate Total");
        Button confirmButton=new Button("Confirm Sale");
        Button cancelButton=new Button("Cancel Sale");

        HBox itemBox=new HBox(10,itemField,quantityField,addButton,clearButton);

        saleItems.setPrefHeight(150);

        GridPane amounts=new GridPane();
        amounts.setHgap(30);
        amounts.setVgap(10);

        amounts.add(new Label("Subtotal:"),0,0);
        amounts.add(subtotal,1,0);

        amounts.add(new Label("Discount:"),0,1);
        amounts.add(discount,1,1);

        amounts.add(new Label("Tax:"),0,2);
        amounts.add(tax,1,2);

        amounts.add(new Label("Total Payable:"),0,3);
        amounts.add(total,1,3);

        total.setStyle("-fx-font-weight: bold;");

        HBox buttons=new HBox(10,calculateButton,confirmButton,cancelButton);

        receipt.setEditable(false);
        receipt.setPrefRowCount(7);
        receipt.setPromptText("Sales receipt");

        newSaleButton.setOnAction(e->handleNewSale());
        addButton.setOnAction(e->handleAddItem());
        removeButton.setOnAction(e->handleRemoveItem());

        clearButton.setOnAction(e->{
            itemField.clear();
            quantityField.clear();
        });

        calculateButton.setOnAction(e->handleCalculateTotal());
        confirmButton.setOnAction(e->handleConfirmSale());
        cancelButton.setOnAction(e->handleCancelSale());

        message.setWrapText(true);

        VBox root=new VBox(12,title,pricingBox,newSaleButton,
                itemBox,new Label("Sale Items"),saleItems,
                removeButton,amounts,buttons,
                new Label("Receipt"),receipt,message);

        root.setPadding(new Insets(25));
        root.setAlignment(Pos.TOP_LEFT);

        ScrollPane scroll=new ScrollPane(root);
        scroll.setFitToWidth(true);

        return scroll;
    }

    public void handleNewSale(){

        try{

            if(saleService!=null&&
                    saleService.getCurrentSale()!=null&&
                    saleService.getCurrentSale().getStatus().equals("PENDING")){

                throw new IllegalArgumentException(
                        "Complete or cancel the current sale first.");
            }

            DiscountPolicyFactory factory;

            if(discountChoice.getValue().equals("Percentage Discount")){

                BigDecimal rate=readRate(discountRateField.getText());

                factory=new PercentageDiscountFactory(rate);
            }
            else{
                factory=new NoDiscountFactory();
            }

            DiscountPolicy discountPolicy=factory.createPolicy();

            BigDecimal taxRate=readRate(taxRateField.getText());

            TaxPolicy taxPolicy=new FixedRateTaxPolicy(taxRate);

            PricingService pricingService=new PricingService(
                    discountPolicy,taxPolicy);

            saleService=new SaleService(inventory,pricingService);

            saleService.startSale();

            saleItems.getItems().clear();

            itemField.clear();
            quantityField.clear();
            receipt.clear();

            clearAmounts();
            lockPricing(true);

            showMessage("New sale started.",false);
        }
        catch(RuntimeException e){
            showMessage(e.getMessage(),true);
        }
    }

    private BigDecimal readRate(String value){

        BigDecimal rate;

        try{
            rate=new BigDecimal(value.trim());
        }
        catch(NumberFormatException e){
            throw new IllegalArgumentException("Enter a valid percentage.");
        }

        if(rate.signum()<0||rate.compareTo(new BigDecimal("100"))>0){
            throw new IllegalArgumentException(
                    "Percentage must be between 0 and 100.");
        }

        return rate.movePointLeft(2);
    }

    public void handleAddItem(){

        try{

            requirePendingSale();

            String key=itemField.getText().trim();

            if(key.isEmpty()){
                throw new IllegalArgumentException("Enter an item ID or barcode.");
            }

            int quantity=Integer.parseInt(quantityField.getText().trim());

            saleService.addItem(key,quantity);

            refreshSaleItems();
            clearAmounts();

            itemField.clear();
            quantityField.clear();

            showMessage("Item added successfully.",false);
        }
        catch(NumberFormatException e){
            showMessage("Quantity must be a whole number.",true);
        }
        catch(RuntimeException e){
            showMessage(e.getMessage(),true);
        }
    }

    public void handleRemoveItem(){

        try{

            requirePendingSale();

            int index=saleItems.getSelectionModel().getSelectedIndex();

            if(index<0){
                throw new IllegalArgumentException("Select an item to remove.");
            }

            SaleLine line=saleService.getCurrentSale().getLines().get(index);

            saleService.removeLine(line.getItemId());

            refreshSaleItems();
            clearAmounts();

            showMessage("Item removed successfully.",false);
        }
        catch(RuntimeException e){
            showMessage(e.getMessage(),true);
        }
    }

    private void refreshSaleItems(){

        saleItems.getItems().clear();

        if(saleService==null||saleService.getCurrentSale()==null){
            return;
        }

        for(SaleLine line:saleService.getCurrentSale().getLines()){

            String text=line.getItemId()+" - "+line.getName()
                    +" | Qty: "+line.getQuantity()
                    +" | Unit Price: "+line.getPrice().toPlainString()
                    +" QAR | Amount: "+line.getAmount().toPlainString()+" QAR";

            saleItems.getItems().add(text);
        }
    }

    public void handleCalculateTotal(){

        try{

            requirePendingSale();
            checkUnfinishedEntry();

            PayableAmount amount=saleService.calculatePayable();

            subtotal.setText(amount.getSubtotal().toPlainString()+" QAR");

            discount.setText(amount.getDiscountTotal().toPlainString()+" QAR");

            tax.setText(amount.getTaxTotal().toPlainString()+" QAR");

            total.setText(amount.getFinalAmount().toPlainString()+" QAR");

            quoted=true;

            showMessage("Total calculated. Review before confirming.",false);
        }
        catch(RuntimeException e){

            clearAmounts();

            showMessage(e.getMessage(),true);
        }
    }

    public void handleConfirmSale(){

        try{

            requirePendingSale();
            checkUnfinishedEntry();

            if(!quoted){
                throw new IllegalArgumentException(
                        "Calculate and review the total first.");
            }

            SalesReceipt result=saleService.confirmSale();

            displayReceipt(result);

            quoted=false;

            lockPricing(false);

            showMessage("Sale completed. Inventory updated successfully.",false);
        }
        catch(RuntimeException e){

            clearAmounts();

            showMessage("Confirmation failed: "+e.getMessage(),true);
        }
    }

    public void handleCancelSale(){

        try{

            requirePendingSale();

            Alert alert=new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to cancel this sale?",
                    ButtonType.YES,ButtonType.NO);

            alert.setHeaderText("Cancel Current Sale");

            ButtonType answer=alert.showAndWait().orElse(ButtonType.NO);

            if(answer!=ButtonType.YES){
                return;
            }

            saleService.cancelSale();

            saleItems.getItems().clear();

            itemField.clear();
            quantityField.clear();
            receipt.clear();

            clearAmounts();
            lockPricing(false);

            showMessage("Sale cancelled. Inventory remains unchanged.",false);
        }
        catch(RuntimeException e){
            showMessage("Cancellation failed: "+e.getMessage(),true);
        }
    }

    private void displayReceipt(SalesReceipt result){

        String text="PEARLMART SALES RECEIPT\n";

        text+="Sale ID: "+result.getSaleId()+"\n\n";

        for(SaleLine line:result.getLines()){

            text+=line.getName()+"\n";

            text+=line.getQuantity()+" x "
                    +line.getPrice().toPlainString()+" QAR = "
                    +line.getAmount().toPlainString()+" QAR\n";
        }

        text+="\nSubtotal: "+result.getSubtotal().toPlainString()+" QAR";

        text+="\nDiscount: "+result.getDiscount().toPlainString()+" QAR";

        text+="\nTax: "+result.getTax().toPlainString()+" QAR";

        text+="\nTotal: "+result.getTotal().toPlainString()+" QAR";

        text+="\n\nStatus: COMPLETED";

        receipt.setText(text);

        subtotal.setText(result.getSubtotal().toPlainString()+" QAR");

        discount.setText(result.getDiscount().toPlainString()+" QAR");

        tax.setText(result.getTax().toPlainString()+" QAR");

        total.setText(result.getTotal().toPlainString()+" QAR");
    }

    private void clearAmounts(){

        subtotal.setText("-");
        discount.setText("-");
        tax.setText("-");
        total.setText("-");

        quoted=false;
    }

    private void requirePendingSale(){

        if(saleService==null||
                saleService.getCurrentSale()==null||
                !saleService.getCurrentSale().getStatus().equals("PENDING")){

            throw new IllegalArgumentException("Start a new sale first.");
        }
    }

    private void checkUnfinishedEntry(){

        if(!itemField.getText().trim().isEmpty()||
                !quantityField.getText().trim().isEmpty()){

            throw new IllegalArgumentException(
                    "Add or clear the current item entry first.");
        }
    }

    private void lockPricing(boolean locked){

        discountChoice.setDisable(locked);
        taxRateField.setDisable(locked);

        discountRateField.setDisable(locked||
                discountChoice.getValue().equals("No Discount"));
    }

    private void showMessage(String text,boolean error){

        message.setText(text);

        if(error){
            message.setStyle("-fx-text-fill: red;");
        }
        else{
            message.setStyle("-fx-text-fill: green;");
        }
    }
}
