Feature: CompanyBox Product Checkout Flow

  @Regression
  Scenario Outline: 3P Order Creation by Vendor Alone CompanyBox Single Item Order (Qty 1)
    Given I initialize the FedEx session
    When I search for the following products:
      | productName            |
      | Custom Box Sample Pack |
    And I add the following products to the cart:
      | productName            | quantity |
      | Custom Box Sample Pack | 1        |
    And I scrape the cart context data
    And I verify the customer load section contains following information:
      | cart.summary_count               | 1          |
      | cart.subtotalAmount              | 12.0000    |
    And I provide the shipping address:
      | firstName   | lastName    | street              | city        | regionId   | regionCode | countryId | postcode   | telephone   | email   |
      | <firstName> | <lastName>  | 550 PEACHTREE ST NE | Los Angeles | 34         | CA         | US        | 90002      | 4247021234  | <email> |
    And I estimate shipping methods and select "FREE_GROUND_US"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    And I provide the payment details:
      | cardNumber       | expMonth | expYear | cvv   |
      | 4111111111111111 | 12       | 2035    | 123   |
    When I submit the order using a secure credit card
    Then the order should be placed successfully with a generated Order Number
    And I verify the order contact details:
      | firstName | <firstName> |
      | lastName  | <lastName>  |
      | email     | <email>     |
    And I verify the transaction payment details:
      | paymentType  | CREDIT_CARD |
      | currency     | USD         |
      | authResponse | APPROVED    |
    And I verify the product line items:
      | productName            | quantity   |
      | Custom Box Sample Pack | 1          |
    And I verify the order contact details:
      | firstName     | <firstName>           |
      | lastName      | <lastName>            |
      | email         | <email>               |
    And I verify the transaction payment details:
      | paymentType   | CREDIT_CARD      |
      | currency      | USD              |
      | amount        | <totalAmount>    |
      | authResponse  | <authStatus>     |
    And I verify the product totals and taxation:
      | taxableAmount        | <taxableAmount>      |
      | taxAmount            | <taxAmount>          |
      | productTotalAmount   | <productTotalAmount> |
    Examples:
      | firstName | lastName | email                         | taxableAmount | taxAmount | productTotalAmount | totalAmount | authStatus |
      | Harvey    | Hamilton | harvey.hamilton.osv@fedex.com | 12.0          | 1.29      | 13.29              | 13.29       | APPROVED   |
