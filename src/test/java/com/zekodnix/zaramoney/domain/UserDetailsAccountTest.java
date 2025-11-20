package com.zekodnix.zaramoney.domain;

import static com.zekodnix.zaramoney.domain.UserDetailsAccountTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.zekodnix.zaramoney.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class UserDetailsAccountTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(UserDetailsAccount.class);
        UserDetailsAccount userDetailsAccount1 = getUserDetailsAccountSample1();
        UserDetailsAccount userDetailsAccount2 = new UserDetailsAccount();
        assertThat(userDetailsAccount1).isNotEqualTo(userDetailsAccount2);

        userDetailsAccount2.setId(userDetailsAccount1.getId());
        assertThat(userDetailsAccount1).isEqualTo(userDetailsAccount2);

        userDetailsAccount2 = getUserDetailsAccountSample2();
        assertThat(userDetailsAccount1).isNotEqualTo(userDetailsAccount2);
    }
}
