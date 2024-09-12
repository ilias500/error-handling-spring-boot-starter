package io.github.wimdeblauwe.errorhandlingspringbootstarter.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wimdeblauwe.errorhandlingspringbootstarter.ApiErrorResponseAccessDeniedHandler;
import io.github.wimdeblauwe.errorhandlingspringbootstarter.UnauthorizedEntryPoint;
import io.github.wimdeblauwe.errorhandlingspringbootstarter.mapper.ErrorCodeMapper;
import io.github.wimdeblauwe.errorhandlingspringbootstarter.mapper.ErrorMessageMapper;
import io.github.wimdeblauwe.errorhandlingspringbootstarter.mapper.HttpStatusMapper;
import io.github.wimdeblauwe.errorhandlingspringbootstarter.servlet.ServletErrorHandlingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(classes = {ServletErrorHandlingConfiguration.class,
        SpringSecurityApiExceptionHandlerTest.TestController.class,
        SpringSecurityApiExceptionHandlerTest.TestConfig.class})
class SpringSecurityApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testUnauthorized() throws Exception {
        mockMvc.perform(get("/test/spring-security/access-denied"))
               .andExpect(status().isUnauthorized())
               .andExpect(header().string("Content-Type", "application/json;charset=UTF-8"))
               .andExpect(jsonPath("code").value("UNAUTHORIZED"))
               .andExpect(jsonPath("message").value("Full authentication is required to access this resource"));
    }

    @Test
    @WithMockUser
    void testForbiddenViaSecuredAnnotation() throws Exception {
        mockMvc.perform(get("/test/spring-security/admin"))
               .andExpect(status().isForbidden())
               .andExpect(header().string("Content-Type", "application/json"))
               .andExpect(jsonPath("code").value("AUTHORIZATION_DENIED"))
               .andExpect(jsonPath("message").value("Access Denied"));
    }

    @Test
    @WithMockUser
    void testForbiddenViaGlobalSecurityConfig() throws Exception {
        mockMvc.perform(get("/test/spring-security/admin-global"))
               .andExpect(status().isForbidden())
               .andExpect(header().string("Content-Type", "application/json;charset=UTF-8"))
               .andExpect(jsonPath("code").value("ACCESS_DENIED"))
               .andExpect(jsonPath("message").value("Access Denied"));
    }

    @Test
    @WithMockUser
    void testAccessDenied() throws Exception {
        mockMvc.perform(get("/test/spring-security/access-denied"))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("code").value("ACCESS_DENIED"))
               .andExpect(jsonPath("message").value("Fake access denied"))
        ;
    }

    @Test
    @WithMockUser
    void testAccountExpired() throws Exception {
        mockMvc.perform(get("/test/spring-security/account-expired"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("code").value("ACCOUNT_EXPIRED"))
               .andExpect(jsonPath("message").value("Fake account expired"))
        ;
    }

    @RestController
    @RequestMapping("/test/spring-security")
    public static class TestController {

        @GetMapping("/access-denied")
        public void throwAccessDenied() {
            throw new AccessDeniedException("Fake access denied");
        }

        @GetMapping("/account-expired")
        public void throwAccountExpired() {
            throw new AccountExpiredException("Fake account expired");
        }

        @GetMapping("/admin")
        @Secured("ADMIN")
        public void requiresAdminRole() {

        }

        @GetMapping("/admin-global")
        public void requiresAdminRoleViaGlobalConfig() {

        }
    }

    @TestConfiguration
    @EnableMethodSecurity(securedEnabled = true)
    static class TestConfig {
        @Bean
        public UnauthorizedEntryPoint unauthorizedEntryPoint(HttpStatusMapper httpStatusMapper, ErrorCodeMapper errorCodeMapper, ErrorMessageMapper errorMessageMapper, ObjectMapper objectMapper) {
            return new UnauthorizedEntryPoint(httpStatusMapper, errorCodeMapper, errorMessageMapper, objectMapper);
        }

        @Bean
        public AccessDeniedHandler accessDeniedHandler(HttpStatusMapper httpStatusMapper, ErrorCodeMapper errorCodeMapper, ErrorMessageMapper errorMessageMapper, ObjectMapper objectMapper) {
            return new ApiErrorResponseAccessDeniedHandler(objectMapper, httpStatusMapper, errorCodeMapper, errorMessageMapper);
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                       UnauthorizedEntryPoint unauthorizedEntryPoint,
                                                       AccessDeniedHandler accessDeniedHandler) throws Exception {
            http.httpBasic().disable();

            http.authorizeHttpRequests()
                .requestMatchers("/test/spring-security/admin-global").hasRole("ADMIN")
                .anyRequest().authenticated();

            http.exceptionHandling()
                .authenticationEntryPoint(unauthorizedEntryPoint)
                .accessDeniedHandler(accessDeniedHandler);

            return http.build();
        }

    }

}
