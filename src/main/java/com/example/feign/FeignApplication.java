package com.example.feign;

import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.um.AisAccountAccessInfoTO;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.UserRoleTO;
import de.adorsys.ledgers.middleware.client.EnableLedgersMiddlewareRestClient;
import de.adorsys.ledgers.middleware.client.rest.AccountRestClient;
import de.adorsys.ledgers.middleware.client.rest.AuthRequestInterceptor;
import de.adorsys.ledgers.middleware.client.rest.ConsentRestClient;
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

import java.time.LocalDate;
import java.util.List;

import static java.util.Collections.singletonList;

@Slf4j
@SpringBootApplication
@EnableLedgersMiddlewareRestClient
@EnableFeignClients(basePackageClasses = {UserMgmtRestClient.class, AccountRestClient.class})
public class FeignApplication implements ApplicationListener<ApplicationReadyEvent> {
    private static final String LOGIN = "anton.brueckner";
    private static final String PIN = "12345";
    private static final String CONSENT_ID = "1";
    private static final LocalDate VALID_UNTIL = LocalDate.of(2020, 12, 12);
    public static final String IBAN = "DE80760700240271232400";

    private final UserMgmtRestClient userMgmtRestClient;
    private final AccountRestClient accountRestClient;
    private final ConsentRestClient consentRestClient;
    private final AuthRequestInterceptor auth;

    @Autowired
    public FeignApplication(UserMgmtRestClient userMgmtRestClient, AccountRestClient accountRestClient, ConsentRestClient consentRestClient, AuthRequestInterceptor auth) {
        this.userMgmtRestClient = userMgmtRestClient;
        this.accountRestClient = accountRestClient;
        this.consentRestClient = consentRestClient;
        this.auth = auth;
    }

    public static void main(String[] args) {
        SpringApplication.run(FeignApplication.class, args);
    }

    @Override
    public void onApplicationEvent(@NotNull ApplicationReadyEvent event) {
        try {
            ResponseEntity<SCALoginResponseTO> authorise = userMgmtRestClient.authorise(LOGIN, PIN, UserRoleTO.CUSTOMER);
            log.info(authorise.getBody().getBearerToken().getAccess_token());
            auth.setAccessToken(authorise.getBody().getBearerToken().getAccess_token());
            ResponseEntity<List<AccountDetailsTO>> listOfAccounts = accountRestClient.getListOfAccounts();
            listOfAccounts.getBody().forEach(a -> log.info(a.toString()));
        } catch (FeignException e) {
            log.error(e.getMessage());
        }

        //Long Term token calls sequence
        getLongTermLoginToken();
    }

    private void getLongTermLoginToken() {
        try {
            String consentId = CONSENT_ID;
            ResponseEntity<SCALoginResponseTO> loginResponse = userMgmtRestClient.authoriseForConsent(LOGIN, PIN, consentId, consentId, OpTypeTO.LOGIN);
            auth.setAccessToken(loginResponse.getBody().getBearerToken().getAccess_token());

            ResponseEntity<SCAConsentResponseTO> startSCA = consentRestClient.startSCA(consentId, getAisConsent(consentId));
            if (startSCA.getBody().getScaStatus() == ScaStatusTO.EXEMPTED) {
                printSuccess(startSCA);
            } else {
                ResponseEntity<SCAConsentResponseTO> selectMethod = consentRestClient.selectMethod(consentId, consentId, startSCA.getBody().getScaMethods().iterator().next().getId());
                //I've got static TAN here, but you can use some expression line following commented to get TAN out of response:
                /*String information = selectMethod.getBody().getChallengeData().getAdditionalInformation();
                int starIndex = information.indexOf("is");
                int endIndex = information.indexOf(". sent");
                String tan = information.substring(starIndex, endIndex).substring(3).trim();*/
                ResponseEntity<SCAConsentResponseTO> authorizeConsent = consentRestClient.authorizeConsent(consentId, consentId, "123456");
                printSuccess(authorizeConsent);
            }
        } catch (FeignException e) {
            log.error(e.getMessage());
        }
    }

    private void printSuccess(ResponseEntity<SCAConsentResponseTO> response) {
        BearerTokenTO token = response.getBody().getBearerToken();
        log.info("Login operation successful! Your long term token for {} days is :{}", token.getExpires_in() / 86400, token.getAccess_token());
    }

    private AisConsentTO getAisConsent(String consentId) {
        List<String> accountsList = singletonList(IBAN);
        return new AisConsentTO(consentId, LOGIN, "some TPP id", 100, new AisAccountAccessInfoTO(accountsList, accountsList, accountsList, null, null), VALID_UNTIL, false);
    }

}

