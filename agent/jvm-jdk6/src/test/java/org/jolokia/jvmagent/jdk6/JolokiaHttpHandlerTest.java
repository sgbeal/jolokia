package org.jolokia.jvmagent.jdk6;

/*
 * Copyright 2009-2010 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.easymock.EasyMock;
import org.jolokia.util.ConfigKey;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.annotations.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 24.10.10
 */
public class JolokiaHttpHandlerTest {

    private JolokiaHttpHandler handler;


    @BeforeMethod
    public void setup() {
        handler = new JolokiaHttpHandler(getConfig());
        handler.start();
    }

    @AfterMethod
    public void tearDown() {
        handler.stop();
    }


    @Test
    public void testCallbackGet() throws IOException, URISyntaxException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia/read/java.lang:type=Memory/HeapMemoryUsage?callback=data");

        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("GET");

        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(handler, exchange, header);

        handler.handle(exchange);

        assertEquals(header.getFirst("content-type"),"text/javascript; charset=utf-8");
        String result = out.toString("utf-8");
        assertTrue(result.endsWith("});"));
        assertTrue(result.startsWith("data({"));
    }

    @Test
    public void testCallbackPost() throws URISyntaxException, IOException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia?callback=data");

        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("POST");

        Headers reqHeaders = new Headers();
        reqHeaders.add("Content-Type","text/plain; charset=UTF-8");
        expect(exchange.getRequestHeaders()).andReturn(reqHeaders);
        String req = "{\"timestamp\":1287914327,\"status\":200," +
                "\"request\":{\"mbean\":\"java.lang:type=Memory\",\"attribute\":\"HeapMemoryUsage\",\"type\":\"read\"}," +
                "\"value\":{\"max\":\"129957888\",\"committed\":\"85000192\",\"init\":\"0\",\"used\":\"6813824\"}}";
        byte[] buf = req.getBytes("utf-8");
        InputStream is = new ByteArrayInputStream(buf);
        expect(exchange.getRequestBody()).andReturn(is);
        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(handler, exchange, header);

        handler.handle(exchange);

        assertEquals(header.getFirst("content-type"),"text/javascript; charset=utf-8");
        String result = out.toString("utf-8");
        assertTrue(result.endsWith("});"));
        assertTrue(result.startsWith("data({"));
    }

    @Test
    public void invalidMethod() throws URISyntaxException, IOException, ParseException {
        HttpExchange exchange = prepareExchange("http://localhost:8080/");

        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("PUT");
        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(handler, exchange, header);
        handler.handle(exchange);

        JSONObject resp = (JSONObject) new JSONParser().parse(out.toString());
        assertTrue(resp.containsKey("error"));
        assertEquals(resp.get("error_type"),IllegalArgumentException.class.getName());
        assertTrue(((String) resp.get("error")).contains("PUT"));
    }

    @Test(expectedExceptions = IllegalStateException.class,expectedExceptionsMessageRegExp = ".*not.*started.*")
    public void handlerNotStarted() throws URISyntaxException, IOException {
        JolokiaHttpHandler newHandler = new JolokiaHttpHandler(getConfig());
        newHandler.handle(prepareExchange("http://localhost:8080/"));

    }

    @Test
    public void customRestrictor() throws URISyntaxException, IOException, ParseException {
        Map<ConfigKey,String> config = getConfig();
        config.put(ConfigKey.POLICY_LOCATION,"classpath:/access-restrictor.xml");
        JolokiaHttpHandler newHandler = new JolokiaHttpHandler(config);
        HttpExchange exchange = prepareExchange("http://localhost:8080/jolokia/read/java.lang:type=Memory/HeapMemoryUsage");
       // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("GET");
        Headers header = new Headers();
        ByteArrayOutputStream out = prepareResponse(handler, exchange, header);
        newHandler.start();
        try {
            newHandler.handle(exchange);
        } finally {
            newHandler.stop();
        }
        JSONObject resp = (JSONObject) new JSONParser().parse(out.toString());
        assertTrue(resp.containsKey("error"));
        assertTrue(((String) resp.get("error")).contains("not allowed"));
    }

    private HttpExchange prepareExchange(String pUri) throws URISyntaxException {
        HttpExchange exchange = EasyMock.createMock(HttpExchange.class);
        URI uri = new URI(pUri);
        expect(exchange.getRequestURI()).andReturn(uri);
        expect(exchange.getRemoteAddress()).andReturn(new InetSocketAddress(8080));
        return exchange;
    }

    private ByteArrayOutputStream prepareResponse(JolokiaHttpHandler handler, HttpExchange exchange, Headers header) throws IOException {
        expect(exchange.getResponseHeaders()).andReturn(header);
        exchange.sendResponseHeaders(anyInt(),anyLong());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        expect(exchange.getResponseBody()).andReturn(out);
        replay(exchange);
        return out;
    }

    private static boolean debugToggle = false;
    public Map<ConfigKey,String> getConfig() {
        Map<ConfigKey,String> map = new HashMap<ConfigKey, String>();
        map.put(ConfigKey.AGENT_CONTEXT,"/jolokia");
        map.put(ConfigKey.DEBUG,debugToggle ? "true" : "false");
        debugToggle = !debugToggle;
        return map;
    }
}
