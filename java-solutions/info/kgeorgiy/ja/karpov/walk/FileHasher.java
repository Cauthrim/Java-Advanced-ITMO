package info.kgeorgiy.ja.karpov.walk;

import java.io.*;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileHasher {
    public static final String nullHash = "0".repeat(64);
    private static final int bufferSize = 1 << 16;
    private static MessageDigest digest;

    public static void setDigest() {
        try {
            FileHasher.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ignored) {
        }
    }

    public static String hash(Path file) {
        byte[] hash;
        try (FileInputStream in = new FileInputStream(file.toString())) {
            byte[] bytes = new byte[bufferSize];
            int res;
            try {
                while ((res = in.read(bytes)) != -1) {
                    digest.update(bytes, 0, res);
                }
            } catch (IOException e) {
                System.out.println("Error reading from file: " + file + e.getMessage());
                return nullHash;
            }
        } catch (SecurityException e) {
            System.out.println("No rights for file: " + file + e.getMessage());
            return nullHash;
        } catch (IOException e) {
            System.out.println("Error opening file: " + file + e.getMessage());
            return nullHash;
        }

        hash = digest.digest();
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        digest.reset();
        return hexString.toString();
    }
}
