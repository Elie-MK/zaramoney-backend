package com.zekodnix.zaramoney.service.dto;

import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import java.io.Serializable;

/** Admin view of a KYC submission. Document links are signed and expire after a few minutes. */
public record KycReviewDTO(
    Long userDetailsId,
    String login,
    String firstName,
    String lastName,
    String phoneNumber,
    String country,
    String address,
    KycStatus kycStatus,
    String facePictureUrl,
    String idCardPictureUrl
)
    implements Serializable {}
