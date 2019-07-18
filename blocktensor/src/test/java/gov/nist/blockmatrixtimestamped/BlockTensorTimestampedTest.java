package gov.nist.blockmatrixtimestamped;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.Security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlockTensorTimestampedTest {
    @Before
    public void prepare() {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        Security.removeProvider("BC");
    }

    @Test
    public void test2Dim() throws IOException {
        int dimCount = 2;
        int width = 3;

        BlockTensor bt = new BlockTensorTimestamped(width, dimCount);

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
    public void testNDim() throws IOException {
        int dimCount = 7;
        int width = 3;

        BlockTensor bt = new BlockTensorTimestamped(width, dimCount);

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
}
