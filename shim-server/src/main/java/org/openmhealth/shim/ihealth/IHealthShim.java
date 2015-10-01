package org.openmhealth.shim.ihealth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.openmhealth.shim.*;
import org.openmhealth.shim.ihealth.mapper.*;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.resource.UserRedirectRequiredException;
import org.springframework.security.oauth2.client.token.AccessTokenRequest;
import org.springframework.security.oauth2.client.token.RequestEnhancer;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeResourceDetails;
import org.springframework.security.oauth2.common.AuthenticationScheme;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.DefaultOAuth2RefreshToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.util.SerializationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.slf4j.LoggerFactory.getLogger;


@Component
@ConfigurationProperties(prefix = "openmhealth.shim.ihealth")
public class IHealthShim extends OAuth2ShimBase {

    public static final String SHIM_KEY = "ihealth";

    private static final String API_URL = "https://api.ihealthlabs.com:8443/openapiv2/";

    private static final String AUTHORIZE_URL = "https://api.ihealthlabs.com:8443/OpenApiV2/OAuthv2/userauthorization/";

    private static final String TOKEN_URL = AUTHORIZE_URL;

    public static final List<String> IHEALTH_SCOPES = Arrays.asList("OpenApiActivity", "OpenApiBP", "OpenApiSleep",
            "OpenApiWeight", "OpenApiBG", "OpenApiSpO2", "OpenApiUserInfo", "OpenApiFood", "OpenApiSport");

    private static final Logger logger = getLogger(IHealthShim.class);

    @Autowired
    public IHealthShim(ApplicationAccessParametersRepo applicationParametersRepo,
            AuthorizationRequestParametersRepo authorizationRequestParametersRepo,
            AccessParametersRepo accessParametersRepo,
            ShimServerConfig shimServerConfig) {
        super(applicationParametersRepo, authorizationRequestParametersRepo, accessParametersRepo, shimServerConfig);
    }

    @Override
    public String getLabel() {
        return "iHealth";
    }

    @Override
    public String getShimKey() {
        return SHIM_KEY;
    }

    @Override
    public String getBaseAuthorizeUrl() {
        return AUTHORIZE_URL;
    }

    @Override
    public String getBaseTokenUrl() {
        return TOKEN_URL;
    }

    @Override
    public List<String> getScopes() {
        return IHEALTH_SCOPES;
    }

    @Override
    public AuthorizationCodeAccessTokenProvider getAuthorizationCodeAccessTokenProvider() {
        return new IHealthAuthorizationCodeAccessTokenProvider();
    }

    @Override
    public ShimDataType[] getShimDataTypes() {
        return new ShimDataType[] {
                IHealthDataTypes.PHYSICAL_ACTIVITY,
                IHealthDataTypes.BLOOD_GLUCOSE,
                IHealthDataTypes.BLOOD_PRESSURE,
                IHealthDataTypes.BODY_WEIGHT,
                IHealthDataTypes.BODY_MASS_INDEX
        };
    }

    @Value("${openmhealth.shim.ihealth.sportSC}")
    public String sportSC;
    @Value("${openmhealth.shim.ihealth.sportSV}")
    public String sportSV;

    public enum IHealthDataTypes implements ShimDataType {

        PHYSICAL_ACTIVITY("sport.json"),
        BLOOD_GLUCOSE("glucose.json"),
        BLOOD_PRESSURE("bp.json"),
        BODY_WEIGHT("weight.json"),
        //SLEEP("sleep"),
        //STEP_COUNT("activity"),
        BODY_MASS_INDEX("weight.json");

        private String endPoint;

        IHealthDataTypes(String endPoint) {

            this.endPoint = endPoint;
        }

        public String getEndPoint() {
            return endPoint;
        }

    }

    @Override
    protected ResponseEntity<ShimDataResponse> getData(OAuth2RestOperations restTemplate,
            ShimDataRequest shimDataRequest) throws ShimException {

        final IHealthDataTypes dataType;
        try {
            dataType = IHealthDataTypes.valueOf(
                    shimDataRequest.getDataTypeKey().trim().toUpperCase());
        }
        catch (NullPointerException | IllegalArgumentException e) {
            throw new ShimException("Null or Invalid data type parameter: "
                    + shimDataRequest.getDataTypeKey()
                    + " in shimDataRequest, cannot retrieve data.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startDate = shimDataRequest.getStartDateTime() == null ?
                now.minusDays(1) : shimDataRequest.getStartDateTime();
        OffsetDateTime endDate = shimDataRequest.getEndDateTime() == null ?
                now.plusDays(1) : shimDataRequest.getEndDateTime();

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(API_URL);
        String userId = "uk";
        if(shimDataRequest.getAccessParameters()!=null){
            OAuth2AccessToken token = SerializationUtils.deserialize(shimDataRequest.getAccessParameters().getSerializedToken());;
            userId = Preconditions.checkNotNull((String) token.getAdditionalInformation().get("UserID"));
//            uriBuilder.path("user")
//                    .path(userId);
            uriBuilder.queryParam("access_token", token.getValue());
        }

        String scValue = getScValue(dataType);
        String svValue = getSvValue(dataType);

        uriBuilder.path("/user/")
                .path(userId+"/")
                .path(dataType.getEndPoint())
                .queryParam("client_id", restTemplate.getResource().getClientId())
                .queryParam("client_secret",restTemplate.getResource().getClientSecret())
                .queryParam("start_time", startDate.toEpochSecond())
                .queryParam("end_time",endDate.toEpochSecond())
                .queryParam("locale","default")
                .queryParam("sc",scValue)
                .queryParam("sv",svValue);

//        String urlRequest = API_URL + "/user/" + userId + "/" + dataType.getEndPoint() + ".json?";
//
//        urlRequest += "&start_time=" + startDate.toEpochSecond();
//        urlRequest += "&end_time=" + endDate.toEpochSecond();
//        urlRequest += "&client_id=" + restTemplate.getResource().getClientId();
//        urlRequest += "&client_secret=" + restTemplate.getResource().getClientSecret();
//        urlRequest += "&access_token=" + token.getValue();
//        urlRequest += "&locale=default";

        ResponseEntity<JsonNode> responseEntity;
        try{
            URI url = uriBuilder.build().encode().toUri();
            responseEntity = restTemplate.getForEntity(url, JsonNode.class);
        }
        catch (HttpClientErrorException | HttpServerErrorException e) {
            // FIXME figure out how to handle this
            logger.error("A request for iHealth data failed.", e);
            throw e;
        }


        //ObjectMapper objectMapper = new ObjectMapper();

        if (shimDataRequest.getNormalize()) {
            //                SimpleModule module = new SimpleModule();
            //                module.addDeserializer(ShimDataResponse.class, dataType.getNormalizer());
            //                objectMapper.registerModule(module);
            IHealthDataPointMapper mapper;

            switch ( dataType ) {

                case PHYSICAL_ACTIVITY:
                    mapper = new IHealthPhysicalActivityDataPointMapper();
                    break;
                case BLOOD_GLUCOSE:
                    mapper = new IHealthBloodGlucoseDataPointMapper();
                    break;
                case BLOOD_PRESSURE:
                    mapper = new IHealthBloodPressureDataPointMapper();
                    break;
                case BODY_WEIGHT:
                    mapper = new IHealthBodyWeightDataPointMapper();
                    break;
                case BODY_MASS_INDEX:
                    mapper = new IHealthBloodPressureDataPointMapper();
                    break;
                default:
                    throw new UnsupportedOperationException();
            }


            return ResponseEntity.ok().body(
                    ShimDataResponse.result(SHIM_KEY, mapper.asDataPoints(singletonList(responseEntity.getBody()))));
            //                return new ResponseEntity<>(
            //                    objectMapper.readValue(responseEntity.getBody(), ShimDataResponse.class),
            // HttpStatus.OK);
        }
        else {

            return ResponseEntity.ok().body(ShimDataResponse.result(SHIM_KEY, responseEntity.getBody()));
        }
    }

    private String getScValue(IHealthDataTypes dataType) {

        switch(dataType){
            case PHYSICAL_ACTIVITY:
                return sportSC;
            default:
                throw new UnsupportedOperationException();
        }
    }

    private String getSvValue(IHealthDataTypes dataType) {

        switch (dataType){

            case PHYSICAL_ACTIVITY:
                return sportSV;
            default:
                throw new UnsupportedOperationException();
        }
    }

    //    @Override
//    public void trigger(OAuth2RestOperations restTemplate, ShimDataRequest shimDataRequest) throws ShimException {
//
//
//    }

    @Override
    public OAuth2ProtectedResourceDetails getResource() {
        AuthorizationCodeResourceDetails resource = (AuthorizationCodeResourceDetails) super.getResource();
        resource.setAuthenticationScheme(AuthenticationScheme.none);
        return resource;
    }

    @Override
    protected String getAuthorizationUrl(UserRedirectRequiredException exception) {
        final OAuth2ProtectedResourceDetails resource = getResource();
        return exception.getRedirectUri()
                + "?client_id=" + resource.getClientId()
                + "&response_type=code"
                + "&APIName=" + Joiner.on(' ').join(resource.getScope())
                + "&redirect_uri=" + getCallbackUrl() + "?state=" + exception.getStateKey();
    }

    public class IHealthAuthorizationCodeAccessTokenProvider extends AuthorizationCodeAccessTokenProvider {

        public IHealthAuthorizationCodeAccessTokenProvider() {
            this.setTokenRequestEnhancer(new RequestEnhancer() {

                @Override
                public void enhance(AccessTokenRequest request,
                        OAuth2ProtectedResourceDetails resource,
                        MultiValueMap<String, String> form, HttpHeaders headers) {

                    form.set("client_id", resource.getClientId());
                    form.set("client_secret", resource.getClientSecret());
                    form.set("redirect_uri", getCallbackUrl());
                    form.set("state", request.getStateKey());
                }
            });
        }

        @Override
        protected HttpMethod getHttpMethod() {
            return HttpMethod.GET;
        }

        @Override
        protected ResponseExtractor<OAuth2AccessToken> getResponseExtractor() {
            return new ResponseExtractor<OAuth2AccessToken>() {

                @Override
                public OAuth2AccessToken extractData(ClientHttpResponse response) throws IOException {

                    JsonNode node = new ObjectMapper().readTree(response.getBody());
                    String token = Preconditions
                            .checkNotNull(node.path("AccessToken").textValue(), "Missing access token: %s", node);
                    String refreshToken = Preconditions
                            .checkNotNull(node.path("RefreshToken").textValue(), "Missing refresh token: %s" + node);
                    String userId =
                            Preconditions.checkNotNull(node.path("UserID").textValue(), "Missing UserID: %s", node);
                    long expiresIn = node.path("Expires").longValue() * 1000;
                    Preconditions.checkArgument(expiresIn > 0, "Missing Expires: %s", node);

                    DefaultOAuth2AccessToken accessToken = new DefaultOAuth2AccessToken(token);
                    accessToken.setExpiration(new Date(System.currentTimeMillis() + expiresIn));
                    accessToken.setRefreshToken(new DefaultOAuth2RefreshToken(refreshToken));
                    accessToken.setAdditionalInformation(ImmutableMap.<String, Object>of("UserID", userId));
                    return accessToken;
                }
            };
        }
    }
}