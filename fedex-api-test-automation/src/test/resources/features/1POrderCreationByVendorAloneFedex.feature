Feature: FedEx Office 1P Product Checkout Flow

  Scenario: 1P Order Creation by Vendor Alone FedEx Single Itemv2
    Given I initialize the FedEx session
    And I search and add the following products to the cart:
      | productName                | quantity | sellerModel |
      | Copies & Custom Documents  | 50       | 1P          |
    And I scrape the cart context data
#   And I check the cart html
    And I estimate shipping methods and select "GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    When I submit the order using a secure credit card
    Then the order should be placed successfully with a generated Order Number


  Scenario: 1P Order Creation by Vendor Alone FedEx Single Item
    Given I initialize the FedEx session
    And I search and add the following products to the cart:
      | productName | quantity | sellerModel |
      | Flyers      | 50       | 1P          |
    And I scrape the cart context data
#   And I check the cart html
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

