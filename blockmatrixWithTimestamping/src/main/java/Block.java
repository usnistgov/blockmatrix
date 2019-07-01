public class Block {
    private String hash;
    private long timeStamp; //number of milliseconds since 1/1/1970
    private String data;

    //Block Constructor
    public Block(String data) {
        timeStamp = System.currentTimeMillis();
        this.data = data;
        hash = calculateHash();
    }

    public String calculateHash() {
        return StringUtil.applySha256(timeStamp + data);
    }

    public String getHash() {
        return hash;
    }

    public String getData() {
        return data;
    }

    public void printBlockHash() {
        System.out.println("\nBlock hash: " + getHash());
    }

    public void clear() {
        timeStamp = System.currentTimeMillis();
        data = "";
        hash = calculateHash();
    }
}
