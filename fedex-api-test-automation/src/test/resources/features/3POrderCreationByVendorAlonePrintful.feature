Feature: Printful 3P Order Creation by Vendor Alone - Product Checkout Flow

  Scenario: 3P Order Creation by Vendor Alone Printful Single Item Order
    Given I initialize the FedEx session
    And I search for the following PRINTFUL products:
      | category               |
      | Hoodies & Sweatshirts  |
    And I resolve the Mirakl offer details for the following Printful products:
      | category               |
      | Hoodies & Sweatshirts  |
    # Protocol & State Handshake
    And I execute the Printful punchout for the resolved products
    And the Printful punchout context should be populated
    # Security & State Validation
    And I validate the Custom Apparel session
    And I generate an auth nonce for the Custom Apparel session
    #  Cart/Checkout Payload Submission
    And I configure the Printful apparel variant:
      | productName                 | color       | techniques |
      | Unisex Heavy Blend Hoodie   | Black       | DTG        |
    And I upload a design asset to the Printful asset library
    And I select quantities and proceed to checkout:
      | S   | M   | L   | XL  | 2XL | 3XL | 4XL | 5XL |
      | 0   | 1   | 0   | 0   | 0   | 0   | 0   | 0   |
    # Fetches the variants API, matches the IDs to the sizes above, and builds the payload
    When I add to cart or checkout the Printful apparel variants
    Then the Printful checkout response should be successful