package gov.nist.blockmatrixtimestamped;

import org.junit.Before;
import org.junit.Test;

import java.security.Security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlockMatrixTest {
    @Before
    public void prepare() {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        Security.removeProvider("BC");
    }

    @Test
    public void test() {
        int dimCount = 2;
        int width = 3;

        BlockMatrix bm = new BlockMatrix(width);

        Block b1 = new Block(System.currentTimeMillis(), "hello".getBytes());
        Block b2 = new Block(System.currentTimeMillis(), "there".getBytes());

        bm.add(b1);

        assertEquals(b1, bm.getBlock(1));
        assertEquals("hello", new String(bm.getBlockData(1)));
        assertEquals(1, bm.getInputCount());

        bm.add(b2);

        assertEquals(b2, bm.getBlock(2));
        assertEquals("there", new String(bm.getBlockData(2)));
        assertEquals(2, bm.getInputCount());

        assertTrue(bm.isMatrixValid());

        bm.clearBlock(1);

        assertEquals(b1, bm.getBlock(1));
        assertEquals("", new String(bm.getBlockData(1)));
        assertEquals(2, bm.getInputCount());
    }
}
