/*
 * #%L
 * Wisdom-Framework
 * %%
 * Copyright (C) 2013 - 2014 Wisdom Framework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wisdom.framework.vertx;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.impl.DefaultVertxFactory;
import org.wisdom.api.Controller;
import org.wisdom.api.DefaultController;
import org.wisdom.api.configuration.ApplicationConfiguration;
import org.wisdom.api.content.ContentEncodingHelper;
import org.wisdom.api.content.ContentEngine;
import org.wisdom.api.content.ContentSerializer;
import org.wisdom.api.http.*;
import org.wisdom.api.router.Route;
import org.wisdom.api.router.RouteBuilder;
import org.wisdom.api.router.Router;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Check the wisdom server behavior.
 * This class is listening for http requests on random port.
 */
public class VertxHttpServerTest {

    private WisdomVertxServer server;

    DefaultVertxFactory factory = new DefaultVertxFactory();
    Vertx vertx = factory.createVertx();

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
        if (vertx != null) {
            vertx.stop();
        }

        failure.clear();
        success.clear();
    }

    @Test
    public void testServerStartSequence() throws InterruptedException, IOException {
        prepareServer();

        server.start();

        waitForStart(server);

        int port = server.httpPort();
        URL url = new URL("http://localhost:" + port + "/test");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        assertThat(connection.getResponseCode()).isEqualTo(404);

        assertThat(server.hostname()).isEqualTo("localhost");
        assertThat(port).isGreaterThan(9000);
        assertThat(server.httpsPort()).isEqualTo(-1);
    }

    public static void waitForStart(WisdomVertxServer server) throws InterruptedException {
        int attempt = 0;
        while (server.httpPort() == 0 && attempt < 10) {
            Thread.sleep(1000);
            attempt++;
        }
    }

    public static void waitForHttpsStart(WisdomVertxServer server) throws InterruptedException {
        int attempt = 0;
        while (server.httpsPort() == 0 && attempt < 10) {
            Thread.sleep(1000);
            attempt++;
        }
    }

    @Test
    public void testOk() throws InterruptedException, IOException {
        Router router = prepareServer();

        // Prepare the router with a controller
        Controller controller = new DefaultController() {
            @SuppressWarnings("unused")
            public Result index() {
                return ok("Alright");
            }
        };
        Route route = new RouteBuilder().route(HttpMethod.GET)
                .on("/")
                .to(controller, "index");
        when(router.getRouteFor("GET", "/")).thenReturn(route);

        server.start();
        waitForStart(server);

        int port = server.httpPort();
        URL url = new URL("http://localhost:" + port + "/");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        assertThat(connection.getResponseCode()).isEqualTo(200);
        String body = IOUtils.toString(connection.getInputStream());
        assertThat(body).isEqualTo("Alright");
    }

    @Test
    public void testInternalError() throws InterruptedException, IOException {
        Router router = prepareServer();

        // Prepare the router with a controller
        Controller controller = new DefaultController() {
            @SuppressWarnings("unused")
            public Result index() throws IOException {
                throw new IOException("My bad");
            }
        };

        Route route = new RouteBuilder().route(HttpMethod.GET)
                .on("/")
                .to(controller, "index");
        when(router.getRouteFor("GET", "/")).thenReturn(route);

        server.start();

        waitForStart(server);
        int port = server.httpPort();
        URL url = new URL("http://localhost:" + port + "/");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        assertThat(connection.getResponseCode()).isEqualTo(500);
    }

    private Router prepareServer() {
        // Prepare the configuration
        ApplicationConfiguration configuration = mock(ApplicationConfiguration.class);
        when(configuration.getIntegerWithDefault(eq("vertx.http.port"), anyInt())).thenReturn(0);
        when(configuration.getIntegerWithDefault(eq("vertx.https.port"), anyInt())).thenReturn(-1);
        when(configuration.getIntegerWithDefault("request.body.max.size", 100 * 1024)).thenReturn(100*1024);
        when(configuration.getIntegerWithDefault("vertx.acceptBacklog", -1)).thenReturn(-1);
        when(configuration.getIntegerWithDefault("vertx.receiveBufferSize", -1)).thenReturn(-1);
        when(configuration.getIntegerWithDefault("vertx.sendBufferSize", -1)).thenReturn(-1);


        Router router = mock(Router.class);

        // Configure the content engine.
        ContentSerializer serializer = new ContentSerializer() {
            @Override
            public String getContentType() {
                return null;
            }

            @Override
            public void serialize(Renderable<?> renderable) {
                if (renderable.content() instanceof Exception) {
                    renderable.setSerializedForm(((Exception) renderable.content()).getMessage());
                }
            }
        };
        ContentEncodingHelper encodingHelper = new ContentEncodingHelper() {

            @Override
            public List<String> parseAcceptEncodingHeader(String headerContent) {
                return new ArrayList<String>();
            }

            @Override
            public boolean shouldEncodeWithRoute(Route route) {
                return true;
            }

            @Override
            public boolean shouldEncodeWithSize(Route route,
                                                Renderable<?> renderable) {
                return true;
            }

            @Override
            public boolean shouldEncodeWithMimeType(Renderable<?> renderable) {
                return true;
            }

            @Override
            public boolean shouldEncode(Context context, Result result,
                                        Renderable<?> renderable) {
                return false;
            }

            @Override
            public boolean shouldEncodeWithHeaders(Map<String, String> headers) {
                return false;
            }
        };
        ContentEngine contentEngine = mock(ContentEngine.class);
        when(contentEngine.getContentEncodingHelper()).thenReturn(encodingHelper);
        when(contentEngine.getContentSerializerForContentType(anyString())).thenReturn(serializer);

        // Configure the server.
        server = new WisdomVertxServer();
        server.configuration = configuration;
        server.accessor = new ServiceAccessor(
                null,
                configuration,
                router,
                contentEngine,
                null,
                null
        );
        server.vertx = vertx;
        return router;
    }

    @Test
    public void testOkWithPlentyOfClients() throws InterruptedException, IOException {
        Router router = prepareServer();

        // Prepare the router with a controller
        Controller controller = new DefaultController() {
            @SuppressWarnings("unused")
            public Result index() {
                return ok(context().parameter("id"));
            }
        };
        Route route = new RouteBuilder().route(HttpMethod.GET)
                .on("/")
                .to(controller, "index");
        when(router.getRouteFor("GET", "/")).thenReturn(route);

        server.start();

        waitForStart(server);

        // Now start bunch of clients
        int num = 100;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(num);

        int port = server.httpPort();

        for (int i = 0; i < num; ++i) // create and start threads
            new Thread(new Client(startSignal, doneSignal, port, i)).start();

        startSignal.countDown();      // let all threads proceed
        doneSignal.await(30, TimeUnit.SECONDS);           // wait for all to finish

        assertThat(failure).isEmpty();
        assertThat(success).hasSize(num);
    }



    @Test
    public void testOkWithPlentyOfClientsReadingJsonContent() throws InterruptedException, IOException {
        Router router = prepareServer();

        // Prepare the router with a controller
        Controller controller = new DefaultController() {
            @SuppressWarnings("unused")
            public Result index() throws IOException {
                String content = IOUtils.toString(context().reader());
                if (!content.equals(context().body())) {
                    return badRequest("should be equal " + content + " / " + context().body());
                }
                return ok(context().body()).json();
            }
        };
        Route route = new RouteBuilder().route(HttpMethod.POST)
                .on("/")
                .to(controller, "index");
        when(router.getRouteFor("POST", "/")).thenReturn(route);

        server.start();

        waitForStart(server);

        // Now start bunch of clients
        int num = 100;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(num);

        int port = server.httpPort();

        for (int i = 0; i < num; ++i) // create and start threads
            new Thread(new PostClient(startSignal, doneSignal, port, i)).start();

        startSignal.countDown();      // let all threads proceed
        doneSignal.await(30, TimeUnit.SECONDS);           // wait for all to finish

        assertThat(failure).isEmpty();
        assertThat(success).hasSize(num);
    }




    private List<Integer> success = new ArrayList<>();
    private List<Integer> failure = new ArrayList<>();

    private class Client implements Runnable {
        private final CountDownLatch startSignal;
        private final CountDownLatch doneSignal;
        private final int port;
        private final int id;

        Client(CountDownLatch startSignal, CountDownLatch doneSignal, int port, int id) {
            this.startSignal = startSignal;
            this.doneSignal = doneSignal;
            this.port = port;
            this.id = id;
        }

        public void run() {
            try {
                startSignal.await();
                doWork();
                success.add(id);
            } catch (Throwable ex) {
                ex.printStackTrace();
                fail(id);
            } finally {
                doneSignal.countDown();
            }
        }

        void doWork() throws IOException {
            URL url = new URL("http://localhost:" + port + "/?id=" + id);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            assertThat(connection.getResponseCode()).isEqualTo(200);
            String body = IOUtils.toString(connection.getInputStream());
            assertThat(body).isEqualTo(String.valueOf(id));
        }
    }

    public synchronized void success(int id) {
        success.add(id);
    }

    public synchronized void fail(int id) {
        failure.add(id);
    }

    private class PostClient implements Runnable {
        private final CountDownLatch startSignal;
        private final CountDownLatch doneSignal;
        private final int port;
        private final int id;

        PostClient(CountDownLatch startSignal, CountDownLatch doneSignal, int port, int id) {
            this.startSignal = startSignal;
            this.doneSignal = doneSignal;
            this.port = port;
            this.id = id;
        }

        public void run() {
            try {
                startSignal.await();
                doWork();
            } catch (Throwable ex) {
                ex.printStackTrace();
                fail(id);
                doneSignal.countDown();
            }
        }

        void doWork() throws IOException {
            final String message = "{'id':" + id + "}";
            vertx.createHttpClient().setPort(port).setHost("localhost")
                    .post("/",
                            new Handler<HttpClientResponse>() {
                                public void handle(final HttpClientResponse response) {
                                    response.bodyHandler(new Handler<Buffer>() {
                                        public void handle(Buffer data) {
                                            try {
                                                assertThat(response.statusCode()).isEqualTo(200);
                                                assertThat(data.toString()).contains(message);
                                                success(id);
                                                doneSignal.countDown();
                                            } catch (Exception e) {
                                                System.err.println(e.getMessage());
                                                failure.add(id);
                                                doneSignal.countDown();
                                            }
                                        }
                                    });
                                }
                            })
                    .putHeader(HeaderNames.CONTENT_LENGTH, String.valueOf(message.length()))
                    .putHeader(HeaderNames.CONTENT_TYPE, MimeTypes.JSON)
                    .write(message)
                    .end();
        }
    }
}