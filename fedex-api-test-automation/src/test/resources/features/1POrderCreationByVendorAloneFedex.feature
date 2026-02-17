Feature: FedEx Office 1P Product Checkout Flow

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