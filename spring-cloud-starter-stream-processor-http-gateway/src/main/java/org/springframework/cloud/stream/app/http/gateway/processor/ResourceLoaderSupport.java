package org.springframework.cloud.stream.app.http.gateway.processor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.WritableResource;
import org.springframework.util.Assert;
import org.springframework.web.util.UriComponentsBuilder;

public class ResourceLoaderSupport {

    private ResourceLoader resourceLoader;

    private String location;

    ResourceLoaderSupport(ResourceLoader resourceLoader, String location) {
        Assert.isTrue(location.endsWith("/") ^ (location.contains("{key}") && location.contains("{extension}")),
                "resourceLocationUri should either end with '/' or has a 'key' and 'extension' variable");
        this.resourceLoader = resourceLoader;
        this.location = location;
    }

    private static Map<String, Object> createUriVariables(String name, String extension) {
        LocalDateTime localDateTime = LocalDateTime.now();
        Map<String, Object> variables = new HashMap<>();
        variables.put("key", Objects.requireNonNull(name));
        variables.put("extension", Objects.requireNonNull(extension));
        variables.put("yyyy", localDateTime.getYear());
        variables.put("MM", String.format("%02d", localDateTime.getMonthValue()));
        variables.put("dd", String.format("%02d", localDateTime.getDayOfMonth()));
        variables.put("HH", String.format("%02d", localDateTime.getHour()));
        variables.put("mm", String.format("%02d", localDateTime.getMinute()));
        variables.put("ss", String.format("%02d", localDateTime.getSecond()));
        return Collections.unmodifiableMap(variables);
    }

    public String externalize(String prefix, Resource resource) throws IOException {
        String filename = resource.getFilename();
        String name = filename.substring(0, filename.lastIndexOf('.'));
        String extension = filename.substring(filename.lastIndexOf('.'));
        String key = prefix + "/" + UUID.nameUUIDFromBytes(name.getBytes()).toString();
        String uriPath = uriPath(key, extension);
        Resource target = createResource(uriPath);
        WritableResource writableResource = (WritableResource) target;
        try (OutputStream outputStream = writableResource.getOutputStream()) {
            IOUtils.copy(resource.getInputStream(), outputStream);
        }
        return uriPath;
    }

    private String uriPath(String name, String extension) {
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(location);
        if (location.endsWith("/")) {
            uriComponentsBuilder.path(name + extension);
        }
        return uriComponentsBuilder.buildAndExpand(createUriVariables(name, extension)).toString();
    }

    public Resource createResource(String uriString) throws IOException {
        Resource resource = resourceLoader.getResource(uriString);
        if (!resource.exists() && resource.isFile()) {
            if (!resource.getFile().getParentFile().exists()) {
                Files.createDirectories(resource.getFile().getParentFile().toPath());
            }
        }
        return resource;
    }

    public Resource getResource(String location) {
        return resourceLoader.getResource(location);
    }
}
