Feature: Essendant 3P Order Creation by Vendor Alone - Product Checkout Flow

  # Variation 1: Single Item (As requested: "what about single item")
  Scenario: 3P Order Creation by Vendor Alone Essendant Single Item Order (Qty 1)
    Given I initialize the FedEx session
    And I search and add the following products to the cart:
      | productName                                                  | quantity |
      | ACCO Metal Book Rings, 1.5 in. Diameter, 100/Box             | 1        |
    And I scrape the cart context data
    And I estimate shipping methods and select "FREE_GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    When I submit the order using a secure credit card
    Then the order should be placed successfully with a generated Order Number
    And I verify the order contact details:
      | firstName     | Diogo                 |
      | lastName      | Pereira               |
      | email         | dpereira@mcfadyen.com |
    And I verify the transaction payment details:
      | paymentType   | CREDIT_CARD      |
      | currency      | USD              |
      | amount        | 83.94            |
      | authResponse  | APPROVED         |
    And I verify the product totals and taxation:
      | taxableAmount | 75.79 |
      | taxAmount     | 8.15  |
      | totalAmount   | 83.94 |
    And I verify the product line items:
      | productName           | quantity |
      | ACCO Metal Book Rings | 1        |

  # Variation 2: Multi-Item with Mixed Quantities
  Scenario: 3P Order Creation by Vendor Alone Essendant Multi-Item Order with Mixed Quantities
    Given I initialize the FedEx session
    And I search and add the following products to the cart:
      | productName                                                                      | quantity |
      | ACCO Metal Book Rings, 1.5 in. Diameter, 100/Box                                 | 1        |
      | Sharpie Liquid Pen Style Highlighters, Fluorescent Yellow Ink, Chisel Tip, Dozen | 2        |
    And I scrape the cart context data
    And I estimate shipping methods and select "FREE_GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    When I submit the order using a secure credit card
    Then the order should be placed successfully with a generated Order Number
    And I verify the order contact details:
      | firstName     | Diogo                 |
      | lastName      | Pereira               |
      | email         | dpereira@mcfadyen.com |
    And I verify the transaction payment details:
      | paymentType   | CREDIT_CARD |
      | currency      | USD         |
      | amount        | 147.91      |
      | authResponse  | APPROVED    |
    And I verify the product totals and taxation:
      | taxableAmount | 133.55 |
      | taxAmount     | 14.36  |
      | totalAmount   | 147.91 |
    # Verifies BOTH items exist in the order
    And I verify the product line items:
      | productName           | quantity |
      | ACCO Metal Book Rings | 1        |
      | Sharpie Liquid Pen    | 2        |

  # Variation 3: Bulk Order (2 of each)
  Scenario: 3P Order Creation by Vendor Alone Essendant Bulk Order (2 of each)
    Given I initialize the FedEx session
    And I search and add the following products to the cart:
      | productName                                                                      | quantity |
      | ACCO Metal Book Rings, 1.5 in. Diameter, 100/Box                                 | 2        |
      | Sharpie Liquid Pen Style Highlighters, Fluorescent Yellow Ink, Chisel Tip, Dozen | 2        |
    And I scrape the cart context data
    And I estimate shipping methods and select "FREE_GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    When I submit the order using a secure credit card
    Then the order should be placed successfully with a generated Order Number
    And I verify the order contact details:
      | firstName     | Diogo |
    And I verify the product totals and taxation:
      | taxableAmount | 209.34 |
      | totalAmount   | 231.84 |
    And I verify the product line items:
      | productName           | quantity |
      | ACCO Metal Book Rings | 2        |
      | Sharpie Liquid Pen    | 2        |

 # Scenario: Registered user adds item to cart
 #   Given I am logged in as a registered user with username "diogomp8484" and password "SenhaForte123"