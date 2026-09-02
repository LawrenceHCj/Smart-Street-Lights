package com.smartlamp.controller;

import com.smartlamp.config.SecurityConfig;
import com.smartlamp.security.JwtUtil;
import com.smartlamp.service.EvidenceChainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvidenceController.class)
@Import(SecurityConfig.class)
class EvidenceControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private EvidenceChainService evidenceChainService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserDetailsService userDetailsService;

    @BeforeEach
    void stubService() {
        when(evidenceChainService.getChainMetadata(anyString()))
                .thenReturn(new EvidenceChainService.ChainMetadata(false, 0L, "NONE", "NORMAL", null, "NONE", null, 0L, false));
        when(evidenceChainService.verify(anyString()))
                .thenReturn(new EvidenceChainService.VerificationResult("VALID_UNANCHORED", "VALID", "VALID", "VALID",
                        0, null, null, null, "NONE", 0L, null, 0L, false));
        when(evidenceChainService.getEntries(anyString(), anyInt(), anyInt(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
    }

    @Test
    void unauthenticatedGets401() throws Exception {
        mockMvc.perform(get("/api/evidence/SL-001/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void operatorCanViewStatus() throws Exception {
        mockMvc.perform(get("/api/evidence/SL-001/status").with(user("op").roles("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void operatorCannotVerify() throws Exception {
        mockMvc.perform(get("/api/evidence/SL-001/verify").with(user("op").roles("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void operatorCannotListEntries() throws Exception {
        mockMvc.perform(get("/api/evidence/SL-001/entries").with(user("op").roles("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void municipalCanVerify() throws Exception {
        mockMvc.perform(get("/api/evidence/SL-001/verify").with(user("muni").roles("municipal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void municipalCanListEntries() throws Exception {
        mockMvc.perform(get("/api/evidence/SL-001/entries").with(user("muni").roles("municipal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void adminCanAccessAll() throws Exception {
        mockMvc.perform(get("/api/evidence/SL-001/status").with(user("adm").roles("admin")))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/evidence/SL-001/verify").with(user("adm").roles("admin")))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/evidence/SL-001/entries").with(user("adm").roles("admin")))
                .andExpect(jsonPath("$.code").value(0));
    }
}
