package gov.nist.timestampingauthority;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class for using TsA server with CLI. This class parses command line arguments, checks their validity and runs the
 * server. It also handles the stopping of the server by listening to 'stop' keyword in the console while the server
 * is working. Consult the help message below for more information.
 *
 * Terminal help message:
 * This is a Timestamping Authority server that accepts time stamp requests via NTP protocol and responds
 * with time aggregated from multiple atomic clocks. It periodically synchronizes with those clocks to store
 * local copy of their time and uses these local times to calculate average and give it to the client. Clock
 * updates are spaced evenly in the update interval to minimize network load (3 clocks, update every 10
 * seconds, 1st starts at 0 ms, 2nd at 333 ms, 3rd at 666 ms). All clocks are assumed to operate on port 123.
 * If the file with clock addresses could not be opened, the execution is aborted.
 * Please do not group arguments, e.g. '-hap'. To stop the server enter 'stop' during execution.
 * List of commands:
 * -h, --help - display this help message
 * -u, --update-interval - interval to update clocks, updated every 10 seconds by default
 * -p, --port - port to run the NTP server, default is 124
 * (since default NTP port 123 is likely occupied on a testing machine)
 * -t, --timeout - clock response timeout, default is 500 ms
 * -f, --file-path - path to file containing clock addresses. This file must have the following format:
 * each line must either contain the NTP server address or start with % to indicate a comment.
 * Empty lines are permitted as well. Invalid or unreachable addresses are ignored. If no working
 * clocks were found, the server is stopped. Please note that if the text file was created with
 * Notepad on Windows it might have some special characters in the beginning of the file that may
 * hinder the file reading (something connected to UTF-8 with BOM). It is advised that you make
 * sure the file does not start with anything but spaces, actual server address or a percentage
 * character (%).
 * -s, --spread-clocks-off - whether the clocks are to be spread out to reduce the network load, e.g. if
 * there are 10 clocks and update interval is 1000, 1st clock starts at 0ms, 2nd at 100ms, etc. If
 * present, all clocks start updating simultaneously. Default is true.
 */
public class TimeStampingAuthorityServerRunner {
    private static final String HELP_MESSAGE =
            "This is a Timestamping Authority server that accepts time stamp requests via NTP protocol and responds\n" +
                    "with time aggregated from multiple atomic clocks. It periodically synchronizes with those clocks to store\n" +
                    "local copy of their time and uses these local times to calculate average and give it to the client. Clock\n" +
                    "updates are spaced evenly in the update interval to minimize network load (3 clocks, update every 10\n" +
                    "seconds, 1st starts at 0 ms, 2nd at 333 ms, 3rd at 666 ms). All clocks are assumed to operate on port 123.\n" +
                    "If the file with clock addresses could not be opened, the execution is aborted. \n" +
                    "Please do not group arguments, e.g. '-hap'. Please put the value of the property right after its name,\n" +
                    "e.g. '-u 10000 -p 124' and not '-u -p 10000 124'. To stop the server enter 'stop' during execution.\n" +
                    "List of commands:\n" +
                    "\t-h, --help - display this help message\n" +
                    "\t-u, --update-interval - interval to update clocks, updated every 10 seconds by default\n" +
                    "\t-p, --port - port to run the NTP server, default is 124\n" +
                    "\t\t(since default NTP port 123 is likely occupied on a testing machine)\n" +
                    "\t-t, --timeout - clock response timeout, default is 500 ms\n" +
                    "\t-f, --file-path - path to file containing clock addresses. This file must have the following format:\n" +
                    "\t\teach line must either contain the NTP server address or start with % to indicate a comment.\n" +
                    "\t\tEmpty lines are permitted as well. Invalid or unreachable addresses are ignored. If no working\n" +
                    "\t\tclocks were found, the server is stopped. Please note that if the text file was created with\n" +
                    "\t\tNotepad on Windows it might have some special characters in the beginning of the file that may\n" +
                    "\t\thinder the file reading (something connected to UTF-8 with BOM). It is advised that you make\n" +
                    "\t\tsure the file does not start with anything but spaces, actual server address or a percentage\n" +
                    "\t\tcharacter (%)." +
                    "\t-s, --spread-clocks-off - whether the clocks are to be spread out to reduce the network load, e.g. if\n" +
                    "\t\tthere are 10 clocks and update interval is 1000, 1st clock starts at 0ms, 2nd at 100ms, etc. If\n" +
                    "\t\tpresent, set to true, so all clocks start updating simultaneously. Default is false.";

    private static final List<String> helpNames = Arrays.asList("-h", "--help");
    private static final List<String> updateIntervalNames = Arrays.asList("-u", "--update-interval");
    private static final List<String> portNames = Arrays.asList("-p", "--port");
    private static final List<String> timeoutNames = Arrays.asList("-t", "--timeout");
    private static final List<String> filePathNames = Arrays.asList("-f", "--file-path");
    private static final List<String> spreadOutClocksNames = Arrays.asList("-s", "--spread-clocks");

    private static final long DEFAULT_UPDATE_INTERVAL = 10000;
    private static final int DEFAULT_PORT = 124;
    private static final int DEFAULT_TIMEOUT = 500;
    private static final Path DEFAULT_FILE_PATH = Paths.get("clocks.txt");
    private static final boolean DEFAULT_SPREAD_OUT_CLOCKS_OFF = false;

    private static Long updateInterval;
    private static Integer port;
    private static Integer timeout;
    private static Path filePath;
    private static boolean spreadOutClocksOff;

    private static TimeStampingAuthorityServer server;

    /**
     * Run the TsA server with given command line arguments. See help message above for details.
     *
     * @param args CLI arguments
     */
    public static void main(String[] args) {
        updateInterval = null;
        port = null;
        timeout = null;
        filePath = null;
        spreadOutClocksOff = DEFAULT_SPREAD_OUT_CLOCKS_OFF;

        try {
            parseArguments(Arrays.asList(args));
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return;
        }

        updateInterval = updateInterval != null ? updateInterval : DEFAULT_UPDATE_INTERVAL;
        port = port != null ? port : DEFAULT_PORT;
        timeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
        filePath = filePath != null ? filePath : DEFAULT_FILE_PATH;
        // default for spreadoutClocksOff is already set and checked in parseArgs

        try {
            server = new TimeStampingAuthorityServer(updateInterval, timeout, filePath, !spreadOutClocksOff, port);
            server.start();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }

        // start the thread reading stop command
        new Thread(TimeStampingAuthorityServerRunner::readStopCommand, "ReadStop").start();
    }

    /**
     * Parse CLI arguments into fields to create a TsA server with needed configuration.
     *
     * @param args CLI arguments
     * @throws IllegalArgumentException when unknown argument is found, when one of the values is invalid or could not
     *                                  be parsed, when multiple values of the same property were provided, when help message is to be shown, when the
     *                                  property name is present but there is no value after it
     */
    private static void parseArguments(List<String> args) throws IllegalArgumentException {
        // combine all lists into one
        List<String> allKnownNames = Stream.of(
                helpNames, updateIntervalNames, portNames, timeoutNames, filePathNames, spreadOutClocksNames
        )
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        List<String> propertyNames = Stream.of(updateIntervalNames, portNames, timeoutNames, filePathNames)
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

        updateInterval = TimeStampingAuthorityServerRunner
                .<Long>parseProperty(args, updateIntervalNames, Long::parseLong, "update interval");
        port = TimeStampingAuthorityServerRunner
                .<Integer>parseProperty(args, portNames, Integer::parseInt, "server port");
        timeout = TimeStampingAuthorityServerRunner
                .<Integer>parseProperty(args, timeoutNames, Integer::parseInt, "clocks timeout");
        filePath = TimeStampingAuthorityServerRunner
                .parseProperty(args, filePathNames, Paths::get, "clocks file path");

        spreadOutClocksOff = TimeStampingAuthorityServerRunner
                .parseFlag(args, spreadOutClocksNames, "spread out clocks flag");
    }

    /**
     * Parse a flag with given set of names (-s and --spread-clocks-off) and return the boolean value of this flag on
     * success. Throw an exception with a meaningful message otherwise.
     *
     * @param args     CLI arguments
     * @param names    list of names the property can be referred to (-t and --timeout)
     * @param flagName pretty name used in error messages
     * @return true if flag is present, false otherwise
     * @throws IllegalArgumentException when the same flag is mentioned more than once
     */
    private static boolean parseFlag(List<String> args, List<String> names, String flagName) {
        boolean result = false;

        for (String arg : args) {
            if (names.contains(arg)) {
                if (result) {
                    throw new IllegalArgumentException("Multiple " + flagName + "s provided.");
                }
                result = true;
            }
        }

        return result;
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
        while (server.isRunning()) {
            try {
                if (System.in.available() == 0) {
                    continue;
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
                server.stop();
            }

            if (!scanner.hasNextLine()) {
                continue;
            }

            String command = scanner.nextLine();
            if (!command.trim().toLowerCase().equals("stop")) {
                System.out.println("Unknown command.");
                continue;
            }

            server.stop();
        }
        scanner.close();
    }
}
