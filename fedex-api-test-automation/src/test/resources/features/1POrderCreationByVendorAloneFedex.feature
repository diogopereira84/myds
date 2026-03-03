Feature: FedEx Office 1P Product Checkout Flow

  Scenario Outline: 1P Order Creation with Dynamic Product Configuration
    Given I initialize the FedEx session

    # 1. Dynamically resolves base SKU and ID from Menu Hierarchy
    And I search for the 1P product "<productName>"
    And I request an internal rate quote using the default template
    And I fetch the Menu Hierarchy to resolve the Product ID
    # 2. Downloads the Source of Truth JSON (e.g., product-1456773326927-v2.json)
    And I fetch the dynamic product details for the selected product
    And I initiate a 1P Configurator Session for the current product
    And I verify the Configurator Session was successfully created
    And I perform a Configurator Session Search
    # 3. Uploads content to be mapped to the dynamically selected dimensions
    And I upload the document "<documentName>" to the FedEx repository
    # 4. Injects BDD choices directly into the Source of Truth schema mapper
    And I create the Configurator State and apply the following features:
      | Print Color   | <printColor>   |
      | Paper Size    | <paperSize>    |
      | Paper Type    | <paperType>    |
      | Sides         | <sides>        |
      | Hole Punching | <holePunching> |
    And I verify the Configurator State was successfully created
    And I add <quantity> configured document product(s) to the cart
    And I scrape the cart context data
    # 5. Dynamic validations based on configuration pricing and quantities
    And I verify the customer load section contains following information:
      | cart.summary_count               | <quantity>       |
      | cart.subtotalAmount              | <subtotalAmount> |
    And I provide the shipping address:
      | firstName   | lastName   | street              | city        | regionId | regionCode | countryId | postcode | telephone  | email   |
      | <firstName> | <lastName> | 550 PEACHTREE ST NE | Los Angeles | 34       | CA         | US        | 90002    | 4247021234 | <email> |
    And I estimate shipping methods and select "LOCAL_DELIVERY_PM"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    And I provide the payment details:
      | cardNumber       | expMonth | expYear | cvv |
      | 4111111111111111 | 12       | 2035    | 123 |
    When I submit the order using a secure credit card
    Then the order should be placed successfully with a generated Order Number
    And I verify the order contact details:
      | firstName | <firstName> |
      | lastName  | <lastName>  |
      | email     | <email>     |
    And I verify the transaction payment details:
      | paymentType   | CREDIT_CARD   |
      | currency      | USD           |
      | authResponse  | <authStatus>  |
    # You can add as many combinations as you want here, and the Java code will automatically
    # adjust IDs, sizes (11x17 vs 8.5x11), and properties directly from the FedEx API schema.
    Examples:
      | productName               | documentName   | printColor    | paperSize | paperType              | sides        | holePunching | quantity | subtotalAmount | firstName | lastName | email                         | authStatus |
      | Copies & Custom Documents | SimpleText.pdf | Black & White | 11.0x17   | Laser (32 lb.)         | Single-Sided | None         | 1        | 0.2600         | Harvey    | Hamilton | harvey.hamilton.osv@fedex.com | APPROVED   |
      | Copies & Custom Documents | SimpleText.pdf | Full Color    | 8.5x11    | Standard White (20lb.) | Single-Sided | None         | 1        | 0.6400         | Harvey    | Hamilton | harvey.hamilton.osv@fedex.com | APPROVED   |

  # Variation 2: Multi-Item with Mixed Quantities
  Scenario Outline: 1P Order Creation by Vendor Alone Fedex Multi-Item Order with Mixed Quantities
    Given I initialize the FedEx session
    # This single step loops over the table, dynamically fetching rules and uploading documents for EACH row!
    And I configure and add the following 1P custom documents to the cart:
      | productName               | documentName   | Print Color   | Paper Size | Paper Type             | Sides        | Hole Punching    | quantity |
      | Copies & Custom Documents | SimpleText.pdf | Black & White | 11.0x17    | Laser (32 lb.)         | Single-Sided | None             | 1        |
      | Copies & Custom Documents | SimpleText.pdf | Full Color    | 8.5x11     | Standard White (20lb.) | Single-Sided | None             | 2        |
    #  | Flyers                    | SimpleText.pdf | Full Color    | 8.5x11     | Standard White (20lb.) | Single-Sided | None             | 50       |
    And I scrape the cart context data
    # 1 item + 2 items = 3 total items in the cart
    And I verify the customer load section contains following information:
      | cart.total_quantity | 3                |
      | cart.subtotalAmount | 1.5400           |
    And I provide the shipping address:
      | firstName    | lastName    | street              | city        | regionId | regionCode | countryId | postcode | telephone  | email     |
      | <firstName>  | <lastName>  | 550 PEACHTREE ST NE | Los Angeles | 34       | CA         | US        | 90002    | 4247021234 | <email>   |
    And I estimate shipping methods and select "LOCAL_DELIVERY_PM"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    And I provide the payment details:
      | cardNumber       | expMonth | expYear | cvv |
      | 4111111111111111 | 12       | 2035    | 123 |
    When I submit the order using a secure credit card
    Then the order should be placed successfully with a generated Order Number
    And I verify the order contact details:
      | firstName | <firstName> |
      | lastName  | <lastName>  |
      | email     | <email>     |
    And I verify the transaction payment details:
      | paymentType   | CREDIT_CARD      |
      | currency      | USD              |
      | amount        | <amount>         |
      | authResponse  | <authStatus>     |
    And I verify the product totals and taxation:
      | taxableAmount        | <taxableAmount>      |
      | taxAmount            | <taxAmount>          |
      | productTotalAmount   | <productTotalAmount> |
    Examples:
      | firstName | lastName | email                         | authStatus | amount | taxableAmount | taxAmount | productTotalAmount |
      | Harvey    | Hamilton | harvey.hamilton.osv@fedex.com | APPROVED   | 23.84  | 1.54          |  0.16     | 1.70               |