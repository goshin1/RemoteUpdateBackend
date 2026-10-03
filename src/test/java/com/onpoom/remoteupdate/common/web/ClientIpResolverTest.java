package com.onpoom.remoteupdate.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.onpoom.remoteupdate.config.AppProperties;

class ClientIpResolverTest {

    private static ClientIpResolver resolver(List<String> trustedProxies) {
        AppProperties props = new AppProperties(null, new AppProperties.Upload(Set.of()),
                new AppProperties.Security(new AppProperties.Login(5, Duration.ofMinutes(15)),
                        new AppProperties.IpFilter(false, true, trustedProxies)),
                null, null);
        return new ClientIpResolver(props);
    }

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void 신뢰하지_않는_곳에서_온_X_Forwarded_For_는_무시() {
        assertThat(resolver(List.of()).resolve(request("203.0.113.7", "10.0.0.1"))).isEqualTo("203.0.113.7");
    }

    @Test
    void 신뢰하는_프록시를_거치면_원래_클라이언트_IP() {
        ClientIpResolver r = resolver(List.of("127.0.0.1"));
        assertThat(r.resolve(request("127.0.0.1", "192.168.0.15"))).isEqualTo("192.168.0.15");
        // 클라이언트가 앞쪽에 위조 값을 넣어도, 프록시가 덧붙인 마지막 값이 사용됨
        assertThat(r.resolve(request("127.0.0.1", "1.2.3.4, 192.168.0.15"))).isEqualTo("192.168.0.15");
        assertThat(r.resolve(request("127.0.0.1", null))).isEqualTo("127.0.0.1");
    }
}
