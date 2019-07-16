package org.springframework.integration.http.inbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.cloud.stream.app.http.gateway.processor.ResourceLoaderSupport;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.integration.http.converter.MultipartAwareFormHttpMessageConverter;
import org.springframework.integration.http.inbound.continuation.Continuation;
import org.springframework.integration.http.inbound.continuation.Continuations;
import org.springframework.integration.http.multipart.MultipartHttpInputMessage;
import org.springframework.integration.http.multipart.UploadedMultipartFile;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.*;
import org.springframework.web.HttpRequestHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * Inbound Messaging Gateway that handles HTTP Requests. May be configured as a bean in the Application Context and
 * delegated to from a simple HttpRequestHandlerServlet in <code>web.xml</code> where the servlet and bean both have the
 * same name. If the {@link #expectReply} property is set to true, a response can generated from a reply Message.
 * Otherwise, the gateway will play the role of a unidirectional Channel Adapter with a simple status-based response
 * (e.g. 200 OK).
 * <p>
 * The default supported request methods are GET and POST, but the list of values can be configured with the {@link
 * RequestMapping#methods} property. The payload generated from a GET request (or HEAD or OPTIONS if supported) will be
 * a {@link MultiValueMap} containing the parameter values. For a request containing a body (e.g. a POST), the type of
 * the payload is determined by the {@link #setRequestPayloadTypeClass(Class)} request payload type}.
 * <p>
 * If the HTTP request is a multipart and a "multipartResolver" bean has been defined in the context, then it will be
 * converted by the {@link MultipartAwareFormHttpMessageConverter} as long as the default message converters have not
 * been overwritten (although providing a customized instance of the Multipart-aware converter is also an option).
 * <p>
 * By default a number of {@link HttpMessageConverter}s are already configured. The list can be overridden by calling
 * the {@link #setMessageConverters(List)} method.
 *
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Artem Bilan
 * @author Haruhiko Nishi
 * @since 2.0
 */

public class AsyncContextServletMessagingGateway extends HttpRequestHandlingEndpointSupport implements
        HttpRequestHandler {

    private static final String ORIGINAL_CONTENT_TYPE = "original_content_type";

    private static final String CONTINUATION_ID = "continuation_id";

    private static final long TIMEOUT = 300000;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private volatile boolean convertExceptions;

    private long timeout = TIMEOUT;

    private ResourceLoaderSupport resourceLoaderSupport;

    public AsyncContextServletMessagingGateway(ResourceLoaderSupport resourceLoaderSupport) {
        super(false);
        this.resourceLoaderSupport = resourceLoaderSupport;
    }

    public void setConvertExceptions(boolean convertExceptions) {
        this.convertExceptions = convertExceptions;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public final void handleRequest(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
            throws IOException {
        Object responseContent = null;
        ServletServerHttpRequest request = prepareRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);
        Continuation continuation = Continuations.getContinuation(servletRequest, timeout);
        request.getHeaders().set(CONTINUATION_ID, continuation.getId().toString());
        RequestEntity<Object> httpEntity = prepareRequestEntity(request);
        Message<?> responseMessage = continuation.dispatch(servletRequest);
        try {
            if (continuation.isExpired()) {
                response.setStatusCode(HttpStatus.GATEWAY_TIMEOUT);
            } else {
                if (responseMessage == null) {
                    responseMessage = doHandleRequest(servletRequest, httpEntity, servletResponse);
                }
                if (responseMessage != null) {
                    Boolean isReference = responseMessage.getHeaders().get("is_reference", Boolean.class);
                    if (isReference != null && isReference) {
                        Object payload = responseMessage.getPayload();
                        JsonNode jsonNode;
                        if (payload instanceof String) {
                            jsonNode = objectMapper.readTree((String) payload);
                        } else {
                            jsonNode = objectMapper.valueToTree(payload);
                        }
                        String originalContentType = jsonNode.path(ORIGINAL_CONTENT_TYPE).asText();
                        String uri = jsonNode.path("uri").asText();
                        Assert.isTrue(!StringUtils.isEmpty(originalContentType), "'original_content_type' not found");
                        Assert.isTrue(!StringUtils.isEmpty(uri), "'uri' not found");
                        Resource resource = resourceLoaderSupport.getResource(uri);
                        MimeType mimeType = MimeType.valueOf(originalContentType);
                        responseMessage = MessageBuilder.withPayload(resource).setHeader(MessageHeaders.CONTENT_TYPE, mimeType.toString()).build();
                    } else {
                        MimeType mimeType = responseMessage.getHeaders()
                                .get(MessageHeaders.CONTENT_TYPE, MimeType.class);
                        responseMessage = MessageBuilder.fromMessage(responseMessage)
                                .setHeader(MessageHeaders.CONTENT_TYPE, mimeType.toString()).build();
                    }
                    responseContent = setupResponseAndConvertReply(response, responseMessage);
                }
            }
        } catch (Exception e) {
            responseContent = handleExceptionInternal(e);
        }
        if (responseContent != null) {
            if (responseContent instanceof HttpStatus) {
                response.setStatusCode((HttpStatus) responseContent);
            } else {
                if (responseContent instanceof ResponseEntity) {
                    ResponseEntity<?> responseEntity = (ResponseEntity<?>) responseContent;
                    responseContent = responseEntity.getBody();
                    response.setStatusCode(responseEntity.getStatusCode());

                    HttpHeaders outputHeaders = response.getHeaders();
                    HttpHeaders entityHeaders = responseEntity.getHeaders();

                    entityHeaders.entrySet()
                            .stream()
                            .filter(entry -> !outputHeaders.containsKey(entry.getKey()))
                            .forEach(entry -> outputHeaders.put(entry.getKey(), entry.getValue()));
                }
                if (responseContent != null) {
                    writeResponse(responseContent, response, request.getHeaders().getAccept());
                } else {
                    response.flush();
                }
            }
        }
    }

    protected RequestEntity<Object> prepareRequestEntity(ServletServerHttpRequest request) throws IOException {
        Object requestBody = null;
        if (isReadable(request)) {
            requestBody = extractRequestBody(request);
        }

        if (request instanceof MultipartHttpInputMessage) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            LinkedMultiValueMap<String, Object> linkedMultiValueMap = (LinkedMultiValueMap<String, Object>) requestBody;
            for (Entry<String, List<Object>> entry : Objects.requireNonNull(linkedMultiValueMap).entrySet()) {
                String name = entry.getKey();
                List<Object> objects = entry.getValue();
                for (Object o : objects) {
                    if (o instanceof UploadedMultipartFile) {
                        UploadedMultipartFile multipartFile = (UploadedMultipartFile) o;

                        ObjectNode objectNode = objectMapper.createObjectNode();
                        URI uri = resourceLoaderSupport.externalize(multipartFile.getResource());
                        objectNode.put("formParameterName", multipartFile.getName());
                        objectNode.put("originalFileName", multipartFile.getOriginalFilename());
                        objectNode.put("contentType", multipartFile.getContentType());
                        objectNode.put("uri", uri.toString());
                        arrayNode.add(objectNode);

                    } else {
                        ObjectNode objectNode = objectMapper.createObjectNode();
                        objectNode.set(name, objectMapper.valueToTree(o));
                        arrayNode.add(objectNode);
                    }
                }
            }
            MediaType mediaType = request.getHeaders().getContentType();

            if (mediaType != null) {
                Assert.isTrue(mediaType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA),
                        "invalid MediaType: " + mediaType);
                request.getHeaders().set("original_content_type", mediaType.toString());
            }
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return new RequestEntity<>(arrayNode, request.getHeaders(), request.getMethod(), request.getURI());
        }

        return new RequestEntity<>(requestBody, request.getHeaders(), request.getMethod(), request.getURI());
    }

    private Object handleExceptionInternal(Exception ex) throws IOException {
        if (this.convertExceptions && isExpectReply()) {
            return ex;
        } else {
            if (ex instanceof IOException) {
                throw (IOException) ex;
            } else if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            } else {
                throw new MessagingException("error occurred handling HTTP request", ex);
            }
        }
    }

    private void writeResponse(Object content, ServletServerHttpResponse response, List<MediaType> acceptTypesArg)
            throws IOException {

        List<MediaType> acceptTypes = acceptTypesArg;
        if (CollectionUtils.isEmpty(acceptTypes)) {
            acceptTypes = Collections.singletonList(MediaType.ALL);
        }

        for (HttpMessageConverter<?> converter : getMessageConverters()) {
            for (MediaType acceptType : acceptTypes) {
                if (converter.canWrite(content.getClass(), acceptType)) {
                    @SuppressWarnings("unchecked")
                    HttpMessageConverter<Object> converterToUse = (HttpMessageConverter<Object>) converter;
                    converterToUse.write(content, acceptType, response);
                    return;
                }
            }
        }
        throw new MessagingException("Could not convert reply: no suitable HttpMessageConverter found for type ["
                + content.getClass().getName() + "] and accept types [" + acceptTypes + "]");
    }
}
