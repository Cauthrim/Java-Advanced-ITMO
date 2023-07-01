package info.kgeorgiy.ja.karpov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server side of UDP connection using NIO with single-thread operations with socket.
 */
public class HelloUDPNonblockingServer implements HelloServer {
    private final Queue<Packet> packetQueue = new ConcurrentLinkedDeque<>();
    private final Queue<ByteBuffer> freeBuffers = new ConcurrentLinkedDeque<>();
    private Selector selector;
    private DatagramChannel datagramChannel;
    private ExecutorService receiver;
    private ExecutorService workers;

    /**
     * Starts a new Hello server.
     * This method should return immediately.
     *
     * @param port    server port.
     * @param threads number of working threads.
     */
    @Override
    public void start(int port, int threads) {
        InetSocketAddress address = new InetSocketAddress(port);
        try {
            selector = Selector.open();

            datagramChannel = DatagramChannel.open();
            datagramChannel.bind(address);
            datagramChannel.configureBlocking(false);
            datagramChannel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            System.out.println("Failed to open connection: " + e.getMessage());
            return;
        }

        int bufferSize;
        try {
            bufferSize = datagramChannel.socket().getReceiveBufferSize();
        } catch (SocketException e) {
            System.out.println("Error getting buffer size: " + e.getMessage());
            close();
            return;
        }
        for (int i = 0; i < threads; i++) {
            freeBuffers.add(ByteBuffer.allocate(bufferSize));
        }

        receiver = Executors.newSingleThreadExecutor();
        workers = Executors.newFixedThreadPool(threads);

        receiver.submit(this::mainReceive);
    }

    private void mainReceive() {
        while (!selector.keys().isEmpty()) {
            try {
                selector.select();
            } catch (IOException e) {
                System.out.println("Failed to select IO channel: " + e.getMessage());
                return;
            }

            for (Iterator<SelectionKey> i = selector.selectedKeys().iterator(); i.hasNext(); ) {
                SelectionKey key = i.next();
                try {
                    if (key.isReadable()) {
                        serverRead(key);
                    }
                    if (key.isWritable()) {
                        serverWrite(key);
                    }
                } finally {
                    i.remove();
                }
            }
        }
    }

    private void serverRead(SelectionKey key) {
        if (freeBuffers.isEmpty()) {
            key.interestOps(SelectionKey.OP_WRITE);
            return;
        }

        DatagramChannel channel = (DatagramChannel) key.channel();
        ByteBuffer sendBuffer = freeBuffers.remove();
        SocketAddress address = Util.receiveBuffer(channel, sendBuffer);
        if (address == null) {
            freeBuffers.add(sendBuffer);
            return;
        }

        workers.submit(() -> {
            String response = "Hello, " + StandardCharsets.UTF_8.decode(sendBuffer);
            packetQueue.add(new Packet(response.getBytes(StandardCharsets.UTF_8), address));
            freeBuffers.add(sendBuffer);
            key.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        });
    }

    private void serverWrite(SelectionKey key) {
        if (packetQueue.isEmpty()) {
            key.interestOps(SelectionKey.OP_READ);
            return;
        }

        Packet packet = packetQueue.remove();
        DatagramChannel channel = (DatagramChannel) key.channel();
        try {
            channel.send(ByteBuffer.wrap(packet.buffer), packet.address);
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        } catch (IOException e) {
            System.out.println("Error writing server response: " + e.getMessage());
        }
    }

    /**
     * Stops server and deallocates all resources.
     */
    @Override
    public void close() {
        try {
            if (datagramChannel != null) {
                datagramChannel.close();
            }
            if (selector != null) {
                selector.close();
            }
        } catch (IOException ignored) {
        }
        Util.shutdownExecutor(receiver);
        Util.shutdownExecutor(workers);
    }

    /**
     * Method used to use the class from command line.
     * The first argument is the server port number, while the second one is the number of threads to use.
     *
     * @param args - arguments to pass.
     */
    public static void main(String[] args) {
        Util.UDPServerMain(args, HelloUDPNonblockingServer.class);
    }

    private static class Packet {
        byte[] buffer;
        SocketAddress address;

        public Packet(byte[] buffer, SocketAddress address) {
            this.buffer = buffer;
            this.address = address;
        }
    }
}
