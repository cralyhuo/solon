package org.noear.solon.boot.websocket.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import org.noear.solon.net.websocket.WebSocket;
import org.noear.solon.net.websocket.WebSocketRouter;

import java.nio.ByteBuffer;

/**
 * @author noear
 * @since 2.3
 */
public class WsServerHandler extends SimpleChannelInboundHandler<Object> {
    public static final AttributeKey<String> ResourceDescriptorKey = AttributeKey.valueOf("ResourceDescriptor");
    public static final AttributeKey<WebSocketServerHandshaker> HandshakerKey = AttributeKey.valueOf("Handshaker");
    public static final AttributeKey<WebSocketImpl> SessionKey = AttributeKey.valueOf("Session");

    private final WebSocketRouter webSocketRouter = WebSocketRouter.getInstance();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        //判断请求是HTTP请求还是WebSocket请求
        if (msg instanceof FullHttpRequest) {
            //处理WebSocket握手请求
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            //处理WebSocket请求
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        //先判断解码是否成功，然后判断是不是请求建立WebSocket连接

        //如果HTTP解码失败，返回HTTP异常
        if (!req.decoderResult().isSuccess()
                || (!"websocket".equals(req.headers().get("Upgrade")))) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        //生成 ResourceDescriptor
        String url = "ws://" + req.headers().get(HttpHeaderNames.HOST) + req.uri();

        //构造握手工厂创建握手处理类 WebSocketServerHandshaker，来构造握手响应返回给客户端
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(url, null, false);
        WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
            ctx.attr(HandshakerKey).set(handshaker);
            ctx.attr(ResourceDescriptorKey).set(req.uri());

            //listener.onOpen();
            WebSocketImpl webSocket = new WebSocketImpl(ctx);
            ctx.attr(SessionKey).set(webSocket);
            webSocketRouter.getListener().onOpen(webSocket);
        }
    }

    //如果接收到的消息是已经解码的WebSocketFrame消息
    public void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        //先对控制帧进行判断
        //判断是否是关闭链路的指令
        if (frame instanceof CloseWebSocketFrame) {
            WebSocketServerHandshaker handshaker = ctx.attr(HandshakerKey).get();
            if (handshaker != null) {
                handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            }
            return;
        }

        //判断是否是维持链路的Ping消息
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        if (frame instanceof TextWebSocketFrame) {
            //listener.onMessage();
            WebSocket webSocket = ctx.attr(SessionKey).get();
            String msgTxt = ((TextWebSocketFrame) frame).text();

            webSocketRouter.getListener().onMessage(webSocket, msgTxt);
            return;
        }

        if (frame instanceof BinaryWebSocketFrame) {
            //listener.onMessage();
            WebSocket webSocket = ctx.attr(SessionKey).get();
            byte[] msgBytes = frame.content().array();

            webSocketRouter.getListener().onMessage(webSocket, ByteBuffer.wrap(msgBytes));
            return;
        }
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse resp) {
        if (resp.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(resp.status().toString(), CharsetUtil.UTF_8);
            resp.content().writeBytes(buf);
            buf.release();
            HttpUtil.setContentLength(resp, resp.content().readableBytes());
        }
        ChannelFuture f = ctx.channel().writeAndFlush(resp);
        if (!HttpUtil.isKeepAlive(resp) || resp.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * 客户端掉线时的操作
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        //listener.onClose();
        WebSocketImpl webSocket = ctx.attr(SessionKey).get();
        if (webSocket.isClosed()) {
            return;
        } else {
            webSocket.close();
        }
        webSocketRouter.getListener().onClose(webSocket);
    }

    /**
     * 发生异常时执行的操作
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //listener.onError();
        WebSocket webSocket = ctx.attr(SessionKey).get();
        webSocketRouter.getListener().onError(webSocket, cause);
        ctx.close();
    }
}