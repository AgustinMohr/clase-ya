package com.claseya.auth.captcha;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TurnstileCaptchaVerifierTest {

    private static final String URL = "https://turnstile.test/siteverify";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private TurnstileCaptchaVerifier verifier(boolean enabled) {
        return new TurnstileCaptchaVerifier(builder.baseUrl(URL).build(), enabled, "secret");
    }

    @Test
    void disabledSkipsVerification() {
        verifier(false).verify(null);
        server.verify();
    }

    @Test
    void acceptsAValidToken() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        verifier(true).verify("turnstile-token");

        server.verify();
    }

    @Test
    void rejectsAnInvalidToken() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"success\":false}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verifier(true).verify("turnstile-token"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void requiresATokenWhenEnabled() {
        assertThatThrownBy(() -> verifier(true).verify(" "))
                .isInstanceOf(BadRequestException.class);
        server.verify();
    }

    @Test
    void failsClosedWhenTheProviderIsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> verifier(true).verify("turnstile-token"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    /** B-06: a provider that accepts the connection but never answers must not park the request. */
    @Test
    void aProviderThatNeverAnswersFailsFastWith503() throws Exception {
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/siteverify", exchange -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        slow.start();
        try {
            String url = "http://127.0.0.1:" + slow.getAddress().getPort() + "/siteverify";
            RestClient client = new TurnstileClientConfig()
                    .captchaRestClient(RestClient.builder(), Duration.ofMillis(300), url);
            TurnstileCaptchaVerifier verifier = new TurnstileCaptchaVerifier(client, true, "secret");

            long started = System.nanoTime();
            assertThatThrownBy(() -> verifier.verify("token"))
                    .isInstanceOf(ServiceUnavailableException.class);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            assertThat(elapsedMs).isLessThan(2_000);
        } finally {
            slow.stop(0);
        }
    }
}
