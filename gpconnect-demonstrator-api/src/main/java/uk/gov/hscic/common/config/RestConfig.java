/*
 * Copyright 2015 Ripple OSI
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package uk.gov.hscic.common.config;

import org.apache.catalina.filters.HttpHeaderSecurityFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import uk.gov.hscic.auth.CertificateValidator;
import uk.gov.hscic.auth.KeyStoreFactory;
import uk.gov.hscic.common.filters.DefaultHeaderFilter;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.AbstractServletWebServerFactory;
import org.springframework.context.annotation.Bean;

@ServletComponentScan
@SpringBootApplication
@ComponentScan(basePackages = "uk.gov.hscic")
@PropertySource(ignoreResourceNotFound = true, value = {
    "file:${config.path}/gpconnect-demonstrator-api.properties",
    "file:${config.path}/gpconnect-demonstrator-api.environment.properties",
    // allow use of an external file from a docker instance
    "file:${config.path}/external/gpconnect-demonstrator-api.environment.properties"
})
public class RestConfig {

    @Value("${config.path}")
    private String configPath;

    @Value("${server.port.http:-1}")
    private Integer httpPort;

    @Value("${server.port:-1}")
    private Integer httpsPort;

    @Value("${server.keystore.password}")
    private String keystorePassword;

    @Value("${server.keystore.name}")
    private String keystoreName;

    @Value("${server.enable-hsts}")
    private Boolean enableHsts;

    public static void main(String[] args) {
        SpringApplication.run(RestConfig.class, args);
    }

    
    @Bean
    public AbstractServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat;

        if (enableHsts && httpPort != null && httpPort > 0) {
            tomcat = new TomcatServletWebServerFactory() {
                @Override
                protected void postProcessContext(Context context) {
                    SecurityConstraint securityConstraint = new SecurityConstraint();
                    securityConstraint.setUserConstraint("CONFIDENTIAL");
                    SecurityCollection collection = new SecurityCollection();
                    collection.addPattern("/*");
                    securityConstraint.addCollection(collection);
                    context.addConstraint(securityConstraint);
                }
            };
        } else {
            tomcat = new TomcatServletWebServerFactory();
        }

        tomcat.addAdditionalTomcatConnectors(createHttpConnector());

        return tomcat;
    }

    private Connector createHttpConnector() {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");

        connector.setScheme("http");
        connector.setSecure(false);
        connector.setPort(httpPort);

        if (enableHsts) {
            connector.setRedirectPort(httpsPort);
        }

        return connector;
    }

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public CertificateValidator certificateValidator() throws Exception {
        return new CertificateValidator(KeyStoreFactory.getKeyStore(configPath + keystoreName, keystorePassword));
    }

    @Bean
    public FilterRegistrationBean myFilterBean() {
        final FilterRegistrationBean filterRegBean = new FilterRegistrationBean();
        filterRegBean.setFilter(new DefaultHeaderFilter());
        filterRegBean.addUrlPatterns("/*");
        filterRegBean.setEnabled(Boolean.TRUE);
        filterRegBean.setName("Meu Filter");
        filterRegBean.setAsyncSupported(Boolean.TRUE);
        return filterRegBean;
    }

    @ConditionalOnProperty(value = "server.enable-hsts")
    @Bean
    public FilterRegistrationBean getHttpHeaderSecurityFilterBean() {
        final FilterRegistrationBean bean = new FilterRegistrationBean();

        HttpHeaderSecurityFilter filter = new HttpHeaderSecurityFilter();

        filter.setHstsEnabled(true);
        filter.setHstsIncludeSubDomains(true);
        filter.setHstsMaxAgeSeconds(31536000);

        bean.setFilter(filter);
        bean.addUrlPatterns("/*");
        bean.setEnabled(Boolean.TRUE);
        bean.setAsyncSupported(Boolean.TRUE);

        return bean;
    }
}
