package gov.nist.timestampingauthority;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * The original code was modified by Temur Saidkhodjaev (temurson1997@gmail.com).
 */

// Acquired from https://commons.apache.org/proper/commons-net/

import org.apache.commons.net.ntp.NtpV3Impl;
import org.apache.commons.net.ntp.NtpV3Packet;
import org.apache.commons.net.ntp.TimeStamp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.file.Path;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The TimeStampingAuthorityServer class uses NTP to communicate with clock servers and clients. This class is an
 * extension of TimeStampingAuthority class, which provides timestamps without serving the clients. NTP is an extension
 * of UDP protocol, so this server uses UDP classes and commands. When the class is run, it creates three Clock classes
 * and makes them synchronize with corresponding time servers periodically. Each clock class updates time using a
 * separate thread. There are also 2 other threads, one for getting client events, and the other to respond to client
 * events. They have their corresponding methods. The events are stored in a thread-safe concurrent queue by the handler
 * method and are taken from there by the responder method.
 *
 * To prevent this class from interfering with the actual NTP service it can be run from any local port. All clocks are
 * assumed to operate on port 123. If the file with clock addresses could not be opened, the execution is aborted.
 */
// TODO allow logging into a separate file instead of System.out and silent mode
// TODO make this or the clock class observable? to notify about new time updates
// TODO make possible adding clocks to the TsA at runtime not from a file
public class TimeStampingAuthorityServer extends TimeStampingAuthority {
    private int port;

    // Each received event is added into event queue for handling in the response loop run in a separate thread
    private final Queue<ClientEvent> eventQueue;
    private volatile boolean runningServer;
    // socket is only connected when the server starts
    private DatagramSocket socket;
    private final DatagramPacket request;


    /**
     * Create TimeStampingAuthorityServer, initialize the event queue and the clocks. You can provide null instead of an
     * argument to use default value. If one of the clock addresses could not be found, the clock is ignored and the
     * message is printed into System.out.
     *
     * @param updateInterval interval for clock update, default is 10000 ms (10 seconds), must be a positive integer in
     *                        ms. Values over 5000 ms are recommended due to clock server restrictions.
     * @param timeout time to wait for clock response, default is 500 ms, must be a positive integer in ms. Values over
     *                 100 ms are recommended, due to network latency.
     * @param filePath path to a file where the clock addresses are stored, must be a path to a readable file
     *                  containing clock addresses. This file must have the following format:
     *                  each line must either contain the NTP server address or start with % to indicate a comment.
     *                  Empty lines are permitted as well. Invalid or unreachable addresses are ignored. If no working
     *                  clocks were found, the server is stopped. Please note that if the text file was created with
     *                  Notepad on Windows it might have some special characters in the beginning of the file that may
     *                  hinder the file reading (something connected to UTF-8 with BOM). It is advised that you make
     *                  sure the file does not start with anything but spaces, actual server address or a percentage
     *                  character (%).
     * @param spreadOutClocks whether the clocks are to be spread out to reduce the network load, e.g. if there are 10
     *                        clocks and update interval is 1000, 1st clock starts at 0ms, 2nd at 100ms, etc. If set to
     *                        false, all clocks start updating simultaneously.
     * @param port port on which the server operates, default is 124, because 123 is probably occupied by the system
     *              time updating service, must be a valid system port (positive integer).
     * @throws IllegalArgumentException when one of the arguments is invalid or when none of the clocks has a valid
     *                                  address
     * @throws IOException when the file could not be opened
     */
    public TimeStampingAuthorityServer(Long updateInterval, Integer timeout, Path filePath, Boolean spreadOutClocks, Integer port)
            throws IllegalArgumentException, IOException {
        super(updateInterval, timeout, filePath, spreadOutClocks);

        port = port != null ? port : 124;

        eventQueue = new ConcurrentLinkedQueue<>();
        runningServer = false;
        // request object for receiving client requests
        request = new DatagramPacket(new byte[48], 48);

        setPort(port);
    }


    /**
     * Get server port.
     *
     * @return server port
     */
    public int getPort() {
        return port;
    }

    /**
     * Set and validate server port. Will only be updated when the server is restarted.
     *
     * @param port server port
     */
    public void setPort(int port) throws IllegalArgumentException {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port number is invalid.");
        }
        this.port = port;
    }

    /**
     * Tell whether the server is running. This implies both clocks being updated, clients' requests being received and
     * responses being sent.
     *
     * @return whether server is running
     */
    @Override
    public boolean isRunning() {
        return super.isRunning() && runningServer;
    }


    /**
     * Wait for new client requests and handle them by putting them into the event queue. Supposed to run in a separate
     * thread.
     */
    private void receiveClientEvents() {
        while (isRunning()) {
            try {
                socket.receive(request);
                final long receiveTime = getAggregateTime();

                System.out.println("Timestamp request received at " + new Date(receiveTime).toString());

                handlePacket(request, receiveTime);
            } catch (IOException e) {
                if (isRunning()) {
                    e.printStackTrace();
                }
                // otherwise socket threw exception during shutdown
            }
        }
    }

    /**
     * Handle incoming packet. If NTP packet is client-mode then respond to that host with a NTP response packet
     * otherwise ignore.
     *
     * @param request incoming DatagramPacket
     * @param receiveTime time packet received
     */
    private void handlePacket(DatagramPacket request, long receiveTime) {
        NtpV3Packet message = new NtpV3Impl();
        message.setDatagramPacket(request);

        // if received packet is other than CLIENT mode then ignore it
        if (message.getMode() != NtpV3Packet.MODE_CLIENT) {
            return;
        }

        // add event to the event queue
        eventQueue.offer(new ClientEvent(message, receiveTime, request.getPort(), request.getAddress()));
    }


    /**
     * Send response. Supposed to run in a separate thread. Take an event from the queue, if any, get the aggregated
     * time from the clocks and return the response.
     */
    private void requestTimeAndRespond() {
        while (isRunning()) {
            if (eventQueue.peek() == null) {
                continue;
            }

            ClientEvent event = eventQueue.poll();
            //TODO let the method use not-so-fresh time
            Long aggregateTime = getAggregateTime();
            // if no time was available at that moment, use server time
            if (aggregateTime == null) {
                aggregateTime = System.currentTimeMillis();
            }
            DatagramPacket responsePacket = createResponse(event, aggregateTime);

            try {
                socket.send(responsePacket);

                System.out.println("Timestamp sent: " + new Date(aggregateTime).toString() + ".");
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    /**
     * Take the client event along with the average time of the clocks at this moment and form a response UDP packet.
     *
     * @param event client event
     * @param averageTime average time from clocks
     * @return response UDP packet
     */
    private DatagramPacket createResponse(ClientEvent event, long averageTime) {
        // setting fields in the response packet
        // please refer to NTP standard RFC-1305
        NtpV3Packet response = new NtpV3Impl();
        response.setStratum(2);
        response.setMode(NtpV3Packet.MODE_SERVER);
        response.setVersion(NtpV3Packet.VERSION_3);
        response.setPrecision(-20);
        response.setPoll(0);
        response.setRootDelay(62);
        response.setRootDispersion((int) (16.51 * 65.536));

        // originate time as defined in RFC-1305 (t1)
        response.setOriginateTimeStamp(event.getMessage().getTransmitTimeStamp());
        // Receive Time is time request received by server (t2)
        response.setReceiveTimeStamp(TimeStamp.getNtpTime(event.getReceiveTime()));
        response.setReferenceTime(response.getReceiveTimeStamp());
        response.setReferenceId(0x4C434C00); // LCL (Undisciplined Local Clock)

        // Transmit time is time reply sent by server (t3)
        response.setTransmitTime(TimeStamp.getNtpTime(averageTime));

        DatagramPacket dp = response.getDatagramPacket();
        dp.setPort(event.getPort());
        dp.setAddress(event.getAddress());
        return dp;
    }


    /**
     * Connect the socket to the port if not done previously, start clock synchronization, start a separate thread for
     * getting aggregate time and responding to events in the queue, start another thread for receiving events and
     * adding them to the queue. This method is non-blocking.
     *
     * @throws IOException when the server failed to connect to the socket.
     */
    @Override
    public void start() throws IOException {
        // create the socket and connect it to the specified port if not done yet
        if (socket == null) {
            socket = new DatagramSocket(getPort());
            System.out.println("Running NTP service on port " + getPort() + "/UDP");
        }

        super.start();
        runningServer = true;

        // start receiving events in a separate thread
        new Thread(this::receiveClientEvents, "ReceiveEvents").start();

        // start response handler in a different thread
        // the response and request threads only use a common event queue, which is thread safe
        new Thread(this::requestTimeAndRespond, "RespondEvents").start();
    }

    /**
     * Close server socket, stop the clocks and stop listening.
     */
    @Override
    public void stop() {
        super.stop();
        runningServer = false;
        if (socket != null) {
            socket.close();  // force closing of the socket
            socket = null;
        }
    }
}
