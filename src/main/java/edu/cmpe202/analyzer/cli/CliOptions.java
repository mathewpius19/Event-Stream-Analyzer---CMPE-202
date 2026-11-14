package edu.cmpe202.analyzer.cli;

import java.nio.file.Path;

public record CliOptions(Path file, Path outdir) {
    public static CliOptions parse(String[] arguments) {
        Path file = null;
        Path outdir = Path.of(".");
        boolean outputSpecified = false;
        for (int index = 0; index < arguments.length; index++) {
            String option = arguments[index];
            if (!option.equals("--file") && !option.equals("--outdir")) {
                throw new IllegalArgumentException("Unknown argument: " + option);
            }
            if (index + 1 >= arguments.length || arguments[index + 1].startsWith("--")
                    || arguments[index + 1].isBlank()) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            Path value = Path.of(arguments[++index]);
            switch (option) {
                case "--file" -> {
                    if (file != null) {
                        throw new IllegalArgumentException("Duplicate --file argument");
                    }
                    file = value;
                }
                case "--outdir" -> {
                    if (outputSpecified) {
                        throw new IllegalArgumentException("Duplicate --outdir argument");
                    }
                    outdir = value;
                    outputSpecified = true;
                }
                default -> throw new IllegalStateException("Unexpected validated option");
            }
        }
        if (file == null) {
            throw new IllegalArgumentException("Missing required --file <filename.txt>");
        }
        return new CliOptions(file, outdir);
    }
}

