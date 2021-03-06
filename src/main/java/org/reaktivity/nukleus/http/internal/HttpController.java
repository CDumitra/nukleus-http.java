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
package org.reaktivity.nukleus.http.internal;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.reaktivity.nukleus.Controller;
import org.reaktivity.nukleus.ControllerSpi;
import org.reaktivity.nukleus.function.MessageConsumer;
import org.reaktivity.nukleus.function.MessagePredicate;
import org.reaktivity.nukleus.http.internal.types.OctetsFW;
import org.reaktivity.nukleus.http.internal.types.control.HttpRouteExFW;
import org.reaktivity.nukleus.http.internal.types.control.Role;
import org.reaktivity.nukleus.http.internal.types.control.RouteFW;
import org.reaktivity.nukleus.http.internal.types.control.UnrouteFW;

public final class HttpController implements Controller
{
    private static final int MAX_SEND_LENGTH = 1024; // TODO: Configuration and Context

    // TODO: thread-safe flyweights or command queue from public methods
    private final RouteFW.Builder routeRW = new RouteFW.Builder();
    private final UnrouteFW.Builder unrouteRW = new UnrouteFW.Builder();

    private final HttpRouteExFW.Builder routeExRW = new HttpRouteExFW.Builder();

    private final ControllerSpi controllerSpi;
    private final AtomicBuffer atomicBuffer;

    public HttpController(ControllerSpi controllerSpi)
    {
        this.controllerSpi = controllerSpi;
        this.atomicBuffer = new UnsafeBuffer(allocateDirect(MAX_SEND_LENGTH).order(nativeOrder()));
    }

    @Override
    public int process()
    {
        return controllerSpi.doProcess();
    }

    @Override
    public void close() throws Exception
    {
        controllerSpi.doClose();
    }

    @Override
    public Class<HttpController> kind()
    {
        return HttpController.class;
    }

    @Override
    public String name()
    {
        return "http";
    }

    public <T> T supplySource(
        String source,
        BiFunction<MessagePredicate, ToIntFunction<MessageConsumer>, T> factory)
    {
        return controllerSpi.doSupplySource(source, factory);
    }

    public <T> T supplyTarget(
        String target,
        BiFunction<ToIntFunction<MessageConsumer>, MessagePredicate, T> factory)
    {
        return controllerSpi.doSupplyTarget(target, factory);
    }

    public CompletableFuture<Long> routeServer(
        String source,
        long sourceRef,
        String target,
        long targetRef,
        Map<String, String> headers)
    {
        return route(Role.SERVER, source, sourceRef, target, targetRef, headers);
    }

    public CompletableFuture<Long> routeClient(
        String source,
        long sourceRef,
        String target,
        long targetRef,
        Map<String, String> headers)
    {
        return route(Role.CLIENT, source, sourceRef, target, targetRef, headers);
    }

    public CompletableFuture<Void> unrouteServer(
        String source,
        long sourceRef,
        String target,
        long targetRef,
        Map<String, String> headers)
    {
        return unroute(Role.SERVER, source, sourceRef, target, targetRef, headers);
    }

    public CompletableFuture<Void> unrouteClient(
        String source,
        long sourceRef,
        String target,
        long targetRef,
        Map<String, String> headers)
    {
        return unroute(Role.CLIENT, source, sourceRef, target, targetRef, headers);
    }

    private Consumer<OctetsFW.Builder> extension(
        Map<String, String> headers)
    {
        if (headers != null)
        {
            return e -> e.set((buffer, offset, limit) ->
                routeExRW.wrap(buffer, offset, limit)
                         .headers(hs ->
                         {
                             headers.forEach((k, v) ->
                             {
                                 hs.item(h -> h.name(k).value(v));
                             });
                         })
                         .build()
                         .sizeof());
        }
        else
        {
            return e -> e.reset();
        }
    }

    private CompletableFuture<Long> route(
        Role role,
        String source,
        long sourceRef,
        String target,
        long targetRef,
        Map<String, String> headers)
    {
        long correlationId = controllerSpi.nextCorrelationId();

        RouteFW routeRO = routeRW.wrap(atomicBuffer, 0, atomicBuffer.capacity())
                                 .correlationId(correlationId)
                                 .role(b -> b.set(role))
                                 .source(source)
                                 .sourceRef(sourceRef)
                                 .target(target)
                                 .targetRef(targetRef)
                                 .extension(extension(headers))
                                 .build();

        return controllerSpi.doRoute(routeRO.typeId(), routeRO.buffer(), routeRO.offset(), routeRO.sizeof());
    }

    private CompletableFuture<Void> unroute(
        Role role,
        String source,
        long sourceRef,
        String target,
        long targetRef,
        Map<String, String> headers)
    {
        long correlationId = controllerSpi.nextCorrelationId();

        UnrouteFW unrouteRO = unrouteRW.wrap(atomicBuffer, 0, atomicBuffer.capacity())
                                 .correlationId(correlationId)
                                 .role(b -> b.set(role))
                                 .source(source)
                                 .sourceRef(sourceRef)
                                 .target(target)
                                 .targetRef(targetRef)
                                 .extension(extension(headers))
                                 .build();

        return controllerSpi.doUnroute(unrouteRO.typeId(), unrouteRO.buffer(), unrouteRO.offset(), unrouteRO.sizeof());
    }
}
