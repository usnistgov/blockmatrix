package gov.nist.blockmatrixtimestamped;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Tensor auxiliary data structure. This data structure is "square" multidimensional array, where the number of
 * dimensions and width of dimensions can be specified. Once created, the size of the array and dimensions is fixed.
 *
 * @param <T> any type to be stored
 */
public class Tensor<T> implements Iterable<T> {
    /**
     * Array to store data.
     */
    private final ArrayList<T> data;
    /**
     * Number of dimensions.
     */
    private final int dimCount;
    /**
     * Width of dimensions.
     */
    private final int width;

    /**
     * Number of elements stored in the tensor at the moment.
     */
    private int size;

    /**
     * Create new tensor with given number of dimensions and width.
     *
     * @param dimCount number of dimensions, a positive integer
     * @param width    width of each dimension, a positive integer
     */
    public Tensor(int dimCount, int width) {
        if (dimCount < 1)
            throw new IllegalArgumentException("Dimension count must be greater than zero.");
        if (width < 1)
            throw new IllegalArgumentException("Width must be greater than zero.");

        this.dimCount = dimCount;
        this.width = width;
        data = new ArrayList<>(Collections.nCopies(binPow(width, dimCount), null));

        size = 0;
    }

    /**
     * Integer binary exponentiation. Returns a^b. b must be non-negative, a and b cannot be 0 at the same time.
     * Overflow is not checked.
     *
     * @param a integer
     * @param b non-negative integer
     * @return a^b
     */
    public static int binPow(int a, int b) {
        int res = 1;
        while (b > 0) {
            if ((b & 1) != 0)
                res = res * a;
            a = a * a;
            b >>= 1;
        }
        return res;
    }

    /**
     * Convert multidimensional index to one-dimensional index of the tensor.
     *
     * @param dimCount dimension count of the tensor, a positive integer
     * @param width    width of the tensor, a positive integer
     * @param indexes  indexes of the element. Either 1 or dimCount indexes must be present. Each index must be in the
     *                 interval [0 .. dimCount). If one-dimensional indexing is used, the index must be in the interval
     *                 [0 .. width^dimCount).
     * @return one-dimensional index equivalent to the multidimensional index provided
     * @throws IndexOutOfBoundsException if the requirements for the arguments are not satisfied
     */
    public static int indexesToIndex(int dimCount, int width, int... indexes)
            throws IndexOutOfBoundsException, IllegalArgumentException {
        if (dimCount < 1)
            throw new IllegalArgumentException("Dimension count must be greater than zero.");
        if (width < 1)
            throw new IllegalArgumentException("Width must be greater than zero.");
        if (indexes.length != dimCount) {
            throw new IndexOutOfBoundsException(indexes.length + " arguments provided, but the dimension count is " + dimCount);
        }
        for (int i : indexes) {
            Objects.checkIndex(i, width);
        }

        int res = 0;
        int multiple = binPow(width, dimCount - 1);
        for (int i : indexes) {
            res += multiple * i;
            multiple /= width;
        }
        return res;
    }

    /**
     * Convert one-dimensional index to multidimensional index of the tensor.
     *
     * @param dimCount dimension count of the tensor, a positive integer
     * @param width    width of the tensor, a positive integer
     * @param index    index of the element, a non-negative integer in the interval [0 .. width^dimCount)
     * @return multidimensional index equivalent to the one-dimensional index provided
     * @throws IndexOutOfBoundsException if the requirements for the arguments are not satisfied
     */
    public static int[] indexToIndexes(int dimCount, int width, int index) throws IndexOutOfBoundsException {
        Objects.checkIndex(index, binPow(width, dimCount));

        int[] res = new int[dimCount];
        int i = dimCount - 1;
        while (index != 0) {
            res[i] = index % width;
            index /= width;
            i--;
        }
        return res;
    }

    /**
     * Get number of dimensions.
     *
     * @return dimension count
     */
    public int getDimCount() {
        return dimCount;
    }

    /**
     * Get width of the tensor.
     *
     * @return width of the tensor
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get number of elements in the tensor at the moment.
     *
     * @return number of elements
     */
    public int size() {
        return size;
    }

    /**
     * Get capacity of the tensor. It is fixed and is equal to width^dimCount.
     *
     * @return number of elements stored in tensor, a non-negative integer
     */
    public int capacity() {
        return data.size();
    }

    /**
     * Return true if the tensor is empty.
     *
     * @return whether the tensor is empty
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Access the element in tensor. You can either use a multidimensional indexing or the underlying one-dimensional
     * index. If dimCount is 4, the multidimensional indexes look like {i1, i2, i3, i4} and one-dimensional index is
     * just {i}, where i = i1*width^3 + i2*width^2 + i3*width + i4. Return null if the given location has not been
     * written to yet.
     *
     * @param indexes indexes of the element. Either 1 or dimCount indexes must be present. Each index must be in the
     *                interval [0 .. dimCount). If one-dimensional indexing is used, the index must be in the interval
     *                [0 .. width^dimCount).
     * @return the element at the given index
     * @throws IndexOutOfBoundsException if any index is not in the required interval or the indexes array is not of
     *                                   proper length
     */
    public T get(int... indexes) throws IndexOutOfBoundsException {
        if (indexes.length == 1) {
            Objects.checkIndex(indexes[0], capacity());
            return data.get(indexes[0]);
        }
        return data.get(indexesToIndex(getDimCount(), getWidth(), indexes));
    }

    /**
     * Set the provided value to the given index. Return the previous value at the given index. Return null if the
     * location has not yet been written to. You can either use a multidimensional indexing or the underlying
     * one-dimensional index. If dimCount is 4, the multidimensional indexes look like {i1, i2, i3, i4} and
     * one-dimensional index is just {i}, where i = i1*width^3 + i2*width^2 + i3*width + i4.
     *
     * @param value   value to be set
     * @param indexes indexes of the element. Either 1 or dimCount indexes must be present. Each index must be in the
     *                interval [0 .. dimCount). If one-dimensional indexing is used, the index must be in the interval
     *                [0 .. width^dimCount).
     * @return the value that was at this index before. Null if the location has not yet been written to.
     */
    public T set(T value, int... indexes) {
        T old;
        if (indexes.length == 1)
            old = data.set(indexes[0], value);
        else
            old = data.set(indexesToIndex(getDimCount(), getWidth(), indexes), value);

        if (old == null && value != null)
            size++;
        else if (old != null && value == null)
            size--;
        return old;
    }

    /**
     * Return one-dimensional index of the given value. The entire tensor is searched linearly for the value. If
     * multidimensional index is needed, use indexesOf instead.
     *
     * @param value value to be searched for
     * @return one-dimensional index
     */
    public int indexOf(T value) {
        return data.indexOf(value);
    }

    /**
     * Return multidimensional index of the given value. The entire tensor is searched linearly for the value. If
     * one-dimensional index is needed, use indexOf instead.
     *
     * @param value value to be searched for
     * @return multidimensional index
     */
    public int[] indexesOf(T value) {
        return indexToIndexes(getDimCount(), getWidth(), data.indexOf(value));
    }

    /**
     * Checks whether given value is present in tensor.
     *
     * @param value value to be searched for
     * @return whether the value is present in the tensor
     */
    public boolean contains(T value) {
        return indexOf(value) != -1;
    }

    /**
     * Checks whether two tensors are equal by comparing their dimension count, width and then comparing the values
     * stored.
     *
     * @param o other object
     * @return whether the objects are equal
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;
        if (o.getClass() != getClass())
            return false;
        Tensor<T> t;
        try {
            t = (Tensor<T>) o;
        } catch (Exception e) {
            return false;
        }

        return getDimCount() == t.getDimCount() &&
                getWidth() == t.getWidth() &&
                size() == t.size() &&
                data.equals(t.data);

    }

    /**
     * Get the line of values in the tensor by letting one index vary and fixing all other indexes. Returns an immutable
     * view of the tensor.
     *
     * @param varDimIdx    index of the variable index (if 4 dimensions and we want 2nd index to vary, it should be 1).
     *                     Should be in the interval [0 .. dimCount).
     * @param fixedIndexes array of other indexes that are fixed, should not include the variable index. For example, if
     *                     4 dimensions and we want to access line at {2, 1, x, 5}, where x is a variable index,
     *                     varDimIdx should be 2 and fixedIndexes should be {2, 1, 5}. Length of the array should be
     *                     dimCount-1. Each index should be in the interval [0 .. width).
     * @return immutable view of the line
     * @throws IndexOutOfBoundsException if any requirement for the arguments is not fulfilled
     */
    public Collection<T> getLine(int varDimIdx, int... fixedIndexes) throws IndexOutOfBoundsException {
        if (fixedIndexes.length != getDimCount() - 1)
            throw new IndexOutOfBoundsException(fixedIndexes.length + " arguments provided, but the dimension count is " + getDimCount());
        for (int i : fixedIndexes)
            Objects.checkIndex(i, getWidth());
        Objects.checkIndex(varDimIdx, dimCount);

        return new LineView(varDimIdx, fixedIndexes);
    }

    /**
     * Return iterator of the entire tensor. Iterates over the underlying array. The same as iterating using get method
     * with one-dimensional index varying from 0 to width^dimCount-1.
     *
     * @return iterator of the tensor
     */
    @Override
    public Iterator<T> iterator() {
        return new TensorIterator(0);
    }

    /**
     * Class to represent view of the line of the tensor. An immutable collection. Stores fixed indexes and the index
     * of the variable index.
     */
    private final class LineView extends AbstractCollection<T> {
        /**
         * Array of other indexes that are fixed, should not include the variable index. For example, if
         * 4 dimensions and we want to access line at {2, 1, x, 5}, where x is a variable index,
         * varDimIdx should be 2 and fixedIndexes should be {2, 1, 5}. Length of the array should be
         * dimCount-1. Each index should be in the interval [0 .. width).
         */
        private final int[] fixedIndexes;
        /**
         * Index of the variable index (if 4 dimensions and we want 2nd index to vary, it should be 1). Should be in
         * the interval [0 .. dimCount).
         */
        private final int varDimIdx;

        /**
         * Create new line view. Refer to the fields description.
         *
         * @param varDimIdx    index of the variable index
         * @param fixedIndexes array of the fixed indexes
         */
        public LineView(int varDimIdx, int... fixedIndexes) {
            this.fixedIndexes = fixedIndexes.clone();
            this.varDimIdx = varDimIdx;
        }

        /**
         * Return length of the line. Does not tell how many elements in the line are set. Basically returns width
         * of the underlying tensor.
         *
         * @return length of the line
         */
        @Override
        public final int size() {
            return getWidth();
        }

        /**
         * Return iterator of the line.
         *
         * @return iterator
         */
        @Override
        public final Iterator<T> iterator() {
            return new LineViewIterator(varDimIdx, fixedIndexes);
        }
    }

    /**
     * Iterator of the line. Does not support removal.
     */
    private final class LineViewIterator implements Iterator<T> {
        /**
         * Array of indexes to access the array. The index at varDimIdx is variable, while other indexes are fixed.
         * The length of this array is dimCount.
         */
        private final int[] indexes;
        /**
         * Index of the variable index (if 4 dimensions and we want 2nd index to vary, it should be 1). Should be in
         * the interval [0 .. dimCount).
         */
        private final int varDimIdx;

        /**
         * Create new line view iterator.
         *
         * @param varDimIdx    index of the variable index (if 4 dimensions and we want 2nd index to vary, it should be 1).
         *                     Should be in the interval [0 .. dimCount).
         * @param fixedIndexes array of other indexes that are fixed, should not include the variable index. For
         *                     example, if 4 dimensions and we want to access line at {2, 1, x, 5}, where x is a
         *                     variable index, varDimIdx should be 2 and fixedIndexes should be {2, 1, 5}. Length of
         *                     the array should be dimCount-1. Each index should be in the interval [0 .. width).
         */
        public LineViewIterator(int varDimIdx, int... fixedIndexes) {
            this.varDimIdx = varDimIdx;
            // make stream of the indexes
            ArrayList<Integer> indexes = IntStream.of(fixedIndexes.clone())
                    // convert int to Integer
                    .boxed()
                    // save to ArrayList
                    .collect(Collectors.toCollection(ArrayList::new));
            // add index at varDimIdx to be variable
            indexes.add(varDimIdx, 0);
            // convert ArrayList back to array
            this.indexes = indexes.stream().mapToInt(x -> x).toArray();
        }

        /**
         * Check if there is the next element in line.
         *
         * @return whether there is the next element
         */
        @Override
        public boolean hasNext() {
            return indexes[varDimIdx] < getWidth();
        }

        /**
         * Return the next element in the line. May return null if the element at the location has not been set yet.
         *
         * @return next element
         */
        @Override
        public T next() {
            T res = get(indexes);
            indexes[varDimIdx]++;
            return res;
        }

        /**
         * Return index of the next element. If there is no next element, then the index at the varDimIdx is equal to
         * width of the underlying tensor.
         *
         * @return multidimensional index of the next element
         */
        public int[] getIndexes() {
            return indexes.clone();
        }

        /**
         * Return the index of the variable index.
         *
         * @return index of the variable index
         */
        public int getVarDimIdx() {
            return varDimIdx;
        }
    }

    /**
     * Iterator of the entire tensor. Used to iterate over the entire tensor. The same as iterating using get method
     * with one-dimensional index varying form 0 to width^dimCount-1.
     */
    private class TensorIterator implements Iterator<T> {
        /**
         * Current index of the iterator.
         */
        private int curIdx;

        /**
         * Create new tensor iterator starting from the given index.
         *
         * @param startIdx starting index
         */
        public TensorIterator(int startIdx) {
            curIdx = startIdx;
        }

        /**
         * Check if there is next element present in the tensor.
         *
         * @return whether there is the next element present
         */
        @Override
        public boolean hasNext() {
            return curIdx >= size();
        }

        /**
         * Return the next element in the tensor.
         *
         * @return next element
         */
        @Override
        public T next() {
            return get(curIdx++);
        }

        /**
         * Get the index of the next element. If the last element has been read, width^dimCount is returned.
         *
         * @return index of the next element
         */
        public int getCurIdx() {
            return curIdx;
        }
    }
}
