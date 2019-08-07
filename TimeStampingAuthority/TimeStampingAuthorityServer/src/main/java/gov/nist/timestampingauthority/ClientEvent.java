package gov.nist.timestampingauthority;// Acquired from https://commons.apache.org/proper/commons-net/

import org.apache.commons.net.ntp.NtpV3Packet;

import java.net.InetAddress;
import java.util.UUID;

/**
 * Data class representing unique client event. It is created each time with a unique id when the new event
 * comes in.
 */
public class ClientEvent {
    private UUID id;
    private NtpV3Packet message;
    private long receiveTime;
    private int port;
    private InetAddress address;

    /**
     * Create new client event. Stores NTP message, time received, port and address from which it was received. New
     * unique ID is generated each time.
     *
     * @param message     client NTP message
     * @param receiveTime server time when the event was received
     * @param port        port from which the event was received
     * @param address     address from which the event was received
     */
    public ClientEvent(NtpV3Packet message, long receiveTime, int port, InetAddress address) {
        this.message = message;
        this.receiveTime = receiveTime;
        this.port = port;
        this.address = address;
        // it is highly unlikely that the collision will happen
        this.id = UUID.randomUUID();
    }

    /**
     * Returns unique event ID.
     *
     * @return id
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the event message. Do not mutate the returned value.
     *
     * @return event message
     */
    public NtpV3Packet getMessage() {
        return message;
    }

    /**
     * Sets the event message.
     *
     * @param message event message
     */
    public void setMessage(NtpV3Packet message) {
        this.message = message;
    }

    /**
     * Returns time when the event was received by the server.
     *
     * @return time received
     */
    public long getReceiveTime() {
        return receiveTime;
    }

    /**
     * Sets the time when the event was received by the server.
     *
     * @param receiveTime time received
     */
    public void setReceiveTime(long receiveTime) {
        this.receiveTime = receiveTime;
    }

    /**
     * Gets port from which the event was received.
     *
     * @return port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets port from which the event was received.
     *
     * @param port port number
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Gets address from which the event was received.
     *
     * @return address
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * Sets address from which the event was received.
     *
     * @param address address
     */
    public void setAddress(InetAddress address) {
        this.address = address;
    }
}
