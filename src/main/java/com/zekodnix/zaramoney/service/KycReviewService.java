package com.zekodnix.zaramoney.service;

import com.zekodnix.zaramoney.domain.enumeration.AuditAction;
import com.zekodnix.zaramoney.domain.UserDetailsAccount;
import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import com.zekodnix.zaramoney.repository.UserDetailsAccountRepository;
import com.zekodnix.zaramoney.service.dto.KycReviewDTO;
import com.zekodnix.zaramoney.web.rest.errors.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Back-office KYC review: new users start PENDING and cannot send money until approved. */
@Service
@Transactional
public class KycReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(KycReviewService.class);

    private final UserDetailsAccountRepository userDetailsAccountRepository;
    private final UploadFileService uploadFileService;
    private final NotificationServicePlus notificationServicePlus;

    private final AuditService auditService;

    public KycReviewService(
        UserDetailsAccountRepository userDetailsAccountRepository,
        UploadFileService uploadFileService,
        NotificationServicePlus notificationServicePlus,
        AuditService auditService
    ) {
        this.auditService = auditService;
        this.userDetailsAccountRepository = userDetailsAccountRepository;
        this.uploadFileService = uploadFileService;
        this.notificationServicePlus = notificationServicePlus;
    }

    @Transactional(readOnly = true)
    public Page<KycReviewDTO> findByStatus(KycStatus status, Pageable pageable) {
        return userDetailsAccountRepository.findAllByKycStatus(status, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public KycReviewDTO findOne(Long userDetailsId) {
        return toDto(load(userDetailsId));
    }

    public KycReviewDTO approve(Long userDetailsId, String adminLogin) {
        return setStatus(userDetailsId, KycStatus.VERIFIED, adminLogin);
    }

    public KycReviewDTO reject(Long userDetailsId, String adminLogin) {
        return setStatus(userDetailsId, KycStatus.REJECTED, adminLogin);
    }

    private KycReviewDTO setStatus(Long userDetailsId, KycStatus status, String adminLogin) {
        UserDetailsAccount details = load(userDetailsId);
        KycStatus previous = details.getKycStatus();
        details.setKycStatus(status);
        userDetailsAccountRepository.save(details);
        LOG.info("KYC {} -> {} for userDetails {} by admin {}", previous, status, userDetailsId, adminLogin);
        if (previous != status) {
            auditService.record(
                status == KycStatus.VERIFIED ? AuditAction.KYC_APPROVED : AuditAction.KYC_REJECTED,
                "USER",
                details.getUser() != null ? details.getUser().getLogin() : "userDetails:" + userDetailsId,
                previous + " -> " + status + " (userDetails " + userDetailsId + ")"
            );
            // stored now, pushed after commit; a repeated approve/reject does not notify again
            notificationServicePlus.createKycStatusNotification(details, status);
        }
        return toDto(details);
    }

    private UserDetailsAccount load(Long userDetailsId) {
        return userDetailsAccountRepository
            .findOneWithEagerRelationships(userDetailsId)
            .orElseThrow(() -> ApiException.notFound("KYC record not found", "kycnotfound"));
    }

    private KycReviewDTO toDto(UserDetailsAccount d) {
        var user = d.getUser();
        return new KycReviewDTO(
            d.getId(),
            user != null ? user.getLogin() : null,
            user != null ? user.getFirstName() : null,
            user != null ? user.getLastName() : null,
            d.getPhoneNumber(),
            d.getCountry(),
            d.getAddress(),
            d.getKycStatus(),
            uploadFileService.signedUrl(d.getFacePicture()),
            uploadFileService.signedUrl(d.getIdCardPicture())
        );
    }
}
