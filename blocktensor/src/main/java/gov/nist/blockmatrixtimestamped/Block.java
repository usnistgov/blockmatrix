package gov.nist.blockmatrixtimestamped;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Class for a block of data. Stores the data, the timestamp and the hash of these two. The data and the hash are
 * stored as byte arrays. The class knows how to calculate hash of itself using SHA-256 algorithm.
 */
public class Block {
    /**
     * To be used for time when the block was created or modified.
     */
    private long timestamp;
    /**
     * The data stored.
     */
    private byte[] data;
    /**
     * Hash of the data and the timestamp.
     */
    private byte[] hash;

    /**
     * Create new block with given parameters.
     *
     * @param timestamp timestamp, a non-negative long.
     * @param data      data to be stored, size of the array should be less than 1073741824 (1 gibibyte)
     * @throws IllegalArgumentException if the timestamp is negative or the size of the data array is too large
     * @throws NullPointerException     if data is null
     */
    public Block(long timestamp, byte[] data) throws IllegalArgumentException, NullPointerException {
        setTimestamp(timestamp);
        setData(data);
        setHash(calculateHash());
    }

    /**
     * Create new block from the existing block. Hash and data fields are copied.
     *
     * @param other block to be copied
     */
    public Block(Block other) {
        if (other == null)
            throw new IllegalArgumentException("Copied block cannot be null.");

        setTimestamp(other.getTimestamp());
        setData(other.getData().clone());
        setHash(other.getHash().clone());
    }

    /**
     * Get timestamp.
     *
     * @return timestamp, non-negative long
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Set timestamp.
     *
     * @param timestamp timestamp, non-negative long
     * @throws IllegalArgumentException if the timestamp is negative
     */
    public void setTimestamp(long timestamp) throws IllegalArgumentException {
        if (timestamp < 0)
            throw new IllegalArgumentException("Timestamp should not be negative.");
        this.timestamp = timestamp;
    }

    /**
     * Calculate the hash of the timestamp and the data concatenated (in this order). SHA-256 is used.
     *
     * @return hash byte array of size 32
     */
    public byte[] calculateHash() {
        ByteBuffer buf = ByteBuffer.allocate(Long.BYTES + data.length);
        buf.putLong(timestamp);
        buf.put(data);
        return SecurityUtil.applySha256(buf.array());
    }

    /**
     * Get hash.
     *
     * @return hash, byte array of size 32
     */
    public byte[] getHash() {
        return hash;
    }

    /**
     * Set hash.
     *
     * @param hash hash, non-null byte array of size 32
     * @throws IllegalArgumentException if hash is not of size 32
     * @throws NullPointerException     if hash is null
     */
    public void setHash(byte[] hash) throws IllegalArgumentException, NullPointerException {
        Objects.requireNonNull(hash);
        if (hash.length != BlockTensor.HASH_ARRAY_SIZE)
            throw new IllegalArgumentException("Hash must be SHA256 - 32 bytes long.");
        this.hash = hash;
    }

    /**
     * Get data.
     *
     * @return data, byte array of size not greater than 1073741824 (1 gibibyte)
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Set data.
     *
     * @param data data, non-null byte array of size not greater than 1073741824 (1 gibibyte)
     * @throws IllegalArgumentException if data size is greater than 1073741824 (1 gibibyte)
     * @throws NullPointerException     if data is null
     */
    public void setData(byte[] data) throws IllegalArgumentException, NullPointerException {
        Objects.requireNonNull(data);
        // if size is more than 1 gibibyte (1024^3)
        if (data.length > 1073741824)
            throw new IllegalArgumentException("Maximum block size is 1 gibibyte (1024^3 byte).");

        this.data = data;
    }
}
