package cl.colegiosaas.platform;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureGuardTest {

    final SchoolRepository schools = mock(SchoolRepository.class);
    final FeatureGuard guard = new FeatureGuard(schools);

    @RequiresFeature(Feature.PAYMENTS)
    static class PaymentsController {
        public void pay() {
        }

        public void other() {
        }
    }

    static class OpenController {
        @RequiresFeature(Feature.SCHEDULING)
        public void book() {
        }

        public void info() {
        }
    }

    @Test
    void pagesOfAModuleTheSchoolDidNotContractDoNotExist() throws Exception {
        when(schools.findSingleton()).thenReturn(Optional.of(new School("Colegio", Plan.BASE)));

        assertThatThrownBy(() -> guard.preHandle(request(), response(), handler(new PaymentsController(), "pay")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> guard.preHandle(request(), response(), handler(new OpenController(), "book")))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(guard.preHandle(request(), response(), handler(new OpenController(), "info"))).isTrue();
    }

    @Test
    void contractedModulesAndAddOnsAreAllowed() throws Exception {
        School school = new School("Colegio", Plan.COMMUNITY);
        school.enableFeature(Feature.PAYMENTS);
        when(schools.findSingleton()).thenReturn(Optional.of(school));

        assertThat(guard.preHandle(request(), response(), handler(new OpenController(), "book"))).isTrue();
        assertThat(guard.preHandle(request(), response(), handler(new PaymentsController(), "other"))).isTrue();
    }

    private static HandlerMethod handler(Object bean, String method) throws NoSuchMethodException {
        return new HandlerMethod(bean, bean.getClass().getMethod(method));
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest();
    }

    private static MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }
}
