package blockmatrix;

import com.google.gson.GsonBuilder;

import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

public class BlockMatrix {

    private int dimension; // block matrix is size dimension^2
    private int inputCount = 0; // how many Blocks have been added to the block matrix
    private boolean deletionValidity; // whether or not all deletions have been valid. Used to check blockmatrix validity
    private Block[][] blockData;
    private String[] rowHashes;
    private String[] columnHashes;
    static float minimumTransaction;
    private Transaction genesisTransaction;
    static HashMap<String, TransactionOutput> UTXOs = new HashMap<>();
    private boolean generated;
    private ArrayList<Integer> blocksWithModifiedData;

    public BlockMatrix(int dimension) {
        if (dimension < 2) {
            throw new IllegalArgumentException("BlockMatrix must have dimensions of at least 2x2.");
        }
        this.dimension = dimension;
        this.blockData = new Block[dimension][dimension];
        this.rowHashes = new String[dimension];
        this.columnHashes = new String[dimension];
        this.minimumTransaction = 0.1f;
        this.deletionValidity = true;
        this.generated = false;
        this.blocksWithModifiedData = new ArrayList<>();
        for (int i = 0; i < dimension; i++) {
            updateRowHash(i);
            updateColumnHash(i);
        }
    }

    //adds a block to our blockmatrix
    private void add(Block block) {
        inputCount++;
        if (inputCount > (dimension* dimension) - dimension) { //no more space in the matrix
            inputCount--;
            System.out.println("Error: Addition of " + block.toString() + " to BlockMatrix failed, no more space");
            return;
        }

        //Insertion location code gotten from block matrix paper
        if (inputCount % 2 == 0) { // Block count is even
            int s = (int) Math.floor(Math.sqrt(inputCount));
            int i = (inputCount <= s*s + s) ? s : s + 1;
            int j = (inputCount - (i*i - i + 2))/2;
            blockData[i][j] = block;
            updateRowHash(i);
            updateColumnHash(j);
            
        } else { // Block count is odd
            int s = (int) Math.floor(Math.sqrt(inputCount + 1));
            int j = (inputCount < s*s + s) ? s: s + 1;
            int i = (inputCount - (j*j - j + 1))/2;
            blockData[i][j] = block;
            updateRowHash(i);
            updateColumnHash(j);
        }
        for (Transaction t: block.getTransactions()) {
            t.setBlockNumber(inputCount);
        }
    }

    public Block getBlock(int blockNumber) {
        return blockData[getBlockRowIndex(blockNumber)][getBlockColumnIndex(blockNumber)];
    }

    //gets all transactions in a certain block
    public ArrayList<Transaction> getBlockTransactions(int blockNumber) {
        return getBlock(blockNumber).getTransactions();
    }

    public String getBlockData(int blockNumber) {
        String transactionsJson = new GsonBuilder().setPrettyPrinting().create().toJson(getBlock(blockNumber).getTransactions());
        return transactionsJson;
    }

    //the "delete" function, which will overwrite any message info passed in along with the transaction for every transaction in the block
    public void clearInfoInTransaction(int blockNumber, int transactionNumber) {
        this.blocksWithModifiedData.add(blockNumber);
        int row = getBlockRowIndex(blockNumber);
        int column = getBlockColumnIndex(blockNumber);
        getBlock(blockNumber).clearInfoInTransactionsInBlock(transactionNumber);
        String[] prevRowHashes = this.getRowHashes().clone();
        String[] prevColumnHashes = this.getColumnHashes().clone();
        updateRowHash(row);
        updateColumnHash(column);
        String[] newRowHashes = this.getRowHashes().clone();
        String[] newColumnHashes = this.getColumnHashes().clone();
        if (!checkValidDeletion(prevRowHashes, prevColumnHashes, newRowHashes, newColumnHashes)) {
            System.out.println("Bad deletion, more or less than one row and column hash affected");
            deletionValidity = false; // This might be better as something that throws an exception.
        }
    }


    //Uses data in each block in the row except those that are null and those in the diagonal
    private void updateRowHash(int row) {
        rowHashes[row] =  calculateRowHash(row);
    }

    //Uses data in each block in the column except those that are null and those in the diagonal
    private void updateColumnHash(int column) {
        columnHashes[column] = calculateColumnHash(column);
    }

    private String calculateRowHash(int row) {
        StringBuilder sb = new StringBuilder();
        for (int column = 0; column < dimension; column++) {
            if (row != column && blockData[row][column] != null) {
                sb.append(blockData[row][column].getHash());
            }
        }
        return StringUtil.applySha256(sb.toString());
    }

    private String calculateColumnHash(int column) {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < dimension; row++) {
            if (row != column && blockData[row][column] != null) {
                sb.append(blockData[row][column].getHash());
            }
        }

        return StringUtil.applySha256(sb.toString());
    }

    // helper method to get the row of a block given a block number
    private int getBlockRowIndex(int blockNumber) {
        if (blockNumber % 2 == 0) { // Block number is even
            int s = (int) Math.floor(Math.sqrt(blockNumber));
            int row = (blockNumber <= s*s + s) ? s : s + 1;
            return row;
        } else { // Block count is odd
            int s = (int) Math.floor(Math.sqrt(blockNumber + 1));
            int column = (blockNumber < s*s + s) ? s: s + 1;
            int row = (blockNumber - (column*column - column + 1))/2;
            return row;
        }
    }

    //helper method to get the column of a block given a block number
    private int getBlockColumnIndex(int blockNumber) {
        if (blockNumber % 2 == 0) { // Block number is even
            int s = (int) Math.floor(Math.sqrt(blockNumber));
            int row = (blockNumber <= s*s + s) ? s : s + 1;
            int column = (blockNumber - (row*row - row + 2))/2;
            return column;
        } else { // Block number is odd
            int s = (int) Math.floor(Math.sqrt(blockNumber + 1));
            int column = (blockNumber < s*s + s) ? s: s + 1;
            return column;
        }
    }

    //tests to make sure only one row hash and one column hash have been modified. If not, then integrity is likely compromised
    private boolean checkValidDeletion(String[] prevRow, String[] prevCol, String[] newRow, String[] newCol) {
        int numRowChanged = 0;
        int numColChanged = 0;
        for (int i = 0; i < dimension; i++) {
            if (!prevRow[i].equals(newRow[i])) {
                numRowChanged++;
            }
            if (!prevCol[i].equals(newCol[i])) {
                numColChanged++;
            }
        }
        if (numRowChanged != 1 || numColChanged != 1) {
            return false;
        }
        return true;
    }

    //gets the number of blocks that have been entered
    public int getInputCount() {
        return inputCount;
    }

    public String[] getRowHashes() {
        return rowHashes;
    }

    public String[] getColumnHashes() {
        return columnHashes;
    }

    public void setMinimumTransaction(float num) {
        minimumTransaction = num;
    }

    public float getMinimumTransaction() {
        return minimumTransaction;
    }

    public int getDimension() {
        return dimension;
    }

    private boolean getDeletionValidity() {
        return this.deletionValidity;
    }

    //returns a list of blocks for which data has been modified
    public ArrayList<Integer> getBlocksWithModifiedData() {
        Collections.sort(this.blocksWithModifiedData);
        return this.blocksWithModifiedData;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Block[] row: blockData) {
            sb.append(String.format("%5s", Arrays.toString(row)));
            sb.append("\n");
        }
        return sb.toString();
    }

    public void printRowHashes() {
        System.out.println("\nRow hashes:");
        System.out.println("----------------------------------------------------------------");
        for (int i = 0; i < rowHashes.length; i++) {
            System.out.println(rowHashes[i]);
        }
        System.out.println("----------------------------------------------------------------\n");
    }

    public void printColumnHashes() {
        System.out.println("\nColumn hashes:");
        System.out.println("----------------------------------------------------------------");
        for (int i = 0; i < columnHashes.length; i++) {
            System.out.println(columnHashes[i]);
        }
        System.out.println("----------------------------------------------------------------\n");
    }

    public void printHashes() {
        printRowHashes();
        printColumnHashes();
    }

    //Creates, mines, and adds the genesis block to the blockmatrix
    public void generate(Wallet wallet, float value) {
        if (!this.generated) {
            Wallet coinbase = new Wallet();
            //create genesis transaction, which sends coins to walletA:
            genesisTransaction = new Transaction(coinbase.publicKey, wallet.publicKey, value, null, null);
            genesisTransaction.generateSignature(coinbase.privateKey);	 //manually sign the genesis transaction
            genesisTransaction.transactionId = "0"; //manually set the transaction id
            genesisTransaction.outputs.add(new TransactionOutput(genesisTransaction.recipient, genesisTransaction.value, genesisTransaction.transactionId)); //manually add the Transactions Output
            UTXOs.put(genesisTransaction.outputs.get(0).id, genesisTransaction.outputs.get(0)); //its important to store our first transaction in the UTXOs list.


            System.out.println("Creating and Mining Genesis block... ");
            Block genesis = new Block(true);
            genesis.addTransaction(genesisTransaction);
            addBlock(genesis);
            this.generated = true;
        } else {
            System.out.println("#Error: BlockMatrix already generated.");
        }
        /**
        Wallet coinbase = new Wallet();
        //create genesis transaction, which sends coins to walletA:
        genesisTransaction = new Transaction(coinbase.publicKey, wallet.publicKey, value, null, null);
        genesisTransaction.generateSignature(coinbase.privateKey);	 //manually sign the genesis transaction
        genesisTransaction.transactionId = "0"; //manually set the transaction id
        genesisTransaction.outputs.add(new TransactionOutput(genesisTransaction.recipient, genesisTransaction.value, genesisTransaction.transactionId)); //manually add the Transactions Output
        UTXOs.put(genesisTransaction.outputs.get(0).id, genesisTransaction.outputs.get(0)); //its important to store our first transaction in the UTXOs list.


        System.out.println("Creating and Mining Genesis block... ");
        Block genesis = new Block(true);
        genesis.addTransaction(genesisTransaction);
        addBlock(genesis);
         **/
    }

    //mines and adds a block
    public void addBlock(Block newBlock) {
        newBlock.mineBlock();
        add(newBlock);
    }

    //sets up our security provider so we can create our wallets
    public void setUpSecurity() {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    //sees if our matrix has maintained its security, or if it has been tampered with
    public Boolean isMatrixValid() {
        Block currentBlock;
        HashMap<String, TransactionOutput> tempUTXOs = new HashMap<String, TransactionOutput>(); //a temporary working list of unspent transactions at a given block state.
        tempUTXOs.put(genesisTransaction.outputs.get(0).id, genesisTransaction.outputs.get(0));

        //loop through matrix to check block hashes:
        for (int i = 2; i < getInputCount(); i++) { // start at 2 because we want to skip the genesis transaction/block
            currentBlock = getBlock(i);
            //compare registered hash and calculated hash:
            if (!currentBlock.getHash().equals(currentBlock.calculateHash())) {
                System.out.println("Hashes for Block " + i + " not equal (first instance of block with unequal hashes, there may be more)");
                return false;
            }

            /**
             //check if hash is solved
             if(!currentBlock.getHash().substring( 0, difficulty).equals(hashTarget)) {
             System.out.println("Block " + i +  " hasn't been mined (first instance of unmined block, there may be more)");
             return false;
             }
             **/

            //loop through blockchains transactions:
            TransactionOutput tempOutput;
            for(int t=0; t <currentBlock.getTransactions().size(); t++) {
                Transaction currentTransaction = currentBlock.getTransactions().get(t);

                if(!currentTransaction.verifySignature()) {
                    System.out.println("#Signature on Transaction(" + t + ") is Invalid");
                    return false;
                }
                if(currentTransaction.getInputsValue() != currentTransaction.getOutputsValue()) {
                    System.out.println("#Inputs are not equal to outputs on Transaction(" + t + ")");
                    return false;
                }

                for(TransactionInput input: currentTransaction.inputs) {
                    tempOutput = tempUTXOs.get(input.transactionOutputId);

                    if(tempOutput == null) {
                        System.out.println("#Referenced input on Transaction(" + t + ") in Block(" + i + ") is Missing");
                        return false;
                    }

                    if(input.UTXO.value != tempOutput.value) {
                        System.out.println("#Referenced input Transaction(" + t + ") value is Invalid");
                        return false;
                    }

                    tempUTXOs.remove(input.transactionOutputId);
                }

                for(TransactionOutput output: currentTransaction.outputs) {
                    tempUTXOs.put(output.id, output);
                }

                if( currentTransaction.outputs.get(0).recipient != currentTransaction.recipient) {
                    System.out.println("#Transaction(" + t + ") output recipient is not who it should be");
                    return false;
                }
                if( currentTransaction.outputs.get(1).recipient != currentTransaction.sender) {
                    System.out.println("#Transaction(" + t + ") output 'change' is not sent to sender.");
                    return false;
                }

            }

        }

        //check if all row hashes are valid
        for (int i = 0; i < this.getDimension(); i++) {
            if (!this.calculateRowHash(i).equals(this.getRowHashes()[i])) {
                System.out.println("Row hashes for row " + i + " not equal (first instance of row with unequal hashes, there may be more");
                return false;
            }
        }

        //check if all column hashes are valid
        for (int i = 0; i < this.getDimension(); i++) {
            if (!this.calculateColumnHash(i).equals(this.getColumnHashes()[i])) {
                System.out.println("Column hashes for row " + i +  " not equal (first instance of column with unequal hashes, there may be more");
                return false;
            }
        }

        //check if all deletions have been valid
        if (!this.getDeletionValidity()) {
            System.out.println("One or more deletions were not valid and altered more than one row and column hash");
            return false;
        }

        return true;
    }

}
