package blockmatrix;

public class TransactionInput {
    String transactionOutputId; //Reference to TransactionOutputs -> transactionId
    TransactionOutput UTXO; //Contains the Unspent transaction output

    TransactionInput(String transactionOutputId) {
        this.transactionOutputId = transactionOutputId;
    }

    public String getTransactionOutputId() {
        return this.transactionOutputId;
    }

    public TransactionOutput getUTXO() {
        return this.UTXO;
    }
}
