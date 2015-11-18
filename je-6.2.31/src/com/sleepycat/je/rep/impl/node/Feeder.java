/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */

package com.sleepycat.je.rep.impl.node;

import static com.sleepycat.je.rep.impl.node.FeederManagerStatDefinition.N_MAX_REPLICA_LAG;
import static com.sleepycat.je.rep.impl.node.FeederManagerStatDefinition.N_MAX_REPLICA_LAG_NAME;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability.SyncPolicy;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.JEVersion;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvironmentFailureReason;
import com.sleepycat.je.log.ChecksumException;
import com.sleepycat.je.rep.impl.RepImpl;
import com.sleepycat.je.rep.impl.RepNodeImpl;
import com.sleepycat.je.rep.impl.RepParams;
import com.sleepycat.je.rep.impl.node.MasterTransfer.VLSNProgress;
import com.sleepycat.je.rep.net.DataChannel;
import com.sleepycat.je.rep.stream.FeederReplicaHandshake;
import com.sleepycat.je.rep.stream.FeederReplicaSyncup;
import com.sleepycat.je.rep.stream.FeederReplicaSyncup.NetworkRestoreException;
import com.sleepycat.je.rep.stream.FeederSource;
import com.sleepycat.je.rep.stream.MasterFeederSource;
import com.sleepycat.je.rep.stream.MasterStatus;
import com.sleepycat.je.rep.stream.MasterStatus.MasterSyncException;
import com.sleepycat.je.rep.stream.OutputWireRecord;
import com.sleepycat.je.rep.stream.Protocol;
import com.sleepycat.je.rep.stream.Protocol.Ack;
import com.sleepycat.je.rep.stream.Protocol.Commit;
import com.sleepycat.je.rep.stream.Protocol.GroupAck;
import com.sleepycat.je.rep.stream.Protocol.HeartbeatResponse;
import com.sleepycat.je.rep.txn.MasterTxn;
import com.sleepycat.je.rep.utilint.BinaryProtocol.Message;
import com.sleepycat.je.rep.utilint.BinaryProtocolStatDefinition;
import com.sleepycat.je.rep.utilint.LongMaxZeroStat;
import com.sleepycat.je.rep.utilint.NamedChannel;
import com.sleepycat.je.rep.utilint.NamedChannelWithTimeout;
import com.sleepycat.je.rep.utilint.RepUtils;
import com.sleepycat.je.rep.vlsn.VLSNIndex;
import com.sleepycat.je.rep.vlsn.VLSNRange;
import com.sleepycat.je.utilint.LoggerUtils;
import com.sleepycat.je.utilint.StatGroup;
import com.sleepycat.je.utilint.StoppableThread;
import com.sleepycat.je.utilint.StringStat;
import com.sleepycat.je.utilint.TestHook;
import com.sleepycat.je.utilint.TestHookExecute;
import com.sleepycat.je.utilint.VLSN;

/**
 * There is an instance of a Feeder for each client that needs a replication
 * stream. Either a master, or replica (providing feeder services) may
 * establish a feeder.
 *
 * A feeder is created in response to a request from a Replica, and is shutdown
 * either upon loss of connectivity, or upon a change in mastership.
 *
 * The protocol used to validate and negotiate a connection is synchronous, but
 * once this phase has been completed, the communication between the feeder and
 * replica is asynchronous. To handle the async communications, the feeder has
 * two threads associated with it:
 *
 * 1) An output thread whose sole purpose is to pump log records (and if
 * necessary heart beat requests) down to the replica as fast as the network
 * will allow it
 *
 * 2) An input thread that listens for responses to transaction commits and
 * heart beat responses.
 */
final public class Feeder {
    /*
     * A heartbeat is written with this period by the feeder output thread.
     * Is mutable.
     */
    private int heartbeatMs;

    /* The manager for all Feeder instances. */
    private final FeederManager feederManager;

    /* The replication node that is associated with this Feeder */
    private final RepNode repNode;
    /* The RepImpl that is associated with this rep node. */
    private final RepImpl repImpl;

    /* The socket on which the feeder communicates with the Replica. */
    private final NamedChannelWithTimeout feederReplicaChannel;

    /* The Threads that implement the Feeder */
    private final InputThread inputThread;
    private final OutputThread outputThread;

    /* The source of log records to be sent to the Replica. */
    private final FeederSource feederSource;

    /* Negotiated message protocol version for the replication stream. */
    private int protocolVersion;

    /**
     * The current position of the feeder, that is, the log record with this
     * VLSN will be sent next to the Replica. Note that this does not mean that
     * the replica has actually processed all log records preceding feederVLSN.
     * The records immediately preceding feederVLSN (down to replicaAckVLSN)
     * may be in the network, in transit to the replica.
     */
    private VLSN feederVLSN;

    /**
     * The latest commit or abort that the replica has reported receiving,
     * either by ack (in the case of a commit), or via heartbeat response.  It
     * serves as a rough indication of the replay state of the replica that is
     * used in exception messages.
     *
     * The following invariant must always hold: replicaTxnEndLSN < feederVLSN
     */
    private volatile VLSN replicaTxnEndVLSN = VLSN.NULL_VLSN;

    /* The time that the feeder last heard from its Replica */
    @SuppressWarnings("unused")
    private volatile long lastResponseTime = 0l;

    /*
     * Used to communicate our progress when getting ready for a Master
     * Transfer operation.
     */
    private volatile MasterTransfer masterXfr;
    private volatile boolean caughtUp = false;

    /* Used to track the status of the master. */
    private final MasterStatus masterStatus;

    /*
     * Determines whether the Feeder has been shutdown. Usually this is held
     * within the StoppableThread, but the Feeder's two child threads have
     * their shutdown coordinated by the parent Feeder.
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final Logger logger;

    /* The Feeder's node ID. */
    private final NameIdPair nameIdPair;

    /*
     * The replica node ID, that is, the node that is the recipient of the
     * replication stream. Its established at the time of the Feeder/Replica
     * handshake.
     */
    private NameIdPair replicaNameIdPair = NameIdPair.NULL;

    /**
     * The agreed upon log format that should be used for writing log entries
     * to send to the replica, or zero if not yet known.
     */
    private volatile int replicaLogVersion = 0;

    /** The JE version of the replica, or null if not known. */
    private volatile JEVersion replicaJEVersion = null;

    /** The RepNodeImpl of the replica, or null if not known. */
    private volatile RepNodeImpl replicaNode = null;


    /**
     * A test hook that is called before a message is written.  Note that the
     * hook is inherited by the ReplicaFeederHandshake, and will be kept in
     * place there for the entire handshake.
     */
    private volatile TestHook<Message> writeMessageHook;

    /**
     * A test hook that is used to set the writeMessageHook for newly created
     * feeders.
     */
    private static volatile TestHook<Message> initialWriteMessageHook;

    /**
     * Returns a configured DataChannel
     *
     * @param channel the channel to be configured
     * @return the configured DataChannel
     * @throws IOException
     */
    private NamedChannelWithTimeout configureChannel(DataChannel channel)
        throws IOException {

        try {
            channel.getSocketChannel().configureBlocking(true);
            LoggerUtils.info
                (logger, repImpl, "Feeder accepted connection from " + channel);
            final int timeoutMs = repNode.getConfigManager().
                getDuration(RepParams.PRE_HEARTBEAT_TIMEOUT);
            final boolean tcpNoDelay = repNode.getConfigManager().
                    getBoolean(RepParams.FEEDER_TCP_NO_DELAY);

            /* Set use of Nagle's algorithm on the socket. */
            channel.getSocketChannel().socket().setTcpNoDelay(tcpNoDelay);
            return new NamedChannelWithTimeout(repNode, channel, timeoutMs);
        } catch (IOException e) {
            LoggerUtils.warning(logger, repImpl,
                                "IO exception while configuring channel " +
                                "Exception:" + e.getMessage());
            throw e;
        }
    }

    Feeder(FeederManager feederManager, DataChannel dataChannel)
        throws DatabaseException, IOException {

        this.feederManager = feederManager;
        this.repNode = feederManager.repNode();
        this.repImpl = repNode.getRepImpl();
        this.masterStatus = repNode.getMasterStatus();
        nameIdPair = repNode.getNameIdPair();
        this.feederSource = new MasterFeederSource(repImpl,
                                                   repNode.getVLSNIndex(),
                                                   nameIdPair);
        logger = LoggerUtils.getLogger(getClass());

        this.feederReplicaChannel = configureChannel(dataChannel);
        inputThread = new InputThread();
        outputThread = new OutputThread();
        heartbeatMs = feederManager.repNode().getHeartbeatInterval();
        writeMessageHook = initialWriteMessageHook;
    }

    void startFeederThreads() {
        inputThread.start();
    }

    /**
     * @hidden
     * Place holder Feeder for testing only
     */
    public Feeder() {
        feederManager = null;
        repNode = null;
        repImpl = null;
        masterStatus = null;
        feederSource = null;
        feederReplicaChannel = null;
        nameIdPair = NameIdPair.NULL;
        logger = LoggerUtils.getLoggerFixedPrefix(getClass(), "TestFeeder");
        inputThread = null;
        outputThread = null;
        shutdown.set(true);
        writeMessageHook = initialWriteMessageHook;
    }

    /* Get the protocol stats of this Feeder. */
    public StatGroup getProtocolStats(StatsConfig config) {
        final Protocol protocol = outputThread.protocol;

        return (protocol != null) ?
               protocol.getStats(config) :
               new StatGroup(BinaryProtocolStatDefinition.GROUP_NAME,
                             BinaryProtocolStatDefinition.GROUP_DESC);
    }

    void resetStats() {
        final Protocol protocol = outputThread.protocol;
        if (protocol != null) {
            protocol.resetStats();
        }
    }

    void setMasterTransfer(MasterTransfer mt) {
        masterXfr = mt;
        if (caughtUp) {
            adviseMasterTransferProgress();
        }
    }

    void adviseMasterTransferProgress() {
        MasterTransfer mt = masterXfr;
        if (mt != null) {
            mt.noteProgress
                (new VLSNProgress(replicaTxnEndVLSN,
                                  replicaNameIdPair.getName()));
        }
    }

    public RepNode getRepNode() {
        return repNode;
    }

    public NameIdPair getReplicaNameIdPair() {
        return replicaNameIdPair;
    }

    /**
     * Returns the latest commit VLSN that was acked by the replica, or
     * NULL_VLSN if no commit was acked since the time the feeder was
     * established.
     */
    public VLSN getReplicaTxnEndVLSN() {
        return replicaTxnEndVLSN;
    }

    /**
     * Returns the JE version supported by the replica, or {@code null} if the
     * value is not yet known.
     *
     * @return the replica JE version or {@code null}
     */
    public JEVersion getReplicaJEVersion() {
        return replicaJEVersion;
    }

    /**
     * Returns a RepNodeImpl that describes the replica, or {@code null} if the
     * value is not yet known.  The value will be non-null if the feeder
     * handshake has completed successfully.
     *
     * @return the replica node or {@code null}
     */
    public RepNodeImpl getReplicaNode() {
        return replicaNode;
    }

    /**
     * Shutdown the feeder, closing its channel and releasing its threads.  May
     * be called internally upon noticing a problem, or externally when the
     * RepNode is shutting down.
     */
    void shutdown(Exception shutdownException) {

        boolean changed = shutdown.compareAndSet(false, true);
        if (!changed) {
            return;
        }

        MasterTransfer mt = masterXfr;
        if (mt != null) {
            mt.giveUp(replicaNameIdPair.getName());
        }
        feederManager.removeFeeder(this);

        StatGroup pstats = (inputThread.protocol != null) ?
            inputThread.protocol.getStats(StatsConfig.DEFAULT) :
            new StatGroup(BinaryProtocolStatDefinition.GROUP_NAME,
                          BinaryProtocolStatDefinition.GROUP_DESC);
        if (outputThread.protocol != null) {
            pstats.addAll(outputThread.protocol.getStats(StatsConfig.DEFAULT));
        }
        feederManager.incStats(pstats);

        LoggerUtils.info(logger, repImpl,
                         "Shutting down feeder for replica " +
                         replicaNameIdPair.getName() +
                         ((shutdownException == null) ?
                          "" :
                          (" Reason: " + shutdownException.getMessage())) +
                         RepUtils.writeTimesString(pstats));

        if (repNode.getReplicaCloseCatchupMs() >= 0) {

            /*
             * Need to shutdown the group cleanly, wait for it to let the
             * replica catchup and exit in the allowed time period.
             */
            try {

                /*
                 * Note that we wait on the Input thread, since it's the one
                 * that will exit on the ShutdownResponse message from the
                 * Replica. The output thread will exit immediately after
                 * sending the ShutdownRequest.
                 */
                inputThread.join();
                /* Timed out, or the input thread exited; keep going. */
            } catch (InterruptedException e) {
                LoggerUtils.warning(logger, repImpl,
                                    "Interrupted while waiting to join " +
                                    "thread:" + outputThread);
            }
        }

        outputThread.shutdownThread(logger);
        inputThread.shutdownThread(logger);

        LoggerUtils.finest(logger, repImpl,
                           feederReplicaChannel + " isOpen=" +
                           feederReplicaChannel.getChannel().isOpen());
    }

    public boolean isShutdown() {
        return shutdown.get();
    }

    /**
     * Implements the thread responsible for processing the responses from a
     * Replica.
     */
    private class InputThread extends StoppableThread {

        Protocol protocol = null;
        private LocalCBVLSNUpdater replicaCBVLSN;

        InputThread() {
            /*
             * The thread will be renamed later on during the life of this
             * thread, when we're sure who the replica is.
             */
            super(repImpl, new IOThreadsHandler(), "Feeder Input");
        }

        /**
         * Does the initial negotiation to validate replication group wide
         * consistency and establish the starting VLSN. It then starts up the
         * Output thread and enters the response loop.
         */
        @Override
        public void run() {

            /* Set to indicate an error-initiated shutdown. */
            Error feederInputError = null;
            Exception shutdownException = null;

            try {
                FeederReplicaHandshake handshake =
                    new FeederReplicaHandshake(repNode,
                                               Feeder.this,
                                               feederReplicaChannel);
                protocol = handshake.execute();
                protocolVersion = protocol.getVersion();
                replicaNameIdPair = handshake.getReplicaNameIdPair();
                replicaLogVersion = handshake.getReplicaLogVersion();
                replicaJEVersion = handshake.getReplicaJEVersion();
                replicaNode = handshake.getReplicaNode();

                /*
                 * Rename the thread when we get the replica name in, so that
                 * it's clear who is on the other end.
                 */
                Thread.currentThread().setName("Feeder Input for " +
                                               replicaNameIdPair.getName());

                FeederReplicaSyncup syncup =
                    new FeederReplicaSyncup(Feeder.this,
                                            feederReplicaChannel,
                                            protocol);

                /*
                 * The replicaCBVLSN can only be instantiated after we know the
                 * replica's name. We need to pass the updater into
                 * FeederReplicaSyncup so that the replica's local CBVLSN can
                 * be updated while the global CBVLSN update is locked out.
                 */
                this.replicaCBVLSN = new LocalCBVLSNUpdater(
                    replicaNameIdPair, replicaNode.getType(), repNode);

                /*
                 * Sync-up produces the VLSN of the next log record needed by
                 * the replica, one beyond the last commit or abort it already
                 * has.
                 */
                VLSN startVLSN = syncup.execute(replicaCBVLSN);
                replicaTxnEndVLSN = startVLSN.getPrev();
                if (replicaTxnEndVLSN.compareTo
                    (repNode.getCurrentTxnEndVLSN()) >= 0) {
                    caughtUp = true;
                }
                feederVLSN = startVLSN;
                feederSource.init(feederVLSN);

                /* Start the thread to pump out log records */
                outputThread.start();
                lastResponseTime = System.currentTimeMillis();
                masterStatus.assertSync();
                feederManager.activateFeeder(Feeder.this);

                runResponseLoop();
            } catch (NetworkRestoreException e) {
                shutdownException = e;
                /* The replica will retry after a network restore. */
                LoggerUtils.info(logger, repImpl, e.getMessage());
            } catch (IOException e) {
                /* Trio of benign "expected" exceptions below. */
                shutdownException = e; /* Expected. */
            } catch (MasterSyncException e) {
                shutdownException = e; /* Expected. */
            } catch (InterruptedException e) {
                shutdownException = e; /* Expected. */
            } catch (ExitException e) {
                shutdownException = e;
                LoggerUtils.warning(logger, repImpl,
                                    "Exiting feeder loop: " + e.getMessage());
            } catch (Error e) {
                feederInputError = e;
                repNode.getRepImpl().invalidate(e);
            } catch (ChecksumException e) {
                shutdownException = e;

                /* An internal, unexpected error. Invalidate the environment. */
                throw new EnvironmentFailureException
                    (repNode.getRepImpl(),
                     EnvironmentFailureReason.LOG_CHECKSUM, e);
            } catch (RuntimeException e) {
                shutdownException = e;

                /*
                 * An internal error. Shut down the rep node as well for now
                 * by throwing the exception out of the thread.
                 *
                 * In future we may want to close down just the impacted Feeder
                 * but this is the safe course of action.
                 */
                LoggerUtils.severe(logger, repImpl,
                                   "Unexpected exception: " + e.getMessage() +
                                   LoggerUtils.getStackTrace(e));
                throw e;
            } finally {
                if (feederInputError != null) {
                    /* Propagate the error, skip cleanup. */
                    throw feederInputError;
                }

                /*
                 * Shutdown the feeder in its entirety, in case the input
                 * thread is the only one to notice a problem. The Replica can
                 * decide to re-establish the connection
                 */
                shutdown(shutdownException);
                cleanup();
            }
        }

        /*
         * This method deals with responses from the Replica. There are exactly
         * two types of responses from the Replica:
         *
         * 1) Responses acknowledging a successful commit by the Replica.
         *
         * 2) Responses to heart beat messages.
         *
         * This loop (like the loop in the OutputThread) is terminated under
         * one of the following conditions:
         *
         * 1) The thread detects a change in masters.
         * 2) There is network connection issue (which might also be an
         *    indication of an unfolding change in masters).
         * 3) If the replica closes its connection -- variation of the above.
         *
         * In addition, the loop will also exit if it gets a ShutdownResponse
         * message sent in response to a ShutdownRequest sent by the
         * OutputThread.
         */
        private void runResponseLoop()
            throws IOException, MasterSyncException {

            /*
             * Start the acknowledgment loop. It's very important that this
             * loop be wait/contention free.
             */
            while (!checkShutdown()) {
                Message response = protocol.read(feederReplicaChannel);
                if (checkShutdown()) {

                    /*
                     * Shutdown quickly, in particular, don't update sync
                     * VLSNs.
                     */
                    break;
                }
                masterStatus.assertSync();

                lastResponseTime = System.currentTimeMillis();

                if (response.getOp() == Protocol.HEARTBEAT_RESPONSE) {
                    processHeartbeatResponse(response);
                } else if (response.getOp() == Protocol.ACK) {

                    /*
                     * Check if a commit has been waiting for this
                     * acknowledgment and signal any waiters.
                     */
                    long txnId = ((Ack) response).getTxnId();
                    if (logger.isLoggable(Level.FINE)) {
                        LoggerUtils.fine(logger, repImpl, "Ack for: " + txnId);
                    }
                    deemAcked(txnId);
                }  else if (response.getOp() == Protocol.GROUP_ACK) {
                    final long txnIds[] = ((GroupAck) response).getTxnIds();

                    for (long txnId : txnIds) {
                        if (logger.isLoggable(Level.FINE)) {
                            LoggerUtils.fine(logger, repImpl,
                                             "Group Ack for: " + txnId);
                        }
                        deemAcked(txnId);
                    }
                } else if (response.getOp() == Protocol.SHUTDOWN_RESPONSE) {
                    LoggerUtils.info(logger, repImpl,
                                     "Shutdown confirmed by replica " +
                                     replicaNameIdPair.getName());
                    /* Exit the loop and the thread. */
                    break;
                } else {
                    throw EnvironmentFailureException.unexpectedState
                        ("Unexpected message: " + response);
                }
            }
        }

        private void processHeartbeatResponse(Message response) {
            /* Last response has been updated, keep going. */
            HeartbeatResponse hbResponse =
                (Protocol.HeartbeatResponse)response;
            replicaCBVLSN.updateForReplica(hbResponse);
            VLSN reportedVLSN = hbResponse.getTxnEndVLSN();
            if (reportedVLSN != null) {
                replicaTxnEndVLSN = reportedVLSN;
                if (replicaTxnEndVLSN.compareTo
                    (repNode.getCurrentTxnEndVLSN()) >= 0) {
                    caughtUp = true;
                    adviseMasterTransferProgress();
                }
            }
        }

        /*
         * Returns true if the InputThread should be shutdown, that is, if the
         * thread has been marked for shutdown and it's not a group shutdown
         * request. For a group shutdown the input thread will wait for an
         * acknowledgment of the shutdown message from the Replica.
         */
        private boolean checkShutdown() {
            return shutdown.get() &&
                   (repNode.getReplicaCloseCatchupMs() < 0);
        }

        @Override
        protected int initiateSoftShutdown() {

            /*
             * Provoke an I/O exception that will cause the input thread to
             * exit.
             */
            RepUtils.shutdownChannel(feederReplicaChannel);
            return repNode.getThreadWaitInterval();
        }

        @Override
        protected Logger getLogger() {
            return logger;
        }
    }

    /**
     * Simply pumps out log entries as rapidly as it can.
     */
    private class OutputThread extends StoppableThread {
        /* Tracks when the last heartbeat was sent. */
        private long lastHeartbeat = 0l;

        Protocol protocol = null;

        private long totalTransferDelay = 0;

        /* The time at which the group shutdown was initiated. */
        private long shutdownRequestStart = 0;

        /**
         * Determines whether writing to the network connection for the replica
         * suffices as a commit acknowledgment.
         */
        private final boolean commitToNetwork;

        /**
         * The threshold used to trigger the logging of transfers of commit
         * records.
         */
        private final int transferLoggingThresholdMs;

        /**
         * The max time interval during which feeder records are grouped.
         */
        private final int batchNs;

        /**
         * The direct byte buffer holding the batched feeder records.
         */
        private final ByteBuffer batchBuff;

        /* Shared stats used to track max replica lag across all feeders. */
        private final LongMaxZeroStat nMaxReplicaLag;
        private final StringStat nMaxReplicaLagName;

        private final VLSNIndex vlsnIndex;

        /*
         * The delay between writes of a replication message. Note that
         * setting this to a non-zero value effectively turns off message
         * batching.
         */
        final int testDelayMs;

        OutputThread() {
            /*
             * The thread will be renamed later on during the life of this
             * thread, when we know who the replica is.
             */
            super(repImpl, new IOThreadsHandler(), "Feeder Output");
            final DbConfigManager configManager = repNode.getConfigManager();
            commitToNetwork  = configManager.
                getBoolean(RepParams.COMMIT_TO_NETWORK);
            transferLoggingThresholdMs = configManager.
                getDuration(RepParams.TRANSFER_LOGGING_THRESHOLD);

            batchNs = Math.min(configManager.
                               getInt(RepParams.FEEDER_BATCH_NS),
                               heartbeatMs * 1000000);

            final int batchBuffSize = configManager.
                getInt(RepParams.FEEDER_BATCH_BUFF_KB) * 1024;

            batchBuff = ByteBuffer.allocateDirect(batchBuffSize);

            if (feederManager != null) {
                nMaxReplicaLag = feederManager.getnMaxReplicaLag();
                nMaxReplicaLagName = feederManager.getnMaxReplicaLagName();
            } else {
                /* Create a placeholder stat for testing. */
                StatGroup stats =
                    new StatGroup(FeederManagerStatDefinition.GROUP_NAME,
                                  FeederManagerStatDefinition.GROUP_DESC);
                nMaxReplicaLag = new LongMaxZeroStat(stats, N_MAX_REPLICA_LAG);
                nMaxReplicaLagName = new StringStat(stats,
                                                    N_MAX_REPLICA_LAG_NAME);
            }

            testDelayMs = feederManager.getTestDelayMs();
            if (testDelayMs > 0) {
                LoggerUtils.info(logger, repImpl,
                                 "Test delay of:" + testDelayMs + "ms." +
                                 " after each message sent");
            }
            vlsnIndex = repNode.getVLSNIndex();
        }

        /**
         * Determines whether we should exit the output loop. If we are trying
         * to shutdown the Replica cleanly, that is, this is a group shutdown,
         * the method delays the shutdown until the Replica has had a chance
         * to catch up to the current commit VLSN on this node, after which
         * it sends the Replica a Shutdown message.
         *
         * @return true if the output thread should be shutdown.
         *
         * @throws IOException
         */
        private boolean checkShutdown()
            throws IOException {

            if (!shutdown.get()) {
                return false;
            }
            if (repNode.getReplicaCloseCatchupMs() >= 0) {
                if (shutdownRequestStart == 0) {
                    shutdownRequestStart = System.currentTimeMillis();
                }
                /* Determines if the feeder has waited long enough. */
                boolean timedOut  =
                    (System.currentTimeMillis() - shutdownRequestStart) >
                    repNode.getReplicaCloseCatchupMs();
                if (!timedOut &&
                    (feederVLSN.compareTo
                                (repNode.getCurrentTxnEndVLSN()) <= 0)) {
                    /*
                     * Replica is not caught up. Note that feederVLSN at stasis
                     * is one beyond the last value that was actually sent,
                     * hence the <= instead of < above.
                     */
                    return false;
                }
                /* Replica is caught up or has timed out, shut it down. */
                writeMessage(protocol.new ShutdownRequest(shutdownRequestStart),
                             feederReplicaChannel);

                String shutdownMessage =
                    String.format("Shutdown message sent to: %s. " +
                                  "Feeder vlsn: %,d. " +
                                  "Shutdown elapsed time: %,dms",
                                  replicaNameIdPair,
                                  feederVLSN.getSequence(),
                                  (System.currentTimeMillis() -
                                   shutdownRequestStart));
                LoggerUtils.info(logger, repImpl, shutdownMessage);
                return true;
            }
            return true;
        }

        /** Write a protocol message to the channel. */
        private void writeMessage(final Message message,
                                  final NamedChannel namedChannel)
            throws IOException {

            assert TestHookExecute.doHookIfSet(writeMessageHook, message);
            protocol.write(message, namedChannel);
        }

        @Override
        public void run() {
            protocol =
                Protocol.get(repNode, protocolVersion, protocolVersion,
                             replicaLogVersion);
            Thread.currentThread().setName
                ("Feeder Output for " +
                 Feeder.this.getReplicaNameIdPair().getName());
            {
                VLSNRange range = vlsnIndex.getRange();
                LoggerUtils.info
                    (logger, repImpl, String.format
                     ("Feeder output thread for replica %s started at " +
                      "VLSN %,d master at %,d VLSN delta=%,d socket=%s",
                      replicaNameIdPair.getName(),
                      feederVLSN.getSequence(),
                      range.getLast().getSequence(),
                      range.getLast().getSequence() - feederVLSN.getSequence(),
                      feederReplicaChannel));
            }

            /* Set to indicate an error-initiated shutdown. */
            Error feederOutputError = null;
            Exception shutdownException = null;
            try {

                /*
                 *  Always start out with a heartbeat; the replica is counting
                 *  on it.
                 */
                sendHeartbeat();
                final int timeoutMs = repNode.getConfigManager().
                        getDuration(RepParams.FEEDER_TIMEOUT);
                feederReplicaChannel.setTimeoutMs(timeoutMs);

                while (!checkShutdown()) {
                    if (feederVLSN.compareTo
                            (repNode.getCurrentTxnEndVLSN()) >= 0) {

                        /*
                         * The replica is caught up, if we are a Primary stop
                         * playing that role, and start requesting acks from
                         * the replica.
                         */
                        repNode.getArbiter().endArbitration();
                    }

                    writeAvailableEntries();

                    masterStatus.assertSync();

                    sendHeartbeat();

                    if (testDelayMs > 0) {
                        Thread.sleep(testDelayMs);
                    }
                }

            } catch (IOException e) {
                /* Trio of benign "expected" exceptions below. */
                shutdownException = e;  /* Expected. */
            } catch (MasterSyncException e) {
                /* Expected, shutdown just the feeder. */
                shutdownException = e; /* Expected. */
            } catch (InterruptedException e) {
                /* Expected, shutdown just the feeder. */
                shutdownException = e;  /* Expected. */
            } catch (RuntimeException e) {
                shutdownException = e;

                /*
                 * An internal error. Shut down the rep node as well for now
                 * by throwing the exception out of the thread.
                 *
                 * In future we may want to close down just the impacted
                 * Feeder but this is the safe course of action.
                 */
                LoggerUtils.severe(logger, repImpl,
                                   "Unexpected exception: " + e.getMessage() +
                                   LoggerUtils.getStackTrace(e));
                throw e;
            } catch (Error e) {
                feederOutputError = e;
                repNode.getRepImpl().invalidate(e);
            } finally {
                if (feederOutputError != null) {
                    /* Propagate the error, skip cleanup. */
                    throw feederOutputError;
                }
                LoggerUtils.info(logger, repImpl,
                                 "Feeder output for replica " +
                                 replicaNameIdPair.getName() +
                                 " shutdown. feeder VLSN: " + feederVLSN +
                                 " currentTxnEndVLSN: " +
                                 repNode.getCurrentTxnEndVLSN());

                /*
                 * Shutdown the feeder in its entirety, in case the output
                 * thread is the only one to notice a problem. The Replica can
                 * decide to re-establish the connection
                 */
                shutdown(shutdownException);
                cleanup();
            }
        }

        /**
         * Write as many readily "available" log entries as possible to the
         * network. The term "available" is used in the sense that these values
         * are typically sitting around in the JE or FS cache especially for
         * messages that are recent enough to need timely acknowledgement. The
         * method tried to batch multiple entries, to minimize the number of
         * network calls permitting better packet utilization and fewer network
         * related interrupts, since FEEDER_TCP_NO_DELAY is set on the channel.
         *
         * The size of the batch is limited by one of:
         *
         * 1) The number of "available" trailing vlsn entries between the
         * current position of the feeder and the end of the log.
         *
         * 2) The size of the batchWriteBuffer and
         *
         * 3) The time it takes to accumulate the batch without exceeding the
         * minimum of:
         *
         *    a) heartbeat interval, a larger time window typically in effect
         *    when the replica is not in the ack window. It effectively favors
         *    batching.
         *
         *    b) (batchNs + time to first ack requiring) transaction,
         *    typically in effect when the replica is in the ack window and
         *    more timely acks are needed.
         *
         * This adaptive time interval strategy effectively adapts the batch
         * sizes to the behavior needed of the replica at any given instant
         * in time.
         */
        private void writeAvailableEntries()
            throws DatabaseException, InterruptedException,
                   IOException, MasterSyncException {

            /*
             * Set the initial limit at the heartbeat and constrain it, if the
             * batch contains commits that need acks. The batchLimitNS
             * calculation is slightly sloppy in that it does not allow for
             * disk and network latencies, but that's ok. We don't need to send
             * heartbeats exactly on a heartbeat boundary since the code is
             * resilient in this regard. It's the feeder timeout that's the
             * main worry here; it's 30 sec by default and is set at 10s for
             * KVS, so lots of built in slop.
             */
            long batchLimitNs = System.nanoTime() + (heartbeatMs * 1000000l);
            boolean batchNeedsAcks = false;
            int nMessages = 0;
            batchBuff.clear();

            do {
                final OutputWireRecord record =
                    feederSource.getWireRecord(feederVLSN, heartbeatMs);

                masterStatus.assertSync();

                if (record == null) {
                    /* Caught up -- no more records from feeder source */
                    break;
                }

                final long txnId = record.getCommitTxnId();
                if (commitToNetwork && txnId != 0) {
                    deemAcked(txnId);
                }

                validate(record);
                final Message message = createMessage(txnId, record);

                if (!batchNeedsAcks && (txnId != 0)) {
                    final Commit commit = (Commit) message;
                    if (commit.getNeedsAck()) {
                        batchNeedsAcks = true;
                        /* Tighten the time constraints if needed. */
                        final long ackLimitNs = System.nanoTime() + batchNs;
                        batchLimitNs = ackLimitNs < batchLimitNs ?
                            ackLimitNs : batchLimitNs;
                    }
                }
                assert TestHookExecute.doHookIfSet(writeMessageHook, message);

                nMessages = protocol.bufferWrite(feederReplicaChannel,
                                                 batchBuff,
                                                 ++nMessages,
                                                 message);

                feederVLSN = feederVLSN.getNext();
            } while ((testDelayMs == 0) && /* Don't batch if set by test. */
                (vlsnIndex.getLatestAllocatedVal() >=
                feederVLSN.getSequence()) &&
                ((System.nanoTime() - batchLimitNs) < 0)) ;

            if (batchBuff.position() == 0) {
                /* No entries -- timed out waiting for one. */
                return;
            }

            /*
             * We have collected the largest possible batch given the
             * batching constraints, flush it out.
             */
            protocol.flushBufferedWrites(feederReplicaChannel,
                                         batchBuff,
                                         nMessages);
        }

        /**
         * Sends a heartbeat message, if we have exceeded the heartbeat
         * interval.
         *
         * @param protocol protocol to use for the heartbeat.
         *
         * @throws IOException
         */
        private void sendHeartbeat()
            throws IOException {

            long now = System.currentTimeMillis();
            long interval = now - lastHeartbeat;

            if (interval <= heartbeatMs) {
                return;
            }
            final VLSN vlsn = repNode.getCurrentTxnEndVLSN();
            writeMessage(protocol.new Heartbeat(now, vlsn.getSequence()),
                         feederReplicaChannel);
            lastHeartbeat = now;

            final long lag = vlsn.getSequence() - feederVLSN.getSequence();
            if (nMaxReplicaLag.setMax(lag)) {
                nMaxReplicaLagName.set(replicaNameIdPair.getName());
            }
        }

        @Override
        protected int initiateSoftShutdown() {

            /*
             * Provoke an I/O exception that will cause the output thread to
             * exit.
             */
            RepUtils.shutdownChannel(feederReplicaChannel);
            return repNode.getThreadWaitInterval();
        }

        /**
         * Converts a log entry into a specific Message to be sent out by the
         * Feeder.
         *
         * @param txnId > 0 if the entry is a LOG_TXN_COMMIT
         *
         * @param logBytes the bytes representing the log entry
         *
         * @return the Message representing the entry
         *
         * @throws DatabaseException
         */
        private Message createMessage(long txnId,
                                      OutputWireRecord wireRecord)

            throws DatabaseException {

            /* A vanilla entry */
            if (txnId == 0) {
                return protocol.new Entry(wireRecord);
            }

            boolean needsAck;

            MasterTxn ackTxn = repNode.getFeederTxns().getAckTxn(txnId);
            SyncPolicy replicaSync = SyncPolicy.NO_SYNC;
            if (ackTxn != null) {
                ackTxn.stampRepWriteTime();
                long messageTransferMs = ackTxn.messageTransferMs();
                totalTransferDelay  += messageTransferMs;
                if (messageTransferMs > transferLoggingThresholdMs) {
                    final String message =
                        String.format("Feeder for: %s, Txn: %,d " +
                                      " log to rep stream time %,dms." +
                                      " Total transfer time: %,dms.",
                                      replicaNameIdPair.getName(),
                                      txnId, messageTransferMs,
                                      totalTransferDelay);
                    LoggerUtils.info(logger, repImpl, message);
                }

                /*
                 * Only request an acknowledgment if we are not committing to
                 * the network and DurabilityQuorum says the acknowledgment
                 * qualifies
                 */
                needsAck = !commitToNetwork &&
                    repNode.getDurabilityQuorum().replicaAcksQualify(
                        replicaNode);
                replicaSync = ackTxn.getCommitDurability().getReplicaSync();
            } else {

                /*
                 * Replica is catching up. Specify the weakest and leave it
                 * up to the replica.
                 */
                needsAck = false;
                replicaSync = SyncPolicy.NO_SYNC;
            }

            return protocol.new Commit(needsAck, replicaSync, wireRecord);
        }

        /**
         * Sanity check the outgoing record.
         */
        private void validate(OutputWireRecord record) {

            /* Check that we've fetched the right message. */
            if (!record.getVLSN().equals(feederVLSN)) {
                throw EnvironmentFailureException.unexpectedState
                    ("Expected VLSN:" + feederVLSN + " log entry VLSN:" +
                     record.getVLSN());
            }

            if (!repImpl.isRepConverted()) {
                assert record.verifyNegativeSequences("node=" + nameIdPair);
            }
        }

        @Override
        protected Logger getLogger() {
            return logger;
        }
    }

    private void deemAcked(long txnId) {
        final VLSN commitVLSN =
            repNode.getFeederTxns().noteReplicaAck(replicaNode, txnId);
        if (commitVLSN != null) {
            if (commitVLSN.compareTo(replicaTxnEndVLSN) > 0) {
                replicaTxnEndVLSN = commitVLSN;
            }
            caughtUp = true;
            adviseMasterTransferProgress();
        }
    }

    /**
     * Defines the handler for the RepNode thread. The handler invalidates the
     * environment by ensuring that an EnvironmentFailureException is in place.
     *
     * The handler communicates the cause of the exception back to the
     * FeederManager's thread by setting the repNodeShutdownException and then
     * interrupting the FM thread. The FM thread upon handling the interrupt
     * notices the exception and propagates it out in turn to other threads
     * that might be coordinating activities with it.
     */
    private class IOThreadsHandler implements UncaughtExceptionHandler {

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            LoggerUtils.severe(logger, repImpl,
                               "Uncaught exception in feeder thread " + t +
                               e.getMessage() +
                               LoggerUtils.getStackTrace(e));

            /* Bring the exception to the parent thread's attention. */
            feederManager.setRepNodeShutdownException
                (EnvironmentFailureException.promote
                 (repNode.getRepImpl(),
                  EnvironmentFailureReason.UNCAUGHT_EXCEPTION,
                  "Uncaught exception in feeder thread:" + t,
                  e));

            /*
             * Bring it to the FeederManager's attention, it's currently the
             * same as the rep node's thread.
             */
            repNode.interrupt();
        }
    }

    /**
     * A marker exception that wraps the real exception. It indicates that the
     * wrapped exception was sufficient cause to exit the Feeder, but not the
     * RepNode.
     */
    @SuppressWarnings("serial")
    public static class ExitException extends Exception {

        public ExitException(String message) {
            super(message);
        }

        public ExitException(Throwable cause) {
            super(cause);
        }

    }

    /**  For debugging and exception messages. */
    public String dumpState() {
        return "feederVLSN=" + feederVLSN +
                " replicaTxnEndVLSN=" + replicaTxnEndVLSN +
            ((replicaNode != null) && !replicaNode.getType().isElectable() ?
             " nodeType=" + replicaNode.getType() :
             "");
    }

    /**
     * Set a test hook that will be called before sending a message using the
     * protocol's write method, supplying the hook with the message as an
     * argument.
     */
    public void setWriteMessageHook(final TestHook<Message> writeMessageHook) {
        this.writeMessageHook = writeMessageHook;
    }

    /**
     * Get the test hook to be called before sending a message using the
     * protocol's write method.
     */
    public TestHook<Message> getWriteMessageHook() {
        return writeMessageHook;
    }

    /**
     * Set the value of the write message hook that will be used for newly
     * created feeders.
     */
    public static void setInitialWriteMessageHook(
        final TestHook<Message> initialWriteMessageHook) {

        Feeder.initialWriteMessageHook = initialWriteMessageHook;
    }
}
