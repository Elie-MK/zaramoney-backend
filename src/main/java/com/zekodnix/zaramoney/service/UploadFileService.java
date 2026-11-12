package com.zekodnix.zaramoney.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * KYC document storage on Cloudinary.
 * <ul>
 *   <li>The bytes are sent directly: nothing is written to disk, so the client file name is never used as a path.</li>
 *   <li>The real type is checked from the file's magic bytes (JPEG, PNG, WEBP only; no SVG, which can carry scripts).</li>
 *   <li>Files are uploaded as {@code type=private}: there is no public URL. The DB stores a reference
 *   {@code "<publicId>.<format>"}; admins get a short-lived signed link through {@link #signedUrl(String)}.</li>
 * </ul>
 */
@Service
public class UploadFileService {

    private static final Logger LOG = LoggerFactory.getLogger(UploadFileService.class);

    static final long MAX_FILE_SIZE = 500 * 1024; // 500KB
    static final String KYC_FOLDER = "kyc";
    static final String DELIVERY_TYPE = "private";
    public static final Duration SIGNED_URL_VALIDITY = Duration.ofMinutes(5);

    private final Cloudinary cloudinary;

    public UploadFileService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /** @return the stored reference, {@code "<publicId>.<format>"}. */
    public String uploadPrivateImage(MultipartFile file) {
        byte[] bytes = readValidatedImage(file);
        try {
            Map<?, ?> result = cloudinary
                .uploader()
                .upload(
                    bytes,
                    ObjectUtils.asMap(
                        "folder",
                        KYC_FOLDER,
                        "type",
                        DELIVERY_TYPE,
                        "resource_type",
                        "image",
                        "allowed_formats",
                        "jpg,png,webp",
                        "use_filename",
                        false,
                        "unique_filename",
                        true
                    )
                );
            Object publicId = result.get("public_id");
            Object format = result.get("format");
            if (publicId == null || format == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Upload failed", "uploadfailed");
            }
            return publicId + "." + format;
        } catch (IOException e) {
            LOG.warn("Cloudinary upload failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Failed to upload the file", "uploadfailed");
        }
    }

    /**
     * Short-lived download link for a stored reference. Legacy rows that already hold a full http(s) URL are returned as-is.
     */
    public String signedUrl(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        if (reference.startsWith("http://") || reference.startsWith("https://")) {
            return reference; // uploaded before the private-storage change (still public on Cloudinary)
        }
        int dot = reference.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        try {
            long expiresAt = Instant.now().plus(SIGNED_URL_VALIDITY).getEpochSecond();
            return cloudinary.privateDownload(
                reference.substring(0, dot),
                reference.substring(dot + 1),
                ObjectUtils.asMap("type", DELIVERY_TYPE, "expires_at", expiresAt)
            );
        } catch (Exception e) {
            LOG.warn("Could not sign KYC document URL: {}", e.getMessage());
            return null;
        }
    }

    /** Best-effort delete (used to clean up after a failed signup). */
    public void deleteQuietly(String reference) {
        if (reference == null || reference.startsWith("http")) {
            return;
        }
        int dot = reference.lastIndexOf('.');
        String publicId = dot > 0 ? reference.substring(0, dot) : reference;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("type", DELIVERY_TYPE, "invalidate", true));
        } catch (Exception e) {
            LOG.warn("Could not delete orphan KYC document {}: {}", publicId, e.getMessage());
        }
    }

    static byte[] readValidatedImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File is empty", "emptyfile");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw ApiException.badRequest("File size must not exceed 500KB", "filetoolarge");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the file", "unreadablefile");
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw ApiException.badRequest("File size must not exceed 500KB", "filetoolarge");
        }
        if (!isJpeg(bytes) && !isPng(bytes) && !isWebp(bytes)) {
            // Never trust the client-supplied Content-Type or file extension
            throw ApiException.badRequest("Only JPEG, PNG and WEBP images are allowed", "invalidimagetype");
        }
        return bytes;
    }

    static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    static boolean isPng(byte[] b) {
        byte[] sig = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
        if (b.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) {
            if (b[i] != sig[i]) return false;
        }
        return true;
    }

    static boolean isWebp(byte[] b) {
        return (
            b.length >= 12 &&
            b[0] == 'R' &&
            b[1] == 'I' &&
            b[2] == 'F' &&
            b[3] == 'F' &&
            b[8] == 'W' &&
            b[9] == 'E' &&
            b[10] == 'B' &&
            b[11] == 'P'
        );
    }
}
