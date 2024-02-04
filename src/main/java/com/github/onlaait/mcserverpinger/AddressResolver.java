package com.github.onlaait.mcserverpinger;

import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;

@FunctionalInterface
public interface AddressResolver {
    Logger LOGGER = Log.INSTANCE.getLogger();
    AddressResolver DEFAULT = address -> {
        try {
            InetAddress inetAddress = InetAddress.getByName(address.getAddress());
            return Optional.of(Address.create(new InetSocketAddress(inetAddress, address.getPort())));
        } catch (UnknownHostException unknownHostException) {
            LOGGER.debug("Couldn't resolve server {} address", address.getAddress());
            return Optional.empty();
        }
    };

    Optional<Address> resolve(ServerAddress var1);
}