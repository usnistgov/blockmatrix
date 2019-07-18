package gov.nist.blockmatrixtimestamped;

import gov.nist.timestampingauthority.TimeStampingAuthority;

import java.io.IOException;

/**
 * Blocktensor that uses Timestamping Authority for timestamping of blocks. You need to have a clocks.txt file
 * in the execution directory.
 */
// TODO make possible to change file source
public class BlockTensorTimestamped extends BlockTensor {
    private static final TimeStampingAuthority tsa;

    // initialize TimeStampingAuthority
    static {
        TimeStampingAuthority tmp = null;
        try {
            tmp = new TimeStampingAuthority(null, null, null, null);
            tmp.start();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.out.println("Could not create and start TimeStampingAuthority for the blocktensor.");
            System.exit(1);
        }
        tsa = tmp;
    }

    /**
     * Create new timestamped blocktensor.
     *
     * @param width    width of each line
     * @param dimCount dimension count
     */
    public BlockTensorTimestamped(int width, int dimCount) {
        super(width, dimCount);
    }

    /**
     * Get timestamp from the Timestamping Authority.
     *
     * @return timestamp
     */
    @Override
    protected long timestamp() {
        while (!tsa.isTimeAvailable()) ;
        Long timestamp = tsa.getAggregateTime();
        // it can actually be null if the availability changes between the first line and the second line
        assert timestamp != null;
        return timestamp;
    }
}
