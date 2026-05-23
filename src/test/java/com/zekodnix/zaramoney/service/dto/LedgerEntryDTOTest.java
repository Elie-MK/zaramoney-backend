package com.zekodnix.zaramoney.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.zekodnix.zaramoney.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class LedgerEntryDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(LedgerEntryDTO.class);
        LedgerEntryDTO ledgerEntryDTO1 = new LedgerEntryDTO();
        ledgerEntryDTO1.setId(1L);
        LedgerEntryDTO ledgerEntryDTO2 = new LedgerEntryDTO();
        assertThat(ledgerEntryDTO1).isNotEqualTo(ledgerEntryDTO2);
        ledgerEntryDTO2.setId(ledgerEntryDTO1.getId());
        assertThat(ledgerEntryDTO1).isEqualTo(ledgerEntryDTO2);
        ledgerEntryDTO2.setId(2L);
        assertThat(ledgerEntryDTO1).isNotEqualTo(ledgerEntryDTO2);
        ledgerEntryDTO1.setId(null);
        assertThat(ledgerEntryDTO1).isNotEqualTo(ledgerEntryDTO2);
    }
}
