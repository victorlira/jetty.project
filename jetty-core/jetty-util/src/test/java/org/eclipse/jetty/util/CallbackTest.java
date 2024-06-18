//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CallbackTest
{
    private Scheduler scheduler;

    @BeforeEach
    public void beforeEach() throws Exception
    {
        scheduler = new ScheduledExecutorScheduler();
        scheduler.start();
    }

    @AfterEach
    public void afterEach() throws Exception
    {
        scheduler.stop();
    }

    @Test
    public void testAbstractSuccess() throws Exception
    {
        TestAbstractCB callback = new TestAbstractCB();
        callback.succeeded();
        assertTrue(callback._complete.await(1, TimeUnit.SECONDS));
        assertThat(callback._success.get(), is(true));
        assertThat(callback._failure.get(), nullValue());
        assertThat(callback._abort.get(), nullValue());
        assertThat(callback._completed.get(), nullValue());

        // Everything now a noop
        assertFalse(callback.abort(new Throwable()));
        assertThat(callback._success.get(), is(true));
        assertThat(callback._failure.get(), nullValue());
        assertThat(callback._abort.get(), nullValue());
        assertThat(callback._completed.get(), nullValue());
    }

    @Test
    public void testAbstractFailure() throws Exception
    {
        Throwable failure = new Throwable();
        TestAbstractCB callback = new TestAbstractCB();
        callback.failed(failure);
        assertTrue(callback._complete.await(1, TimeUnit.SECONDS));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(failure));
        assertThat(callback._abort.get(), nullValue());
        assertThat(callback._completed.get(), sameInstance(failure));

        // Everything now a noop, other than suppression
        callback.succeeded();
        Throwable late = new Throwable();
        assertFalse(callback.abort(late));
        assertTrue(callback._complete.await(1, TimeUnit.SECONDS));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(failure));
        assertThat(callback._abort.get(), nullValue());
        assertThat(callback._completed.get(), sameInstance(failure));
        assertTrue(ExceptionUtil.areAssociated(failure, late));
    }

    @Test
    public void testAbstractAbortSuccess() throws Exception
    {
        TestAbstractCB callback = new TestAbstractCB();

        Throwable abort = new Throwable();
        callback.abort(abort);
        assertThat(callback._complete.getCount(), is(1L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), nullValue());

        callback.succeeded();
        assertThat(callback._complete.getCount(), is(0L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), sameInstance(abort));

        Throwable late = new Throwable();
        callback.failed(late);
        assertFalse(callback.abort(late));
        assertThat(callback._complete.getCount(), is(0L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), sameInstance(abort));
        assertTrue(ExceptionUtil.areAssociated(abort, late));
    }

    @Test
    public void testAbstractAbortFailure() throws Exception
    {
        TestAbstractCB callback = new TestAbstractCB();

        Throwable abort = new Throwable();
        callback.abort(abort);
        assertThat(callback._complete.getCount(), is(1L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), nullValue());

        Throwable failure = new Throwable();
        callback.failed(failure);
        assertThat(callback._complete.getCount(), is(0L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), sameInstance(abort));
        assertTrue(ExceptionUtil.areAssociated(abort, failure));

        Throwable late = new Throwable();
        callback.failed(late);
        assertFalse(callback.abort(late));
        assertThat(callback._complete.getCount(), is(0L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), sameInstance(abort));
        assertTrue(ExceptionUtil.areAssociated(abort, late));
    }

    @Test
    public void testAbstractSerializedAbortSuccess() throws Exception
    {
        CountDownLatch latch = new CountDownLatch(1);
        TestAbstractCB callback = new TestAbstractCB()
        {
            @Override
            protected void onFailed(Throwable cause)
            {
                try
                {
                    latch.await();
                    super.onFailed(cause);
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
            }
        };

        Throwable abort = new Throwable();
        new Thread(() -> callback.abort(abort)).start();
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(() -> callback._abort.get() != null);

        assertThat(callback._complete.getCount(), is(1L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), nullValue());
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), nullValue());

        callback.succeeded();
        assertThat(callback._complete.getCount(), is(1L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), nullValue());
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), nullValue());

        latch.countDown();
        assertTrue(callback._complete.await(1, TimeUnit.SECONDS));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), sameInstance(abort));
    }

    @Test
    public void testAbstractSerializedAbortFailure() throws Exception
    {
        CountDownLatch latch = new CountDownLatch(1);
        TestAbstractCB callback = new TestAbstractCB()
        {
            @Override
            protected void onFailed(Throwable cause)
            {
                try
                {
                    latch.await();
                    super.onFailed(cause);
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
            }
        };

        Throwable abort = new Throwable();
        new Thread(() -> callback.abort(abort)).start();
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(() -> callback._abort.get() != null);

        assertThat(callback._complete.getCount(), is(1L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), nullValue());
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), nullValue());

        Throwable failure = new Throwable();
        callback.failed(failure);
        assertThat(callback._complete.getCount(), is(1L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), nullValue());
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), nullValue());

        latch.countDown();
        assertTrue(callback._complete.await(1, TimeUnit.SECONDS));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), sameInstance(abort));
        assertTrue(ExceptionUtil.areAssociated(abort, failure));
    }

    @Test
    public void testCombineSuccess() throws Exception
    {
        TestAbstractCB callbackA = new TestAbstractCB();
        TestAbstractCB callbackB = new TestAbstractCB();
        Callback combined = Callback.combine(callbackA, callbackB);

        combined.succeeded();
        assertTrue(callbackA._complete.await(1, TimeUnit.SECONDS));
        assertThat(callbackA._success.get(), is(true));
        assertThat(callbackA._failure.get(), nullValue());
        assertThat(callbackA._abort.get(), nullValue());
        assertThat(callbackA._completed.get(), nullValue());

        assertTrue(callbackB._complete.await(1, TimeUnit.SECONDS));
        assertThat(callbackB._success.get(), is(true));
        assertThat(callbackB._failure.get(), nullValue());
        assertThat(callbackB._abort.get(), nullValue());
        assertThat(callbackB._completed.get(), nullValue());
    }

    @Test
    public void testCombineFailure() throws Exception
    {
        TestAbstractCB callbackA = new TestAbstractCB();
        TestAbstractCB callbackB = new TestAbstractCB();
        Callback combined = Callback.combine(callbackA, callbackB);

        Throwable failure = new Throwable();
        combined.failed(failure);

        assertTrue(callbackA._complete.await(1, TimeUnit.SECONDS));
        assertThat(callbackA._success.get(), is(false));
        assertThat(callbackA._failure.get(), sameInstance(failure));
        assertThat(callbackA._abort.get(), nullValue());
        assertThat(callbackA._completed.get(), sameInstance(failure));

        assertTrue(callbackB._complete.await(1, TimeUnit.SECONDS));
        assertThat(callbackB._success.get(), is(false));
        assertThat(callbackB._failure.get(), sameInstance(failure));
        assertThat(callbackB._abort.get(), nullValue());
        assertThat(callbackB._completed.get(), sameInstance(failure));
    }

    @Test
    public void testCombineAbortSuccess() throws Exception
    {
        TestAbstractCB callbackA = new TestAbstractCB();
        TestAbstractCB callbackB = new TestAbstractCB();
        Callback combined = Callback.combine(callbackA, callbackB);

        Throwable abort = new Throwable();
        combined.abort(abort);

        assertThat(callbackA._complete.getCount(), is(1L));
        assertThat(callbackA._success.get(), is(false));
        assertThat(callbackA._failure.get(), sameInstance(abort));
        assertThat(callbackA._abort.get(), sameInstance(abort));
        assertThat(callbackA._completed.get(), nullValue());

        assertThat(callbackB._complete.getCount(), is(1L));
        assertThat(callbackB._success.get(), is(false));
        assertThat(callbackB._failure.get(), sameInstance(abort));
        assertThat(callbackB._abort.get(), sameInstance(abort));
        assertThat(callbackB._completed.get(), nullValue());


        combined.succeeded();

        assertThat(callbackA._complete.getCount(), is(0L));
        assertThat(callbackA._success.get(), is(false));
        assertThat(callbackA._failure.get(), sameInstance(abort));
        assertThat(callbackA._abort.get(), sameInstance(abort));
        assertThat(callbackA._completed.get(), sameInstance(abort));
        assertThat(callbackB._complete.getCount(), is(0L));
        assertThat(callbackB._success.get(), is(false));
        assertThat(callbackB._failure.get(), sameInstance(abort));
        assertThat(callbackB._abort.get(), sameInstance(abort));
        assertThat(callbackB._completed.get(), sameInstance(abort));

    }

    @Test
    public void testCombineAbortFailure() throws Exception
    {
        TestAbstractCB callbackA = new TestAbstractCB();
        TestAbstractCB callbackB = new TestAbstractCB();
        Callback combined = Callback.combine(callbackA, callbackB);

        Throwable abort = new Throwable();
        combined.abort(abort);
        assertThat(callbackA._complete.getCount(), is(1L));
        assertThat(callbackA._success.get(), is(false));
        assertThat(callbackA._failure.get(), sameInstance(abort));
        assertThat(callbackA._abort.get(), sameInstance(abort));
        assertThat(callbackA._completed.get(), nullValue());

        assertThat(callbackB._complete.getCount(), is(1L));
        assertThat(callbackB._success.get(), is(false));
        assertThat(callbackB._failure.get(), sameInstance(abort));
        assertThat(callbackB._abort.get(), sameInstance(abort));
        assertThat(callbackB._completed.get(), nullValue());

        Throwable failure = new Throwable();
        combined.failed(failure);

        assertThat(callbackA._complete.getCount(), is(0L));
        assertThat(callbackA._success.get(), is(false));
        assertThat(callbackA._failure.get(), sameInstance(abort));
        assertThat(callbackA._abort.get(), sameInstance(abort));
        assertThat(callbackA._completed.get(), sameInstance(abort));
        assertThat(callbackB._complete.getCount(), is(0L));
        assertThat(callbackB._success.get(), is(false));
        assertThat(callbackB._failure.get(), sameInstance(abort));
        assertThat(callbackB._abort.get(), sameInstance(abort));
        assertThat(callbackB._completed.get(), sameInstance(abort));

        assertTrue(ExceptionUtil.areAssociated(failure, abort));
    }

    @Test
    public void testWrapperSuccess() throws Exception
    {
        TestAbstractCB callback = new TestAbstractCB();
        Callback nested = new Callback.Wrapper(callback);
        nested.succeeded();
        assertTrue(callback._complete.await(1, TimeUnit.SECONDS));
        assertThat(callback._success.get(), is(true));
        assertThat(callback._failure.get(), nullValue());
        assertThat(callback._abort.get(), nullValue());
        assertThat(callback._completed.get(), nullValue());
    }

    @Test
    public void testWrapperFailure() throws Exception
    {
        Throwable failure = new Throwable();
        TestAbstractCB callback = new TestAbstractCB();
        Callback nested = new Callback.Wrapper(callback);
        nested.failed(failure);
        assertTrue(callback._complete.await(1, TimeUnit.SECONDS));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(failure));
        assertThat(callback._abort.get(), nullValue());
        assertThat(callback._completed.get(), sameInstance(failure));
    }

    @Test
    public void testWrapperAbortSuccess() throws Exception
    {
        TestAbstractCB callback = new TestAbstractCB();
        Callback nested = new Callback.Wrapper(callback);

        Throwable abort = new Throwable();
        nested.abort(abort);
        assertThat(callback._complete.getCount(), is(1L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), nullValue());

        nested.succeeded();
        assertThat(callback._complete.getCount(), is(0L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), sameInstance(abort));
    }

    @Test
    public void testWrapperAbortFailure() throws Exception
    {
        TestAbstractCB callback = new TestAbstractCB();
        Callback nested = new Callback.Wrapper(callback);

        Throwable abort = new Throwable();
        nested.abort(abort);
        assertThat(callback._complete.getCount(), is(1L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), nullValue());

        Throwable failure = new Throwable();
        nested.failed(failure);
        assertThat(callback._complete.getCount(), is(0L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), sameInstance(abort));
        assertTrue(ExceptionUtil.areAssociated(failure, abort));
    }

    @Test
    public void testNestedSuccess() throws Exception
    {
        TestAbstractCB callback = new TestAbstractCB();
        Callback nested = new Callback.Nested(callback);
        nested.succeeded();
        assertTrue(callback._complete.await(1, TimeUnit.SECONDS));
        assertThat(callback._success.get(), is(true));
        assertThat(callback._failure.get(), nullValue());
        assertThat(callback._abort.get(), nullValue());
        assertThat(callback._completed.get(), nullValue());
    }

    @Test
    public void testNestedFailure() throws Exception
    {
        Throwable failure = new Throwable();
        TestAbstractCB callback = new TestAbstractCB();
        Callback nested = new Callback.Nested(callback);
        nested.failed(failure);
        assertTrue(callback._complete.await(1, TimeUnit.SECONDS));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(failure));
        assertThat(callback._abort.get(), nullValue());
        assertThat(callback._completed.get(), sameInstance(failure));
    }

    @Test
    public void testNestedAbortSuccess() throws Exception
    {
        TestAbstractCB callback = new TestAbstractCB();
        Callback nested = new Callback.Nested(callback);

        Throwable abort = new Throwable();
        nested.abort(abort);
        assertThat(callback._complete.getCount(), is(1L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), nullValue());

        nested.succeeded();
        assertThat(callback._complete.getCount(), is(0L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), sameInstance(abort));
    }

    @Test
    public void testNestedAbortFailure() throws Exception
    {
        TestAbstractCB callback = new TestAbstractCB();
        Callback nested = new Callback.Nested(callback);

        Throwable abort = new Throwable();
        nested.abort(abort);
        assertThat(callback._complete.getCount(), is(1L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), nullValue());

        Throwable failure = new Throwable();
        nested.failed(failure);
        assertThat(callback._complete.getCount(), is(0L));
        assertThat(callback._success.get(), is(false));
        assertThat(callback._failure.get(), sameInstance(abort));
        assertThat(callback._abort.get(), sameInstance(abort));
        assertThat(callback._completed.get(), sameInstance(abort));
        assertTrue(ExceptionUtil.areAssociated(failure, abort));
    }

    @Test
    public void testAbortingWrappedByLegacyCallback() throws Exception
    {
        TestAbstractCB callback = new TestAbstractCB();
        Callback legacyCb = new Callback()
        {
            @Override
            public void succeeded()
            {
                callback.succeeded();
            }

            @Override
            public void failed(Throwable cause)
            {
                callback.failed(cause);
            }
        };

        Throwable cause = new Throwable();
        legacyCb.abort(cause);
        assertTrue(callback._complete.await(100, TimeUnit.MILLISECONDS));
        assertThat(callback._completed.get(), sameInstance(cause));
    }

    @Test
    public void testFromRunnableSuccess()
    {
        CountDownLatch completed = new CountDownLatch(1);
        Callback callback = Callback.from(completed::countDown);

        callback.succeeded();
        assertThat(completed.getCount(), is(0L));
    }

    @Test
    public void testFromRunnableFailure()
    {
        CountDownLatch completed = new CountDownLatch(1);
        Callback callback = Callback.from(completed::countDown);

        Throwable failure = new Throwable();
        callback.failed(failure);
        assertThat(completed.getCount(), is(0L));
    }

    @Test
    public void testFromRunnableAbort()
    {
        CountDownLatch completed = new CountDownLatch(1);
        Callback callback = Callback.from(completed::countDown);

        Throwable abort = new Throwable();
        callback.abort(abort);
        assertThat(completed.getCount(), is(1L));

        callback.succeeded();
        assertThat(completed.getCount(), is(0L));
    }

    @Test
    public void testFromRunnableCallbackSuccess()
    {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicLong success = new AtomicLong(-1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        Callback callback = Callback.from(complete::countDown, Callback.from(() -> success.set(complete.getCount()), failed::set));

        callback.succeeded();
        assertThat(complete.getCount(), is(0L));
        assertThat(success.get(), is(0L));
        assertThat(failed.get(), nullValue());
    }

    @Test
    public void testFromRunnableCallbackFailed()
    {
        CountDownLatch complete = new CountDownLatch(1);
        CountDownLatch success = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        Callback callback = Callback.from(complete::countDown, Callback.from(success::countDown, x -> failed.set(complete.getCount() == 0 ? x : null)));

        Throwable failure = new Throwable();
        callback.failed(failure);
        assertThat(complete.getCount(), is(0L));
        assertThat(success.getCount(), is(1L));
        assertThat(failed.get(), sameInstance(failure));
    }

    @Test
    public void testFromRunnableCallbackAborted()
    {
        CountDownLatch complete = new CountDownLatch(1);
        CountDownLatch success = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        Callback callback = Callback.from(complete::countDown, Callback.from(success::countDown, x -> failed.set(complete.getCount() == 0 ? x : null)));

        Throwable failure = new Throwable();
        callback.failed(failure);
        assertThat(complete.getCount(), is(0L));
        assertThat(success.getCount(), is(1L));
        assertThat(failed.get(), sameInstance(failure));
    }

    @Test
    public void testFromCallbackRunnableSuccess()
    {
        CountDownLatch success = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        Callback callback = Callback.from(Callback.from(success::countDown, failed::set), () ->
        {
            if (success.getCount() == 0)
                complete.countDown();
        });

        callback.succeeded();
        assertThat(success.getCount(), is(0L));
        assertThat(complete.getCount(), is(0L));
        assertThat(failed.get(), nullValue());
    }

    @Test
    public void testFromCallbackRunnableFailed()
    {
        CountDownLatch success = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        Callback callback = Callback.from(Callback.from(success::countDown, failed::set), () ->
        {
            if (failed.get() != null)
                complete.countDown();
        });

        Throwable failure = new Throwable();
        callback.failed(failure);
        assertThat(success.getCount(), is(1L));
        assertThat(complete.getCount(), is(0L));
        assertThat(failed.get(), sameInstance(failure));
    }

    @Test
    public void testFromRunnableConsumerSuccess()
    {
        CountDownLatch success = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        Callback callback = Callback.from(success::countDown, failed::set);

        callback.succeeded();
        assertThat(success.getCount(), is(0L));
        assertThat(failed.get(), nullValue());
    }

    @Test
    public void testFromRunnableConsumerFailure()
    {
        CountDownLatch success = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        Callback callback = Callback.from(success::countDown, failed::set);

        Throwable failure = new Throwable();
        callback.failed(failure);
        assertThat(success.getCount(), is(1L));
        assertThat(failed.get(), sameInstance(failure));
    }

    @Test
    public void testFromRunnableConsumerAbort()
    {
        CountDownLatch success = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        Callback callback = Callback.from(success::countDown, failed::set);

        // This from is a simple stateless wrapper that always calls the methods
        Throwable abort = new Throwable();
        callback.abort(abort);
        assertThat(success.getCount(), is(1L));
        assertThat(failed.get(), sameInstance(abort));

        Throwable failure = new Throwable();
        callback.failed(failure);
        assertThat(success.getCount(), is(1L));
        assertThat(failed.get(), sameInstance(failure));

        callback.succeeded();
        assertThat(success.getCount(), is(0L));
    }

    @Test
    public void testFromRunnableConsumerConsumerSuccess()
    {
        CountDownLatch success = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        AtomicMarkableReference<Throwable> completed = new AtomicMarkableReference<>(null, false);
        Callback callback = Callback.from(success::countDown, failed::set, x -> completed.set(x, true));

        callback.succeeded();
        assertThat(success.getCount(), is(0L));
        assertThat(failed.get(), nullValue());
        assertTrue(completed.isMarked());
        assertThat(completed.getReference(), nullValue());
    }

    @Test
    public void testFromRunnableConsumerConsumerFailed()
    {
        CountDownLatch success = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        AtomicMarkableReference<Throwable> completed = new AtomicMarkableReference<>(null, false);
        Callback callback = Callback.from(success::countDown, failed::set, x -> completed.set(x, true));

        Throwable failure = new Throwable();
        callback.failed(failure);
        assertThat(success.getCount(), is(1L));
        assertThat(failed.get(), sameInstance(failure));
        assertTrue(completed.isMarked());
        assertThat(completed.getReference(), sameInstance(failure));
    }

    @Test
    public void testFromRunnableConsumerConsumerAbort()
    {
        CountDownLatch success = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        AtomicMarkableReference<Throwable> completed = new AtomicMarkableReference<>(null, false);
        Callback callback = Callback.from(success::countDown, failed::set, x -> completed.set(x, true));

        Throwable abort = new Throwable();
        callback.abort(abort);
        assertThat(success.getCount(), is(1L));
        assertThat(failed.get(), sameInstance(abort));
        assertFalse(completed.isMarked());
        assertThat(completed.getReference(), nullValue());

        callback.succeeded();
        assertThat(success.getCount(), is(1L));
        assertThat(failed.get(), sameInstance(abort));
        assertTrue(completed.isMarked());
        assertThat(completed.getReference(), sameInstance(abort));
    }

    @Test
    public void testFromRunnableConsumerConsumerConsumerSuccess()
    {
        CountDownLatch success = new CountDownLatch(1);
        AtomicReference<Throwable> aborted = new AtomicReference<>();
        AtomicReference<Throwable> failed = new AtomicReference<>();
        AtomicMarkableReference<Throwable> completed = new AtomicMarkableReference<>(null, false);
        Callback callback = Callback.from(success::countDown, aborted::set, failed::set, x -> completed.set(x, true));

        callback.succeeded();
        assertThat(success.getCount(), is(0L));
        assertThat(aborted.get(), nullValue());
        assertThat(failed.get(), nullValue());
        assertTrue(completed.isMarked());
        assertThat(completed.getReference(), nullValue());
    }

    @Test
    public void testFromRunnableConsumerConsumerConsumerFailed()
    {
        CountDownLatch success = new CountDownLatch(1);
        AtomicReference<Throwable> aborted = new AtomicReference<>();
        AtomicReference<Throwable> failed = new AtomicReference<>();
        AtomicMarkableReference<Throwable> completed = new AtomicMarkableReference<>(null, false);
        Callback callback = Callback.from(success::countDown, aborted::set, failed::set, x -> completed.set(x, true));

        Throwable failure = new Throwable();
        callback.failed(failure);
        assertThat(success.getCount(), is(1L));
        assertThat(aborted.get(), nullValue());
        assertThat(failed.get(), sameInstance(failure));
        assertTrue(completed.isMarked());
        assertThat(completed.getReference(), sameInstance(failure));
    }

    @Test
    public void testFromRunnableConsumerConsumerConsumerAbort()
    {
        CountDownLatch success = new CountDownLatch(1);
        AtomicReference<Throwable> aborted = new AtomicReference<>();
        AtomicReference<Throwable> failed = new AtomicReference<>();
        AtomicMarkableReference<Throwable> completed = new AtomicMarkableReference<>(null, false);
        Callback callback = Callback.from(success::countDown, aborted::set, failed::set, x -> completed.set(x, true));

        Throwable abort = new Throwable();
        callback.abort(abort);
        assertThat(success.getCount(), is(1L));
        assertThat(aborted.get(), sameInstance(abort));
        assertThat(failed.get(), sameInstance(abort));
        assertFalse(completed.isMarked());
        assertThat(completed.getReference(), nullValue());

        callback.succeeded();
        assertThat(success.getCount(), is(1L));
        assertThat(aborted.get(), sameInstance(abort));
        assertThat(failed.get(), sameInstance(abort));
        assertTrue(completed.isMarked());
        assertThat(completed.getReference(), sameInstance(abort));
    }

    private static class TestAbstractCB extends Callback.Abstract
    {
        final AtomicBoolean _success = new AtomicBoolean();
        final AtomicReference<Throwable> _failure = new AtomicReference<>();
        final AtomicReference<Throwable> _abort = new AtomicReference<>();
        final AtomicReference<Throwable> _completed = new AtomicReference<>();
        final CountDownLatch _complete = new CountDownLatch(1);

        @Override
        protected void onAborted(Throwable cause)
        {
            _abort.compareAndSet(null, cause);
        }

        @Override
        protected void onCompleted(Throwable causeOrNull)
        {
            _completed.compareAndSet(null, causeOrNull);
            _complete.countDown();
        }

        @Override
        protected void onFailed(Throwable cause)
        {
            _failure.compareAndSet(null, cause);
        }

        @Override
        protected void onSucceeded()
        {
            _success.compareAndSet(false, true);
        }
    }
}
