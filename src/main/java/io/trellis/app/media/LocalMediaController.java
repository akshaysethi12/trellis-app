package io.trellis.app.media;

import io.trellis.app.config.TrellisRuntimeProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Local stand-in for the S3 signed-URL upload flow that rn-trellis-core's MediaUploadManager uses:
 *   1. POST /api/v1/media/signed-url  {hashKey, fileName, taskId, assetId, ...} -> {signedUrl, imgPath}
 *   2. PUT  {signedUrl}  (raw bytes)
 *   3. the app saves {path: imgPath, hashKey} as the task's captured value
 *   4. GET  {mediaUrl}{imgPath} shows the file
 * Point the SDK's uploadEndpoint at /api/v1/media/signed-url. In production keep the real S3/Lambda endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Tag(name = "Local media", description = "Signed-URL emulation for local development")
public class LocalMediaController {

    private static final String FILES = "/api/v1/media/files/";

    private final TrellisRuntimeProperties properties;

    @PostMapping("/signed-url")
    @Operation(summary = "Returns a URL the app can PUT the file to, and the path to store in capturedValue")
    public Map<String, String> signedUrl(@RequestBody Map<String, Object> body) {
        String assetId = safe(body.getOrDefault("assetId", "unknown"));
        String taskId = safe(body.getOrDefault("taskId", "task"));
        String hashKey = safe(body.getOrDefault("hashKey", "media"));
        String fileName = safe(body.getOrDefault("fileName", hashKey + ".jpg"));
        String imgPath = "trellis/" + assetId + "/" + taskId + "/" + hashKey + "_" + fileName;
        return Map.of("signedUrl", mediaBaseUrl() + imgPath, "imgPath", imgPath);
    }

    @PutMapping(value = "/files/**", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Map<String, Object>> upload(HttpServletRequest request, @RequestBody byte[] bytes) throws IOException {
        Path target = resolve(request);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        log.info("Stored {} bytes at {}", bytes.length, target);
        return ResponseEntity.ok(Map.of("stored", target.toString(), "bytes", bytes.length));
    }

    @GetMapping("/files/**")
    public ResponseEntity<Resource> download(HttpServletRequest request) {
        Path target = resolve(request);
        if (!Files.exists(target)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        MediaType type = MediaTypeFactory.getMediaType(target.getFileName().toString()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).body(new FileSystemResource(target));
    }

    /** Base URL the client prepends to stored paths (returned as mediaUrl in the task tree). */
    public String mediaBaseUrl() {
        String base = properties.getMedia().getPublicBaseUrl();
        if (StringUtils.isBlank(base)) {
            try {
                base = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
            } catch (IllegalStateException noRequest) {
                base = "http://localhost:8080";
            }
        }
        return StringUtils.removeEnd(base, "/") + FILES;
    }

    private Path resolve(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String relative = uri.substring(uri.indexOf(FILES) + FILES.length());
        Path root = Path.of(properties.getMedia().getLocalDir()).toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid media path");
        }
        return target;
    }

    private String safe(Object value) {
        return String.valueOf(value).replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
