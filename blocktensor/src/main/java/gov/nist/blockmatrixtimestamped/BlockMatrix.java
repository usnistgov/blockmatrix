package gov.nist.blockmatrixtimestamped;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class BlockMatrix {
    private static final int HASH_ARRAY_SIZE = 32;
    private final int matrixWidth; // blockmatrix size is matrixWidth^dimension
    private int inputCount; // how many Blocks have been added to the blockmatrix
    private boolean deletionValidity; // whether or not all deletions have been valid. Used to check blockmatrix validity
    private Block[][] blockData;
    private byte[][] rowHashes;
    private byte[][] columnHashes;

    public BlockMatrix(int matrixWidth) throws IllegalArgumentException {
        if (matrixWidth < 2)
            throw new IllegalArgumentException("Minimal matrix width is 2.");

        this.matrixWidth = matrixWidth;
        this.inputCount = 0;
        this.deletionValidity = true;
        this.blockData = new Block[matrixWidth][matrixWidth];
        this.rowHashes = new byte[matrixWidth][];
        this.columnHashes = new byte[matrixWidth][];

        for (int i = 0; i < matrixWidth; i++) {
            updateRowHash(i);
            updateColumnHash(i);
        }
    }

    // helper method to get the row of a block given a block number
    private static int getBlockRowIndex(int blockNumber) {
        if (blockNumber % 2 == 0) { // Block number is even
            int s = (int) Math.floor(Math.sqrt(blockNumber));
            return (blockNumber <= s * s + s) ? s : s + 1;
        } else { // Block count is odd
            int s = (int) Math.floor(Math.sqrt(blockNumber + 1));
            int column = (blockNumber < s * s + s) ? s : s + 1;
            return (blockNumber - (column * column - column + 1)) / 2;
        }
    }

    //helper method to get the column of a block given a block number
    private static int getBlockColumnIndex(int blockNumber) {
        if (blockNumber % 2 == 0) { // Block number is even
            int s = (int) Math.floor(Math.sqrt(blockNumber));
            int row = (blockNumber <= s * s + s) ? s : s + 1;
            return (blockNumber - (row * row - row + 2)) / 2;
        } else { // Block number is odd
            int s = (int) Math.floor(Math.sqrt(blockNumber + 1));
            return (blockNumber < s * s + s) ? s : s + 1;
        }
    }

    //adds a block to the blockmatrix
    public void add(Block block) {
        inputCount++;
        if (inputCount > (matrixWidth * matrixWidth) - matrixWidth) { //no more space in the matrix
            inputCount--;
            System.out.println("Error: Addition of " + block.toString() + " to BlockMatrix failed, no more space");
            return;
        }

        //Insertion location code from block matrix paper
        if (inputCount % 2 == 0) { // Block count is even
            int s = (int) Math.floor(Math.sqrt(inputCount));
            int i = (inputCount <= s * s + s) ? s : s + 1;
            int j = (inputCount - (i * i - i + 2)) / 2;

            blockData[i][j] = block;

            updateRowHash(i);
            updateColumnHash(j);
        } else { // Block count is odd
            int s = (int) Math.floor(Math.sqrt(inputCount + 1));
            int j = (inputCount < s * s + s) ? s : s + 1;
            int i = (inputCount - (j * j - j + 1)) / 2;
            blockData[i][j] = block;
            updateRowHash(i);
            updateColumnHash(j);
        }
    }

    public Block getBlock(int blockNumber) {
        return blockData[getBlockRowIndex(blockNumber)][getBlockColumnIndex(blockNumber)];
    }

    public byte[] getBlockData(int blockNumber) {
        return getBlock(blockNumber).getData();
    }

    //the "delete" function, which will overwrite any message info passed in along with the transaction for every transaction in the block
    public void clearBlock(int blockNumber) {
        int row = getBlockRowIndex(blockNumber);
        int column = getBlockColumnIndex(blockNumber);

        Block b = getBlock(blockNumber);
        b.setData(new byte[0]);
        b.setTimestamp(System.currentTimeMillis());
        b.setHash(b.calculateHash());

        byte[][] prevRowHashes = this.getRowHashes().clone();
        byte[][] prevColumnHashes = this.getColumnHashes().clone();

        updateRowHash(row);
        updateColumnHash(column);

        byte[][] newRowHashes = this.getRowHashes().clone();
        byte[][] newColumnHashes = this.getColumnHashes().clone();

        if (!checkValidDeletion(prevRowHashes, prevColumnHashes, newRowHashes, newColumnHashes)) {
            System.out.println("Bad deletion, more or less than one row and column hash affected");
            deletionValidity = false; // This might be better as something that throws an exception.
        }
    }

    //Uses data in each block in the row except those that are null and those in the diagonal
    private void updateRowHash(int row) {
        rowHashes[row] = calculateRowHash(row);
    }

    //Uses data in each block in the column except those that are null and those in the diagonal
    private void updateColumnHash(int column) {
        columnHashes[column] = calculateColumnHash(column);
    }

    private byte[] calculateRowHash(int row) {
        ByteBuffer buf = ByteBuffer.allocate(HASH_ARRAY_SIZE * matrixWidth);
        for (int column = 0; column < matrixWidth; column++) {
            if (row != column && blockData[row][column] != null) {
                buf.put(blockData[row][column].getHash());
            }
        }
        return SecurityUtil.applySha256(buf.array());
    }

    private byte[] calculateColumnHash(int column) {
        ByteBuffer buf = ByteBuffer.allocate(HASH_ARRAY_SIZE * matrixWidth);
        for (int row = 0; row < matrixWidth; row++) {
            if (row != column && blockData[row][column] != null) {
                buf.put(blockData[row][column].getHash());
            }
        }

        return SecurityUtil.applySha256(buf.array());
    }

    //tests to make sure only one row hash and one column hash have been modified. If not, then integrity is likely compromised
    private boolean checkValidDeletion(byte[][] prevRow, byte[][] prevCol, byte[][] newRow, byte[][] newCol) {
        int numRowChanged = 0;
        int numColChanged = 0;
        for (int i = 0; i < matrixWidth; i++) {
            if (!Arrays.equals(prevRow[i], newRow[i])) {
                numRowChanged++;
            }
            if (!Arrays.equals(prevCol[i], newCol[i])) {
                numColChanged++;
            }
        }
        return numRowChanged == 1 && numColChanged == 1;
    }

    //gets the number of blocks that have been entered
    public int getInputCount() {
        return inputCount;
    }

    public byte[][] getRowHashes() {
        return rowHashes;
    }

    public byte[][] getColumnHashes() {
        return columnHashes;
    }

    public int getMatrixWidth() {
        return matrixWidth;
    }

    private boolean getDeletionValidity() {
        return deletionValidity;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Block[] row : blockData) {
            sb.append(String.format("%5s", Arrays.toString(row)));
            sb.append("\n");
        }
        return sb.toString();
    }

    public void printRowHashes() {
        System.out.println("\nRow hashes:");
        System.out.println("----------------------------------------------------------------");
        for (byte[] rowHash : rowHashes) {
            System.out.println(SecurityUtil.bytesToHex(rowHash));
        }
        System.out.println("----------------------------------------------------------------\n");
    }

    public void printColumnHashes() {
        System.out.println("\nColumn hashes:");
        System.out.println("----------------------------------------------------------------");
        for (byte[] columnHash : columnHashes) {
            System.out.println(SecurityUtil.bytesToHex(columnHash));
        }
        System.out.println("----------------------------------------------------------------\n");
    }

    public void printHashes() {
        printRowHashes();
        printColumnHashes();
    }

    //sees if the matrix has maintained security, or if it has been tampered with
    public Boolean isMatrixValid() {
        Block currentBlock;

        //loop through matrix to check block hashes:
        for (int i = 1; i <= getInputCount(); i++) {
            currentBlock = getBlock(i);
            //compare registered hash and calculated hash:
            if (!Arrays.equals(currentBlock.getHash(), currentBlock.calculateHash())) {
                System.out.println("Hashes for Block " + i + " not equal (first instance of block with unequal hashes, there may be more)");
                return false;
            }
        }

        //check if all row hashes are valid
        for (int i = 0; i < getMatrixWidth(); i++) {
            if (!Arrays.equals(calculateRowHash(i), getRowHashes()[i])) {
                System.out.println("Row hashes for row " + i + " not equal (first instance of row with unequal hashes, there may be more");
                return false;
            }
        }

        //check if all column hashes are valid
        for (int i = 0; i < getMatrixWidth(); i++) {
            if (!Arrays.equals(calculateColumnHash(i), getColumnHashes()[i])) {
                System.out.println("Column hashes for row " + i + " not equal (first instance of column with unequal hashes, there may be more");
                return false;
            }
        }

        //check if all deletions have been valid
        if (!getDeletionValidity()) {
            System.out.println("One or more deletions were not valid and altered more than one row and column hash");
            return false;
        }

        return true;
    }
}
