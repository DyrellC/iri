package com.iota.iri.service;

import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.iota.iri.controllers.TransactionViewModel.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionSolidifier {

    private static final Logger log = LoggerFactory.getLogger(TransactionSolidifier.class);


    /**
     * If true use {@link #newSolidTransactionsOne} while solidifying. Else use {@link #newSolidTransactionsTwo}.
     */
    private final AtomicBoolean useFirst = new AtomicBoolean(true);
    private final AtomicBoolean useFirstRetry = new AtomicBoolean(true);


    /**
     * Is {@link #newSolidThread} shutting down
     */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);


    private SnapshotProvider snapshotProvider;
    private Tangle tangle;
    private TransactionRequester transactionRequester;
    private TipsViewModel tipsViewModel;

    private final Object cascadeSync = new Object();
    private final Object retrySync = new Object();

    private Set<Hash> newSolidTransactionsOne = new LinkedHashSet<>();
    private Set<Hash> newSolidTransactionsTwo = new LinkedHashSet<>();
    private Set<Hash> transactionsForSolidificationOne = new LinkedHashSet<>();
    private Set<Hash> transactionsForSolidificationTwo = new LinkedHashSet<>();


    public static int SOLID_SLEEP_TIME = 500;
    public static int RESCAN_TIME = 250;

    private static int MAX_PROCESSED_TRANSACTIONS = 5000;

    private Thread newSolidThread;
    private Thread solidificationRetryThread;


    public TransactionSolidifier(Tangle tangle, SnapshotProvider snapshotProvider, TransactionRequester transactionRequester,
                          TipsViewModel tipsViewModel){
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.transactionRequester = transactionRequester;
        this.tipsViewModel = tipsViewModel;
    }

    public void init(){
        newSolidThread = new Thread(spawnSolidTransactionsPropagation(), "Solid TX cascader");
        solidificationRetryThread = new Thread(spawnSolidifyRetry(), "Solidification retry cascader");
        newSolidThread.start();
        solidificationRetryThread.start();
    }


    /**
     * Creates a runnable that runs {@link #propagateSolidTransactions()} in a loop every {@value #SOLID_SLEEP_TIME} ms
     * @return runnable that is not started
     */
    private Runnable spawnSolidTransactionsPropagation() {
        return () -> {
            while(!shuttingDown.get()) {
                propagateSolidTransactions();
                try {
                    Thread.sleep(SOLID_SLEEP_TIME);
                } catch (InterruptedException e) {
                    // Ignoring InterruptedException. Do not use Thread.currentThread().interrupt() here.
                    log.error("Thread was interrupted: ", e);
                }
            }
        };
    }



    private Runnable spawnSolidifyRetry() {
        return () -> {
            while(!shuttingDown.get()) {
                rescan();
                try {
                    Thread.sleep(RESCAN_TIME);
                } catch (InterruptedException e) {
                    // Ignoring InterruptedException. Do not use Thread.currentThread().interrupt() here.
                    log.error("Thread was interrupted: ", e);
                }
            }
        };
    }


    /**
     * Shutdown roots to tip solidification thread
     * @throws InterruptedException
     * @see #spawnSolidTransactionsPropagation()
     */
    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        newSolidThread.join();
        solidificationRetryThread.join();
    }


    void rescan() {
        Set<Hash> transactionsToProcess = new LinkedHashSet<>();
        useFirst.set(!useFirst.get());

        synchronized (retrySync){
            if(useFirstRetry.get()){
                transactionsToProcess.addAll(transactionsForSolidificationOne);
                transactionsForSolidificationOne.clear();
            } else {
                transactionsToProcess.addAll(transactionsForSolidificationTwo);
                transactionsForSolidificationTwo.clear();
            }
        }

        Iterator<Hash> transactionIterator = transactionsToProcess.iterator();
        while(transactionIterator.hasNext() && !shuttingDown.get()) {

            try {
                Hash hash = transactionIterator.next();

                LinkedHashSet<Hash> analyzedHashes = new LinkedHashSet<>(snapshotProvider.getInitialSnapshot().getSolidEntryPoints().keySet());
                if(cascadeSolidityCheck(hash, analyzedHashes, MAX_PROCESSED_TRANSACTIONS)){
                    updateSolidTransactions(tangle, snapshotProvider.getInitialSnapshot(), analyzedHashes);
                }
            } catch (Exception e ){
                //////
                log.error("Error retrying transaction solidification " + e.getMessage());
            }
        }
    }



    public void addSolidTransaction(Hash hash) {
        synchronized (cascadeSync) {
            if (useFirst.get()) {
                newSolidTransactionsOne.add(hash);
            } else {
                newSolidTransactionsTwo.add(hash);
            }
        }
    }


    public void addRetryTransaction(Hash hash) {
        synchronized (retrySync) {
            if (useFirstRetry.get()) {
                transactionsForSolidificationOne.add(hash);
            } else {
                transactionsForSolidificationTwo.add(hash);
            }
        }
    }


    /**
     * Iterates over all currently known solid transactions. For each solid transaction, we find
     * its children (approvers) and try to quickly solidify them with {@link #quietQuickSetSolid}.
     * If we manage to solidify the transactions, we add them to the solidification queue for a traversal by a later run.
     */
    //Package private for testing
    void propagateSolidTransactions() {
        Set<Hash> newSolidHashes = new HashSet<>();
        useFirst.set(!useFirst.get());
        //synchronized to make sure no one is changing the newSolidTransactions collections during addAll
        synchronized (cascadeSync) {
            //We are using a collection that doesn't get updated by other threads
            if (useFirst.get()) {
                newSolidHashes.addAll(newSolidTransactionsTwo);
                newSolidTransactionsTwo.clear();
            } else {
                newSolidHashes.addAll(newSolidTransactionsOne);
                newSolidTransactionsOne.clear();
            }
        }
        Iterator<Hash> cascadeIterator = newSolidHashes.iterator();
        while(cascadeIterator.hasNext() && !shuttingDown.get()) {
            try {
                Hash hash = cascadeIterator.next();
                TransactionViewModel transaction = fromHash(tangle, hash);
                Set<Hash> approvers = transaction.getApprovers(tangle).getHashes();
                for(Hash h: approvers) {
                    TransactionViewModel tx = fromHash(tangle, h);
                    updateTransactionStatus(tx);
                }
            } catch (Exception e) {
                log.error("Error while propagating solidity upwards", e);
            }
        }
    }

    /**
     * Perform a {@link #quickSetSolid} while capturing and logging errors
     * @param transactionViewModel transaction we try to solidify.
     * @return <tt>true</tt> if we managed to solidify, else <tt>false</tt>.
     */
    public boolean quietQuickSetSolid(TransactionViewModel transactionViewModel) {
        try {
            return quickSetSolid(transactionViewModel);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }


    /**
     * Tries to solidify the transactions quickly by performing {@link #checkApproovee} on both parents (trunk and
     * branch). If the parents are solid, mark the transactions as solid.
     * @param transactionViewModel transaction to solidify
     * @return <tt>true</tt> if we made the transaction solid, else <tt>false</tt>.
     * @throws Exception
     */
    private boolean quickSetSolid(final TransactionViewModel transactionViewModel) throws Exception {
        if(!transactionViewModel.isSolid()) {
            boolean solid = true;
            if (!checkApproovee(transactionViewModel.getTrunkTransaction(tangle))) {
                solid = false;
            }
            if (!checkApproovee(transactionViewModel.getBranchTransaction(tangle))) {
                solid = false;
            }
            if(solid) {
                Hash txHash = transactionViewModel.getHash();
                transactionViewModel.updateSolid(true);
                transactionViewModel.updateHeights(tangle, snapshotProvider.getInitialSnapshot());
                transactionViewModel.update(tangle, snapshotProvider.getInitialSnapshot(), "solid|height");
                tipsViewModel.setSolid(txHash);
                addSolidTransaction(txHash);
                return true;
            }
        }
        return false;
    }


    /**
     * If the the {@code approvee} is missing, request it from a neighbor.
     * @param approovee transaction we check.
     * @return true if {@code approvee} is solid.
     * @throws Exception if we encounter an error while requesting a transaction
     */
    private boolean checkApproovee(TransactionViewModel approovee) throws Exception {
        if(snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(approovee.getHash())) {
            return true;
        }
        if(approovee.getType() == PREFILLED_SLOT) {
            // don't solidify from the bottom until cuckoo filters can identify where we deleted -> otherwise we will
            // continue requesting old transactions forever
            //transactionRequester.requestTransaction(approovee.getHash(), false);
            return false;
        }
        return approovee.isSolid();
    }


    public boolean cascadeSolidityCheck(Hash txHash, LinkedHashSet<Hash> analyzedHashes, Integer maxProcessedTransactions) throws Exception {
        if(maxProcessedTransactions != Integer.MAX_VALUE) {
            maxProcessedTransactions += analyzedHashes.size();
        }
        boolean solid = true;
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(txHash));
        Hash hashPointer;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            if (analyzedHashes.add(hashPointer)) {
                continue;
            }

            if(analyzedHashes.size() >= maxProcessedTransactions) {
                    return false;
            }

                final TransactionViewModel transaction = fromHash(tangle, hashPointer);
                if(!transaction.isSolid() && !snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(hashPointer)) {
                    if (transaction.getType() == PREFILLED_SLOT) {
                        solid = false;

                        if (!transactionRequester.isTransactionRequested(hashPointer)) {
                            transactionRequester.requestTransaction(hashPointer);
                            break;
                        }
                    } else {
                        nonAnalyzedTransactions.offer(transaction.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transaction.getBranchTransactionHash());
                    }
                }
            }

        return solid;
    }


    public void updateTransactionStatus(TransactionViewModel tx) throws Exception {
        if(quietQuickSetSolid(tx)) {
            tx.update(tangle, snapshotProvider.getInitialSnapshot(), "solid|height");
            tipsViewModel.setSolid(tx.getHash());
            addSolidTransaction(tx.getHash());
        } else {
            addRetryTransaction(tx.getHash());
        }
    }

}
