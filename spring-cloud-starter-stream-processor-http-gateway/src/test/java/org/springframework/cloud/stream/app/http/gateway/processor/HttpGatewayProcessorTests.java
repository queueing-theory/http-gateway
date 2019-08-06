package org.springframework.cloud.stream.app.http.gateway.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@DirtiesContext
public abstract class HttpGatewayProcessorTests {

    @Autowired
    protected Processor channels;

    @Autowired
    protected MessageCollector messageCollector;

    @Autowired
    protected ObjectMapper objectMapper;

    @TestPropertySource(properties = {"server.port=1234", "http-gateway.timeout=10000",
            "logging.level.org.springframework.integration.http.inbound.continuation.AsyncContextContinuation=DEBUG",
            "http-gateway.resourceLocationUri=file://tmp/files/{yyyy}/{MM}/{dd}/{HH}-{mm}-{ss}/{key}{extension}"})
    public static class DefaultHttpGatewayProcessorTests extends HttpGatewayProcessorTests {

        @Autowired
        private WebApplicationContext webApplicationContext;

        @Test
        public void testHttpGatewayProcessor() throws Exception {
            Thread thread = new Thread(() -> {
                Message<?> message = null;
                try {
                    message = messageCollector.forChannel(channels.output()).take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                channels.input().send(message);
            });
            thread.start();

            HttpUriRequest request = new HttpGet("http://localhost:1234/test");
            HttpResponse response = HttpClientBuilder.create().build().execute(request);
            assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
        }

        @Test
        public void testHttpGatewayProcessorMultiPart() throws Exception {

            MockMultipartFile firstFile = new MockMultipartFile("data1", "filename.txt", "text/plain",
                    "some xml".getBytes());
            MockMultipartFile secondFile = new MockMultipartFile("data2", "other-file-name.data", "text/plain",
                    "some other type".getBytes());
            MockMultipartFile jsonFile = new MockMultipartFile("json", "test.json", "application/json",
                    "{\"json\": \"someValue\"}".getBytes());

            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
            mockMvc.perform(MockMvcRequestBuilders.multipart("/upload")
                    .file(firstFile)
                    .file(secondFile)
                    .file(jsonFile)
                    .param("some-random", "4"))
                    .andDo(result -> {
                        Message<?> message = messageCollector.forChannel(channels.output()).take();
                        JsonNode jsonNode = objectMapper.readTree((String) message.getPayload());
                        assertThat(jsonNode.isArray(), is(true));
                        assertThat(jsonNode.size(), is(4));
                    });
        }

        @Test
        public void testHttpGatewayProcessorTimeout() throws Exception {
            Thread thread = new Thread(() -> {
                Message<?> message = null;
                try {
                    message = messageCollector.forChannel(channels.output()).take();
                    Thread.sleep(11000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                channels.input().send(message);
            });
            thread.start();

            HttpUriRequest request = new HttpGet("http://localhost:1234/test");
            HttpResponse response = HttpClientBuilder.create().build().execute(request);
            assertThat(response.getStatusLine().getStatusCode(), equalTo(504));
        }

        @Test
        public void testHttpGatewayProcessorResourceZeroCopy() throws Exception {
            Thread thread = new Thread(() -> {
                Message<?> message = null;
                try {
                    message = messageCollector.forChannel(channels.output()).take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ObjectNode objectNode = objectMapper.createObjectNode();
                objectNode.put("original_content_type", MediaType.IMAGE_JPEG.toString());
                objectNode.put("uri", "classpath:1MB_file");
                message = MessageBuilder.withPayload(objectNode.toString().getBytes()).setHeader("is_reference", true)
                        .setHeader("continuation_id", message.getHeaders().get("continuation_id", String.class))
                        .build();
                channels.input().send(message);
            });
            thread.start();

            HttpUriRequest request = new HttpGet("http://localhost:1234/test");
            HttpResponse response = HttpClientBuilder.create().build().execute(request);
            assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
            assertThat(response.getEntity().getContentLength(), equalTo(1048576L));
        }

    }

    @SpringBootApplication
    public static class DefaultHttpGatewayProcessorApplication {

    }
}
