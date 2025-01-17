package org.noear.solon.boot.websocket;

import org.noear.solon.net.websocket.Handshake;
import org.noear.solon.net.websocket.WebSocketBase;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;

/**
 * @author noear
 * @since 2.6
 */
public class WebSocketImpl extends WebSocketBase {
    private final org.java_websocket.WebSocket real;

    public WebSocketImpl(org.java_websocket.WebSocket real) {
        this.real = real;
        this.init(URI.create(real.getResourceDescriptor()));
    }

    @Override
    public boolean isValid() {
        return isClosed() == false && real.isOpen();
    }

    @Override
    public boolean isSecure() {
        return real.hasSSLSupport();
    }

    @Override
    public Handshake getHandshake() {
        return null;
    }

    @Override
    public InetSocketAddress remoteAddress() throws IOException {
        return real.getRemoteSocketAddress();
    }

    @Override
    public InetSocketAddress localAddress() throws IOException {
        return real.getLocalSocketAddress();
    }

    @Override
    public void send(String text) {
        real.send(text);
    }

    @Override
    public void send(ByteBuffer binary) {
        real.send(binary);
    }

    @Override
    public void close() {
        super.close();
        real.close();
    }
}
