package com.cake.clockify.addon.core.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SecurityHeadersFilterTest {

    private ClockifyAddonProperties props;
    private SecurityHeadersFilter filter;

    @BeforeEach
    void setUp() {
        props = new ClockifyAddonProperties("key", "name", "http://localhost", "desc", true, "", "", null);
        filter = new SecurityHeadersFilter(props);
    }

    @Test
    void setsDefaultSecurityHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        assertEquals("camera=(), microphone=(), geolocation=()", response.getHeader("Permissions-Policy"));
        assertNotNull(response.getHeader("Content-Security-Policy"));
        assertTrue(response.getHeader("Content-Security-Policy").contains("frame-ancestors https://app.clockify.me https://*.clockify.me"));
    }

    @Test
    void trimsExtraFrameAncestorsBeforeAddingThemToCsp() throws ServletException, IOException {
        props = new ClockifyAddonProperties(
                "key",
                "name",
                "http://localhost",
                "desc",
                true,
                "",
                " https://preview.example.com , https://embed.example.com ,, ",
                null);
        filter = new SecurityHeadersFilter(props);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertTrue(response.getHeader("Content-Security-Policy")
                .contains("frame-ancestors https://app.clockify.me https://*.clockify.me https://preview.example.com https://embed.example.com"));
    }

    @Test
    void setsHstsHeaderOnlyIfSecureAndEnabled() throws ServletException, IOException {
        // HSTS enabled, but request insecure -> no HSTS header
        MockHttpServletRequest insecureRequest = new MockHttpServletRequest("GET", "/test");
        insecureRequest.setSecure(false);
        MockHttpServletResponse insecureResponse = new MockHttpServletResponse();
        filter.doFilter(insecureRequest, insecureResponse, new MockFilterChain());
        assertNull(insecureResponse.getHeader("Strict-Transport-Security"));

        // HSTS enabled, request secure -> HSTS header present
        MockHttpServletRequest secureRequest = new MockHttpServletRequest("GET", "/test");
        secureRequest.setSecure(true);
        MockHttpServletResponse secureResponse = new MockHttpServletResponse();
        filter.doFilter(secureRequest, secureResponse, new MockFilterChain());
        assertNotNull(secureResponse.getHeader("Strict-Transport-Security"));
        assertEquals("max-age=63072000; includeSubDomains", secureResponse.getHeader("Strict-Transport-Security"));
    }

    @Test
    void setsHstsHeaderWhenForwardedProtoIsHttps() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.setSecure(false);
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("max-age=63072000; includeSubDomains", response.getHeader("Strict-Transport-Security"));
    }

    @Test
    void setsCacheControlNoStoreForApiRoutesOnly() throws ServletException, IOException {
        // Non-api route
        MockHttpServletRequest nonApiReq = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse nonApiResp = new MockHttpServletResponse();
        filter.doFilter(nonApiReq, nonApiResp, new MockFilterChain());
        assertNull(nonApiResp.getHeader("Cache-Control"));

        // Api route
        MockHttpServletRequest apiReq = new MockHttpServletRequest("GET", "/api/data");
        MockHttpServletResponse apiResp = new MockHttpServletResponse();
        filter.doFilter(apiReq, apiResp, new MockFilterChain());
        assertEquals("no-store", apiResp.getHeader("Cache-Control"));

        MockHttpServletRequest clockifyFacadeReq = new MockHttpServletRequest("GET", "/clockify/workspaces");
        MockHttpServletResponse clockifyFacadeResp = new MockHttpServletResponse();
        filter.doFilter(clockifyFacadeReq, clockifyFacadeResp, new MockFilterChain());
        assertEquals("no-store", clockifyFacadeResp.getHeader("Cache-Control"));
    }
}
