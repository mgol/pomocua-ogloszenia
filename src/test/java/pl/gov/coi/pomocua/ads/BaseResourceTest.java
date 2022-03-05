package pl.gov.coi.pomocua.ads;

import org.junit.jupiter.api.BeforeEach;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pl.gov.coi.pomocua.ads.authentication.TestCurrentUser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestConfiguration.class)
public abstract class BaseResourceTest<T extends BaseOffer> {

    @MockBean
    private DateTimeProvider dateTimeProvider;

    @SpyBean
    private AuditingHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        handler.setDateTimeProvider(dateTimeProvider);
        LocalDateTime createdDateTime = createLocalDateTime("2020-10-17 00:00");

        when(dateTimeProvider.getNow()).thenReturn(Optional.of(createdDateTime));
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected TestCurrentUser testCurrentUser;

    @BeforeEach
    public void clear() {
        testCurrentUser.clear();
    }

    @Test
    void shouldCreateOffer() {
        assertPostResponseStatus(sampleOfferRequest(), HttpStatus.CREATED);
    }

    @Test
    void shouldReturnCreatedOfferOnList() {
        T response = postSampleOffer();

        T[] content = listOffers();
        assertThat(content).contains(response);
    }

    @Test
    void shouldReturnCreatedOffer() {
        T created = postSampleOffer();
        T returned = restTemplate.getForObject("/api/" + getOfferSuffix() + "/{id}", getClazz(), created.id);
        assertThat(returned).isEqualTo(created);
    }

    @Test
    void shouldRejectBlankTitle() {
        T offer = sampleOfferRequest();
        offer.title = "       ";
        assertPostResponseStatus(offer, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"<", ">", "(", ")", "%", "#", "@", "\"", "'"})
    void shouldRejectIncorrectTitle(String notAllowedChar) {
        T offer = sampleOfferRequest();
        offer.title = "title" + notAllowedChar;
        assertPostResponseStatus(offer, HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectTooLongTitle() {
        T offer = sampleOfferRequest();
        offer.title = "x".repeat(100);
        assertPostResponseStatus(offer, HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectBlankDescription() {
        T offer = sampleOfferRequest();
        offer.description = "       ";
        assertPostResponseStatus(offer, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"<", ">", "(", ")", "%", "#", "@", "\"", "'"})
    void shouldRejectIncorrectDescription(String notAllowedChar) {
        T offer = sampleOfferRequest();
        offer.description = "description" + notAllowedChar;
        assertPostResponseStatus(offer, HttpStatus.BAD_REQUEST);
    }

    protected void assertPostResponseStatus(T offer, HttpStatus status) {
        ResponseEntity<T> response = restTemplate.postForEntity("/api/secure/" + getOfferSuffix(), offer, getClazz());
        assertThat(response.getStatusCode()).isEqualTo(status);
    }

    @Test
    void shouldIgnoreSuppliedIdOnCreate() {
        T request = sampleOfferRequest();
        request.id = 42L;
        T response = restTemplate.postForObject("/api/secure/" + getOfferSuffix(), request, getClazz());
        assertThat(response.id).isNotNull();
        assertThat(response.id).isNotEqualTo(request.id);
    }

    protected abstract Class<T> getClazz();

    protected abstract String getOfferSuffix();

    protected abstract ParameterizedTypeReference<PageableResponse<T>> getResponseType();

    protected abstract T sampleOfferRequest();

    private T[] listOffers() {
        ResponseEntity<PageableResponse<T>> list = restTemplate.exchange("/api/" + getOfferSuffix(), HttpMethod.GET, null,
                getResponseType());
        return list.getBody().content;
    }

    protected T postSampleOffer() {
        T request = sampleOfferRequest();
        return postOffer(request);
    }

    @NotNull
    protected T postOffer(T request) {
        ResponseEntity<T> response = restTemplate.postForEntity("/api/secure/" + getOfferSuffix(), request, getClazz());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        T entity = response.getBody();
        assertThat(entity.id).isNotNull();
        assertThat(entity).usingRecursiveComparison().ignoringFields("id").isEqualTo(request);
        return entity;
    }

    private LocalDateTime createLocalDateTime(String date) {
        return LocalDateTime.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}