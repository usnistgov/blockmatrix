package gov.nist.timestampingauthority;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Acquired from https://commons.apache.org/proper/commons-net/

/**
 * Test class making NTP requests to local TsA server.
 * <p>
 * The client assumes the server is serving at port 124 at localhost. Apache Commons NTP implementation has been used.
 * <p>
 * Terminal help message:
 * This is an NTP client that requests time from a given port at a specified rate. It logs the received
 * time stamps in a file called 'NAME-millisecondsFrom1970.txt'. In order to start the client, please
 * provide at least the address of the NTP server, e.g. '-a time.nist.gov'. All times are in milliseconds.
 * Please do not group arguments, e.g. '-hap'. To stop the client enter 'stop' during execution.
 * List of commands:
 * -h, --help - display this help message
 * -a, --address - address of the NTP server, can be IP-address
 * -p, --port - port to connect to the NTP server, default is 123 (default NTP port)
 * -r, --repeat - interval between requests to the server, default is 100
 * -n, --name - name of this client to be used in log file name, default is current machine name
 * -c, --count - number of time stamps to collect before stopping, default is 1000, 0 or negative
 * values mean that the execution can only be stopped manually
 * -f, --file-path - path to a directory to save log file
 */
public class Client {
    private static final String HELP_MESSAGE =
            "This is an NTP client that requests time from a given port at a specified rate. It logs the received\n" +
                    "time stamps in a file called 'NAME-millisecondsFrom1970.txt'. In order to start the client, please\n" +
                    "provide at least the address of the NTP server, e.g. '-a time.nist.gov'. All times are in milliseconds.\n" +
                    "Please do not group arguments, e.g. '-hap'. To stop the client enter 'stop' during execution.\n" +
                    "List of commands:\n" +
                    "\t-h, --help - display this help message\n" +
                    "\t-a, --address - address of the NTP server, can be IP-address\n" +
                    "\t-p, --port - port to connect to the NTP server, default is 123 (default NTP port)\n" +
                    "\t-r, --repeat - interval between requests to the server, default is 100\n" +
                    "\t-n, --name - name of this client to be used in log file name, default is current machine name\n" +
                    "\t-c, --count - number of time stamps to collect before stopping, default is 10000, 0 or negative\n" +
                    "\t\tvalues mean that the execution can only be stopped manually\n" +
                    "\t-f, --file-path - path to a directory to save log file";

    private static final List<String> helpNames = Arrays.asList("-h", "--help");
    private static final List<String> addressNames = Arrays.asList("-a", "--address");
    private static final List<String> portNames = Arrays.asList("-p", "--port");
    private static final List<String> repeatNames = Arrays.asList("-r", "--repeat");
    private static final List<String> nameNames = Arrays.asList("-n", "--name");
    private static final List<String> countNames = Arrays.asList("-c", "--count");
    private static final List<String> pathNames = Arrays.asList("-f", "--file-path");

    private static final int DEFAULT_PORT = 123;
    private static final int DEFAULT_REPEAT = 100;
    private static final String DEFAULT_NAME;
    private static final int DEFAULT_COUNT = 1000;
    private static final Path DEFAULT_PATH = Paths.get("");
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    // we can use non-thread-safe StringBuffer instead of StringBuilder because only one thread writes into the buffer.
    private static final StringBuffer buffer = new StringBuffer();
    private static InetAddress address;
    private static Integer port;
    private static Integer repeat;
    private static String name;
    private static Integer count;
    private static Path path;
    private static int requestCount;
    // represents percentage of requests completed, can be 0-10 (0%-100%)
    private static int completionStage;
    private static volatile boolean runningClient;

    // set default file name prefix to be the machine name
    static {
        // a portable way to get the machine name on Windows, macOS and Linux
        Map<String, String> env = System.getenv();
        if (env.containsKey("COMPUTERNAME"))
            DEFAULT_NAME = env.get("COMPUTERNAME");
        else
            DEFAULT_NAME = env.getOrDefault("HOSTNAME", "Unknown Computer");
    }

    /**
     * Run the client.
     *
     * @param args CLI arguments
     */
    public static void main(String[] args) {
        requestCount = 0;
        completionStage = 0;
        runningClient = false;

        repeat = null;
        name = null;
        count = null;
        address = null;
        port = null;
        path = null;

        try {
            parseArguments(Arrays.asList(args));
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return;
        }

        if (address == null) {
            System.out.println("NTP server address is required.");
            return;
        }

        repeat = repeat != null ? repeat : DEFAULT_REPEAT;
        name = name != null ? name : DEFAULT_NAME;
        count = count != null ? count : DEFAULT_COUNT;
        // no default address, required field
        port = port != null ? port : DEFAULT_PORT;
        path = path != null ? path : DEFAULT_PATH;

        try {
            start();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }

        // start the thread reading stop command
        new Thread(Client::readStopCommand, "ReadStop").start();
    }

    /**
     * Start the client by scheduling periodic time requests. Non-blocking.
     */
    public static void start() {
        runningClient = true;
        executor.scheduleAtFixedRate(Client::requestTimeAndLog, 0, repeat, TimeUnit.MILLISECONDS);
        System.out.println("Client started.");
    }

    /**
     * Stop client, both input thread and request thread are stopped, each time stamps from the server is logged and
     * saved into the file NAME-millisecondsFrom1970.txt.
     */
    private static void stopAndWrite() {
        executor.shutdown();
        runningClient = false;

        String filePath = path.toAbsolutePath().toString() + "\\" + name + "-" + System.currentTimeMillis() + ".txt";
        System.out.println("Writing to " + filePath);
        try (FileWriter writer = new FileWriter(filePath)) {
            BufferedWriter bufferedWriter = new BufferedWriter(writer);
            bufferedWriter.append(buffer);
            bufferedWriter.flush();
        } catch (IOException e) {
            System.out.println("Could not write to file.");
            System.out.println(e.getMessage());
        }
    }

    /**
     * Make a request to the specified address. The result of the request is written into the buffer, which is logged
     * into a file when the client is stopped. The client is stopped when count time stamps have been recorded.
     */
    private static void requestTimeAndLog() {
        try {
            // NTPUDPClient, NtpV3Packet and TimeInfo are from Apache Commons
            NTPUDPClient timeClient = new NTPUDPClient();

            System.out.println("Requesting timestamp from " + address.toString() + ".");

            TimeInfo timeInfo = timeClient.getTime(address, port);
            timeInfo.computeDetails();
            // t1 + offset, where t1 is time when the message was sent by the client
            long timestamp = timeInfo.getMessage().getOriginateTimeStamp().getTime() + timeInfo.getOffset();

            System.out.println("Timestamp received: " + new Date(timestamp).toString() + ".");

            buffer.append(timestamp)
                    .append(" ").append(System.currentTimeMillis())
                    .append('\n');
            requestCount++;
            if (count > 0 && requestCount >= completionStage * count / 10) {
//                System.out.println((completionStage * 10) + "% of requests completed.");
                completionStage++;
            }
            if (requestCount == count) {
                stopAndWrite();
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Parse CLI arguments into fields to create a client with needed configuration.
     *
     * @param args CLI arguments
     * @throws IllegalArgumentException when unknown argument is found, when one of the values is invalid or could not
     *                                  be parsed, when multiple values of the same property were provided, when help
     *                                  message is to be shown, when the property name is present but there is no value
     *                                  after it
     */
    private static void parseArguments(List<String> args) throws IllegalArgumentException, UncheckedIOException {
        // combine all lists into one
        List<String> allKnownNames = Stream.of(
                helpNames, addressNames, portNames, repeatNames, nameNames, countNames, pathNames
        )
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        List<String> propertyNames = Stream.of(
                addressNames, portNames, repeatNames, nameNames, countNames, pathNames
        )
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        boolean unknownNamePresent = args.stream()
                // leave only arguments that are not known names
                .filter((String arg) -> !allKnownNames.contains(arg))
                // check if any of the unknown names does not go after a property name
                // (which would mean it is a property value)
                .anyMatch((String arg) -> {
                    // arg will always be there, so index != -1
                    int index = args.indexOf(arg) - 1;
                    return index == -1 || !propertyNames.contains(args.get(index));
                });

        if (unknownNamePresent) {
            throw new IllegalArgumentException("Unknown argument. Please consult message below.\n" + HELP_MESSAGE);
        }

        if (!Collections.disjoint(args, helpNames)) {
            throw new IllegalArgumentException(HELP_MESSAGE);
        }

        address = Client
                .parseProperty(args, addressNames, (String s) -> {
                    // because Consumer class's accept method is not declared to throw any checked exceptions
                    try {
                        return InetAddress.getByName(s);
                    } catch (UnknownHostException e) {
                        throw new UncheckedIOException(e);
                    }
                }, "NTP server address");
        port = Client
                .<Integer>parseProperty(args, portNames, Integer::parseInt, "NTP server port");
        repeat = Client
                .<Integer>parseProperty(args, repeatNames, Integer::parseInt, "repeat interval");
        name = Client
                .parseProperty(args, nameNames, Function.identity(), "file name prefix");
        count = Client
                .<Integer>parseProperty(args, countNames, Integer::parseInt, "request count");
        path = Client
                .parseProperty(args, pathNames, Paths::get, "clocks file path");
    }

    /**
     * Parse a property with given set of names (-p and --port) and return the value of this property on success.
     * Throw an exception with a meaningful message otherwise. Other exceptions may be thrown by parsing function.
     *
     * @param args         CLI arguments
     * @param names        list of names the property can be referred to (-t and --timeout)
     * @param parse        function taking a string and converting it to a value of the property
     * @param propertyName pretty name used in error messages
     * @param <T>          type of the property (e.g., Integer for port number)
     * @return parsed property value
     * @throws IllegalArgumentException when the same property is mentioned more than once, when the property name
     *                                  is provided, but the value is not
     */
    private static <T> T parseProperty(List<String> args, List<String> names, Function<String, T> parse,
                                       String propertyName) throws IllegalArgumentException {
        T result = null;

        for (int i = 0; i < args.size(); ++i) {
            if (names.contains(args.get(i))) {
                if (result != null) {
                    throw new IllegalArgumentException("Multiple " + propertyName + "s provided.");
                }
                i++;
                if (i >= args.size()) {
                    throw new IllegalArgumentException("No " + propertyName + " provided, but the property name is.");
                }
                result = parse.apply(args.get(i));
            }
        }

        return result;
    }

    /**
     * Run an input loop that waits for "stop" command to stop the client. Any other command result in the corresponding
     * error message and the execution is continued.
     */
    private static void readStopCommand() {
        Scanner scanner = new Scanner(System.in);
        while (runningClient) {
            try {
                if (System.in.available() == 0) {
                    continue;
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
                stopAndWrite();
            }

            if (!scanner.hasNextLine()) {
                continue;
            }

            String command = scanner.nextLine();
            if (!command.trim().toLowerCase().equals("stop")) {
                System.out.println("Unknown command.");
                continue;
            }

            stopAndWrite();
        }
        scanner.close();
    }
}
