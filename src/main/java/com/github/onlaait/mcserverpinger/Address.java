package com.github.onlaait.mcserverpinger;

import java.net.InetSocketAddress;

public interface Address {
    String getHostName();

    String getHostAddress();

    int getPort();

    InetSocketAddress getInetSocketAddress();

    static Address create(final InetSocketAddress address) {
        return new Address(){

            @Override
            public String getHostName() {
                return address.getAddress().getHostName();
            }

            @Override
            public String getHostAddress() {
                return address.getAddress().getHostAddress();
            }

            @Override
            public int getPort() {
                return address.getPort();
            }

            @Override
            public InetSocketAddress getInetSocketAddress() {
                return address;
            }
        };
    }
}