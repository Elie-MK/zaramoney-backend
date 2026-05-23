package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.web.rest.vm.UserAccountVM;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CloudinaryService {

    private final UploadFileService uploadFileService;

    public CloudinaryService(UploadFileService uploadFileService) {
        this.uploadFileService = uploadFileService;
    }


    public Map<String, Map<String, String>> getUploadedPictures(UserAccountVM userAccountVM) {
        Map<String, Map<String, String>> pictures = new HashMap<>();

        // Upload face picture
        if (userAccountVM.getFacePicture() != null) {
            Map<String, String> faceUpload = uploadFileService.uploadFile(userAccountVM.getFacePicture());
            validateUploadedFile(faceUpload);
            pictures.put("facePicture", faceUpload);
        }

        // Upload ID card picture
        if (userAccountVM.getIdCardPicture() != null) {
            Map<String, String> idCardUpload = uploadFileService.uploadFile(userAccountVM.getIdCardPicture());
            validateUploadedFile(idCardUpload);
            pictures.put("idCardPicture", idCardUpload);
        }

        if (pictures.isEmpty()) {
            throw new IllegalArgumentException("No valid image files found in UserAccountVM.");
        }

        return pictures;
    }

    private void validateUploadedFile(Map<String, String> file) {
        if (
            file == null ||
                !file.containsKey("url") ||
                !file.containsKey("publicId") ||
                file.get("url") == null ||
                file.get("publicId") == null
        ) {
            throw new IllegalStateException("Uploaded file missing url or publicId");
        }
    }
}
