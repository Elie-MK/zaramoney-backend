package com.zekodnix.zaramoney.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UploadFileService {

    private final Cloudinary cloudinary;
    private static final long MAX_FILE_SIZE = 500 * 1024; // 500KB

    public UploadFileService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public Map<String, String> uploadFile(MultipartFile imgUrl) {
        validateImageFile(imgUrl);
        try {
            File convFile = new File(System.getProperty("java.io.tmpdir") + "/" + imgUrl.getOriginalFilename());
            FileOutputStream fos = new FileOutputStream(convFile);
            fos.write(imgUrl.getBytes());
            fos.close();

            var pic = cloudinary.uploader().upload(convFile, ObjectUtils.asMap("folder", "/images/"));
            var publicId = pic.get("public_id").toString();
            var picUrl = pic.get("url").toString();

            return Map.of("publicId", publicId, "url", picUrl);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to upload the file.");
        }
    }

    public void deleteFile(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("invalidate", true));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to delete the file.");
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty or null");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size must not exceed 500KB");
        }

        // MIME type validation (more secure than extension)
        String contentType = file.getContentType();
        if (
            contentType == null ||
            !(contentType.equals("image/jpeg") ||
                contentType.equals("image/png") ||
                contentType.equals("image/jpg") ||
                contentType.equals("image/webp") ||
                contentType.equals("image/svg"))
        ) {
            throw new IllegalArgumentException("Only JPEG, JPG, WEBP, SVG and PNG images are allowed");
        }

        // Optional: extension validation (extra layer)
        String filename = file.getOriginalFilename();
        if (filename != null && !filename.toLowerCase().matches(".*\\.(jpeg|jpg|png|webp|svg)$")) {
            throw new IllegalArgumentException("File extension must be .jpeg, .jpg, webp, svg or .png");
        }
    }
}
