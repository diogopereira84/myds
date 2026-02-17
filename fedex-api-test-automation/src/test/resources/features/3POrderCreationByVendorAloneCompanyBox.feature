Feature: CompanyBox Product Checkout Flow

  # Variation 1: Single Item
  Scenario: 3P Order Creation by Vendor Alone CompanyBox Single Item Order (Qty 1)
    Given I initialize the FedEx session
    And I search and add the following products to the cart:
      | productName                        | quantity |
      | Custom Box Sample Pack             | 1        |
    And I scrape the cart context data
    And I estimate shipping methods and select "FREE_GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    When I submit the order using a secure credit card
    Then the order should be placed successfully with a generated Order Number
#   And I trigger the order export to Mirakl

  # Variation 2: Single Item - Item Order Rates & Transit
  Scenario: 3P Order Creation by Vendor Alone CompanyBox Single Item Order Rates & Transit (Qty 1)
    Given I initialize the FedEx session
    And I search and add the following products to the cart:
      | productName                        | quantity |
      | Custom Self-Sealing Boxes          | 1        |
    And I scrape the cart context data
    And I estimate shipping methods and select "FREE_GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    When I submit the order using a secure credit card
    Then the order should be placed successfully with a generated Order Number
#   And I trigger the order export to Mirakl