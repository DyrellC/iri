Feature: Rescan and Revalidate tests
  These tests are responsible for ensuring that updates do not effect the rescan and revalidate functionality. This is
  done by first checking that on each node, the provided milestones are null in the database, as they should have been
  removed from the db during the rescanning or revalidation process. Revalidation only removes the milestones and
  balance differences, so to make sure that the functionality is running, a balance check on the original addresses that
  are present in the snapshot will be conducted. If the balance is still there, then the revalidation is successful, if
  not, then the balance change has not been removed from the db as intended. For revalidation, a similar check will be
  conducted to ensure that milestones and stateDiff's have been removed properly. Additionally the test will also check
  to see that transactions containing a specific tag are not present in the db as well. If any of the above fail, then
  something may have broken the intended functionality in either a local snapshot enabled or disabled environment.

  Scenario: Rescan with Local Snapshots enabled
    Check that the rescan functionality operates as intended with LS enabled.

    Given "checkConsistency" is called on "nodeA" with:
      |keys                       |values                   |type             |
      |tails                      |RESCAN_TEST_TRANSACTIONS |staticValue      |

    Then the response for "checkConsistency" should return with:
      |keys                       |values                   |type             |
      |state                      |False                    |bool             |

    When "getBalances" is called on "nodeA" with:
      |keys                       |values                   |type             |
      |addresses                  |RESCAN_TEST_ADDRESS      |staticValue      |

    Then the response for "getBalances" should return with:
      |keys                       |values                   |type             |
      |balances                   |100                      |int              |

    And "findTransactions" is called on "nodeA" with:
      |keys                       |values                   |type             |
      |tags                       |TESTING                  |list             |

    Then the response for "findTransactions" should return with:
      |keys                       |values                   |type             |
      |hashes                     |                         |list             |


  Scenario: Rescan with Local Snapshots disabled
  Check that the rescan functionality operates as intended with LS disabled.

    Given "checkConsistency" is called on "nodeB" with:
      |keys                       |values                   |type             |
      |tails                      |RESCAN_TEST_TRANSACTIONS |staticValue      |

    Then the response for "checkConsistency" should return with:
      |keys                       |values                   |type             |
      |state                      |False                    |bool             |

    When "getBalances" is called on "nodeB" with:
      |keys                       |values                   |type             |
      |addresses                  |RESCAN_TEST_ADDRESS      |staticValue      |

    Then the response for "getBalances" should return with:
      |keys                       |values                   |type             |
      |balances                   |100                      |int              |

    And "findTransactions" is called on "nodeB" with:
      |keys                       |values                   |type             |
      |tags                       |TESTING                  |list             |

    Then the response for "findTransactions" should return with:
      |keys                       |values                   |type             |
      |hashes                     |                         |list             |



  Scenario: Revalidate with Local Snapshots enabled
    Check that the revalidate functionality operates as intended with LS enabled.

    Given "checkConsistency" is called on "nodeC" with:
      |keys                       |values                   |type             |
      |tails                      |RESCAN_TEST_TRANSACTIONS |staticValue      |

    Then the response for "checkConsistency" should return with:
      |keys                       |values                   |type             |
      |state                      |False                    |bool             |

    And "getBalances" is called on "nodeC" with:
      |keys                       |values                   |type             |
      |addresses                  |RESCAN_TEST_ADDRESS      |staticValue      |

    Then the response for "getBalances" should return with:
      |keys                       |values                   |type             |
      |balances                   |100                      |int              |


  Scenario: Revalidate with Local Snapshots disabled
  Check that the revalidate functionality operates as intended with LS disabled.

    Given "checkConsistency" is called on "nodeD" with:
      |keys                       |values                   |type             |
      |tails                      |RESCAN_TEST_TRANSACTIONS |staticValue      |

    Then the response for "checkConsistency" should return with:
      |keys                       |values                   |type             |
      |state                      |False                    |bool             |

    And "getBalances" is called on "nodeD" with:
      |keys                       |values                   |type             |
      |addresses                  |RESCAN_TEST_ADDRESS      |staticValue      |

    Then the response for "getBalances" should return with:
      |keys                       |values                   |type             |
      |balances                   |100                      |int              |

