package com.iota.iri.service.tipselection.impl;

import java.util.*;

import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.storage.Tangle;

/**
 * Implementation of {@link RatingCalculator} that calculates the cumulative weight 
 * Calculates the weight recursively/on the fly for each transaction referencing {@code entryPoint}. <br>
 * Works using DFS search for new hashes and a BFS calculation. 
 * Uses cached values to prevent double database lookup for approvers
 */
public class CumulativeWeightCalculator implements RatingCalculator {

    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;

    /**
     * Constructor for Cumulative Weight Calculator
     * 
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider accesses ledger's snapshots
     */
    public CumulativeWeightCalculator(Tangle tangle, SnapshotProvider snapshotProvider) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
    }

    @Override
    public List<Hash> calculate(Hash entryPoint) throws Exception {
        List<Hash> hashWeightMap = calculateRatingDfs(entryPoint);
        
        return hashWeightMap;
    }
    
    private List<Hash> calculateRatingDfs(Hash entryPoint) throws Exception {
        Map<Hash, Set<Hash>> txToDirectApprovers = new HashMap<>();
        Deque<Hash> stack = new ArrayDeque<>();

        //A set of tips that will be populated and returned for tip selection
        Set<Hash> tipHashList = new HashSet<>();

        stack.addAll(getTxDirectApproversHashes(entryPoint, txToDirectApprovers));

        while (!stack.isEmpty()) {
            Hash txHash = stack.pollLast();

            Set<Hash> approvers = getTxDirectApproversHashes(txHash, txToDirectApprovers);
            
            // If its empty, its a tip!
            if (approvers.isEmpty()) {
                tipHashList.add(txHash);

            // Else we go deeper
            } else {
                // Add all approvers, given we didnt go there
                for (Hash h : approvers) {
                    if (!tipHashList.contains(h)) {
                        stack.add(h);
                    }
                }
                
                // Add the tx to the approvers list to prevent self-referencing
                approvers.add(txHash);
                
                // calculate and add rating. Naturally the first time all approvers need to be looked up. Then its cached.
                tipHashList.add(txHash);
            } 
        }

        // If we have a self-reference, its already added, otherwise we save a big calculation
        if (!tipHashList.contains(entryPoint)) {
            tipHashList.add(entryPoint);
        }
        return new ArrayList<>(tipHashList);
    }

    /**
     * Gets the rating of a set, calculated by checking its approvers
     * 
     * @param startingSet All approvers of a certain hash, including the hash itself. 
     *                    Should always start with at least 1 hash.
     * @param txToDirectApproversCache The cache of approvers, used to prevent double db lookups
     * @return The weight, or rating, of the starting hash
     * @throws Exception If we can't get the approvers
     */
    private int getRating(Set<Hash> startingSet, Map<Hash, Set<Hash>> txToDirectApproversCache) throws Exception {
        Deque<Hash> stack = new ArrayDeque<>(startingSet);
        while (!stack.isEmpty()) {
            Set<Hash> approvers = getTxDirectApproversHashes(stack.pollLast(), txToDirectApproversCache);
            for (Hash hash : approvers) {
                if (startingSet.add(hash)) {
                    stack.add(hash);
                }
            }
        }

        return startingSet.size();
    }
    
    /**
     * Finds the approvers of a transaction, and adds it to the txToDirectApprovers map if they weren't there yet.
     * 
     * @param txHash The tx we find the approvers of
     * @param txToDirectApprovers The map we look in, and add to
     * @param fallback The map we check in before going in the database, can be <code>null</code>
     * @return A set with the direct approvers of the given hash
     * @throws Exception
     */
    private Set<Hash> getTxDirectApproversHashes(Hash txHash, Map<Hash, Set<Hash>> txToDirectApprovers)
            throws Exception {
        
        Set<Hash> txApprovers = txToDirectApprovers.get(txHash);
        if (txApprovers == null) {
            ApproveeViewModel approvers = ApproveeViewModel.load(tangle, txHash);
            Collection<Hash> appHashes;
            if (approvers == null || approvers.getHashes() == null) {
                appHashes = Collections.emptySet();
            } else {
                appHashes = approvers.getHashes();
            }
            
            txApprovers = new HashSet<>(appHashes.size());
            for (Hash appHash : appHashes) {
                // if not genesis (the tx that confirms itself)
                if (!snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(appHash)) {
                    txApprovers.add(appHash);
                }
            }
            txToDirectApprovers.put(txHash, txApprovers);
        }
        
        return new HashSet<Hash>(txApprovers);
    }
    
    private static Map<Hash, Integer> createTxHashToCumulativeWeightMap(int size) {
        return new HashMap<Hash, Integer>(size); //new TransformingMap<>(size, HashPrefix::createPrefix, null);
    }
}
