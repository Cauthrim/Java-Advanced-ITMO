package info.kgeorgiy.ja.karpov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for shared executor shutdown.
 */
public class Util {
    public static final int SOCKET_SO_TIMEOUT = 100;

    private static String convertStr(String provided) {
        StringBuilder ans = new StringBuilder();
        for (int i = 0; i < provided.length(); i++) {
            if (Character.isDigit(provided.charAt(i))) {
                ans.append(Character.getNumericValue(provided.charAt(i)));
            } else {
                ans.append(provided.charAt(i));
            }
        }
        return ans.toString();
    }

    /**
     * Checks whether the response is valid.
     *
     * @param response      - response to check.
     * @param threadNumber  - first number to check.
     * @param requestNumber - second number to check.
     * @return the validation.
     */
    public static boolean checkResponse(String response, int threadNumber, int requestNumber) {
        return (convertStr(response).matches("[\\D]*" + threadNumber + "[\\D]*" + requestNumber + "[\\D]*"));
    }

    /**
     * Writes the result of a correct UDP response.
     *
     * @param message  - the string sent.
     * @param response - the string received.
     */
    public static void writeCorrectResponse(String message, String response) {
        System.out.println("Request: " + message + System.lineSeparator() + "Response: " + response);
    }

    /**
     * Generates request message for client.
     *
     * @param prefix        - prefix.
     * @param threadNumber  - thread number.
     * @param requestNumber - request number.
     * @return - request message.
     */
    public static String generateRequest(String prefix, int threadNumber, int requestNumber) {
        return prefix + threadNumber + '_' + requestNumber;
    }

    /**
     * Method for writing data into buffer from a channel.
     *
     * @param channel - channel to read from.
     * @param buffer - where to write.
     * @return - socket address.
     */
    public static SocketAddress receiveBuffer(DatagramChannel channel, ByteBuffer buffer) {
        SocketAddress address = null;
        try {
            buffer.clear();
            address = channel.receive(buffer);
            buffer.flip();
        } catch (IOException e) {
            System.out.println("Error while reading server response: " + e.getMessage());
        }
        return address;
    }

    /**
     * Method for writing data into buffer from byte array.
     *
     * @param buffer - where to write.
     * @param bytes - array to write from.
     */
    public static void putBuffer(ByteBuffer buffer, byte[] bytes) {
        buffer.clear();
        buffer.put(bytes);
        buffer.flip();
    }

    /**
     * Shuts down an executor as stated in {@link ExecutorService}.
     *
     * @param executor - the executor to shut down.
     */
    public static void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Generalised method used to use HelloClient from command line.
     * The arguments are:
     * 1. The server host or IP.
     * 2. Server port number.
     * 3. Request prefix.
     * 4. Number of threads to use.
     * 5. Number of requests per thread.
     *
     * @param args - arguments to pass.
     */
    public static void UDPClientMain(String[] args, Class<? extends HelloClient> client) {
        if (args == null) {
            System.out.println("Null arguments provided.");
            return;
        }
        if (args.length != 5) {
            System.out.println("Wrong number of arguments provided.");
            return;
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(args[0]);
        } catch (UnknownHostException e) {
            try {
                address = InetAddress.getByAddress(args[0].getBytes());
            } catch (UnknownHostException ex) {
                System.out.println("Illegal host name or ip");
                return;
            }
        }

        try {
            client.getDeclaredConstructor().newInstance().run(address.getHostName(), Integer.parseInt(args[1]), args[2],
                    Integer.parseInt(args[3]), Integer.parseInt(args[4]));
        } catch (NumberFormatException e) {
            System.out.println("Non-integer arguments provided where integer is required.");
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            System.out.println("Failed to create Client instance.");
        }
    }

    /**
     * Generalised method used to use HelloServer from command line.
     * The first argument is the server port number, while the second one is the number of threads to use.
     *
     * @param args - arguments to pass.
     */
    public static void UDPServerMain(String[] args, Class<? extends HelloServer> server) {
        if (args == null) {
            System.out.println("Null arguments provided");
            return;
        }
        if (args.length != 2) {
            System.out.println("Wrong number of arguments provided");
            return;
        }

        try (HelloServer UDPServer = server.getDeclaredConstructor().newInstance()) {
            UDPServer.start(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        } catch (NumberFormatException e) {
            System.out.println("Non-integer argument provided");
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
