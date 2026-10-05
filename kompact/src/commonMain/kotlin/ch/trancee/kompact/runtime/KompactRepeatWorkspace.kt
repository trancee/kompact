package ch.trancee.kompact.runtime

/**
 * Caller-owned sparse index storage for variable-width repeated payloads.
 *
 * Allocate [checkpointStorage] before decoding. The workspace stores one
 * element offset per [CHECKPOINT_INTERVAL] elements and is reused by subsequent
 * repeat reads. Keep the indexed source bytes unchanged while using it.
 */
/**
 * Caller-owned sparse-index storage for one variable- or fixed-width repeat.
 *
 * The workspace retains [checkpointStorage] and the buffer bound by the last
 * successful repeat read. Keep that buffer unchanged while using the index.
 * Reusing this workspace replaces its current repeat binding.
 *
 * @param checkpointStorage caller-owned slots for sparse bit-offset checkpoints
 */
public class KompactRepeatWorkspace(
    checkpointStorage: IntArray,
) {
    private val checkpoints: IntArray = checkpointStorage
    private var boundBuffer: ByteArray? = null

    /** Number of sparse checkpoints that fit in the supplied storage. */
    public val capacity: Int
        get() = checkpoints.size

    /** Element count from the last successful repeat read. */
    public var count: Int = 0
        private set

    /** Inclusive bit offset of the first repeat element. */
    public var startBit: Int = 0
        private set

    /** Exclusive bit offset after the last repeat element. */
    public var endBit: Int = 0
        private set

    internal var elementPrefixWidth: Int = 0
        private set

    internal var elementBitWidth: Int = 0
        private set

    internal fun isBoundTo(buffer: ByteArray): Boolean = boundBuffer === buffer

    internal fun checkpointAt(index: Int): Int = checkpoints[index]

    internal fun bind(
        buffer: ByteArray,
        count: Int,
        startBit: Int,
        endBit: Int,
        elementPrefixWidth: Int,
        elementBitWidth: Int,
    ) {
        boundBuffer = buffer
        this.count = count
        this.startBit = startBit
        this.endBit = endBit
        this.elementPrefixWidth = elementPrefixWidth
        this.elementBitWidth = elementBitWidth
    }

    internal fun storeCheckpoint(
        index: Int,
        bitOffset: Int,
    ) {
        checkpoints[index] = bitOffset
    }

    public companion object {
        /** Maximum number of elements between indexed checkpoints. */
        public const val CHECKPOINT_INTERVAL: Int = 64
    }
}
