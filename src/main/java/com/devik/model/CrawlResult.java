package com.devik.model;

import lombok.Builder;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Data
@Builder
public class CrawlResult {
    private UUID id;
    private String url;
    private String title;
    private String content;
    private List<String> links;
    private LocalDateTime crawledAt;

    public static CrawlResult create(String url, String title, String content, List<String> links) {
        return CrawlResult.builder()
                .id(generateUUID("url", url))
                .url(url)
                .title(title)
                .content(content)
                .links(links)
                .crawledAt(LocalDateTime.now())
                .build();
    }

    public static UUID generateUUID(String namespace, String name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");

            // Convert namespace UUID to bytes
            UUID namespaceUUID = UUID.nameUUIDFromBytes(namespace.getBytes(StandardCharsets.UTF_8));
            byte[] namespaceBytes = toBytes(namespaceUUID);

            // Hash namespace + name
            sha1.update(namespaceBytes);
            sha1.update(name.getBytes(StandardCharsets.UTF_8));
            byte[] hash = sha1.digest();

            // Set version (5) and variant bits
            hash[6] &= 0x0f;
            hash[6] |= 0x50; // version 5
            hash[8] &= 0x3f;
            hash[8] |= 0x80;

            return fromBytes(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate UUIDv5", e);
        }
    }

    private static byte[] toBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) bytes[i] = (byte) (msb >>> (8 * (7 - i)));
        for (int i = 8; i < 16; i++) bytes[i] = (byte) (lsb >>> (8 * (15 - i)));
        return bytes;
    }

    private static UUID fromBytes(byte[] bytes) {
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (bytes[i] & 0xff);
        for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (bytes[i] & 0xff);
        return new UUID(msb, lsb);
    }

}