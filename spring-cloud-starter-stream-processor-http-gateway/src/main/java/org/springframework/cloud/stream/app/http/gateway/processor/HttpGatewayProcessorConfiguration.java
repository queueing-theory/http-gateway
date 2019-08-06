package org.springframework.cloud.stream.app.http.gateway.processor;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.integration.http.dsl.AsyncContextServletEndpointSpec;
import org.springframework.integration.http.inbound.AsyncContextServletMessagingGateway;
import org.springframework.integration.http.inbound.HttpRequestHandlingEndpointSupport;
import org.springframework.integration.http.inbound.continuation.Continuation;
import org.springframework.integration.http.inbound.continuation.Continuations;
import org.springframework.integration.http.support.DefaultHttpHeaderMapper;
import org.springframework.messaging.Message;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

/**
 * A processor module that listens for HTTP requests and emits the body as a message payload. If the Content-Type
 * matches 'text/*' or 'application/json', the payload will be a String, otherwise the payload will be a byte array.
 *
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Marius Bogoevici
 * @author Artem Bilan
 * @author Gary Russell
 * @author Christian Tzolov
 * @author Haruhiko Nishi
 */
@EnableBinding(Processor.class)
@EnableConfigurationProperties({HttpGatewayProcessorProperties.class})
@ComponentScan
public class HttpGatewayProcessorConfiguration {

    @Autowired
    private Processor channels;

    @Autowired
    private HttpGatewayProcessorProperties properties;

    @Autowired
    private ResourceLoader resourceLoader;

    @Bean
    public HttpRequestHandlingEndpointSupport httpSourceString() {
        return buildHttpRequestHandlerEndpointSpec("text/*", "application/json")
                .requestPayloadType(String.class)
                .get();
    }

    @Bean
    public HttpRequestHandlingEndpointSupport httpSourceBytes() {
        return buildHttpRequestHandlerEndpointSpec("*/*")
                .get();
    }

    private AsyncContextServletEndpointSpec buildHttpRequestHandlerEndpointSpec(final String... consumes) {
        return new AsyncContextServletEndpointSpec(new AsyncContextServletMessagingGateway(
                new ResourceLoaderSupport(resourceLoader, properties.getResourceLocationUri())),
                this.properties.getPathPattern())
                .headerMapper(new DefaultHttpHeaderMapper() {

                    {
                        DefaultHttpHeaderMapper.setupDefaultInboundMapper(this);
                        setInboundHeaderNames(properties.getMappedRequestHeaders());
                        setOutboundHeaderNames(properties.getMappedResponseHeaders());
                    }

                    protected Object getHttpHeader(HttpHeaders source, String name) {
                        if (ACCEPT.equalsIgnoreCase(name)) {
                            List<MediaType> mediaTypes = source.getAccept();
                            return mediaTypes.stream().map(MimeType::toString).collect(Collectors.toList());
                        } else {
                            return super.getHttpHeader(source, name);
                        }
                    }
                })
                .setTimeout(properties.getTimeout())
                .requestMapping(requestMapping ->
                        requestMapping.methods(HttpMethod.POST, HttpMethod.GET, HttpMethod.DELETE, HttpMethod.PUT,
                                HttpMethod.OPTIONS)
                                .consumes(consumes))
                .crossOrigin(crossOrigin ->
                        crossOrigin.origin(this.properties.getCors().getAllowedOrigins())
                                .allowedHeaders(this.properties.getCors().getAllowedHeaders())
                                .allowCredentials(this.properties.getCors().getAllowCredentials()))
                .requestChannel(this.channels.output())
                .replyChannel(this.channels.input());
    }

    @Bean
    public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        firewall.setAllowSemicolon(true);
        return firewall;
    }

    @Bean
    public CommonsMultipartResolver multipartResolver() {
        CommonsMultipartResolver multipartResolver = new CommonsMultipartResolver();
        return multipartResolver;
    }
}


