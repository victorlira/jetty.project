//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.client.util;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeferredContentProviderTest
{
    private ExecutorService executor;

    @BeforeEach
    public void prepare() throws Exception
    {
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    public void dispose() throws Exception
    {
        executor.shutdownNow();
    }

    @Test
    public void testWhenEmptyFlushDoesNotBlock() throws Exception
    {
        final DeferredContentProvider provider = new DeferredContentProvider();

        Future<?> task = executor.submit(new Callable<Object>()
        {
            @Override
            public Object call() throws Exception
            {
                provider.flush();
                return null;
            }
        });

        assertTrue(await(task, 5, TimeUnit.SECONDS));
    }

    @Test
    public void testOfferFlushBlocksUntilSucceeded() throws Exception
    {
        final DeferredContentProvider provider = new DeferredContentProvider();
        Iterator<ByteBuffer> iterator = provider.iterator();

        provider.offer(ByteBuffer.allocate(0));

        Future<?> task = executor.submit(new Callable<Object>()
        {
            @Override
            public Object call() throws Exception
            {
                provider.flush();
                return null;
            }
        });

        // Wait until flush() blocks.
        assertFalse(await(task, 1, TimeUnit.SECONDS));

        // Consume the content and succeed the callback.
        iterator.next();
        ((Callback)iterator).succeeded();

        // Flush should return.
        assertTrue(await(task, 5, TimeUnit.SECONDS));
    }

    @Test
    public void testCloseFlushDoesNotBlock() throws Exception
    {
        final DeferredContentProvider provider = new DeferredContentProvider();

        provider.close();

        Future<?> task = executor.submit(new Callable<Object>()
        {
            @Override
            public Object call() throws Exception
            {
                provider.flush();
                return null;
            }
        });

        // Wait until flush() blocks.
        assertTrue(await(task, 5, TimeUnit.SECONDS));
    }

    @Test
    public void testCloseNextHasNextReturnsFalse() throws Exception
    {
        DeferredContentProvider provider = new DeferredContentProvider();
        Iterator<ByteBuffer> iterator = provider.iterator();

        provider.close();

        assertFalse(iterator.hasNext());

        assertThrows(NoSuchElementException.class, () -> iterator.next());

        assertFalse(iterator.hasNext());
    }

    private boolean await(Future<?> task, long time, TimeUnit unit) throws Exception
    {
        try
        {
            task.get(time, unit);
            return true;
        }
        catch (TimeoutException x)
        {
            return false;
        }
    }
}