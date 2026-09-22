package kr.ac.siheung.tourpass

import jakarta.servlet.DispatcherType
import kr.ac.siheung.tourpass.filter.SecurityFilter
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.Bean

@SpringBootApplication(proxyBeanMethods = false)
class TourpassApplication : SpringBootServletInitializer() {
    override fun configure(builder: SpringApplicationBuilder): SpringApplicationBuilder =
        builder.sources(TourpassApplication::class.java)

    @Bean
    fun securityFilter() = FilterRegistrationBean(SecurityFilter()).apply {
        addUrlPatterns("/*")
        setDispatcherTypes(DispatcherType.REQUEST)
        order = 0
    }
}

fun main(args: Array<String>) {
    runApplication<TourpassApplication>(*args)
}
