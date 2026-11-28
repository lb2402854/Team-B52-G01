package application;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import controller.AddGoodsController;
import controller.ProcessSaleController;
import repository.Inventory;
import service.InventoryService;

// @author LOLWA BAALI

public class Main extends Application{

    @Override
    public void start(Stage stage){

        Inventory inventory=new Inventory();

        InventoryService inventoryService=new InventoryService(inventory);

        loadSampleData(inventoryService);

        AddGoodsController addGoods=new AddGoodsController(inventoryService);
        ProcessSaleController processSale=new ProcessSaleController(inventory);

        Tab addGoodsTab=new Tab("Add Goods");
        addGoodsTab.setContent(addGoods.getView());
        addGoodsTab.setClosable(false);

        Tab processSaleTab=new Tab("Process Sale");
        processSaleTab.setContent(processSale.getView());
        processSaleTab.setClosable(false);

        TabPane tabs=new TabPane();
        tabs.getTabs().addAll(addGoodsTab,processSaleTab);

        addGoodsTab.setOnSelectionChanged(e->{
            if(addGoodsTab.isSelected()){
                addGoods.refreshQuantity();
            }
        });

        Scene scene=new Scene(tabs,1000,850);

        stage.setTitle("PearlMart Inventory Management System");
        stage.setScene(scene);
        stage.show();
    }

    private void loadSampleData(InventoryService inventoryService){

        inventoryService.register("P001","10001","Milk",
                "10.00","Baladna","Qatar","","","20");

        inventoryService.register("P002","10002","Rice",
                "25.00","Sunwhite","Australia","","","30");

        inventoryService.register("P003","10003","Orange Juice",
                "8.50","Baladna","Qatar","","","15");

        inventoryService.register("P004","10004","Pasta",
                "12.00","Barilla","Italy","","","10");

        inventoryService.register("P005","10005","Water",
                "2.00","Rayyan","Qatar","","","50");
    }

    public static void main(String[] args){
        launch(args);
    }
}
