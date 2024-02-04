package com.github.onlaait.mcserverpinger;

import com.google.common.net.HostAndPort;

import java.net.IDN;

public final class ServerAddress {
    private final HostAndPort hostAndPort;

    public ServerAddress(String host, int port) {
        this(HostAndPort.fromParts(host, port));
    }

    private ServerAddress(HostAndPort hostAndPort) {
        this.hostAndPort = hostAndPort;
    }

    public String getAddress() {
        try {
            return IDN.toASCII(this.hostAndPort.getHost());
        } catch (IllegalArgumentException illegalArgumentException) {
            return "";
        }
    }

    public int getPort() {
        return this.hostAndPort.getPort();
    }

    public static ServerAddress parse(String address) throws PingerException {
        if (address == null) {
            throw new PingerException("com.github.onlaait.mcserverpinger.Address is null");
        }
        try {
            HostAndPort hostAndPort = HostAndPort.fromString(address).withDefaultPort(25565);
            if (hostAndPort.getHost().isEmpty()) {
                throw new PingerException("Host is empty");
            }
            return new ServerAddress(hostAndPort);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new PingerException("Failed to parse URL " + address);
        }
    }

    static int portOrDefault(String port) {
        try {
            return Integer.parseInt(port.trim());
        } catch (Exception exception) {
            return 25565;
        }
    }

    public String toString() {
        return this.hostAndPort.toString();
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof ServerAddress) {
            return this.hostAndPort.equals(((ServerAddress)o).hostAndPort);
        }
        return false;
    }

    public int hashCode() {
        return this.hostAndPort.hashCode();
    }
}

