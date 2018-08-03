package blockmatrix;

import java.util.ArrayList;
import java.util.Date;

public class Block {

    private String hash;
    private String merkleRoot;
    private ArrayList<Transaction> transactions = new ArrayList<>();
    private long timeStamp; //number of milliseconds since 1/1/1970
    private int nonce;
    private boolean genesis; // whether or not this block is the genesis block, by default it is not

    //Block Constructor
    public Block() {
        this.timeStamp = new Date().getTime();
        this.hash = calculateHash();
        this.genesis = false;
    }

    Block(boolean genesis) {
        this.timeStamp = new Date().getTime();
        this.hash = calculateHash();
        this.genesis = genesis;
    }

    String calculateHash() {
        return StringUtil.applySha256(Long.toString(timeStamp) + Integer.toString(nonce) + merkleRoot); // calculates and returns hash
    }

    void mineBlock() {
        merkleRoot = StringUtil.getMerkleRoot(transactions);
        hash = calculateHash();
        System.out.println("Block Mined: " + hash);
    }

    //Add transactions to this block
    public boolean addTransaction(Transaction transaction) {
        //process transaction and check if valid, unless block is genesis block then ignore.
        if (transaction == null) {
            return false;
        }
        if(!genesis) {
            if((transaction.processTransaction() != true)) {
                System.out.println("Transaction failed to process. Discarded.");
                return false;
            }
        }
        transactions.add(transaction);
        System.out.println("Transaction Successfully added to Block");
        return true;
    }

    public ArrayList<Transaction> getTransactions() {
        return this.transactions;
    }

    public String getHash() {
        return hash;
    }

    public void printBlockTransactions() {
        System.out.println("\nBlock transactions: ");
        int count = 1;
        for (Transaction t: transactions) {
            System.out.println("Info for transaction " + count + " in this block:");
            System.out.println(t.toString());
            count++;
        }

    }

    void clearInfoInTransactionsInBlock(int transactionNumber) {
        transactions.get(transactionNumber).clearInfo();
        merkleRoot = StringUtil.getMerkleRoot(transactions);
        this.hash = calculateHash();
    }

}
