package gov.nist.timestampingauthority;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * The TimeStampingAuthority class uses NTP to communicate with clock servers. NTP is an extension of UDP protocol, so
 * this server uses UDP classes and commands under the hood. When the class is run, it creates Clock classes with
 * addresses from the file and makes them synchronize with corresponding time servers periodically. Each clock class
 * updates time using a separate thread.
 * <p>
 * All clocks are assumed to operate on port 123. If the file with clock addresses could not be opened, the execution
 * is aborted.
 */
public class TimeStampingAuthority {
    private static final long DEFAULT_UPDATE_INTERVAL = 10000;
    private static final int DEFAULT_TIMEOUT = 500;
    private static final Path DEFAULT_FILE_PATH = Paths.get("clocks.txt");
    private static final boolean DEFAULT_SPREAD_OUT_CLOCKS = false;

    // A set of clocks that know their NTP addresses and can get time from them.
    private final List<Clock> clocks;

    private long updateInterval;
    private int timeout;
    private Path filePath;
    private boolean spreadOutClocks;

    // Each received event is added into event queue for handling in the response loop run in a separate thread
    private volatile boolean runningClocks;


    /**
     * Create TimeStampingAuthority, initialize the clocks. You can provide null instead of an argument to use default
     * value. If one of the clock addresses could not be found, the clock is ignored and the message is printed into
     * System.out.
     *
     * @param updateInterval interval for clock update, default is 10000 ms (10 seconds), must be a positive integer in
     *                       ms. Values over 5000 ms are recommended due to clock server restrictions.
     * @param timeout        time to wait for clock response, default is 500 ms, must be a positive integer in ms. Values over
     *                       100 ms are recommended, due to network latency.
     * @param filePath       path to a file where the clock addresses are stored, must be a path to a readable file
     *                       containing clock addresses. This file must have the following format:
     *                       each line must either contain the NTP server address or start with % to indicate a comment.
     *                       Empty lines are permitted as well. Invalid or unreachable addresses are ignored. If no working
     *                       clocks were found, the server is stopped. Please note that if the text file was created with
     *                       Notepad on Windows it might have some special characters in the beginning of the file that may
     *                       hinder the file reading (something connected to UTF-8 with BOM). It is advised that you make
     *                       sure the file does not start with anything but spaces, actual server address or a percentage
     *                       character (%).
     * @param spreadOutClocks whether the clocks are to be spread out to reduce the network load, e.g. if there are 10
     *                        clocks and update interval is 1000, 1st clock starts at 0ms, 2nd at 100ms, etc. If set to
     *                        false, all clocks start updating simultaneously.
     * @throws IllegalArgumentException when one of the arguments is invalid or when none of the clocks has a valid
     *                                  address
     * @throws IOException              when the file could not be opened
     */
    public TimeStampingAuthority(Long updateInterval, Integer timeout, Path filePath, Boolean spreadOutClocks)
            throws IllegalArgumentException, IOException {
        clocks = new ArrayList<>();
        runningClocks = false;

        // set default values
        updateInterval = updateInterval != null ? updateInterval : DEFAULT_UPDATE_INTERVAL;
        timeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
        filePath = filePath != null ? filePath : DEFAULT_FILE_PATH;
        spreadOutClocks = spreadOutClocks != null ? spreadOutClocks : DEFAULT_SPREAD_OUT_CLOCKS;

        setUpdateInterval(updateInterval);
        setTimeout(timeout);
        setFilePath(filePath);
        setSpreadOutClocks(spreadOutClocks);

        loadClocks();

        if (clocks.size() == 0) {
            throw new IllegalArgumentException("None of the clock addresses were found.");
        }
    }

    /**
     * Read clock addresses from the file and initialize Clock classes with the data read. Can only be called when
     * the TsA is not running.
     *
     * @throws IOException           when the file could not be read
     * @throws IllegalStateException when called while the TsA is running
     */
    public void loadClocks() throws IOException, IllegalStateException {
        if (runningClocks) {
            throw new IllegalStateException("Cannot load new clocks when the TsA is running.");
        }

        Scanner scanner = new Scanner(filePath);
        List<String> clockAddresses = new ArrayList<>();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            // check if it is a comment
            if (line.trim().isEmpty() || line.startsWith("%")) {
                continue;
            }

            clockAddresses.add(line.trim());
        }

        // initialize clocks
        // each clock is queried every updateInterval milliseconds
        // in order to spread out the network load, the clocks are queried in equal intervals
        // i.e. if we have 10 clocks and update interval is 1 second, 1st starts at 0ms, 2nd at 100ms, etc.

        long startDelayMs = 0;
        long step = getUpdateInterval() / clockAddresses.size();
        // no need to change start delay
        if (!isSpreadOutClocks()) {
            step = 0;
        }
        for (String clockAddress : clockAddresses) {
            // when creating the clock, UnknownHostException can be thrown
            // if the address is invalid, the clock is not added
            try {
                InetAddress address = InetAddress.getByName(clockAddress);
                clocks.add(new Clock(address, 123, startDelayMs, getTimeout(), getUpdateInterval()));
            } catch (UnknownHostException e) {
                System.out.println(e.getMessage());
            } finally {
                startDelayMs += step;
            }
        }
    }


    /**
     * Get clock update interval.
     *
     * @return clock update interval
     */
    public long getUpdateInterval() {
        return updateInterval;
    }

    /**
     * Set and validate clock update interval. Will only be updated when the server is restarted.
     *
     * @param updateInterval clock update interval
     * @throws IllegalArgumentException when update interval is not a positive integer
     */
    public void setUpdateInterval(long updateInterval) throws IllegalArgumentException {
        if (updateInterval < 1) {
            throw new IllegalArgumentException("Update interval is invalid, please enter a positive integer.");
        }
        this.updateInterval = updateInterval;
        for (Clock clock : clocks) {
            clock.setUpdateInterval(getUpdateInterval());
        }
    }

    /**
     * Get clock timeout.
     *
     * @return clock timeout
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Set and validate clock timeout. Will only be updated when the server is restarted.
     *
     * @param timeout clock timeout
     * @throws IllegalArgumentException when timeout is not a positive integer
     */
    public void setTimeout(Integer timeout) throws IllegalArgumentException {
        if (timeout < 1) {
            throw new IllegalArgumentException("Timeout is invalid, please enter a positive integer.");
        }
        this.timeout = timeout;
        for (Clock clock : clocks) {
            clock.setTimeoutMs(getTimeout());
        }
    }

    /**
     * Get clocks file path.
     *
     * @return clocks file path
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Set and validate clocks file path. Will only be used when loadClocks() method is called.
     *
     * @param filePath clocks file path
     * @throws IllegalArgumentException when the file path is not pointing to a file
     */
    public void setFilePath(Path filePath) throws IllegalArgumentException {
        if (!Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("Provided path is not a file.");
        }
        this.filePath = filePath;
    }

    /**
     * Tell if the clocks are to be spread out to reduce the network load, e.g. if there are 10 clocks and
     * update interval is 1000, 1st clock starts at 0ms, 2nd at 100ms, etc. If set to false, all clocks start updating
     * simultaneously.
     *
     * @return whether the clocks are to be spread out in update interval
     */
    public boolean isSpreadOutClocks() {
        return spreadOutClocks;
    }

    /**
     * Set flag if the clocks are to be spread out to reduce the network load, e.g. if there are 10 clocks and
     * update interval is 1000, 1st clock starts at 0ms, 2nd at 100ms, etc. If set to false, all clocks start updating
     * simultaneously. Will only be updated when the server is restarted.
     *
     * @param spreadOutClocks whether the clocks are to be spread out in update interval
     */
    public void setSpreadOutClocks(boolean spreadOutClocks) {
        this.spreadOutClocks = spreadOutClocks;
    }


    /**
     * Return state of whether the server is running.
     *
     * @return true if server is running
     */
    public boolean isRunning() {
        return runningClocks;
    }

    /**
     * Check if accurate time available from the TsA. Time is not available if none of the clocks could be
     * reached. The state can theoretically change after the call if other thread tries to reach the available clocks
     * and fails.
     *
     * @return if time is available
     */
    public boolean isTimeAvailable() {
        return getAccurateClockCount() > 0;
    }

    /**
     * Number of synchronized clocks at the moment. The state can theoretically change after the call if other thread
     * tries to reach the available clocks and fails.
     *
     * @return number of synchronized clocks
     */
    public int getAccurateClockCount() {
        int count = 0;
        for (Clock clock : clocks) {
            if (clock.isAccurate())
                count++;
        }
        return count;
    }

    /**
     * Request aggregate time from the clocks in ms. Currently returns the average time. Returns null if no clocks were
     * used because none were synchronized at the moment of request.
     *
     * @return aggregate time from clocks in ms or null if no clocks are synchronized
     */
    public Long getAggregateTime() {
        // no overflow here
        long sum = 0;
        int clocksUsed = 0;
        for (Clock clock : clocks) {
            // synchronized on clock because we need to check for accuracy and get time atomically
            // otherwise the state could change between the calls
            synchronized (clock) {
                // if the clock is inaccurate, it is not used
                if (!clock.isAccurate())
                    continue;
                long time = clock.getTime();
                ++clocksUsed;
                sum += time;
            }
        }
        if (clocksUsed == 0) {
            return null;
        }
        return sum / clocksUsed;
    }


    /**
     * Start clock synchronization. You might need to wait some time between calling start() and getAggregateTime(),
     * because some time is needed to reach the clocks over the network. Use isTimeAvailable() to check if the time
     * can be queried now. This method is non-blocking.
     *
     * @throws IOException           when the server failed to connect to the socket.
     * @throws IllegalStateException when the TsA is already running
     */
    public void start() throws IOException, IllegalStateException {
        if (isRunning()) {
            throw new IllegalStateException("TsA is already running.");
        }

        runningClocks = true;
        // start clock synchronization
        for (Clock clock : clocks) {
            clock.start();
        }
    }

    /**
     * Stop clock synchronization.
     *
     * @throws IllegalStateException when the TsA is already stopped
     */
    public void stop() {
        if (!isRunning()) {
            throw new IllegalStateException("TsA is already stopped");
        }

        runningClocks = false;
        for (Clock clock : clocks) {
            clock.stop();
        }
    }
}
