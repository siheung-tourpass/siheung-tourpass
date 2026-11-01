package kr.ac.siheung.tourpass;

import jakarta.servlet.DispatcherType;
import kr.ac.siheung.tourpass.filter.SecurityFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(proxyBeanMethods = false)
public class TourpassApplication extends SpringBootServletInitializer {
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(TourpassApplication.class);
    }

    @Bean
    public FilterRegistrationBean<SecurityFilter> securityFilter() {
        var registration = new FilterRegistrationBean<>(new SecurityFilter());
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setOrder(0);
        return registration;
    }

    public static void main(String[] args) {
        SpringApplication.run(TourpassApplication.class, args);
    }
}
