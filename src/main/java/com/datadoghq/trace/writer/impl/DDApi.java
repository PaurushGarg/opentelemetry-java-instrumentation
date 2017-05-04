package com.datadoghq.trace.writer.impl;

import com.datadoghq.trace.impl.DDSpanSerializer;
import io.opentracing.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * The API pointing to a DD agent
 */
public class DDApi {

    private static final Logger logger = LoggerFactory.getLogger(DDApi.class.getName());

    private static final String TRACES_ENDPOINT = "/v0.3/traces";
    private static final String SERVICES_ENDPOINT = "/v0.3/services";

    private final String host;
    private final int port;
    private final String tracesEndpoint;
    private final String servicesEndpoint;

    /**
     * The spans serializer: can be replaced. By default, it serialize in JSON.
     */
    private final DDSpanSerializer spanSerializer;

    public DDApi(String host, int port) {
        this(host, port, new DDSpanSerializer());
    }

    public DDApi(String host, int port, DDSpanSerializer spanSerializer) {
        super();
        this.host = host;
        this.port = port;
        this.tracesEndpoint = "http://" + host + ":" + port + TRACES_ENDPOINT;
        this.servicesEndpoint = "http://" + host + ":" + port + SERVICES_ENDPOINT;
        this.spanSerializer = spanSerializer;
    }

    /**
     * Send traces to the DD agent
     *
     * @param traces the traces to be sent
     * @return the staus code returned
     */
    public boolean sendTraces(List<List<Span>> traces) {
        String payload = null;
        try {
            payload = spanSerializer.serialize(traces);
        } catch (Exception e) {
            logger.error("Error during serialization of " + traces.size() + " traces.", e);
            return false;
        }

        int status = callPUT(tracesEndpoint, payload);
        if (status == 200) {
            logger.debug("Succesfully sent {} traces to the DD agent.", traces.size());
            return true;
        } else {
            logger.warn("Error while sending {} traces to the DD agent. Status: {}", traces.size(), status);
            return false;
        }
    }

    /**
     * PUT to an endpoint the provided JSON content
     *
     * @param endpoint
     * @param content
     * @return the status code
     */
    private int callPUT(String endpoint, String content) {
        HttpURLConnection httpCon = null;
        try {
            URL url = new URL(endpoint);
            httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("PUT");
            httpCon.setRequestProperty("Content-Type", "application/json");
        } catch (Exception e) {
            logger.warn("Error thrown before PUT call to the DD agent.", e);
            return -1;
        }

        try {
            OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
            out.write(content);
            out.close();
            int responseCode = httpCon.getResponseCode();
            if (responseCode == 200) {
                logger.debug("Sent the payload to the DD agent.");
            } else {
                logger.warn("Could not send the payload to the DD agent. Status: {} ResponseMessage: {}", httpCon.getResponseCode(), httpCon.getResponseMessage());
            }
            return responseCode;
        } catch (Exception e) {
            logger.warn("Could not send the payload to the DD agent.", e);
            return -1;
        }
    }

}
