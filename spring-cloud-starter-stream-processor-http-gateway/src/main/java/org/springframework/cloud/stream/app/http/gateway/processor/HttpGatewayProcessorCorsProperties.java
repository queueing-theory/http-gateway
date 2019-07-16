package org.springframework.cloud.stream.app.http.gateway.processor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.cors.CorsConfiguration;

import javax.validation.constraints.NotEmpty;
/**
 * @author Artem Bilan
 * @author Haruhiko Nishi
 */
@ConfigurationProperties("http-gateway.cors")
@Validated
public class HttpGatewayProcessorCorsProperties {
    /**
     * List of allowed origins, e.g. "https://domain1.com".
     */
    private String[] allowedOrigins = { CorsConfiguration.ALL };

    /**
     * List of request headers that can be used during the actual request.
     */
    private String[] allowedHeaders = { CorsConfiguration.ALL };

    /**
     * Whether the browser should include any cookies associated with the domain of the request being annotated.
     */
    private Boolean allowCredentials;

    @NotEmpty
    public String[] getAllowedOrigins() {
        return this.allowedOrigins;
    }

    public void setAllowedOrigins(String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @NotEmpty
    public String[] getAllowedHeaders() {
        return this.allowedHeaders;
    }

    public void setAllowedHeaders(String[] allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    public Boolean getAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(Boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }
}
