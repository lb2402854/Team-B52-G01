package controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import model.InventoryItem;
import service.InventoryService;

// @author LOLWA BAALI

public class AddGoodsController{

    private InventoryService inventoryService;

    private TextField searchField=new TextField();
    private TextField idField=new TextField();
    private TextField barcodeField=new TextField();
    private TextField nameField=new TextField();
    private TextField priceField=new TextField();
    private TextField manufacturerField=new TextField();
    private TextField originField=new TextField();
    private TextField quantityField=new TextField();

    private DatePicker expirationDate=new DatePicker();
    private TextArea commentsField=new TextArea();

    private Label currentQuantity=new Label("-");
    private Label proposedQuantity=new Label("-");
    private Label message=new Label();

    private String selectedId;
    private String searchedKey;

    public AddGoodsController(InventoryService inventoryService){
        this.inventoryService=inventoryService;
    }

    public ScrollPane getView(){

        Label title=new Label("Add Goods");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        searchField.setPromptText("Enter item ID or barcode");

        Button searchButton=new Button("Search");
        Button clearButton=new Button("Clear");

        HBox searchBox=new HBox(10,searchField,searchButton,clearButton);

        GridPane form=new GridPane();
        form.setHgap(15);
        form.setVgap(10);

        form.add(new Label("Item ID:"),0,0);
        form.add(idField,1,0);

        form.add(new Label("Barcode:"),0,1);
        form.add(barcodeField,1,1);

        form.add(new Label("Item Name:"),0,2);
        form.add(nameField,1,2);

        form.add(new Label("Selling Price (QAR):"),0,3);
        form.add(priceField,1,3);

        form.add(new Label("Manufacturer:"),0,4);
        form.add(manufacturerField,1,4);

        form.add(new Label("Origin:"),0,5);
        form.add(originField,1,5);

        form.add(new Label("Quantity to Add:"),0,6);
        form.add(quantityField,1,6);

        expirationDate.setEditable(false);

        form.add(new Label("Expiration Date (Optional):"),0,7);
        form.add(expirationDate,1,7);

        commentsField.setPrefRowCount(2);

        form.add(new Label("Comments (Optional):"),0,8);
        form.add(commentsField,1,8);

        form.add(new Label("Current Quantity:"),0,9);
        form.add(currentQuantity,1,9);

        form.add(new Label("Proposed Quantity:"),0,10);
        form.add(proposedQuantity,1,10);

        Button confirmButton=new Button("Confirm Addition");

        searchButton.setOnAction(e->handleSearch());
        searchField.setOnAction(e->handleSearch());
        clearButton.setOnAction(e->handleClear());
        confirmButton.setOnAction(e->handleConfirmAdd());

        quantityField.textProperty().addListener(
                (observable,oldValue,newValue)->refreshQuantity());

        searchField.textProperty().addListener(
                (observable,oldValue,newValue)->refreshQuantity());

        message.setWrapText(true);

        VBox root=new VBox(15,title,searchBox,form,confirmButton,message);

        root.setPadding(new Insets(25));
        root.setAlignment(Pos.TOP_LEFT);

        ScrollPane scroll=new ScrollPane(root);
        scroll.setFitToWidth(true);

        return scroll;
    }

    public void handleSearch(){

        String key=searchField.getText().trim();

        if(key.isEmpty()){
            showMessage("Enter an item ID or barcode.",true);
            return;
        }

        selectedId=null;
        searchedKey=key;

        clearFields();
        setFieldsEditable(true);

        InventoryItem item=inventoryService.lookup(key);

        if(item==null){

            currentQuantity.setText("0");
            proposedQuantity.setText("-");

            showMessage("New item. Enter the required item details.",false);
        }
        else{

            selectedId=item.getId();

            displayItem(item);
            setFieldsEditable(false);

            showMessage("Item found. Enter the quantity to add.",false);
        }

        refreshQuantity();
    }

    private void displayItem(InventoryItem item){

        idField.setText(item.getId());
        barcodeField.setText(item.getBarcode());
        nameField.setText(item.getName());
        priceField.setText(item.getPrice().toPlainString());
        manufacturerField.setText(item.getManufacturer());
        originField.setText(item.getOrigin());

        expirationDate.setValue(item.getExpiry());
        commentsField.setText(item.getComments());

        currentQuantity.setText(String.valueOf(item.getQuantity()));
    }

    public void refreshQuantity(){

        if(searchedKey==null||
                !searchedKey.equals(searchField.getText().trim())){

            currentQuantity.setText("-");
            proposedQuantity.setText("-");
            return;
        }

        int current=0;

        if(selectedId!=null){

            InventoryItem item=inventoryService.lookup(selectedId);

            if(item==null){
                currentQuantity.setText("-");
                proposedQuantity.setText("-");
                return;
            }

            current=item.getQuantity();
        }

        currentQuantity.setText(String.valueOf(current));

        try{

            int added=InventoryService.positiveQuantity(quantityField.getText());

            int proposed=Math.addExact(current,added);

            proposedQuantity.setText(String.valueOf(proposed));
        }
        catch(IllegalArgumentException|ArithmeticException e){
            proposedQuantity.setText("-");
        }
    }

    public void handleConfirmAdd(){

        try{

            if(searchedKey==null||
                    !searchedKey.equals(searchField.getText().trim())){

                throw new IllegalArgumentException("Search for the item first.");
            }

            InventoryService.positiveQuantity(quantityField.getText());

            String savedId;

            if(selectedId==null){

                if(!searchedKey.equals(idField.getText().trim())&&
                        !searchedKey.equals(barcodeField.getText().trim())){

                    throw new IllegalArgumentException(
                            "The ID or barcode must match your search.");
                }

                String expiry="";

                if(expirationDate.getValue()!=null){
                    expiry=expirationDate.getValue().toString();
                }

                inventoryService.register(
                        idField.getText(),
                        barcodeField.getText(),
                        nameField.getText(),
                        priceField.getText(),
                        manufacturerField.getText(),
                        originField.getText(),
                        expiry,
                        commentsField.getText(),
                        quantityField.getText());

                savedId=idField.getText().trim();
            }
            else{

                inventoryService.restock(selectedId,quantityField.getText());

                savedId=selectedId;
            }

            InventoryItem updated=inventoryService.lookup(savedId);

            selectedId=updated.getId();

            displayItem(updated);
            setFieldsEditable(false);

            quantityField.clear();
            refreshQuantity();

            showMessage("Goods added successfully. Updated quantity: "
                    +updated.getQuantity(),false);
        }
        catch(RuntimeException e){

            refreshQuantity();
            showMessage("Addition failed: "+e.getMessage(),true);
        }
    }

    private void setFieldsEditable(boolean editable){

        idField.setEditable(editable);
        barcodeField.setEditable(editable);
        nameField.setEditable(editable);
        priceField.setEditable(editable);
        manufacturerField.setEditable(editable);
        originField.setEditable(editable);
        commentsField.setEditable(editable);

        expirationDate.setDisable(!editable);
    }

    private void clearFields(){

        idField.clear();
        barcodeField.clear();
        nameField.clear();
        priceField.clear();
        manufacturerField.clear();
        originField.clear();
        quantityField.clear();

        expirationDate.setValue(null);
        commentsField.clear();

        currentQuantity.setText("-");
        proposedQuantity.setText("-");
    }

    public void handleClear(){

        selectedId=null;
        searchedKey=null;

        searchField.clear();
        clearFields();
        setFieldsEditable(true);

        message.setText("");
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
