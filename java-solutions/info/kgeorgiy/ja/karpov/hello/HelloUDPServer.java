package info.kgeorgiy.ja.karpov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server side of UDP connection.
 */
public class HelloUDPServer implements HelloServer {
    private DatagramSocket socket;
    private ExecutorService requestExecutor;

    /**
     * Starts a new Hello server.
     * This method should return immediately.
     *
     * @param port    server port.
     * @param threads number of working threads.
     */
    @Override
    public void start(int port, int threads) {
        int bufferSize;
        try {
            socket = new DatagramSocket(port);
            bufferSize = socket.getReceiveBufferSize();
        } catch (SocketException e) {
            System.out.println("Could not initialize socket");
            return;
        }

        requestExecutor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            requestExecutor.submit(() -> {
                byte[] buffer = new byte[bufferSize];
                DatagramPacket packet = new DatagramPacket(buffer, bufferSize);
                while (!socket.isClosed()) {
                    try {
                        socket.receive(packet);
                        packet.setData(("Hello, " + new String(packet.getData(), packet.getOffset(),
                                packet.getLength())).getBytes(StandardCharsets.UTF_8));
                        socket.send(packet);
                    } catch (IOException ignored) {
                    }
                }
            });
        }
    }

    /**
     * Stops server and deallocates all resources.
     */
    @Override
    public void close() {
        socket.close();
        Util.shutdownExecutor(requestExecutor);
    }

    /**
     * Method used to use the class from command line.
     * The first argument is the server port number, while the second one is the number of threads to use.
     *
     * @param args - arguments to pass.
     */
    public static void main(String[] args) {
        Util.UDPServerMain(args, HelloUDPServer.class);
    }
}
