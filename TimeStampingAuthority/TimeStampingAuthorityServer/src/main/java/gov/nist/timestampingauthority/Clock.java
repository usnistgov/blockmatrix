package gov.nist.timestampingauthority;// Acquired from https://commons.apache.org/proper/commons-net/

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Class linked to an NTP server (clock) providing time. It allows for local storage of this clock by storing offset
 * from local time and calculating the actual time using the offset. The clock is updated periodically to provide
 * accurate time.
 */
public class Clock {
    // class to schedule periodic clock synchronization
    // maximum number of threads is the number of clocks used
    // each thread will be named clock for debug purposes
    private static final ScheduledThreadPoolExecutor executor;

    static {
        // if corePoolSize (first parameter) is set to anything other than 0, the threads are not removed when the
        // program finishes, resulting in the program "runnning" while doing nothing
        // each thread where updateTime is run, is called clock (for debugging purposes
        executor = (ScheduledThreadPoolExecutor)
                Executors.newScheduledThreadPool(0, r -> new Thread(r, "clock"));
        executor.setRemoveOnCancelPolicy(true);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    private final NTPUDPClient client;

    // the fields from address to updateInterval can only be changed after clock restarts (stop and start)

    // address of the NTP server
    private InetAddress address;
    // save currently used address so that we can change it for the next restart
    private InetAddress currentAddress;
    private int port;
    // save currently used port so that we can change it for the next restart
    private int currentPort;
    private long startDelayMs;
    private int timeoutMs;
    private long updateInterval;

    private volatile boolean running;
    private volatile boolean accurate;
    private volatile long offset;
    private ScheduledFuture<?> updateFuture;

    /**
     * Create new clock by providing NTP server address and port.
     *
     * @param address        NTP server address
     * @param port           NTP server port
     * @param startDelayMs   start synchronization after specified time
     * @param timeoutMs      NTP server request timeout
     * @param updateInterval time interval between clock updates
     */
    public Clock(InetAddress address, int port, long startDelayMs, int timeoutMs, long updateInterval) {
        // each clock has a separate NTPUPDClient because the class is not thread-safe
        client = new NTPUDPClient();

        setAddress(address);
        setPort(port);
        setStartDelayMs(startDelayMs);
        setTimeoutMs(timeoutMs);
        setUpdateInterval(updateInterval);

        running = false;
        accurate = false;
    }

    /**
     * Get clock's NTP server address.
     *
     * @return server address
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * Set clock's NTP server address. Will only be updated after the clock is restarted.
     *
     * @param address NTP server address
     */
    public void setAddress(InetAddress address) {
        this.address = address;
    }

    /**
     * Get clock's NTP server port.
     *
     * @return server port
     */
    public int getPort() {
        return port;
    }

    /**
     * Set clock's NTP server port. Will only be updated after the clock is restarted.
     *
     * @param port NTP server port
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Get clock's start delay in ms.
     *
     * @return start delay in ms
     */
    public long getStartDelayMs() {
        return startDelayMs;
    }

    /**
     * Set clock's start delay in ms. Will only be updated after the clock is restarted.
     *
     * @param startDelayMs start delay in ms
     */
    public void setStartDelayMs(long startDelayMs) {
        this.startDelayMs = startDelayMs;
    }

    /**
     * Get clock's timeout - time waiting for the time server to respond.
     *
     * @return clock's timeout in ms
     */
    public int getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * Set clock's timeout - time waiting for the time server to respond. Will only be updated after the clock is
     * restarted.
     *
     * @param timeoutMs clock's timeout in ms
     */
    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Get clock's update interval.
     *
     * @return update interval in ms
     */
    public long getUpdateInterval() {
        return updateInterval;
    }

    /**
     * Set clock's update interval. Will only be updated after the clock is restarted.
     *
     * @param updateInterval update interval in ms
     */
    public void setUpdateInterval(long updateInterval) {
        this.updateInterval = updateInterval;
    }


    /**
     * Tells if the clock is running, i.e. being updated regularly. This does not guarantee that the clock is accurate
     * at the moment and can provide accurate time. Use isAccurate for that purpose.
     *
     * @return if the clock is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Tell whether the clock is accurate. The state can theoretically change between the calls to isAccurate() and
     * getTime(), but should not happen because of long update intervals.
     *
     * @return false if the last clock update was unsuccessful or the clock was not updated yet
     */
    public boolean isAccurate() {
        return accurate;
    }

    /**
     * Get clock's offset from the local time.
     *
     * @return offset in ms (add it to current local time to get correct time)
     * @throws IllegalStateException if the clock is currently inaccurate
     */
    public long getOffset() throws IllegalStateException {
        if (!isAccurate())
            throw new IllegalStateException("This clock is unsynchronized right now, so it should not be queried.");
        return offset;
    }

    /**
     * Get time of the clock by adding offset to the local machine time. Non-blocking.
     *
     * @return time in ms
     * @throws IllegalStateException if the clock is currently inaccurate
     */
    public long getTime() throws IllegalStateException {
        if (!isAccurate())
            throw new IllegalStateException("This clock is unsynchronized right now, so it should not be queried.");
        return System.currentTimeMillis() + offset;
    }


    /**
     * Start clock updates. From this point on every updateInterval milliseconds the clock gets updated, and if it
     * doesn't, it is in inaccurate state and cannot give time.
     */
    public void start() {
        running = true;
        client.setDefaultTimeout(getTimeoutMs());
        currentAddress = getAddress();
        currentPort = getPort();
        updateFuture = executor.scheduleAtFixedRate(this::updateTime, getStartDelayMs(),
                getUpdateInterval(), TimeUnit.MILLISECONDS);
    }

    /**
     * Stop clock update. After this method is called, isAccurate always returns false until the clock is started again.
     */
    public void stop() {
        // TODO when the task is completed it might override accurate to true, so it will be true even though the clock
        //  isn't running
        // if this is set to true, when the program stops, updateTime tasks do not, so we need to let them finish
        updateFuture.cancel(false);
        accurate = false;
        running = false;
    }

    /**
     * Update the offset of the clock by getting the accurate time from the server. TimeInfo class calculates the offset
     * that needs to be used automatically.
     */
    private void updateTime() {
        try {
            // this line is blocking!
            TimeInfo accurateTime = client.getTime(currentAddress, currentPort);
            // compute delay, offset and other details
            accurateTime.computeDetails();
            synchronized (this) {
                offset = accurateTime.getOffset();
                accurate = true;
            }
            // this should solve problem mentioned in stop method
            if (Thread.interrupted())
                accurate = false;
            System.out.println("Updated time from " + currentAddress.toString() + ".");
            System.out.println("Offset: " + offset + ". Delay: " + accurateTime.getDelay() + ".");
        } catch (IOException e) {
            System.out.println(currentAddress.toString() + " " + e.getMessage());
            accurate = false;
        }
    }
}
