package com.zekodnix.zaramoney.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.zekodnix.zaramoney.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class UserDetailsAccountDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(UserDetailsAccountDTO.class);
        UserDetailsAccountDTO userDetailsAccountDTO1 = new UserDetailsAccountDTO();
        userDetailsAccountDTO1.setId(1L);
        UserDetailsAccountDTO userDetailsAccountDTO2 = new UserDetailsAccountDTO();
        assertThat(userDetailsAccountDTO1).isNotEqualTo(userDetailsAccountDTO2);
        userDetailsAccountDTO2.setId(userDetailsAccountDTO1.getId());
        assertThat(userDetailsAccountDTO1).isEqualTo(userDetailsAccountDTO2);
        userDetailsAccountDTO2.setId(2L);
        assertThat(userDetailsAccountDTO1).isNotEqualTo(userDetailsAccountDTO2);
        userDetailsAccountDTO1.setId(null);
        assertThat(userDetailsAccountDTO1).isNotEqualTo(userDetailsAccountDTO2);
    }
}
