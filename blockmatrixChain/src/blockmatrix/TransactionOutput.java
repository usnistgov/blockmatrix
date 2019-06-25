package blockmatrix;

import java.security.PublicKey;

public class TransactionOutput {
    String id;
    PublicKey recipient; // The new owner of these coins.
    float value; // The amount of coins they own
    private String parentTransactionId; // The id of the transaction this output was created in

    //Constructor
    TransactionOutput(PublicKey reciepient, float value, String parentTransactionId) {
        this.recipient = reciepient;
        this.value = value;
        this.parentTransactionId = parentTransactionId;
        this.id = StringUtil.applySha256(StringUtil.getStringFromKey(reciepient)+Float.toString(value)+parentTransactionId);
    }

    //Check if coin belongs to you
    boolean isMine(PublicKey publicKey) {
        return (publicKey.equals(recipient));
    }

    public String getId() {
        return this.id;
    }

    public PublicKey getRecipient() {
        return this.recipient;
    }

    public float getValue() {
        return this.value;
    }

    public String getParentTransactionId() {
        return this.parentTransactionId;
    }

}
