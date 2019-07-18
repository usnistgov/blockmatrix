package gov.nist.blockmatrixtimestamped;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Blocktensor data structure, an alternative to blockchain allowing addition and erasure of blocks with
 * log(size) complexity if used with optimal settings. Optimal width is 3 and optimal dimCount is log(size)/log(3).
 * You can choose dimCount and width manually or just provide the size you need and optimal parameters will be chosen
 * automatically.
 */
public class BlockTensor {
    /**
     * Since SHA-256 is used, hash array size must be 32.
     */
    public static final int HASH_ARRAY_SIZE = 32;

    /**
     * Width of the blocktensor.
     */
    private final int width;
    /**
     * Dimension count of the blocktensor.
     */
    private final int dimCount; // dimension count
    /**
     * Tensor to store the blocks. Its size is width^dimCount.
     */
    private final Tensor<Block> blockData;
    /**
     * Tensor to store hashes of the lines. The indexes of the tensor represent the fixed indexes of the line in
     * question, while the index of the array of hashes is the index of the variable index. Therefore, if we have line
     * represented by fixedIndexes and varDimIdx, we can access its hash via hashes.get(fixedIndexes)[varDimIdx].
     */
    private final Tensor<byte[][]> hashes;
    /**
     * Converts index in the underlying tensor to block number. Block numbers start at {0,0,...,0} and are propagated
     * via breadth-first search. This tensor is initialized when the blocktensor is created.
     */
    private final Tensor<Integer> indexesToBlockNumber;
    /**
     * Converts block number in the underlying tensor to index. Block numbers start at {0,0,...,0} and are propagated
     * via breadth-first search. This array is initialized when the blocktensor is created.
     */
    private final ArrayList<int[]> blockNumberToIndexes;

    /**
     * Number of blocks added to the blocktensor.
     */
    private int size;


    /**
     * Create new blocktensor with given width and dimCount. May not be optimal.
     *
     * @param width    width, a positive integer
     * @param dimCount dimension count, an integer greater than 1
     * @throws IllegalArgumentException if any argument does not satisfy the requirements
     */
    public BlockTensor(int width, int dimCount) throws IllegalArgumentException {
        this.width = width;
        this.dimCount = dimCount;
        this.size = 0;
        this.blockData = new Tensor<>(dimCount, width);
        /*
        It is problematic to handle null values instead of data byte arrays, since both zero-length array and null
        contribute nothing to the hash, so it is impossible to distinguish the default block and the block that has been
        deliberately given the empty data array.
         */
        // fill the data tensor with the template block copies
        Block template = new Block(timestamp(), new byte[0]);
        for (int i = 0; i < blockData.capacity(); ++i)
            this.blockData.set(new Block(template), i);
        this.hashes = new Tensor<>(dimCount - 1, width);
        // fill the hash tensor with arrays of empty strings
        for (int i = 0; i < hashes.capacity(); ++i)
            this.hashes.set(new byte[dimCount][], i);
        this.indexesToBlockNumber = new Tensor<>(dimCount, width);
        // fill the array with nulls
        this.blockNumberToIndexes = new ArrayList<>(Collections.nCopies(Tensor.binPow(width, dimCount), null));

        initIndexing();
        updateAllHashes();
    }

    /**
     * Create new blocktensor to accomodate at least the provided size. dimCount is set to log(size)/log(3) and width
     * is set to 3.
     *
     * @param size maximum number of elements you will have, a positive integer
     * @throws IllegalArgumentException if size is not positive
     */
    public BlockTensor(int size) throws IllegalArgumentException {
        this(3, Math.max(2, (int) Math.ceil(Math.log(size) / Math.log(3))));
        if (size < 1)
            throw new IllegalArgumentException("Size must be a positive integer.");
    }

    /**
     * Remove the index at the given index from the array of indexes.
     *
     * @param varDimIdx index of the index to be removed
     * @param indexes   array of indexes
     * @return array of indexes without the given index removed
     */
    private static int[] removeVariableIndex(int varDimIdx, int... indexes) {
        assert varDimIdx < indexes.length && varDimIdx >= 0;
        assert Arrays.stream(indexes).allMatch(i -> i >= 0);

        // make a stream of indexes in the interval [0 .. indexes.length)
        return IntStream.range(0, indexes.length)
                // remove the index we don't need
                .filter(i -> i != varDimIdx)
                // map the indexes to their corresponding values
                .map(i -> indexes[i])
                // convert back to array
                .toArray();
    }

    /**
     * Get data of the given block number.
     *
     * @param blockNumber block number, an integer in interval [0 .. capacity)
     * @return data of the block, returns zero-length array if the block has not been set yet
     * @throws IndexOutOfBoundsException if the block number is not in the required interval
     */
    public byte[] getData(int blockNumber) throws IndexOutOfBoundsException {
        Objects.checkIndex(blockNumber, size());
        return getBlock(blockNumber).getData().clone();
    }

    /**
     * Get timestamp of the given block number.
     *
     * @param blockNumber block number, an integer in interval [0 .. capacity)
     * @return timestamp of the block, returns time when the blocktensor was created if the block has not been set yet
     * @throws IndexOutOfBoundsException if the block number is not in the required interval
     */
    public long getTimestamp(int blockNumber) throws IndexOutOfBoundsException {
        Objects.checkIndex(blockNumber, size());
        return getBlock(blockNumber).getTimestamp();
    }

    /**
     * Get hash of the given block number.
     *
     * @param blockNumber block number, an integer in interval [0 .. capacity)
     * @return hash of the block
     * @throws IndexOutOfBoundsException if the block number is not in the required interval
     */
    public byte[] getHash(int blockNumber) throws IndexOutOfBoundsException {
        Objects.checkIndex(blockNumber, size());
        return getBlock(blockNumber).getHash().clone();
    }

    /**
     * Set data to the given block. Return the data that was there before or zero-length byte array if the block has
     * not been set yet. You can do set(size(), data) to add a block to the end.
     *
     * @param blockNumber block number, an integer in interval [0 .. capacity)
     * @param data        data byte array
     * @return data that was there before or zero-length byte array if the block has not been set yet
     * @throws IndexOutOfBoundsException if the block is not in the required interval
     */
    public byte[] set(int blockNumber, byte[] data)
            throws IndexOutOfBoundsException {
        if (blockNumber == size())
            Objects.checkIndex(size(), capacity());
        else
            Objects.checkIndex(blockNumber, size());
        if (data == null)
            data = new byte[0];

        int[] indexes = blockNumberToIndexes.get(blockNumber);

        Block b = getBlock(blockNumber);
        byte[] old = b.getData();
        b.setData(data.clone());
        b.setTimestamp(timestamp());
        b.setHash(b.calculateHash());

        // update hashes of lines which intersect the modified index
        // iterate through indexes and make each one variable one by one
        for (int varDimIdx = 0; varDimIdx < getDimCount(); ++varDimIdx) {
            int[] fixedIndexes = removeVariableIndex(varDimIdx, indexes);
            updateLineHash(varDimIdx, fixedIndexes);
        }

        if (blockNumber == size())
            size++;
        return old;
    }

    /**
     * Add data to the blocktensor. Equivalent to set(size(), data).
     *
     * @param data data byte array
     * @return block number of the added block
     * @throws IndexOutOfBoundsException if there is not enough space in blocktensor
     */
    public int add(byte[] data) throws IndexOutOfBoundsException {
        set(size(), data);
        return size() - 1;
    }

    /**
     * Erase the block at given block number. Equivalent to set(blockNumber, new byte[0]).
     *
     * @param blockNumber block number, an integer in interval [0 .. capacity)
     * @return previous data at the given block number
     */
    public byte[] erase(int blockNumber) throws IndexOutOfBoundsException {
        return set(blockNumber, null);
    }

    /**
     * Number of set blocks. Does not decrease after erase and set.
     *
     * @return number of blocks modified
     */
    public int size() {
        return size;
    }

    /**
     * Maximal number of blocks the blocktensor can accommodate. This number is fixed.
     *
     * @return capacity of blocktensor
     */
    public int capacity() {
        return blockData.capacity();
    }

    /**
     * Get width of the blocktensor. This number is fixed.
     *
     * @return width of each line in blocktensor
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get dimension count of the blocktensor. This number is fixed.
     *
     * @return dimension count
     */
    public int getDimCount() {
        return dimCount;
    }

    /**
     * Check the validity of the blocktensor by checking whether each block has the same hash value stored as the one
     * calculated for it, and checking whether each line hash stored is the same as the one calculated.
     *
     * @return whether the tensor is valid
     */
    public Boolean isValid() {
        //loop through matrix to check block hashes:
        for (int i = 0; i < size(); i++) {
            Block b = getBlock(i);
            //compare registered hash and calculated hash:
            if (!Arrays.equals(b.getHash(), b.calculateHash())) {
                return false;
            }
        }

        // check if all line hashes are valid
        for (int varDimIdx = 0; varDimIdx < dimCount; ++varDimIdx) {
            for (int i = 0; i < hashes.capacity(); ++i) {
                int[] fixedIndexes = Tensor.indexToIndexes(dimCount - 1, width, i);
                byte[] existingHash = hashes.get(fixedIndexes)[varDimIdx];
                byte[] calculatedHash = calculateLineHash(varDimIdx, fixedIndexes);
                if (!Arrays.equals(existingHash, calculatedHash)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Initialize indexing from the data tensor indexes to block numbers. Block number 0 corresponds to {0,0,...,0} and
     * all subsequent block numbers are assigned in order of breadth-first search from this point, i.e. block number 1
     * is {0,0,...,1} and block number 2 is {0,0,...,1,0}. The mapping in both directions is calculated and stored to be
     * used later.
     */
    private void initIndexing() {
        int[] start = new int[dimCount];
        Queue<int[]> q = new ArrayDeque<>();

        q.add(start);

        // get next indexes from the current one by adding 1 to each index
        // example in 3 dimensions: {0,0,0} -> [{0,0,1},{0,1,0},{1,0,0}]
        Function<int[], int[][]> nextIndexes = (int[] idx) -> {
            int[] next;
            int[][] res = new int[dimCount][];

            for (int i = 0; i < dimCount; ++i) {
                next = idx.clone();
                next[i]++;
                if (next[i] >= width)
                    continue;
                res[i] = next;
            }

            return res;
        };

        // BFS
        int idx = 0;
        while (!q.isEmpty()) {
            int[] cur = q.poll();

            if (indexesToBlockNumber.get(cur) != null)
                continue;

            indexesToBlockNumber.set(idx, cur);
            idx++;

            for (int[] next : nextIndexes.apply(cur)) {
                if (next != null)
                    q.add(next);
            }
        }


        // save backwards mapping
        for (int i = 0; i < indexesToBlockNumber.size(); ++i) {
            int blockNumber = indexesToBlockNumber.get(i);
            blockNumberToIndexes.set(blockNumber, Tensor.indexToIndexes(dimCount, width, i));
        }
    }

    /**
     * Update all line hashes in the blocktensor.
     */
    private void updateAllHashes() {
        // iterate through all possible positions of variable index
        for (int varDimIdx = 0; varDimIdx < getDimCount(); ++varDimIdx) {
            // iterate through all fixed indexes for those positions
            for (int i = 0; i < Tensor.binPow(getWidth(), getDimCount() - 1); ++i) {
                int[] fixedIndexes = Tensor.indexToIndexes(getDimCount() - 1, getWidth(), i);
                updateLineHash(varDimIdx, fixedIndexes);
            }
        }
    }

    /**
     * Update the hash of the line.
     *
     * @param varDimIdx    index of the variable index
     * @param fixedIndexes fixed indexes
     */
    private void updateLineHash(int varDimIdx, int[] fixedIndexes) {
        assert varDimIdx < getDimCount() && varDimIdx >= 0;
        assert fixedIndexes.length == getDimCount() - 1;
        // all indexes are in the interval [0 .. width)
        assert Arrays.stream(fixedIndexes).allMatch(i -> i < getWidth() && i >= 0);

        assert hashes.get(fixedIndexes) != null;
        hashes.get(fixedIndexes)[varDimIdx] = calculateLineHash(varDimIdx, fixedIndexes);
    }

    /**
     * Calculate the hash of the line.
     *
     * @param varDimIdx    index of the variable index
     * @param fixedIndexes fixed indexes
     * @return hash of the line
     */
    private byte[] calculateLineHash(int varDimIdx, int[] fixedIndexes) {
        assert varDimIdx < getDimCount() && varDimIdx >= 0;
        assert fixedIndexes.length == getDimCount() - 1;
        // all indexes are in the interval [0 .. width)
        assert Arrays.stream(fixedIndexes).allMatch(i -> i < getWidth() && i >= 0);

        ByteBuffer buf = ByteBuffer.allocate(HASH_ARRAY_SIZE * getWidth());
        for (Block b : blockData.getLine(varDimIdx, fixedIndexes)) {
            assert b != null;
            buf.put(b.getHash());
        }
        return SecurityUtil.applySha256(buf.array());
    }

    /**
     * Get block at the given block number.
     *
     * @param blockNumber block number
     * @return block
     */
    private Block getBlock(int blockNumber) {
        assert blockNumber >= 0 && blockNumber < capacity();
        return blockData.get(blockNumberToIndexes.get(blockNumber));
    }

    /**
     * Get current time.
     *
     * @return current time
     */
    protected long timestamp() {
        return System.currentTimeMillis();
    }
}
