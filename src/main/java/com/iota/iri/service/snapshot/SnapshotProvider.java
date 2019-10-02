package com.iota.iri.service.snapshot;

import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.storage.PersistenceProvider;

/**
 * The data provider that allows to retrieve the {@link Snapshot} instances that are relevant for the node.
 */
public interface SnapshotProvider {
    /**
     * Returns the Snapshot that reflects the start of the ledger (either the last global or a local {@link Snapshot}).
     *
     * This Snapshot will be modified whenever a new local {@link Snapshot} is taken.
     *
     * @return the Snapshot that reflects the start of the ledger (either the last global or a local {@link Snapshot})
     */
    Snapshot getInitialSnapshot();

    /**
     * Returns the Snapshot that reflects the most recent "confirmed" state of the ledger.
     *
     * This Snapshot will be modified whenever a new milestone arrives and new transactions are confirmed.
     *
     * @return the Snapshot that represents the most recent "confirmed" state of the ledger
     */
    Snapshot getLatestSnapshot();

    /**
     * This method dumps the whole snapshot to the hard disk.
     *
     * It is used to persist the in memory state of the snapshot and allow IRI to resume from the local snapshot after
     * restarts.
     *
     * Note: This method writes two files - the meta data file and the state file. The path of the corresponding file is
     *       determined by appending ".snapshot.meta" / ".snapshot.state" to the given base path.
     *
     * @param snapshot the {@link Snapshot} that shall be persisted
     * @param basePath base path of the local snapshot files
     * @throws SnapshotException if anything goes wrong while writing the file
     */
    void writeSnapshotToDisk(Snapshot snapshot, String basePath) throws SnapshotException;

    /**
     * Removes an existing old local snapshot and persists the newly given one.
     *
     * @param snapshot the snapshot to persist
     * @throws SnapshotException if anything goes wrong while deleting or persisting data
     */
    void persistSnapshot(Snapshot snapshot) throws SnapshotException;

    /**
     * Frees the resources of the {@link SnapshotProvider}.
     *
     * Snapshots require quite a bit of memory and should be cleaned up when they are not required anymore. This is
     * particularly important for unit tests, that create a separate instance of the {@link SnapshotProvider}.
     */
    void shutdown();

    /**
     * This method initializes the instance by loading the snapshots.
     *
     * @throws SnapshotException if anything goes wrong while trying to read the snapshots
     */
    void init(PersistenceProvider localSnapshotsDb) throws SnapshotException, SpentAddressesException;
}
