/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.quarkus.runtime.services.resources;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.quarkus.runtime.Environment;
import org.keycloak.quarkus.runtime.configuration.Configuration;
import org.keycloak.services.Urls;
import org.keycloak.services.resources.Cors;
import org.keycloak.theme.FreeMarkerException;
import org.keycloak.theme.Theme;
import org.keycloak.theme.freemarker.FreeMarkerProvider;
import org.keycloak.urls.UrlType;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Path("/realms")
public class DebugHostnameSettingsResource {
    public static final String DEFAULT_PATH_SUFFIX = "hostname-debug";
    public static final String PATH_FOR_TEST_CORS_IN_HEADERS = "test";


    @Context
    private KeycloakSession keycloakSession;

    private final Map<String, String> allConfigPropertiesMap;

    public DebugHostnameSettingsResource() {

        this.allConfigPropertiesMap = new LinkedHashMap<>();
        for (String key : ConstantsDebugHostname.RELEVANT_OPTIONS) {
            addOption(key);
        }

    }

    @GET
    @Path("/{realmName}/" + DEFAULT_PATH_SUFFIX)
    @Produces(MediaType.TEXT_HTML)
    public String debug(final @PathParam("realmName") String realmName) throws IOException, FreeMarkerException {
        FreeMarkerProvider freeMarkerProvider = keycloakSession.getProvider(FreeMarkerProvider.class);
        RealmModel realmModel = keycloakSession.realms().getRealmByName(realmName);

        URI frontendUri = keycloakSession.getContext().getUri(UrlType.FRONTEND).getBaseUri();
        URI backendUri = keycloakSession.getContext().getUri(UrlType.BACKEND).getBaseUri();
        URI adminUri = keycloakSession.getContext().getUri(UrlType.ADMIN).getBaseUri();

        String frontendTestUrl = getTest(realmModel, frontendUri);
        String backendTestUrl = getTest(realmModel, backendUri);
        String adminTestUrl = getTest(realmModel, adminUri);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("frontendUrl", frontendUri.toString());
        attributes.put("backendUrl", backendUri.toString());
        attributes.put("adminUrl", adminUri.toString());

        attributes.put("realm", realmModel.getName());
        attributes.put("realmUrl", realmModel.getAttribute("frontendUrl"));

        attributes.put("frontendTestUrl", frontendTestUrl);
        attributes.put("backendTestUrl", backendTestUrl);
        attributes.put("adminTestUrl", adminTestUrl);

        attributes.put("serverMode", Environment.isDevMode() ? "dev [start-dev]" : "production [start]");

        attributes.put("config", this.allConfigPropertiesMap);
        attributes.put("headers", getHeaders());

        return freeMarkerProvider.processTemplate(
                attributes,
                "debug-hostname-settings.ftl",
                keycloakSession.theme().getTheme("base", Theme.Type.LOGIN)
        );
    }

    @GET
    @Path("/{realmName}/" + DEFAULT_PATH_SUFFIX + "/" + PATH_FOR_TEST_CORS_IN_HEADERS)
    @Produces(MediaType.TEXT_PLAIN)
    public Response test(final @PathParam("realmName") String realmName) {
        Response.ResponseBuilder builder = Response.ok(PATH_FOR_TEST_CORS_IN_HEADERS + "-OK");
        String origin = keycloakSession.getContext().getRequestHeaders().getHeaderString(Cors.ORIGIN_HEADER);
        builder.header(Cors.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        builder.header(Cors.ACCESS_CONTROL_ALLOW_METHODS, "GET");
        return builder.build();
    }

    private void addOption(String key) {
        String rawValue = Configuration.getRawValue("kc." + key);
        if (rawValue != null && !rawValue.isEmpty()) {
            this.allConfigPropertiesMap.put(key, rawValue);
        }
    }


    private Map<String, String> getHeaders() {
        Map<String, String> headers = new TreeMap<>();
        HttpHeaders requestHeaders = keycloakSession.getContext().getRequestHeaders();
        for (String h : ConstantsDebugHostname.RELEVANT_HEADERS) {
            addProxyHeader(h, headers, requestHeaders);
        }
        return headers;
    }

    private void addProxyHeader(String header, Map<String, String> proxyHeaders, HttpHeaders requestHeaders) {
        String value = requestHeaders.getHeaderString(header);
        if (value != null && !value.isEmpty()) {
            proxyHeaders.put(header, value);
        }
    }

    private String getTest(RealmModel realmModel, URI baseUri) {
        return Urls.realmBase(baseUri)
                   .path("/{realmName}/{debugHostnameSettingsPath}/{pathForTestCORSInHeaders}")
                   .build(realmModel.getName(), DEFAULT_PATH_SUFFIX, PATH_FOR_TEST_CORS_IN_HEADERS)
                   .toString();
    }

}
