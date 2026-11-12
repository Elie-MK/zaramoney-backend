package com.zekodnix.zaramoney.web.rest;

import com.zekodnix.zaramoney.domain.enumeration.KycStatus;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import com.zekodnix.zaramoney.security.SecurityUtils;
import com.zekodnix.zaramoney.service.BackOfficeService;
import com.zekodnix.zaramoney.service.KycReviewService;
import com.zekodnix.zaramoney.service.RevenueReportService;
import com.zekodnix.zaramoney.service.dto.DepositDto;
import com.zekodnix.zaramoney.service.dto.KycReviewDTO;
import com.zekodnix.zaramoney.service.dto.RevenueReportDTO;
import com.zekodnix.zaramoney.service.dto.TransactionRecordDTO;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/** Back office. Everything under /api/admin/** requires ROLE_ADMIN (SecurityConfiguration), and the class is admin-only too. */
@RestController
@PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
@RequestMapping("/api/admin/backoffice")
public class BackOfficeResource {

    private static final Logger LOG = LoggerFactory.getLogger(BackOfficeResource.class);

    private final BackOfficeService backOfficeService;
    private final KycReviewService kycReviewService;
    private final RevenueReportService revenueReportService;

    public BackOfficeResource(
        BackOfficeService backOfficeService,
        KycReviewService kycReviewService,
        RevenueReportService revenueReportService
    ) {
        this.backOfficeService = backOfficeService;
        this.kycReviewService = kycReviewService;
        this.revenueReportService = revenueReportService;
    }

    /**
     * {@code GET /reports/revenue?from=2026-01-01&to=2026-12-31&groupBy=month} : ZaraMoney income (fees, bonus cost,
     * net) per day / month / year, Kinshasa time. {@code from}/{@code to} are inclusive days (ISO dates).
     */
    @GetMapping("/reports/revenue")
    public ResponseEntity<RevenueReportDTO> revenueReport(
        @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(name = "groupBy", defaultValue = "month") String groupBy
    ) {
        return ResponseEntity.ok(revenueReportService.report(from, to, RevenueReportService.GroupBy.parse(groupBy)));
    }

    @PostMapping("/user/deposit")
    public ResponseEntity<TransactionRecordDTO> moneyDeposit(@RequestBody @Valid DepositDto depositDto) {
        LOG.debug("REST request to deposit {} on account {}", depositDto.getAmount(), depositDto.getUserAccountNumber());

        var res = backOfficeService.addMoneyToUserAccount(depositDto);
        return ResponseEntity.ok(res);
    }

    /** {@code GET /kyc?status=PENDING} : KYC submissions to review, with short-lived document links. */
    @GetMapping("/kyc")
    public ResponseEntity<List<KycReviewDTO>> listKyc(
        @RequestParam(name = "status", defaultValue = "PENDING") KycStatus status,
        Pageable pageable
    ) {
        Page<KycReviewDTO> page = kycReviewService.findByStatus(status, pageable);
        var headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/kyc/{userDetailsId}")
    public ResponseEntity<KycReviewDTO> getKyc(@PathVariable Long userDetailsId) {
        return ResponseEntity.ok(kycReviewService.findOne(userDetailsId));
    }

    @PostMapping("/kyc/{userDetailsId}/approve")
    public ResponseEntity<KycReviewDTO> approveKyc(@PathVariable Long userDetailsId) {
        return ResponseEntity.ok(kycReviewService.approve(userDetailsId, SecurityUtils.getCurrentUserLogin().orElse("?")));
    }

    @PostMapping("/kyc/{userDetailsId}/reject")
    public ResponseEntity<KycReviewDTO> rejectKyc(@PathVariable Long userDetailsId) {
        return ResponseEntity.ok(kycReviewService.reject(userDetailsId, SecurityUtils.getCurrentUserLogin().orElse("?")));
    }
}
