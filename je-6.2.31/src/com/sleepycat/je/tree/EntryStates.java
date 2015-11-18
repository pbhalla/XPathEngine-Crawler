package com.sleepycat.je.tree;

/*
 * The state byte holds knownDeleted state in bit 0 and dirty state in bit
 * 1. Bit flags are used here because of the desire to keep the child
 * reference compact. State is persistent because knownDeleted is
 * persistent, but the dirty bit is cleared when read in from the log.
 *
 * -- KnownDeleted is a way of indicating that the reference is invalid
 * without logging new data. This happens in aborts and recoveries. If
 * knownDeleted is true, this entry is surely deleted. If knownDeleted is
 * false, this entry may or may not be deleted. Future space optimizations:
 * store as a separate bit array in the BIN, or subclass ChildReference and
 * make a special reference only used by BINs and not by INs.
 *
 * -- Dirty is true if the LSN or key has been changed since the last time
 * the owning node was logged. This supports the calculation of BIN deltas.
 */
public class EntryStates {

    /*
     * Note that MIGRATE_BIT is no longer used but is reserved because it was
     * accidentally set on logged INs in the past.
     */
    static final byte KNOWN_DELETED_BIT = 0x1;
    static final byte CLEAR_KNOWN_DELETED_BIT = ~0x1;
    static final byte DIRTY_BIT = 0x2;
    static final byte CLEAR_DIRTY_BIT = ~0x2;
    static final byte MIGRATE_BIT = 0x4; // no longer used, see above
    static final byte CLEAR_MIGRATE_BIT = ~0x4;
    static final byte PENDING_DELETED_BIT = 0x8;
    static final byte CLEAR_PENDING_DELETED_BIT = ~0x8;
}
