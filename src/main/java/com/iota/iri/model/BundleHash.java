package com.iota.iri.model;

/**
 * Creates a <tt>Bundle</tt> hash object
 *
 *  <p>
 *     Bundles are collections of transactions that can be attached to the tangle at once.
 *     When transactions are placed in a bundle, they have the Bundle hash added to them.
 *     This bundle hash can be used to find these transactions and confirm that they were
 *     a part of the same batch of transactions. If a bundle is deemed invalid, all
 *     transactions contained will also be deemed invalid.
 * </p>
 */
public class BundleHash extends AbstractHash {

    /**
     * Constructor for a <tt>Bundle</tt> hash object using a source array and starting point
     *
     * @param bytes The trit or byte array source that the object will be generated from
     * @param offset The starting point in the array for the beginning of the Bundle Hash object
     * @param sizeInBytes The size of the Bundle Hash object that is to be created
     */
    protected BundleHash(byte[] bytes, int offset, int sizeInBytes) {
        super(bytes, offset, sizeInBytes);
    }
}
