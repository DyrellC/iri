package com.iota.iri;

import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.conf.ProtocolConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotMockUtils;
import com.iota.iri.service.validation.TransactionSolidifier;
import com.iota.iri.service.validation.TransactionValidator;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static com.iota.iri.TransactionTestUtils.getTransactionHash;
import static com.iota.iri.TransactionTestUtils.getTransactionTrits;
import static com.iota.iri.TransactionTestUtils.getTransactionTritsWithTrunkAndBranch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionValidatorTest {

  private static final int MAINNET_MWM = 14;
  private static final TemporaryFolder dbFolder = new TemporaryFolder();
  private static final TemporaryFolder logFolder = new TemporaryFolder();
  private static Tangle tangle;
  private static TransactionValidator txValidator;

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  private static SnapshotProvider snapshotProvider;

  @Mock
  private static TransactionSolidifier txSolidifier;

  @Mock
  private static TransactionRequester txRequester;

  @BeforeClass
  public static void setUp() throws Exception {
    dbFolder.create();
    logFolder.create();
    tangle = new Tangle();
    tangle.addPersistenceProvider(
        new RocksDBPersistenceProvider(
            dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
    tangle.init();
  }

  @AfterClass
  public static void tearDown() throws Exception {
    tangle.shutdown();
    dbFolder.delete();
    logFolder.delete();
  }

  @Before
  public void setUpEach() {
    when(snapshotProvider.getInitialSnapshot()).thenReturn(SnapshotMockUtils.createSnapshot());
    TipsViewModel tipsViewModel = new TipsViewModel();
    TransactionRequester txRequester = new TransactionRequester(tangle, snapshotProvider);
    txValidator = new TransactionValidator(tangle, snapshotProvider, tipsViewModel, txRequester, new MainnetConfig(), txSolidifier);
    txValidator.setMwm(false, MAINNET_MWM);
    txValidator.init();
  }

  @Test
  public void testMinMwm() {
    ProtocolConfig protocolConfig = mock(ProtocolConfig.class);
    when(protocolConfig.getMwm()).thenReturn(5);
    TransactionValidator transactionValidator = new TransactionValidator(null, null, null, null, protocolConfig, txSolidifier);
    assertEquals("Expected testnet minimum minWeightMagnitude", 13, transactionValidator.getMinWeightMagnitude());
  }

  @Test
  public void validateTrits() {
    byte[] trits = getTransactionTrits();
    Converter.copyTrits(0, trits, 0, trits.length);
    txValidator.validateTrits(trits, MAINNET_MWM);
  }

  @Test(expected = RuntimeException.class)
  public void validateTritsWithInvalidMetadata() {
    byte[] trits = getTransactionTrits();
    txValidator.validateTrits(trits, MAINNET_MWM);
  }

  @Test
  public void validateBytesWithNewCurl() {
    byte[] trits = getTransactionTrits();
    Converter.copyTrits(0, trits, 0, trits.length);
    byte[] bytes = Converter.allocateBytesForTrits(trits.length);
    Converter.bytes(trits, 0, bytes, 0, trits.length);
    txValidator.validateBytes(bytes, txValidator.getMinWeightMagnitude(), SpongeFactory.create(SpongeFactory.Mode.CURLP81));
  }


  @Test
  public void addSolidTransactionWithoutErrors() {
    byte[] trits = getTransactionTrits();
    Converter.copyTrits(0, trits, 0, trits.length);
    txValidator.addSolidTransaction(TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
  }



    @Test
    public void testTransactionPropagation() throws Exception {
        TransactionViewModel leftChildLeaf = TransactionTestUtils.createTransactionWithTrytes("CHILDTX");
        leftChildLeaf.updateSolid(true);
        leftChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel rightChildLeaf = TransactionTestUtils.createTransactionWithTrytes("CHILDTWOTX");
        rightChildLeaf.updateSolid(true);
        rightChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel parent = TransactionTestUtils.createTransactionWithTrunkAndBranch("PARENT",
                leftChildLeaf.getHash(), rightChildLeaf.getHash());
        parent.updateSolid(false);
        parent.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel parentSibling = TransactionTestUtils.createTransactionWithTrytes("PARENTLEAF");
        parentSibling.updateSolid(true);
        parentSibling.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel grandParent = TransactionTestUtils.createTransactionWithTrunkAndBranch("GRANDPARENT", parent.getHash(),
                        parentSibling.getHash());
        grandParent.updateSolid(false);
        grandParent.store(tangle, snapshotProvider.getInitialSnapshot());

        txValidator.addSolidTransaction(leftChildLeaf.getHash());
        while (!txValidator.isNewSolidTxSetsEmpty()) {
            txValidator.propagateSolidTransactions();
        }

        parent = TransactionViewModel.fromHash(tangle, parent.getHash());
        assertTrue("Parent tx was expected to be solid", parent.isSolid());
        grandParent = TransactionViewModel.fromHash(tangle, grandParent.getHash());
        assertTrue("Grandparent  was expected to be solid", grandParent.isSolid());
    }

  @Test
  public void testTransactionPropagationFailure() throws Exception {
    TransactionViewModel leftChildLeaf = new TransactionViewModel(getTransactionTrits(), getTransactionHash());
    leftChildLeaf.updateSolid(true);
    leftChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

    TransactionViewModel rightChildLeaf = new TransactionViewModel(getTransactionTrits(), getTransactionHash());
    rightChildLeaf.updateSolid(true);
    rightChildLeaf.store(tangle, snapshotProvider.getInitialSnapshot());

    TransactionViewModel parent = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(leftChildLeaf.getHash(),
            rightChildLeaf.getHash()), getTransactionHash());
    parent.updateSolid(false);
    parent.store(tangle, snapshotProvider.getInitialSnapshot());

    TransactionViewModel parentSibling = new TransactionViewModel(getTransactionTrits(), getTransactionHash());
    parentSibling.updateSolid(false);
    parentSibling.store(tangle, snapshotProvider.getInitialSnapshot());

    TransactionViewModel grandParent = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(parent.getHash(),
            parentSibling.getHash()), getTransactionHash());
    grandParent.updateSolid(false);
    grandParent.store(tangle, snapshotProvider.getInitialSnapshot());

    txValidator.addSolidTransaction(leftChildLeaf.getHash());
    while (!txValidator.isNewSolidTxSetsEmpty()) {
      txValidator.propagateSolidTransactions();
    }

    parent = TransactionViewModel.fromHash(tangle, parent.getHash());
    assertTrue("Parent tx was expected to be solid", parent.isSolid());
    grandParent = TransactionViewModel.fromHash(tangle, grandParent.getHash());
    assertFalse("GrandParent tx was expected to be not solid", grandParent.isSolid());
  }


}
