package info.kgeorgiy.ja.karpov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

/**
 * Client side of UDP connection.
 */
public class HelloUDPClient implements HelloClient {
    private void sendRequest(int threadNumber, int requestNumber, int bufferSize, SocketAddress address,
                             DatagramSocket socket, String prefix) {
        String message = prefix + threadNumber + '_' + requestNumber;
        byte[] buffer = new byte[bufferSize];
        DatagramPacket request = new DatagramPacket(message.getBytes(), message.getBytes().length, address);
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        while (!socket.isClosed()) {
            try {
                socket.send(request);
                socket.receive(response);

                String answer = new String(response.getData(), response.getOffset(), response.getLength(),
                        StandardCharsets.UTF_8);
                if (Util.checkResponse(answer, threadNumber, requestNumber)) {
                    Util.writeCorrectResponse(message, answer);
                    break;
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void threadSubmit(ExecutorService executor, Phaser ph, SocketAddress address,
                              String prefix, int requests, int threadNumber) {
        ph.register();
        executor.submit(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(Util.SOCKET_SO_TIMEOUT);

                for (int requestNumber = 1; requestNumber <= requests; requestNumber++) {
                    sendRequest(threadNumber, requestNumber, socket.getReceiveBufferSize(), address, socket, prefix);
                }
            } catch (SocketException e) {
                System.out.println("Socket error " + e.getMessage());
            } finally {
                ph.arriveAndDeregister();
            }
        });
    }

    /**
     * Runs Hello client.
     * This method should return when all requests are completed.
     *
     * @param host     server host.
     * @param port     server port.
     * @param prefix   request prefix.
     * @param threads  number of request threads.
     * @param requests number of requests per thread.
     */
    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        try {
            Phaser ph = new Phaser(1);
            SocketAddress address = new InetSocketAddress(InetAddress.getByName(host), port);
            ExecutorService requestExecutor = Executors.newFixedThreadPool(threads);

            for (int i = 1; i <= threads; i++) {
                threadSubmit(requestExecutor, ph, address, prefix, requests, i);
            }

            ph.arriveAndAwaitAdvance();
            Util.shutdownExecutor(requestExecutor);
        } catch (UnknownHostException e) {
            System.out.println("Unknown host name " + e.getMessage());
        }
    }

    /**
     * Method used to use the class from command line.
     * The arguments are:
     * 1. The server host or IP.
     * 2. Server port number.
     * 3. Request prefix.
     * 4. Number of threads to use.
     * 5. Number of requests per thread.
     *
     * @param args - arguments to pass.
     */
    public static void main(String[] args) {
        Util.UDPClientMain(args, HelloUDPClient.class);
    }
}
