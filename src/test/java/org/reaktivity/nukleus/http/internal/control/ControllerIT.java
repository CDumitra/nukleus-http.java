/**
 * Copyright 2016-2017 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.reaktivity.nukleus.http.internal.control;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.reaktivity.nukleus.http.internal.HttpController;
import org.reaktivity.reaktor.test.ReaktorRule;

public class ControllerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("route", "org/reaktivity/specification/nukleus/http/control/route")
        .addScriptRoot("unroute", "org/reaktivity/specification/nukleus/http/control/unroute");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final ReaktorRule controller = new ReaktorRule()
        .directory("target/nukleus-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(1024)
        .controller(HttpController.class::isAssignableFrom);

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout).around(controller);

    @Test
    @Specification({
        "${route}/server/nukleus"
    })
    public void shouldRouteServer() throws Exception
    {
        long targetRef = new Random().nextLong();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":authority", "localhost:8080");

        k3po.start();

        controller.controller(HttpController.class)
                  .routeServer("source", 0L, "target", targetRef, headers)
                  .get();

        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/client/nukleus"
    })
    public void shouldRouteClient() throws Exception
    {
        long targetRef = new Random().nextLong();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":authority", "localhost:8080");

        k3po.start();

        controller.controller(HttpController.class)
                  .routeClient("source", 0L, "target", targetRef, headers)
                  .get();

        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/nukleus",
        "${unroute}/server/nukleus"
    })
    public void shouldUnrouteServer() throws Exception
    {
        long targetRef = new Random().nextLong();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":authority", "localhost:8080");

        k3po.start();

        long sourceRef = controller.controller(HttpController.class)
                  .routeServer("source", 0L, "target", targetRef, headers)
                  .get();

        k3po.notifyBarrier("ROUTED_SERVER");

        controller.controller(HttpController.class)
                  .unrouteServer("source", sourceRef, "target", targetRef, headers)
                  .get();

        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/client/nukleus",
        "${unroute}/client/nukleus"
    })
    public void shouldUnrouteClient() throws Exception
    {
        long targetRef = new Random().nextLong();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":authority", "localhost:8080");

        k3po.start();

        long sourceRef = controller.controller(HttpController.class)
                  .routeClient("source", 0L, "target", targetRef, headers)
                  .get();

        k3po.notifyBarrier("ROUTED_CLIENT");

        controller.controller(HttpController.class)
                  .unrouteClient("source", sourceRef, "target", targetRef, headers)
                  .get();

        k3po.finish();
    }
}
