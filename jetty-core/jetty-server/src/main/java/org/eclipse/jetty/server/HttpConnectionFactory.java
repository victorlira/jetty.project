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

package org.eclipse.jetty.server;

import java.util.Objects;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.internal.HttpConnection;
import org.eclipse.jetty.util.annotation.Name;

/**
 * A Connection Factory for HTTP Connections.
 * <p>Accepts connections either directly or via SSL and/or ALPN chained connection factories.  The accepted
 * {@link HttpConnection}s are configured by a {@link HttpConfiguration} instance that is either created by
 * default or passed in to the constructor.
 */
public class HttpConnectionFactory extends AbstractConnectionFactory implements HttpConfiguration.ConnectionFactory
{
    private final HttpConfiguration _config;

    public HttpConnectionFactory()
    {
        this(new HttpConfiguration());
    }

    public HttpConnectionFactory(@Name("config") HttpConfiguration config)
    {
        super(HttpVersion.HTTP_1_1.asString());
        _config = Objects.requireNonNull(config);
        installBean(_config);
        setUseInputDirectByteBuffers(_config.isUseInputDirectByteBuffers());
        setUseOutputDirectByteBuffers(_config.isUseOutputDirectByteBuffers());
    }

    @Override
    public HttpConfiguration getHttpConfiguration()
    {
        return _config;
    }

    public boolean isUseInputDirectByteBuffers()
    {
        return _config.isUseInputDirectByteBuffers();
    }

    @Deprecated(forRemoval = true, since = "12.1.0")
    public void setUseInputDirectByteBuffers(boolean useInputDirectByteBuffers)
    {
        _config.setUseInputDirectByteBuffers(useInputDirectByteBuffers);
    }

    public boolean isUseOutputDirectByteBuffers()
    {
        return _config.isUseOutputDirectByteBuffers();
    }

    @Deprecated(forRemoval = true, since = "12.1.0")
    public void setUseOutputDirectByteBuffers(boolean useOutputDirectByteBuffers)
    {
        _config.setUseOutputDirectByteBuffers(useOutputDirectByteBuffers);
    }

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint)
    {
        HttpConnection connection = new HttpConnection(_config, connector, endPoint);
        connection.setUseInputDirectByteBuffers(isUseInputDirectByteBuffers());
        connection.setUseOutputDirectByteBuffers(isUseOutputDirectByteBuffers());
        return configure(connection, connector, endPoint);
    }
}
