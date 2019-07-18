package gov.nist.blockmatrixtimestamped;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TensorTest {
    @Test
    public void testSetWidth2Dim4() {
        int dimCount = 4;
        int width = 2;

        Tensor<Integer> t1 = new Tensor<>(dimCount, width);
        for (int i = 0; i < Tensor.binPow(width, dimCount); ++i) {
            t1.set(i + 1, i);
        }

        Tensor<Integer> t2 = new Tensor<>(dimCount, width);
        for (int i = 0; i < Tensor.binPow(width, dimCount); ++i) {
            t2.set(i + 1, Tensor.indexToIndexes(t2.getDimCount(), t2.getWidth(), i));
            System.out.print(i + ": ");
            for (int idx : Tensor.indexToIndexes(t2.getDimCount(), t2.getWidth(), i)) {
                System.out.print(idx + " ");
            }
            System.out.println();
        }

        assertEquals(t1, t2);
    }

    @Test
    public void testSetWidth3Dim5() {
        int dimCount = 5;
        int width = 3;

        Tensor<Integer> t1 = new Tensor<>(dimCount, width);
        for (int i = 0; i < Tensor.binPow(width, dimCount); ++i) {
            t1.set(i + 1, i);
        }

        Tensor<Integer> t2 = new Tensor<>(dimCount, width);
        for (int i = 0; i < Tensor.binPow(width, dimCount); ++i) {
            t2.set(i + 1, Tensor.indexToIndexes(t2.getDimCount(), t2.getWidth(), i));
            System.out.print(i + ": ");
            for (int idx : Tensor.indexToIndexes(t2.getDimCount(), t2.getWidth(), i)) {
                System.out.print(idx + " ");
            }
            System.out.println();
        }

        assertEquals(t1, t2);
    }

    @Test
    public void testGetSet() {
        int dimCount = 3;
        int width = 3;

        Tensor<Integer> t1 = new Tensor<>(dimCount, width);

        assertEquals(t1.getDimCount(), dimCount);
        assertEquals(t1.getWidth(), width);
        assertEquals(t1.size(), 0);
        assertEquals(t1.capacity(), Tensor.binPow(width, dimCount));

        for (int i = 0; i < t1.capacity(); ++i) {
            t1.set(i + 1, i);
        }

        for (int i = 0; i < t1.capacity(); ++i) {
            System.out.print(t1.get(i) + ": ");
            for (int idx : Tensor.indexToIndexes(t1.getDimCount(), t1.getWidth(), i)) {
                System.out.print(idx + " ");
            }
            System.out.println();
            assertEquals(t1.get(i), t1.get(Tensor.indexToIndexes(dimCount, width, i)));
        }

        assertEquals(t1.getDimCount(), dimCount);
        assertEquals(t1.getWidth(), width);
        assertEquals(t1.capacity(), t1.size());
        assertEquals(t1.capacity(), Tensor.binPow(width, dimCount));

        t1.set(100, 5);

        assertEquals(t1.get(5), Integer.valueOf(100));

        for (int i = 0; i < t1.capacity(); ++i) {
            System.out.println(t1.get(i));
            assertEquals(t1.get(i), t1.get(Tensor.indexToIndexes(dimCount, width, i)));
        }

        assertEquals(t1.getDimCount(), dimCount);
        assertEquals(t1.getWidth(), width);
        assertEquals(t1.capacity(), t1.size());
        assertEquals(t1.capacity(), Tensor.binPow(width, dimCount));
    }

    @Test
    public void testBFS() {
        int dimCount = 7;
        int width = 3;

        Tensor<Integer> tensor = new Tensor<>(dimCount, width);

        int[] start = new int[dimCount];
        Queue<int[]> queue = new ArrayDeque<>();

        queue.add(start);

        Function<int[], int[][]> nextIndexes = (int[] idx) -> {
            int[] next;
            int[][] res = new int[dimCount][];

            for (int i = 0; i < dimCount; ++i) {
                next = idx.clone();
                next[i]++;
                if (next[i] >= width)
                    continue;
                res[i] = next;
            }

            return res;
        };

        int idx = 0;
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();

            if (tensor.get(cur) != null)
                continue;

            tensor.set(idx, cur);
            idx++;

            for (int[] next : nextIndexes.apply(cur)) {
                if (next != null)
                    queue.add(next);
            }
        }
    }

    @Test
    public void testIteration() {
        int dimCount = 4;
        int width = 3;

        Tensor<Integer> t = new Tensor<>(dimCount, width);
        for (int i = 0; i < Tensor.binPow(width, dimCount); ++i) {
            t.set(i + 1, i);
        }

        boolean equal = true;
        int i = 0;
        for (int val : t.getLine(3, 0, 0, 0)) {
            System.out.println(val);
            if (i + 1 != val) {
                equal = false;
            }
            i++;
        }

        assertTrue(equal);
    }
}
