package com.example.feign;

import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.UserRoleTO;
import de.adorsys.ledgers.middleware.client.EnableLedgersMiddlewareRestClient;
import de.adorsys.ledgers.middleware.client.rest.AccountRestClient;
import de.adorsys.ledgers.middleware.client.rest.AuthRequestInterceptor;
import de.adorsys.ledgers.middleware.client.rest.UserMgmtRestClient;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationListener;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Slf4j
@SpringBootApplication
@EnableLedgersMiddlewareRestClient
@EnableFeignClients(basePackageClasses = {UserMgmtRestClient.class, AccountRestClient.class})
public class FeignApplication implements ApplicationListener<ApplicationReadyEvent> {
    private final UserMgmtRestClient userMgmtRestClient;
    private final AccountRestClient accountRestClient;
    private final AuthRequestInterceptor auth;

    @Autowired
    public FeignApplication(UserMgmtRestClient userMgmtRestClient, AccountRestClient accountRestClient, AuthRequestInterceptor auth) {
        this.userMgmtRestClient = userMgmtRestClient;
        this.accountRestClient = accountRestClient;
        this.auth = auth;
    }

    public static void main(String[] args) {
        SpringApplication.run(FeignApplication.class, args);
    }

    @Override
    public void onApplicationEvent(@NotNull ApplicationReadyEvent event) {
        try {
            ResponseEntity<SCALoginResponseTO> authorise = userMgmtRestClient.authorise("anton.brueckner", "12345", UserRoleTO.CUSTOMER);
            log.info(authorise.getBody().getBearerToken().getAccess_token());
            auth.setAccessToken(authorise.getBody().getBearerToken().getAccess_token());
            ResponseEntity<List<AccountDetailsTO>> listOfAccounts = accountRestClient.getListOfAccounts();
            listOfAccounts.getBody().forEach(a -> log.info(a.toString()));
        }catch (FeignException e){
            log.error(e.getMessage());
        }
    }

}

