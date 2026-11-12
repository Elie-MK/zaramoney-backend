package com.zekodnix.zaramoney.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zekodnix.zaramoney.IntegrationTest;
import com.zekodnix.zaramoney.security.AuthoritiesConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression test: the JHipster-generated CRUD endpoints must never be reachable by a regular customer
 * (e.g. {@code PATCH /api/bank-accounts/{id}} with an arbitrary balance).
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(authorities = AuthoritiesConstants.USER)
class AdminOnlyCrudResourcesIT {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(
        strings = {
            "/api/bank-accounts",
            "/api/transaction-records",
            "/api/ledger-entries",
            "/api/transaction-fees",
            "/api/transaction-limits",
            "/api/idempotency-records",
            "/api/notifications",
            "/api/user-details-accounts",
        }
    )
    void regularUserIsForbidden(String baseUrl) throws Exception {
        String body = "{\"id\":1}";

        mockMvc.perform(get(baseUrl)).andExpect(status().isForbidden());
        mockMvc.perform(get(baseUrl + "/count")).andExpect(status().isForbidden());
        mockMvc.perform(get(baseUrl + "/1")).andExpect(status().isForbidden());
        mockMvc.perform(post(baseUrl).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(put(baseUrl + "/1").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc
            .perform(patch(baseUrl + "/1").contentType("application/merge-patch+json").content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete(baseUrl + "/1")).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/ledger-entries/plus/me", "/api/bank-accounts/plus/accounts/000000000000/verify" })
    void customerPlusEndpointsNestedUnderCrudPathsStayAccessible(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isOk());
    }
}
