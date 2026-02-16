Feature: FedEx Office 1P Product Checkout Flow

  Scenario: 1P Order Creation by Vendor Alone FedEx Single Item
    Given I initialize the FedEx session
    When I configure and add the following 1P products to the cart:
      | productName | quantity |
      | Flyers      | 50       |
  #  Then I check the cart html
    Then I scrape the cart context data
    And I estimate shipping methods and select "FREE_GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    And I submit the order using a secure credit card
    And the order should be placed successfully with a generated Order Number