package com.foodapp.orderservice.security;

import com.foodapp.orderservice.config.InternalSecretFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalSecretFilterTest {

    private static final String VALID_SECRET = "my-test-internal-secret";

    private InternalSecretFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalSecretFilter();
        ReflectionTestUtils.setField(filter, "internalSecret", VALID_SECRET);
    }

    @Test
    void shouldPassRequestWithCorrectSecret() throws Exception {
        var request = new MockHttpServletRequest("POST", "/internal/orders/123/payment-callback");
        request.addHeader("X-Internal-Secret", VALID_SECRET);
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReject401WhenSecretHeaderIsMissing() throws Exception {
        var request = new MockHttpServletRequest("POST", "/internal/orders/123/payment-callback");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldReject401WhenSecretIsWrong() throws Exception {
        var request = new MockHttpServletRequest("POST", "/internal/orders/123/payment-callback");
        request.addHeader("X-Internal-Secret", "wrong-secret");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldReject401WhenSecretIsEmpty() throws Exception {
        var request = new MockHttpServletRequest("POST", "/internal/orders/123/payment-callback");
        request.addHeader("X-Internal-Secret", "");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldSkipFilterForNonInternalPaths() throws Exception {
        var request = new MockHttpServletRequest("GET", "/orders/123");
        request.addHeader("X-Internal-Secret", VALID_SECRET);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        // shouldNotFilter returns true for non-/internal/ paths — filter logic is skipped
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldApplyFilterForInternalPaths() throws Exception {
        var request = new MockHttpServletRequest("GET", "/internal/anything");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldWriteUnauthorizedJsonBody() throws Exception {
        var request = new MockHttpServletRequest("POST", "/internal/orders/123/payment-callback");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getContentAsString()).contains("Unauthorized");
    }

    @Test
    void shouldPassSubPathsUnderInternalWithCorrectSecret() throws Exception {
        var request = new MockHttpServletRequest("POST", "/internal/orders/abc/payment-callback");
        request.addHeader("X-Internal-Secret", VALID_SECRET);
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
