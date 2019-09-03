package org.springframework.cloud.stream.app.http.gateway.processor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.integration.http.support.DefaultHttpHeaderMapper;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * @author Artem Bilan
 * @author Haruhiko Nishi
 */
@ConfigurationProperties("http-gateway")
public class HttpGatewayProcessorProperties {

    /**
     * HTTP endpoint path mapping.
     */
    private String pathPattern = "**";

    /**
     * Timeout value for the connection
     */
    private Long timeout = 300000L;

    /**
     * Http Request Headers that will be mapped.
     */
    private String[] mappedRequestHeaders = {DefaultHttpHeaderMapper.HTTP_REQUEST_HEADER_NAME_PATTERN,
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers",
            "continuation_id",
            "original_content_type",
            "X-*"
    };

    /**
     * Http Response Headers that will be mapped.
     */
    private String[] mappedResponseHeaders = {DefaultHttpHeaderMapper.HTTP_RESPONSE_HEADER_NAME_PATTERN,
            "Access-Control-Allow-Origin",
            "Access-Control-Expose-Headers",
            "Access-Control-Max-Age",
            "Access-Control-Allow-Credentials",
            "Access-Control-Allow-Methods",
            "Access-Control-Allow-Headers",
            "X-*"
    };

    /**
     * Base URI where externalized contents will be stored.
     */
    private String resourceLocationUri = "file:///tmp/";

    /**
     * CORS properties.
     */
    @NestedConfigurationProperty
    private HttpGatewayProcessorCorsProperties cors = new HttpGatewayProcessorCorsProperties();

    @NotEmpty
    public String getPathPattern() {
        return this.pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }

    @NotNull
    public Long getTimeout() {
        return this.timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public String[] getMappedRequestHeaders() {
        return this.mappedRequestHeaders;
    }

    public void setMappedRequestHeaders(String[] mappedRequestHeaders) {
        this.mappedRequestHeaders = mappedRequestHeaders;
    }

    public HttpGatewayProcessorCorsProperties getCors() {
        return this.cors;
    }

    public void setCors(HttpGatewayProcessorCorsProperties cors) {
        this.cors = cors;
    }

    public String[] getMappedResponseHeaders() {
        return mappedResponseHeaders;
    }

    public void setMappedResponseHeaders(String[] mappedResponseHeaders) {
        this.mappedResponseHeaders = mappedResponseHeaders;
    }

    public String getResourceLocationUri() {
        return resourceLocationUri;
    }

    public void setResourceLocationUri(String resourceLocationUri) {
        this.resourceLocationUri = resourceLocationUri;
    }
}
