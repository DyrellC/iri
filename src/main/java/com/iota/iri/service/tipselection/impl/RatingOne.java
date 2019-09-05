package com.iota.iri.service.tipselection.impl;

import java.util.*;

import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.storage.Tangle;

/**
 * Implementation of <tt>RatingCalculator</tt> that gives a uniform rating of 1 to each transaction.
 * Used to create uniform random walks.
 */
public class RatingOne implements RatingCalculator {

    private final Tangle tangle;

    public RatingOne(Tangle tangle) {
        this.tangle = tangle;
    }

    @Override
    public List<Hash> calculate(Hash entryPoint) throws Exception {
        Queue<Hash> queue = new LinkedList<>();
        Set<Hash> txsForward = new HashSet<>();
        queue.add(entryPoint);
        txsForward.add(entryPoint);
        Hash hash;

        //traverse all transactions that reference entryPoint
        while ((hash = queue.poll()) != null) {
            Set<Hash> approvers = ApproveeViewModel.load(tangle, hash).getHashes();
            for (Hash tx : approvers) {
                if (!txsForward.contains(tx)) {
                    //add to rating w/ value "1"
                    txsForward.add(tx);
                    queue.add(tx);
                }
            }
        }
        return new ArrayList<>(txsForward);
    }


}
