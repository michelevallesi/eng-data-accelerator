package org.eda.simulator.teamcenter.config;

import java.util.List;
import org.eda.simulator.teamcenter.oslc.ModelHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, new ModelHttpMessageConverter());
    }
}
