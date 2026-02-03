Feature: Essendant Product Checkout Flow

  Scenario Outline: 3P Order Creation by Vendor Alone (Essendant) - <itemsCount> item(s)
    Given I initialize the FedEx session
    When I search for the product "<productName>"
    And I fetch the dynamic Offer ID from Mirakl
    And I add "<quantity>" quantity of the product to the cart
    And I scrape the cart context data
    And I estimate shipping methods and select "<shippingMethod>"
    And I retrieve the delivery rate
    And I create a quote
    And I validate the pay rate API
    Then I submit the order using a secure credit card
    And the order should be placed successfully with a generated Order Number
    And I trigger the order export to Mirakl

    Examples:
      | itemsCount | productName                                                                    | quantity | shippingMethod   |
      | 1          | ACCO Metal Book Rings, 1.5 in. Diameter, 100/Box                               | 1        | FREE_GROUND_US   |
<<<<<<< HEAD
      #| 1          | Sharpie Liquid Pen Style Highlighters, Fluorescent Yellow Ink, Chisel Tip, Dozen    | 1        | FREE_GROUND_US   |
=======
     # | 1          | ACCO Paper Clips, Jumbo, Nonskid, Silver, 100 Clips/Box, 10 Boxes/Pack                | 1        | FREE_GROUND_US   |
>>>>>>> 889d7437c829f193627b39e0d8fd8328835b8d54

