Feature: Essendant Product Checkout Flow

  # Variation 1: Single Item (As requested: "what about single item")
  Scenario: 3P Order Creation by Vendor Alone Essendant Single Item Order (Qty 1)
    Given I initialize the FedEx session
    When I add the following products to the cart:
      | productName                                                  | quantity |
      | ACCO Metal Book Rings, 1.5 in. Diameter, 100/Box             | 1        |
    And I scrape the cart context data
    And I estimate shipping methods and select "FREE_GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    Then I submit the order using a secure credit card
    And the order should be placed successfully with a generated Order Number
    And I trigger the order export to Mirakl

  # Variation 2: Multi-Item with Mixed Quantities
  Scenario: 3P Order Creation by Vendor Alone Essendant Multi-Item Order with Mixed Quantities
    Given I initialize the FedEx session
    When I add the following products to the cart:
      | productName                                                                      | quantity |
      | ACCO Metal Book Rings, 1.5 in. Diameter, 100/Box                                 | 1        |
      | Sharpie Liquid Pen Style Highlighters, Fluorescent Yellow Ink, Chisel Tip, Dozen | 2        |
    And I scrape the cart context data
    And I estimate shipping methods and select "FREE_GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    Then I submit the order using a secure credit card
    And the order should be placed successfully with a generated Order Number
    And I trigger the order export to Mirakl

  # Variation 3: Bulk Order (As requested: "considering 2 items of each product")
  Scenario: 3P Order Creation by Vendor Alone Essendant Bulk Order (2 of each)
    Given I initialize the FedEx session
    When I add the following products to the cart:
      | productName                                                                      | quantity |
      | ACCO Metal Book Rings, 1.5 in. Diameter, 100/Box                                 | 2        |
      | Sharpie Liquid Pen Style Highlighters, Fluorescent Yellow Ink, Chisel Tip, Dozen | 2        |
    And I scrape the cart context data
    And I estimate shipping methods and select "FREE_GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    Then I submit the order using a secure credit card
    And the order should be placed successfully with a generated Order Number
    And I trigger the order export to Mirakl