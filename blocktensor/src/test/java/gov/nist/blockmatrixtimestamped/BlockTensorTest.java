package gov.nist.blockmatrixtimestamped;

import org.junit.Before;
import org.junit.Test;

import java.security.Security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlockTensorTest {
    @Before
    public void prepare() {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        Security.removeProvider("BC");
    }

    @Test
    public void test2Dim() {
        int dimCount = 2;
        int width = 3;

        BlockTensor bt = new BlockTensor(width, dimCount);

        byte[] data1 = "hello".getBytes();
        byte[] data2 = "there".getBytes();

        bt.add(data1);

        assertEquals("hello", new String(bt.getData(0)));
        assertEquals(1, bt.size());

        bt.add(data2);

        assertEquals("there", new String(bt.getData(1)));
        assertEquals(2, bt.size());

        assertTrue(bt.isValid());

        bt.erase(0);

        assertEquals("", new String(bt.getData(0)));
        assertEquals(2, bt.size());
    }

    @Test
    public void testNDim() {
        int dimCount = 7;
        int width = 3;

        BlockTensor bt = new BlockTensor(width, dimCount);

        for (int i = 0; i < Tensor.binPow(width, dimCount); ++i) {
            byte[] data = ("Block " + i).getBytes();
            bt.add(data);

            assertEquals("Block " + i, new String(bt.getData(i)));
            assertEquals(i + 1, bt.size());

            assertTrue(bt.isValid());
        }

        bt.erase(0);

        assertEquals("", new String(bt.getData(0)));
        assertEquals(Tensor.binPow(width, dimCount), bt.size());
    }

    @Test
    // takes around 7 minutes to run with width = 1000
    public void testPerformance() {
        int width = 250;

        warmUp();

        long start;

        long blocktensorTotal = 0;
        long blockmatrixTotal = 0;

        start = System.nanoTime();
        BlockMatrix bm = new BlockMatrix(width);
        long bmInit = (System.nanoTime() - start) / 1000000;
        blockmatrixTotal += bmInit;
        System.out.println("Blockmatrix init: " + bmInit);

        start = System.nanoTime();
        BlockTensor bt = new BlockTensor(width * width);
        long btInit = (System.nanoTime() - start) / 1000000;
        blocktensorTotal += btInit;
        System.out.println("Blocktensor init: " + btInit);

        start = System.nanoTime();
        for (int i = 0; i < width * width - width; ++i) {
            bm.add(new Block(System.currentTimeMillis(), ("Block " + i).getBytes()));
        }
        long bmAddition = (System.nanoTime() - start) / 1000000;
        blockmatrixTotal += bmAddition;
        System.out.println("Blockmatrix: " + bmAddition);

        start = System.nanoTime();
        for (int i = 0; i < width * width - width; ++i) {
            bt.add(("Block " + i).getBytes());
        }
        long btAddition = (System.nanoTime() - start) / 1000000;
        blocktensorTotal += btAddition;
        System.out.println("Blocktensor: " + btAddition);

        System.out.println();
        System.out.println("Blocktensor TOTAL: " + blocktensorTotal);
        System.out.println("Blockmatrix TOTAL: " + blockmatrixTotal);

        /*
        1,000,000 additions
        Blockmatrix init: 346 ms
        Blocktensor init: 20839 ms
        Blockmatrix: 312743 ms
        Blocktensor: 41570 ms
        ~8 times faster with blocktensor
         */
    }

    @Test
    public void testAddAnDGet() {
        BlockTensor bt = new BlockTensor(100);

        int b1 = bt.add("First".getBytes());
        int b2 = bt.add("Second".getBytes());

        assertEquals(new String(bt.getData(b1)), "First");
        assertEquals(new String(bt.getData(b2)), "Second");
    }

    private void warmUp() {
        long x = 0;
        for (int i = 0; i < 1000000; ++i) {
            x *= Math.sqrt(i) * i + 3;
        }
    }
}
