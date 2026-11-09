package com.ecommerce.service;

import com.ecommerce.entity.PageView;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PageViewRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService — recording page views")
class PageViewRecordingTest {

    private static final String BROWSER = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1";

    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock PageViewRepository pageViewRepository;
    @InjectMocks AnalyticsService service;

    private PageView saved() {
        ArgumentCaptor<PageView> captor = ArgumentCaptor.forClass(PageView.class);
        verify(pageViewRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("stores the page without query string or trailing slash, with session and source")
    void recordsShopperView() {
        assertThat(service.recordPageView("/products/cojin-azul/?utm_source=instagram#top", "abc123", "Instagram", BROWSER, false)).isTrue();

        PageView view = saved();
        assertThat(view.getPath()).isEqualTo("/products/cojin-azul");
        assertThat(view.getSessionId()).isEqualTo("abc123");
        assertThat(view.getSource()).isEqualTo("instagram");
        assertThat(view.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("sources are trimmed to a plain host-like word; www. is dropped")
    void normalizesSource() {
        assertThat(AnalyticsService.normalizeSource(" www.Google.com ")).isEqualTo("google.com");
        assertThat(AnalyticsService.normalizeSource("<script>")).isEqualTo("script");
        assertThat(AnalyticsService.normalizeSource("  ")).isNull();
        assertThat(AnalyticsService.normalizeSource(null)).isNull();
    }

    @Test
    @DisplayName("a malformed session id is dropped, the view still counts")
    void badSessionId() {
        service.recordPageView("/", "x".repeat(65), null, BROWSER, false);
        assertThat(saved().getSessionId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/orders", "/login", "/register", "/forgot-password", "/reset-password", "/checkout"})
    @DisplayName("admin, sign-in and checkout pages are not counted")
    void untrackedPages(String path) {
        assertThat(service.recordPageView(path, "s", null, BROWSER, false)).isFalse();
        verify(pageViewRepository, never()).save(any());
    }

    @Test
    @DisplayName("a page that only starts like an excluded one is counted")
    void similarPrefix() {
        assertThat(service.recordPageView("/administracion-de-hogar", "s", null, BROWSER, false)).isTrue();
    }

    @Test
    @DisplayName("an administrator browsing the store is not counted")
    void adminNotCounted() {
        assertThat(service.recordPageView("/", "s", null, BROWSER, true)).isFalse();
        verify(pageViewRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/131.0 Safari/537.36",
            "facebookexternalhit/1.1",
            "WhatsApp/2.23.20",
            "curl/8.4.0",
            "",
    })
    @DisplayName("bots, link previews, headless browsers and empty user agents are not counted")
    void botsNotCounted(String userAgent) {
        assertThat(service.recordPageView("/", "s", null, userAgent, false)).isFalse();
        verify(pageViewRepository, never()).save(any());
    }

    @Test
    @DisplayName("a blank path is ignored")
    void blankPath() {
        assertThat(service.recordPageView("  ", "s", null, BROWSER, false)).isFalse();
    }
}
