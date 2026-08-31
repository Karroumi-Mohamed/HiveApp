package com.hiveapp.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void aValidIncomingIdIsPreservedForTheRequestAndResponse() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelation.HEADER, "gateway-42:retry.1");
        var response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> duringRequest.set(RequestCorrelation.currentId()));

        assertThat(duringRequest).hasValue("gateway-42:retry.1");
        assertThat(response.getHeader(RequestCorrelation.HEADER)).isEqualTo("gateway-42:retry.1");
        assertThat(RequestCorrelation.currentId()).isNull();
    }

    @Test
    void anUnsafeIncomingIdIsReplacedAndMdcIsClearedAfterFailure() {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelation.HEADER, "unsafe id with spaces\nheader");
        var response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> filter.doFilter(
                        request,
                        response,
                        (ignoredRequest, ignoredResponse) -> {
                            duringRequest.set(RequestCorrelation.currentId());
                            throw new ServletException("expected");
                        }))
                .isInstanceOf(ServletException.class);

        assertThat(duringRequest.get())
                .isNotBlank()
                .isNotEqualTo("unsafe id with spaces\nheader")
                .matches("[0-9a-f-]{36}");
        assertThat(response.getHeader(RequestCorrelation.HEADER)).isEqualTo(duringRequest.get());
        assertThat(RequestCorrelation.currentId()).isNull();
    }
}
