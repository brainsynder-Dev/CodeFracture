package org.bsdevelopment.codefracture.utils;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

public class PasteClient {
    private static final String BASE_URL = "https://paste.bsdevelopment.org/data/";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /**
     * Uploads content and returns the paste key (e.g. "aB3dEf12").
     *
     * @param content the text to upload
     *
     * @return the paste key
     */
    public static String upload(String content) throws Exception {
        return upload(content, "plain");
    }

    /**
     * Uploads content and returns the paste key (e.g. "aB3dEf12").
     *
     * @param content  the text to upload
     * @param language the language id, e.g. "plain", "java", "json"
     *
     * @return the paste key
     */
    public static String upload(String content, String language) throws Exception {
        byte[] compressed = gzip(content.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(BASE_URL + "post"))
                .header("Content-Type", "text/" + language)
                .header("Content-Encoding", "gzip")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(compressed)).build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201) {
            // Response body: {"key":"aB3dEf12"}
            String body = response.body();
            int start = body.indexOf('"', body.indexOf("\"key\"") + 5) + 1;
            int end = body.indexOf('"', start);
            return body.substring(start, end);
        }

        throw new RuntimeException("Upload failed with HTTP " + response.statusCode() + ": " + response.body());
    }

    /**
     * Returns the full URL for a paste key.
     */
    public static String pasteUrl(String key) {
        return "https://paste.bsdevelopment.org/" + key;
    }

    /**
     * Fetches the raw content of a paste by key.
     *
     * @param key the paste key, e.g. "aB3dEf12"
     *
     * @return the paste content
     */
    public static String read(String key) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(BASE_URL + key)).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) return response.body();

        throw new RuntimeException("Read failed with HTTP " + response.statusCode());
    }

    private static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(buf)) {
            gz.write(data);
        }
        return buf.toByteArray();
    }
}