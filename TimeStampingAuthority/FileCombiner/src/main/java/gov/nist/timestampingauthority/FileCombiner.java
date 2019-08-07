package gov.nist.timestampingauthority;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileCombiner {
    public static void main(String[] args) throws IOException {
        Path dirPath = Paths.get("");

        // get paths to all files that start with "NIST" in the PATH directory
        List<Path> filePaths;
        try (Stream<Path> paths = Files.list(dirPath)) {
            filePaths = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("NIST"))
                    .filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .collect(Collectors.toList());
        }

        List<String> lines = new ArrayList<>();

        int i = 1;
        for (Path path : filePaths) {
            System.out.println("Opening file " + path.getFileName() + ".");
            Scanner scanner = new Scanner(path);

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine() + " " + i;
                lines.add(line);
            }

            scanner.close();
            System.out.println("File " + path.getFileName() + " read.");

            ++i;
        }

        Collections.sort(lines);

        BufferedWriter writer = new BufferedWriter(new FileWriter("all.txt"));
        writer.write(String.join("\n", lines) + "\n");
        writer.close();
    }
}
