package gov.nist.blockmatrixtimestamped;

import org.junit.Test;

public class BlockTest {
    @Test
    public void test() {
        Block b = new Block(System.currentTimeMillis(), "hello".getBytes());
        System.out.println("Block created.");
        System.out.println();

        System.out.println("Data: " + new String(b.getData()));
        System.out.println("Timestamp: " + b.getTimestamp());
        System.out.println("Hash: " + SecurityUtil.bytesToHex(b.getHash()));
    }
}
