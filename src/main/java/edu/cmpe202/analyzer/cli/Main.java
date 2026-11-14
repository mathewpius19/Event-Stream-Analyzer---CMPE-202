package edu.cmpe202.analyzer.cli;

import edu.cmpe202.analyzer.application.AnalyzerFactory;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;

public final class Main {
    private static final String USAGE = "Usage: java -jar analyzer.jar --file <filename.txt> [--outdir <directory>]";

    private Main() {
    }

    public static void main(String[] arguments) {
        System.exit(run(arguments, System.out, System.err));
    }

    /** Returns 0 for success/help, 2 for argument errors, and 1 for file/I/O errors. */
    public static int run(String[] arguments, PrintStream out, PrintStream err) {
        if (arguments.length == 1 && (arguments[0].equals("--help") || arguments[0].equals("-h"))) {
            out.println(USAGE);
            return 0;
        }
        CliOptions options;
        try {
            options = CliOptions.parse(arguments);
        } catch (IllegalArgumentException exception) {
            err.println("Error: " + exception.getMessage());
            err.println(USAGE);
            return 2;
        }
        try {
            if (!Files.isRegularFile(options.file()) || !Files.isReadable(options.file())) {
                err.println("Error: Input file does not exist or is not a readable regular file: " + options.file());
                return 1;
            }
            AnalyzerFactory.createDefault().analyze(options.file(), options.outdir());
            return 0;
        } catch (IOException | SecurityException exception) {
            err.println("Error: Unable to process input or write reports: " + exception.getMessage());
            return 1;
        }
    }
}

