package org.eluder.coveralls.maven.plugin.httpclient;

/*
 * #[license]
 * coveralls-maven-plugin
 * %%
 * Copyright (C) 2013 - 2014 Tapio Rautonen
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * %[license]
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.eluder.coveralls.maven.plugin.ProcessingException;
import org.eluder.coveralls.maven.plugin.domain.CoverallsResponse;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;

import static org.apache.http.conn.ssl.SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

public class CoverallsClient {
    private static final X509TrustManager TRUST_ALL = new TrustAll();

    static {
        for (Provider provider : Security.getProviders()) {
            if (provider.getName().startsWith("SunPKCS11")) {
                Security.removeProvider(provider.getName());
            }
        }
    }

    private static final String FILE_NAME = "coveralls.json";
    private static final ContentType MIME_TYPE = ContentType.create("application/octet-stream", "utf-8");

    private static final int DEFAULT_CONNECTION_TIMEOUT = 10000;
    private static final int DEFAULT_SOCKET_TIMEOUT = 60000;

    private final String coverallsUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CoverallsClient(final String coverallsUrl) {
        this(coverallsUrl, createDefaultClient(), new ObjectMapper());
    }

    public CoverallsClient(final String coverallsUrl, final HttpClient httpClient, final ObjectMapper objectMapper) {
        this.coverallsUrl = coverallsUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public CoverallsResponse submit(final File file) throws ProcessingException, IOException {
        HttpEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addBinaryBody("json_file", file, MIME_TYPE, FILE_NAME)
                .build();
        HttpPost post = new HttpPost(coverallsUrl);
        post.setEntity(entity);
        HttpResponse response = httpClient.execute(post);
        return parseResponse(response);
    }

    private CoverallsResponse parseResponse(final HttpResponse response) throws ProcessingException, IOException {
        HttpEntity entity = response.getEntity();
        ContentType contentType = ContentType.getOrDefault(entity);
        InputStreamReader reader = null;
        try {
            final Charset charset = contentType.getCharset();
            reader = new InputStreamReader(entity.getContent(),
                    charset == null ? Consts.ISO_8859_1 : contentType.getCharset());

            CoverallsResponse cr = objectMapper.readValue(reader, CoverallsResponse.class);
            if (cr.isError()) {
                throw new ProcessingException(getResponseErrorMessage(response, cr.getMessage()));
            }
            return cr;
        } catch (JsonProcessingException ex) {
            throw new ProcessingException(getResponseErrorMessage(response, ex.getMessage()), ex);
        } catch (IOException ex) {
            throw new IOException(getResponseErrorMessage(response, ex.getMessage()), ex);
        } finally {
            IOUtil.close(reader);
        }
    }

    private String getResponseErrorMessage(final HttpResponse response, final String message) {
        int status = response.getStatusLine().getStatusCode();
        String reason = response.getStatusLine().getReasonPhrase();
        String errorMessage = "Report submission to Coveralls API failed with HTTP status " + status + ":";
        if (StringUtils.isNotBlank(reason)) {
            errorMessage += " " + reason;
        }
        if (StringUtils.isNotBlank(message)) {
            errorMessage += " (" + message + ")";
        }
        return errorMessage;
    }

    private static HttpClient createDefaultClient() {
        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(DEFAULT_CONNECTION_TIMEOUT)
                .setSocketTimeout(DEFAULT_SOCKET_TIMEOUT)
                .build();

        final HttpClientBuilder builder = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig);

        try {
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[] {TRUST_ALL}, new SecureRandom());
            final SSLConnectionSocketFactory factory =
                    new SSLConnectionSocketFactory(sslContext, ALLOW_ALL_HOSTNAME_VERIFIER);
            final Registry<ConnectionSocketFactory> registry =
                    RegistryBuilder.<ConnectionSocketFactory>create()
                                   .register("http", PlainConnectionSocketFactory.getSocketFactory())
                                   .register("https", factory)
                                   .build();

            builder.setConnectionManager(new BasicHttpClientConnectionManager(registry));
        } catch (KeyManagementException e) {
            throw new IllegalStateException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        return builder.build();
    }

    private static class TrustAll implements X509TrustManager {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] certs, final String type) {
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] certs, final String authType) {
        }
    }
}
