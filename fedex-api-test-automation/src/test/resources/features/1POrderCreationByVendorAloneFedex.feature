Feature: FedEx Office 1P Product Checkout Flow

  @1P @E2E @Configurator
  Scenario: 1P E2E Order Creation with Copies & Custom Documents
    Given I initialize the FedEx session
    When I search for the 1P product "Copies & Custom Documents"
    And I request an internal rate quote using the default template
    And I fetch the Menu Hierarchy to resolve the Product ID
    And I fetch the dynamic product details for the selected product
    And I initiate a 1P Configurator Session for the current product
    And I verify the Configurator Session was successfully created
    And I perform a Configurator Session Search
    And I upload the document "SimpleText.pdf" to the FedEx repository
    And I create the Configurator State and apply the following features:
      | Paper Size  | 8.5x11       |
      | Print Color | Full Color   |
      | Sides       | Single-Sided |
    And I verify the Configurator State was successfully created
    And I add 1 configured document product(s) to the cart
    And I verify the configured product was added to the Magento cart
    And I verify the product is visible in the cart via section load
    And I scrape the cart context data
    And I estimate shipping methods and select "GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    When I submit the order using a secure credit card
    Then the order should be placed successfully with a generated Order Number

  Scenario: 1P Order Creation by Vendor Alone FedEx Single Item
    Given I initialize the FedEx session
    When I search for the following products:
      | productName | sellerModel |
      | Flyers      | 1P          |
    And I add the following products to the cart:
      | productName | quantity |
      | Flyers      | 50       |
    And I scrape the cart context data
    And I check the cart html
    And I estimate shipping methods and select "GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    When I submit the order using a secure credit card
    Then the order should be placed successfully with a generated Order Number
    And I verify the order contact details:
      | firstName | Diogo                 |
      | lastName  | Pereira               |
      | email     | dpereira@mcfadyen.com |
    And I verify the transaction payment details:
      | paymentType | CREDIT_CARD |
      | currency    | USD         |
      | amount      | 43.2        |
    And I verify the product totals and taxation:
      | taxableAmount | 29.99 |
      | taxAmount     | 3.22  |
      | totalAmount   | 33.21 |
    And I verify the product line items:
      | productName | quantity |
      | Flyers      | 50       |
    And I verify the order totals:
      | grossAmount | 39.98 |
      | netAmount   | 39.98 |
      | taxAmount   | 3.22  |
      | totalAmount | 43.20 |
    And I verify the unified data layer:
      | customerName  | Diogo Pereira         |
      | customerType  | GUEST                 |
      | customerEmail | dpereira@mcfadyen.com |
      | orderTotal    | $43.20                |
      | currency      | USD                   |

  # Variation 2: Multi-Item with Mixed Quantities
  Scenario: 1P Order Creation by Vendor Alone Fedex Multi-Item Order with Mixed Quantities
    Given I initialize the FedEx session
    When I search for the following products:
      | productName               | sellerModel |
      | Flyers                    | 1P          |
      | Copies & Custom Documents | 1P          |

    And I add the following products to the cart:
      | productName                    | quantity |
      | Flyers                         | 50       |
      | Copies & Custom Documents      | 1        |
    And I scrape the cart context data
    And I check the cart html
    And I estimate shipping methods and select "GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    When I submit the order using a secure credit card
    Then the order should be placed successfully with a generated Order Number