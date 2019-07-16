package org.springframework.integration.http.dsl;

import org.springframework.integration.http.inbound.AsyncContextServletMessagingGateway;

public class AsyncContextServletEndpointSpec extends BaseHttpInboundEndpointSpec<AsyncContextServletEndpointSpec, AsyncContextServletMessagingGateway> {

    public AsyncContextServletEndpointSpec(AsyncContextServletMessagingGateway endpoint, String... path) {
        super(endpoint, path);
    }

    public AsyncContextServletEndpointSpec convertExceptions(boolean convertExceptions) {
        this.target.setConvertExceptions(convertExceptions);
        return this;
    }

    public AsyncContextServletEndpointSpec setTimeout(long timeout) {
        this.target.setTimeout(timeout);
        return this;
    }
}
