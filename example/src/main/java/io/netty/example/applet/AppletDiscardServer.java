/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
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
package io.netty.example.applet;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;

import javax.swing.JApplet;

/**
 * {@link JApplet} which starts up a Server that receive data and discard it.
 */
public class AppletDiscardServer extends JApplet {

    private static final long serialVersionUID = -7824894101960583175L;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    /**
     * applet启动初始化
     */
    @Override
    public void init() {
    	// 这里其实只是关于线程池的准备东西，具体的线程启动不在这部分, 如下
    	// 1. 线程工厂ThreadFactory创建
    	// 2. 具体的thread生成在SingleThreadEventExecutor的构造器中。
    	// 3. 创建SingleThreadEventExecutor数组。
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
        	// ServerBootstrap的默认构造器什么也没做。
        	// ServerBootstrap extends AbstractBootstrap<ServerBootstrap, ServerChannel>
            ServerBootstrap bootstrap = new ServerBootstrap();
            
            // group方法其实就是设置了引导类中的boss线程组对象(group)和worker线程组对象(childGroup)。
            // channel方法主要来负责设置当前对象的channel工厂
            bootstrap.group(bossGroup, workerGroup)
                     .channel(NioServerSocketChannel.class)								// why need a class ?
                     // 其实ChannelInitializer本身就是一个ChannelHandler。用在这里
                     // 为了简单的初始化
                     .childHandler(new ChannelInitializer<SocketChannel>() {			
                         @Override
                         public void initChannel(SocketChannel ch) throws Exception {
                             ch.pipeline().addLast(new DiscardServerHandler());			// 初始化: add some handler to pipeline
                         }
                     });
            ChannelFuture f = bootstrap.bind(9999).sync();				// bind port to 9999. SYNC:DefaultChannelPromise
            f.channel().closeFuture().sync();							// 没看懂这个closeFuture的sync方法是想要干啥
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * 本例中的DiscardServer收到消息后的事件处理机制。
     * 
     * @author Administrator
     *
     */
    private static final class DiscardServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            System.out.println("Received: " + msg.toString(CharsetUtil.UTF_8));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
