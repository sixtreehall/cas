package org.apereo.cas.config;

import org.apereo.cas.CentralAuthenticationService;
import org.apereo.cas.authentication.AuthenticationContextValidator;
import org.apereo.cas.authentication.AuthenticationEventExecutionPlan;
import org.apereo.cas.authentication.AuthenticationMetaDataPopulator;
import org.apereo.cas.authentication.AuthenticationSystemSupport;
import org.apereo.cas.authentication.MultifactorTriggerSelectionStrategy;
import org.apereo.cas.authentication.ProtocolAttributeEncoder;
import org.apereo.cas.authentication.principal.ResponseBuilder;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.config.support.authentication.AuthenticationEventExecutionPlanConfigurer;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.saml.OpenSamlConfigBean;
import org.apereo.cas.support.saml.authentication.SamlAuthenticationMetaDataPopulator;
import org.apereo.cas.support.saml.authentication.principal.SamlService;
import org.apereo.cas.support.saml.authentication.principal.SamlServiceFactory;
import org.apereo.cas.support.saml.authentication.principal.SamlServiceResponseBuilder;
import org.apereo.cas.support.saml.util.Saml10ObjectBuilder;
import org.apereo.cas.support.saml.util.SamlCompliantUniqueTicketIdGenerator;
import org.apereo.cas.support.saml.web.SamlValidateController;
import org.apereo.cas.support.saml.web.view.Saml10FailureResponseView;
import org.apereo.cas.support.saml.web.view.Saml10SuccessResponseView;
import org.apereo.cas.ticket.UniqueTicketIdGenerator;
import org.apereo.cas.ticket.proxy.ProxyHandler;
import org.apereo.cas.validation.ValidationSpecification;
import org.apereo.cas.web.support.ArgumentExtractor;
import org.apereo.cas.web.support.DefaultArgumentExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.View;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * This is {@link SamlConfiguration} that creates the necessary OpenSAML context and beans.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Configuration("samlConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
public class SamlConfiguration  {

    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    @Qualifier("casAttributeEncoder")
    private ProtocolAttributeEncoder protocolAttributeEncoder;

    @Autowired
    @Qualifier("cas3ServiceJsonView")
    private View cas3ServiceJsonView;

    @Autowired
    @Qualifier("proxy20Handler")
    private ProxyHandler proxy20Handler;

    @Autowired
    @Qualifier("shibboleth.OpenSAMLConfig")
    private OpenSamlConfigBean configBean;

    @Autowired
    @Qualifier("servicesManager")
    private ServicesManager servicesManager;

    @Autowired
    @Qualifier("centralAuthenticationService")
    private CentralAuthenticationService centralAuthenticationService;

    @Autowired
    @Qualifier("authenticationContextValidator")
    private AuthenticationContextValidator authenticationContextValidator;

    @Autowired
    @Qualifier("defaultAuthenticationSystemSupport")
    private AuthenticationSystemSupport authenticationSystemSupport;

    @Autowired
    @Qualifier("cas20WithoutProxyProtocolValidationSpecification")
    private ValidationSpecification cas20WithoutProxyProtocolValidationSpecification;

    @Autowired
    @Qualifier("defaultArgumentExtractor")
    private ArgumentExtractor argumentExtractor;

    @Autowired
    @Qualifier("defaultMultifactorTriggerSelectionStrategy")
    private MultifactorTriggerSelectionStrategy multifactorTriggerSelectionStrategy;

    @Autowired
    @Qualifier("uniqueIdGeneratorsMap")
    private Map uniqueIdGeneratorsMap;

    @RefreshScope
    @Bean
    public View casSamlServiceSuccessView() {
        return new Saml10SuccessResponseView(protocolAttributeEncoder,
                servicesManager, casProperties.getAuthn().getMfa().getAuthenticationContextAttribute(),
                saml10ObjectBuilder(), new DefaultArgumentExtractor(new SamlServiceFactory()),
                StandardCharsets.UTF_8.name(), casProperties.getSamlCore().getSkewAllowance(),
                casProperties.getSamlCore().getIssuer(), casProperties.getSamlCore().getAttributeNamespace());
    }

    @RefreshScope
    @Bean
    public View casSamlServiceFailureView() {
        return new Saml10FailureResponseView(protocolAttributeEncoder,
                servicesManager, casProperties.getAuthn().getMfa().getAuthenticationContextAttribute(),
                saml10ObjectBuilder(), new DefaultArgumentExtractor(new SamlServiceFactory()),
                StandardCharsets.UTF_8.name(), casProperties.getSamlCore().getSkewAllowance());
    }


    @Bean
    public ServiceFactory<SamlService> samlServiceFactory() {
        return new SamlServiceFactory();
    }

    @ConditionalOnMissingBean(name = "samlServiceResponseBuilder")
    @Bean
    public ResponseBuilder samlServiceResponseBuilder() {
        return new SamlServiceResponseBuilder();
    }

    @Bean
    public Saml10ObjectBuilder saml10ObjectBuilder() {
        return new Saml10ObjectBuilder(this.configBean);
    }

    @Bean
    public UniqueTicketIdGenerator samlServiceTicketUniqueIdGenerator() {
        final SamlCompliantUniqueTicketIdGenerator gen = new SamlCompliantUniqueTicketIdGenerator(casProperties.getServer().getName());
        gen.setSaml2compliant(casProperties.getSamlCore().isTicketidSaml2());
        return gen;
    }

    @Bean
    public SamlValidateController samlValidateController() {
        final SamlValidateController c = new SamlValidateController();
        c.setValidationSpecification(cas20WithoutProxyProtocolValidationSpecification);
        c.setSuccessView(casSamlServiceSuccessView());
        c.setFailureView(casSamlServiceFailureView());
        c.setProxyHandler(proxy20Handler);
        c.setAuthenticationSystemSupport(authenticationSystemSupport);
        c.setServicesManager(servicesManager);
        c.setCentralAuthenticationService(centralAuthenticationService);
        c.setArgumentExtractor(argumentExtractor);
        c.setMultifactorTriggerSelectionStrategy(multifactorTriggerSelectionStrategy);
        c.setAuthenticationContextValidator(authenticationContextValidator);
        c.setJsonView(cas3ServiceJsonView);
        c.setAuthnContextAttribute(casProperties.getAuthn().getMfa().getAuthenticationContextAttribute());
        return c;
    }

    @PostConstruct
    protected void initializeRootApplicationContext() {
        this.argumentExtractor.getServiceFactories().add(0, samlServiceFactory());
        uniqueIdGeneratorsMap.put(SamlService.class.getCanonicalName(), samlServiceTicketUniqueIdGenerator());
    }


}
