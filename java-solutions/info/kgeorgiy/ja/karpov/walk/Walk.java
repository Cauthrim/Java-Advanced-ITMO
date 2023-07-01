package info.kgeorgiy.ja.karpov.walk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;


public class Walk {
    private static Path makePath(String file, String error) {
        Path res;
        try {
            res = Path.of(file);
            if (res.getParent() != null) {
                try {
                    Files.createDirectories(res.getParent());
                } catch (IOException e) {
                    System.out.println("Cannot create parent directory for " + error);
                    return null;
                }
            }
        } catch (InvalidPathException e) {
            System.out.println("Cannot get " + error + " path");
            return null;
        }

        return res;
    }

    public static void doTheWalk(String[] args, boolean isRecursive) {
        if (args == null) {
            System.out.println("Null arguments provided");
            return;
        }
        if (args.length != 2) {
            System.out.println("Wrong number of arguments provided");
            return;
        }
        if (args[0] == null) {
            System.out.println("Input file provided is null");
            return;
        }
        if (args[1] == null) {
            System.out.println("Output file provided is null");
            return;
        }

        Path inPath = makePath(args[0], "input");
        Path outPath = makePath(args[1], "output");
        if (inPath == null || outPath == null) {
            return;
        }

        try (BufferedReader in = Files.newBufferedReader(inPath, StandardCharsets.UTF_8)) {
            try (BufferedWriter out = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE)) {
                String path;
                FileHasher.setDigest();
                try {
                    while ((path = in.readLine()) != null) {
                        try {
                            Files.walkFileTree(Path.of(path), new SimpleFileVisitor<>() {
                                boolean isDirectory = false;
                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                    try {
                                        out.write(FileHasher.hash(file) + " " + file + System.lineSeparator());
                                        isDirectory = true;
                                    } catch (IOException e) {
                                        System.out.println("Error writing to output file " + e.getMessage());
                                    }
                                    return FileVisitResult.CONTINUE;
                                }

                                @Override
                                public FileVisitResult postVisitDirectory(Path file, IOException exc) {
                                    if (!isDirectory && !isRecursive) {
                                        try {
                                            out.write(FileHasher.nullHash + " " + file + System.lineSeparator());
                                        } catch (IOException e) {
                                            System.out.println("Error writing to output file " + e.getMessage());
                                        }
                                    }
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        } catch (InvalidPathException e) {
                            System.out.println("Error trying to get file path " + e.getMessage());
                            out.write(FileHasher.nullHash + " " + path + System.lineSeparator());
                        } catch (IOException e) {
                            System.out.println("Error trying to read from file " + e.getMessage());
                            out.write(FileHasher.nullHash + " " + path + System.lineSeparator());
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Error reading from input file " + e.getMessage());
                }
            } catch (SecurityException e) {
                System.out.println("No rights for output " + e.getMessage());
            } catch (IOException e) {
                System.out.println("Cannot access output file " + args[1] + ": " + e.getMessage());
            }
        } catch (SecurityException e) {
            System.out.println("No rights for input " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Cannot access input file " + args[0] + ": " + e.getMessage());
        }
    }
    public static void main(String[] args) {
        doTheWalk(args, false);
    }
}
