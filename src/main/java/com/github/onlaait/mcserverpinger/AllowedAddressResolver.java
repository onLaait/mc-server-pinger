package com.github.onlaait.mcserverpinger;

import java.util.Optional;

public class AllowedAddressResolver {
    public static final AllowedAddressResolver DEFAULT = new AllowedAddressResolver(AddressResolver.DEFAULT, RedirectResolver.createSrv());
    private final AddressResolver addressResolver;
    private final RedirectResolver redirectResolver;

    AllowedAddressResolver(AddressResolver addressResolver, RedirectResolver redirectResolver) {
        this.addressResolver = addressResolver;
        this.redirectResolver = redirectResolver;
    }

    public Optional<Address> resolve(ServerAddress address) {
        Optional<Address> optional = this.addressResolver.resolve(address);
        Optional<ServerAddress> optional2 = this.redirectResolver.lookupRedirect(address);
        if (optional2.isPresent()) {
            optional = this.addressResolver.resolve(optional2.get());
        }
        return optional;
    }
}