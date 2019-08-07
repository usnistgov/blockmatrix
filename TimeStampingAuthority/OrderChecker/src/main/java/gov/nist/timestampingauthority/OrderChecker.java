package gov.nist.timestampingauthority;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class that checks the order of logged time stamps. It checks whether the order in which they were added is
 * consistent with the times. If there is inconsistency, it prints a message to the console.
 * <p>
 * Terminal help message:
 * This is a program that checks order of logged time stamps in multiple log files produced by timestampingauthority.Client class.
 * The time stamps are expected to be entered in chronological order, so this program checks if time stamps
 * are indeed in ascending order. You can check multiple files at once. By default, all .txt files in the
 * current directory are checked. You can provide a file name prefix to filter the files to be checked. You
 * can also provide a path to the directory where the files are located. Please do not group arguments, e.g.
 * '-hap'. List of commands:
 * -h, --help - display this help message
 * -p, --path - path to the directory where the files are located, e.g. -p ../data
 * --prefix - files only with this prefix are to be analyzed
 */
public class OrderChecker {
    private static final String HELP_MESSAGE =
            "This is a program that checks order of logged time stamps in multiple log files produced by timestampingauthority.Client class.\n" +
                    "The time stamps are expected to be entered in chronological order, so this program checks if time stamps\n" +
                    "are indeed in ascending order. You can check multiple files at once. By default, all .txt files in the\n" +
                    "current directory are checked. You can provide a file name prefix to filter the files to be checked. You\n" +
                    "can also provide a path to the directory where the files are located. Please do not group arguments, e.g.\n" +
                    "'-hap'. List of commands:\n" +
                    "\t-h, --help - display this help message\n" +
                    "\t-p, --path - path to the directory where the files are located, e.g. -p ../data\n" +
                    "\t--prefix - files only with this prefix are to be analyzed\n";

    private static final List<String> helpNames = Arrays.asList("-h", "--help");
    private static final List<String> pathNames = Arrays.asList("-p", "--path");
    private static final List<String> prefixNames = Arrays.asList("-p", "--prefix");

    private static final Path DEFAULT_PATH = Paths.get("");
    private static final String DEFAULT_PREFIX = "NIST";

    private static Path path;
    private static String prefix;

    private static volatile boolean runningChecker;

    /**
     * Start order checker program.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        path = null;
        prefix = null;

        try {
            parseArguments(Arrays.asList(args));
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return;
        }

        path = path != null ? path : DEFAULT_PATH;
        prefix = prefix != null ? prefix : DEFAULT_PREFIX;

        try {
            checkOrder();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }

        // start the thread reading stop command
        new Thread(OrderChecker::readStopCommand, "ReadStop").start();
    }

    /**
     * Check order of time stamps in the files. The time stamps are simply checked to be in strictly ascending order.
     * There is a message if a discrepancy is found. Nothing is printed if the order was never violated. The file is
     * expected to have a single 64-bit integer on every line. If the format is incorrect, the corresponding message
     * is printed.
     *
     * @throws IOException if a file could not be opened or any other filesystem error occurred
     */
    private static void checkOrder() throws IOException {
        path = path != null ? path : Paths.get("");
        prefix = prefix != null ? prefix : "NIST";

        // get paths to all files that start with PREFIX in the PATH directory
        List<Path> filePaths;
        try (Stream<Path> paths = Files.list(path)) {
            filePaths = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .collect(Collectors.toList());
        }

        for (Path path : filePaths) {
            System.out.println("Opening file " + path.getFileName() + ".");

            Scanner scanner = new Scanner(path);

            long prevTime = 0;
            int lineNumber = 1;
            while (scanner.hasNextLine()) {
                long curTime;
                try {
                    curTime = Long.parseLong(scanner.nextLine().split(" ")[0]);
                } catch (NumberFormatException e) {
                    System.out.println("Incorrect file format. 64-bit integers on every line expected.");
                    System.out.println(e.getMessage());
                    scanner.close();
                    return;
                }

                if (curTime == prevTime) {
                    System.out.println("LOSS OF STRICT ORDER on line " + lineNumber +
                            " in file " + path.getFileName() + ".");
                } else if (curTime < prevTime) {
                    System.out.println("LOSS OF ORDER on line " + lineNumber +
                            " in file " + path.getFileName() + ".");
                }
                prevTime = curTime;
                lineNumber++;
            }

            scanner.close();

            System.out.println("File " + path.getFileName() + " checked.");
        }
    }

    /**
     * Parse CLI arguments into fields to create an order checker with needed configuration.
     *
     * @param args CLI arguments
     * @throws IllegalArgumentException when unknown argument is found, when one of the values is invalid or could not
     *                                  be parsed, when multiple values of the same property were provided, when help message is to be shown, when the
     *                                  property name is present but there is no value after it
     */
    private static void parseArguments(List<String> args) throws IllegalArgumentException {
        // combine all lists into one
        List<String> allKnownNames = Stream.of(helpNames, pathNames, prefixNames)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        List<String> propertyNames = Stream.of(pathNames, prefixNames)
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

        prefix = OrderChecker.
                parseProperty(args, prefixNames, Function.identity(), "update interval");
        path = OrderChecker.
                parseProperty(args, pathNames, Paths::get, "time stamps file path");
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
     * Run an input loop that waits for "stop" command to stop the order checker. Any other command result in the
     * corresponding error message and the execution is continued.
     */
    private static void readStopCommand() {
        Scanner scanner = new Scanner(System.in);
        while (runningChecker) {
            try {
                if (System.in.available() == 0) {
                    continue;
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
                runningChecker = false;
            }

            if (!scanner.hasNextLine()) {
                continue;
            }

            String command = scanner.nextLine();
            if (!command.trim().toLowerCase().equals("stop")) {
                System.out.println("Unknown command.");
                continue;
            }

            runningChecker = false;
        }
        scanner.close();
    }
}
