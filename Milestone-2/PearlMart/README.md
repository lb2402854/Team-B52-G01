**PearlMart Inventory Management System**

CMPS310 – Software Engineering  
Milestone 2 – Fall 2026

**System Overview**

PearlMart is a JavaFX desktop application developed to support UC04 – Add Goods and UC13 – Process Sale, including UC14, UC15, UC16 and UC17. Both operations use the same inventory repository.

**Software Requirements**

- JDK 21
- JavaFX SDK 21.0.13
- Eclipse IDE for Java Developers

**Project Setup**

1. Extract the PearlMart ZIP file.
2. Open Eclipse and go to File → Import → General → Existing Projects into Workspace.
3. Select the extracted project directory.
4. Select PearlMart and click Finish.

**JavaFX Configuration**

1. Download and extract the JavaFX SDK suitable for your operating system and processor.
2. Open Eclipse Preferences → Java → Build Path → User Libraries.
3. Create a User Library named `javafx`.
4. Click Add External JARs and add the JAR files from the JavaFX SDK's `lib` folder.
5. Make sure the `javafx` library is included under Modulepath in the project's Java Build Path.
6. Make sure the project uses JavaSE-21.

**Compilation and Running Instructions**

1. Go to Project → Build Automatically and make sure it is enabled.
2. Go to Project → Clean and rebuild PearlMart.
3. Right-click `PearlMart.launch` and select Run As to launch the application.

If the launch configuration is unavailable, create a new Java Application configuration with the following settings:

Project: `PearlMart`

Main class: `application.Main`

VM arguments: `--add-modules javafx.controls,javafx.fxml`

Click Apply, then Run.

**Application Features**

- Register new inventory items and increase existing quantities.
- Validate item information and stock availability.
- Record inventory movements.
- Process sales containing multiple items.
- Calculate discounts, taxes and final payable amounts.
- Confirm sales and generate receipts.
- Cancel pending sales without changing inventory quantities.

**Sample Inventory Data**

<table border="1">
<tr>
<th>Item ID</th>
<th>Barcode</th>
<th>Product</th>
<th>Manufacturer</th>
<th>Origin</th>
<th>Price (QAR)</th>
<th>Quantity</th>
</tr>
<tr>
<td>P001</td>
<td>10001</td>
<td>Milk</td>
<td>Baladna</td>
<td>Qatar</td>
<td>10.00</td>
<td>20</td>
</tr>
<tr>
<td>P002</td>
<td>10002</td>
<td>Rice</td>
<td>Sunwhite</td>
<td>Australia</td>
<td>25.00</td>
<td>30</td>
</tr>
<tr>
<td>P003</td>
<td>10003</td>
<td>Orange Juice</td>
<td>Baladna</td>
<td>Qatar</td>
<td>8.50</td>
<td>15</td>
</tr>
<tr>
<td>P004</td>
<td>10004</td>
<td>Pasta</td>
<td>Barilla</td>
<td>Italy</td>
<td>12.00</td>
<td>10</td>
</tr>
<tr>
<td>P005</td>
<td>10005</td>
<td>Water</td>
<td>Rayyan</td>
<td>Qatar</td>
<td>2.00</td>
<td>50</td>
</tr>
</table>

These items are automatically loaded when the application starts and can be accessed using their item ID or barcode.

**Storage and Limitations**

The application uses in-memory storage. Inventory updates and transaction records are maintained while the application is running. Restarting the application restores the original sample data.

Authentication, persistent storage, supplier integration and concurrent multi-user access are outside the required implementation scope of Milestone 2.

**Design Patterns**

Factory Method (Creational): Used to create the appropriate discount policy.

Strategy (Behavioral): Used to apply different discount and tax calculation rules.
