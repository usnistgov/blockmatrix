package blockmatrix;

import java.security.Security;
import java.util.HashMap;

public class MatrixChain {

    private static BlockMatrix bm;
    public static HashMap<String, TransactionOutput> UTXOs = new HashMap<>();

    public static int difficulty = 3;
    public static float minimumTransaction = 0.1f;
    public static Wallet walletA;
    public static Wallet walletB;
    public static Transaction genesisTransaction;

    public static void main(String[] args) {

        bm = new BlockMatrix(5);
        bm.setUpSecurity();
        bm.setMinimumTransaction(3f);

        //Create wallets:
        walletA = new Wallet();

        bm.generate(walletA, 200f);
        walletB = new Wallet();

        //testing
        Block block2 = new Block();
        System.out.println("\nWalletA's balance is: " + walletA.getBalance());
        System.out.println("\nWalletA is Attempting to send funds (40) to WalletB...");
        block2.addTransaction(walletA.sendFunds(walletB.publicKey, 40f, "Here is 40 coins!"));
        block2.addTransaction(walletA.sendFunds(walletB.publicKey, 20f, "Here is another 20!"));
        block2.printBlockTransactions();
        bm.addBlock(block2);
        bm.clearInfoInTransaction(2, 1);
        block2.printBlockTransactions();
        System.out.println("\nWalletA's balance is: " + walletA.getBalance());
        System.out.println("WalletB's balance is: " + walletB.getBalance());


        System.out.println("\nMatrix is Valid: " + bm.isMatrixValid());


    }



}
