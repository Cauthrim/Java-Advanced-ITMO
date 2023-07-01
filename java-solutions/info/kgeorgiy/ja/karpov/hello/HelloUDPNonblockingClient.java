package info.kgeorgiy.ja.karpov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * Client side of UDP connection using NIO in a single thread.
 */
public class HelloUDPNonblockingClient implements HelloClient {
    /**
     * Runs Hello client.
     * This method should return when all requests are completed.
     *
     * @param host     server host.
     * @param port     server port.
     * @param prefix   request prefix.
     * @param channels  number of channels.
     * @param requests number of requests per thread.
     */
    @Override
    public void run(String host, int port, String prefix, int channels, int requests) {
        Selector selector;
        try {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(host), port);
            selector = Selector.open();
            for (int i = 1; i <= channels; i++) {
                DatagramChannel channel = DatagramChannel.open();
                channel.connect(address);
                channel.configureBlocking(false);
                int bufferSize = channel.socket().getReceiveBufferSize();
                channel.register(selector, SelectionKey.OP_WRITE, new Attachment(i, ByteBuffer.allocate(bufferSize)));
            }
        } catch (IOException e) {
            System.out.println("Error initializing selector and channels: " + e.getMessage());
            return;
        }

        while(!selector.keys().isEmpty()) {
            try {
                selector.select(Util.SOCKET_SO_TIMEOUT);
            } catch (IOException e) {
                System.out.println("Error while selecting channel: " + e.getMessage());
                break;
            }

            if (selector.selectedKeys().isEmpty()) {
                for (SelectionKey key : selector.keys()) {
                    clientWrite(key, prefix);
                }
                continue;
            }

            for (Iterator<SelectionKey> i = selector.selectedKeys().iterator(); i.hasNext();) {
                SelectionKey key = i.next();
                try {
                    if (key.isReadable()) {
                        clientRead(key, requests);
                    } else if (key.isWritable()) {
                        clientWrite(key, prefix);
                    }
                } finally {
                    i.remove();
                }
            }
        }
        try {
            selector.close();
        } catch (IOException ignored) {}
    }

    private void clientRead(SelectionKey key, int requests) {
        Attachment attachment = (Attachment) key.attachment();
        DatagramChannel channel = (DatagramChannel) key.channel();

        Util.receiveBuffer(channel, attachment.buffer);
        String response = StandardCharsets.UTF_8.decode(attachment.buffer).toString();

        if (Util.checkResponse(response, attachment.channelNumber, attachment.requestNumber)) {
            System.out.println("Response: " + response);
            attachment.requestNumber++;
        }

        if (attachment.requestNumber != requests + 1) {
            key.interestOps(SelectionKey.OP_WRITE);
        } else {
            try {
                key.channel().close();
            } catch (IOException ignored) {}
        }
    }

    private void clientWrite(SelectionKey key, String prefix) {
        Attachment attachment = (Attachment) key.attachment();
        DatagramChannel channel = (DatagramChannel) key.channel();

        try {
            String request = Util.generateRequest(prefix, attachment.channelNumber, attachment.requestNumber);
            System.out.println("Request: " + request);
            Util.putBuffer(attachment.buffer, request.getBytes());

            try {
                channel.send(attachment.buffer, channel.getRemoteAddress());
                key.interestOps(SelectionKey.OP_READ);
            } catch (ClosedChannelException e) {
                System.out.println("Error: a channel is closed: " + e.getMessage());
            }
        } catch (IOException e) {
            System.out.println("Error while generating client request: " + e.getMessage());
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
        Util.UDPClientMain(args, HelloUDPNonblockingClient.class);
    }

    private static class Attachment {
        int channelNumber;
        int requestNumber;
        final ByteBuffer buffer;

        Attachment(int channelNumber, ByteBuffer buffer) {
            this.channelNumber = channelNumber;
            requestNumber = 1;
            this.buffer = buffer;
        }
    }
}
