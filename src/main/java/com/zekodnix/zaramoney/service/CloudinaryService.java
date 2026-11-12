package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import com.zekodnix.zaramoney.web.rest.vm.UserAccountVM;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CloudinaryService {

    private final UploadFileService uploadFileService;

    public CloudinaryService(UploadFileService uploadFileService) {
        this.uploadFileService = uploadFileService;
    }

    public record KycDocuments(String facePicture, String idCardPicture) {}

    /**
     * Uploads both KYC pictures (both are required) as private files and returns their references.
     * If the surrounding transaction rolls back, the uploaded files are deleted so no orphan ID documents remain.
     */
    public KycDocuments uploadKycDocuments(UserAccountVM userAccountVM) {
        if (userAccountVM.getFacePicture() == null || userAccountVM.getIdCardPicture() == null) {
            throw ApiException.badRequest("Both the face picture and the ID card picture are required", "kycdocumentsrequired");
        }
        // Validate both before uploading anything
        UploadFileService.readValidatedImage(userAccountVM.getFacePicture());
        UploadFileService.readValidatedImage(userAccountVM.getIdCardPicture());

        List<String> uploaded = new ArrayList<>();
        registerCleanupOnRollback(uploaded);
        String face = uploadFileService.uploadPrivateImage(userAccountVM.getFacePicture());
        uploaded.add(face);
        String idCard = uploadFileService.uploadPrivateImage(userAccountVM.getIdCardPicture());
        uploaded.add(idCard);
        return new KycDocuments(face, idCard);
    }

    private void registerCleanupOnRollback(List<String> uploaded) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        uploaded.forEach(uploadFileService::deleteQuietly);
                    }
                }
            }
        );
    }
}
