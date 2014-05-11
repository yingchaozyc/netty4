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

package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.StringUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <PRE>
 * 		AbstractBootstrap是一个抽象的帮助类。他可以非常容易的引导通道(代码更容易，优雅?).
 * 		它支持方法链的方式去配置bootstrap。
 * 
 * 		bind()方法一般用在服务器端。如果没有在上下文使用ServerBootstrap， 那么bind一般会
 * 		使用在使用了UDP的场合。
 * </PRE>
 * 
 * {@link AbstractBootstrap} is a helper class that makes it easy to bootstrap a {@link Channel}. It support
 * method-chaining to provide an easy way to configure the {@link AbstractBootstrap}.
 *
 * <p>When not used in a {@link ServerBootstrap} context, the {@link #bind()} methods are useful for connectionless
 * transports such as datagram (UDP).</p>
 */
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> implements Cloneable {
	
	// 一般来说， 这里放的应该是boss线程组对象。 下边的这些值为什么用volatile来修饰不明白。 TODO
    private volatile EventLoopGroup group;
    
    // bootstrap channel工厂。
    private volatile ChannelFactory<? extends C> channelFactory;
    
    private volatile SocketAddress localAddress;
    
    // channel选项
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<ChannelOption<?>, Object>();
    private final Map<AttributeKey<?>, Object> attrs = new LinkedHashMap<AttributeKey<?>, Object>();
    private volatile ChannelHandler handler;

    AbstractBootstrap() {
        // Disallow extending from a different package.
    }

    AbstractBootstrap(AbstractBootstrap<B, C> bootstrap) {
        group = bootstrap.group;
        channelFactory = bootstrap.channelFactory;
        handler = bootstrap.handler;
        localAddress = bootstrap.localAddress;
        synchronized (bootstrap.options) {
            options.putAll(bootstrap.options);
        }
        synchronized (bootstrap.attrs) {
            attrs.putAll(bootstrap.attrs);
        }
    }

    /**
     * The {@link EventLoopGroup} which is used to handle all the events for the to-be-creates
     * {@link Channel}
     */
    @SuppressWarnings("unchecked")
    public B group(EventLoopGroup group) {
        if (group == null) {
            throw new NullPointerException("group");
        }
        if (this.group != null) {
            throw new IllegalStateException("group set already");
        }
        this.group = group;
        return (B) this;
    }

    /**
     * <pre>
     * 		ServerBootstrap extends AbstractBootstrap<ServerBootstrap, ServerChannel>
     * 		Bootstrap 		extends AbstractBootstrap<Bootstrap, 	   Channel>
     * 
     * 		TODO 这段原生的注释想说明什么？
     * </pre>
     * 
     * The {@link Class} which is used to create {@link Channel} instances from.
     * You either use this or {@link #channelFactory(ChannelFactory)} if your
     * {@link Channel} implementation has no no-args constructor.
     */
    public B channel(Class<? extends C> channelClass) {
    	// 首先如果传入的class不是extends于C， 则会在编译这层就会报错。
    	// 其次如果传入为null是不允许的。
        if (channelClass == null) {
            throw new NullPointerException("channelClass");
        }
        
        // new BootstrapChannelFactory<C>(channelClass)负责创建了一个channel工厂。他的newChannel方法
        // 可以返回channelClass的一个实例。
        //
        // channelFactory方法主要来负责设置当前对象的channel工厂。
        return channelFactory(new BootstrapChannelFactory<C>(channelClass));
    }

    /**
     * {@link ChannelFactory} which is used to create {@link Channel} instances from
     * when calling {@link #bind()}. This method is usually only used if {@link #channel(Class)}
     * is not working for you because of some more complex needs. If your {@link Channel} implementation
     * has a no-args constructor, its highly recommend to just use {@link #channel(Class)} for
     * simplify your code.
     */
    @SuppressWarnings("unchecked")
    public B channelFactory(ChannelFactory<? extends C> channelFactory) {
    	// channel工厂不可以为空
        if (channelFactory == null) {
            throw new NullPointerException("channelFactory");
        }
        
        // channel工厂不可以重复设置
        if (this.channelFactory != null) {
            throw new IllegalStateException("channelFactory set already");
        }

        // 设置channel工厂。
        this.channelFactory = channelFactory;
        
        // 返回当前对象，保持链式调用。about B:
        // boss：     ServerBootstrap
        // worker: Bootstrap
        return (B) this;
    }

    /**
     * The {@link SocketAddress} which is used to bind the local "end" to.
     *
     */
    @SuppressWarnings("unchecked")
    public B localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return (B) this;
    }

    /**
     * @see {@link #localAddress(SocketAddress)}
     */
    public B localAddress(int inetPort) {
        return localAddress(new InetSocketAddress(inetPort));
    }

    /**
     * @see {@link #localAddress(SocketAddress)}
     */
    public B localAddress(String inetHost, int inetPort) {
        return localAddress(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * @see {@link #localAddress(SocketAddress)}
     */
    public B localAddress(InetAddress inetHost, int inetPort) {
        return localAddress(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link Channel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     */
    @SuppressWarnings("unchecked")
    public <T> B option(ChannelOption<T> option, T value) {
        if (option == null) {
            throw new NullPointerException("option");
        }
        if (value == null) {
            synchronized (options) {
                options.remove(option);
            }
        } else {
            synchronized (options) {
                options.put(option, value);
            }
        }
        return (B) this;
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link Channel}.  If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    public <T> B attr(AttributeKey<T> key, T value) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (value == null) {
            synchronized (attrs) {
                attrs.remove(key);
            }
        } else {
            synchronized (attrs) {
                attrs.put(key, value);
            }
        }

        @SuppressWarnings("unchecked")
        B b = (B) this;
        return b;
    }

    /**
     * <pre>
     * bind方法验证。
     * </pre>
     * 
     * Validate all the parameters. Sub-classes may override this, but should
     * call the super method in that case.
     */
    @SuppressWarnings("unchecked")
    public B validate() {
        if (group == null) {
            throw new IllegalStateException("group not set");
        }
        if (channelFactory == null) {
            throw new IllegalStateException("channel or channelFactory not set");
        }
        return (B) this;
    }

    /**
     * Returns a deep clone of this bootstrap which has the identical configuration.  This method is useful when making
     * multiple {@link Channel}s with similar settings.  Please note that this method does not clone the
     * {@link EventLoopGroup} deeply but shallowly, making the group a shared resource.
     */
    @Override
    @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
    public abstract B clone();

    /**
     * Create a new {@link Channel} and register it with an {@link EventLoop}.
     */
    public ChannelFuture register() {
        validate();
        return initAndRegister();
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind() {
        validate();
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            throw new IllegalStateException("localAddress not set");
        }
        return doBind(localAddress);
    }

    /**
     * <pre>
     * server和client的端口公共绑定方法。
     * </pre>
     * 
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(int inetPort) {
        return bind(new InetSocketAddress(inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(String inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(InetAddress inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * <pre>
     * server和client的公共绑定方法。
     * </pre>
     * 
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(SocketAddress localAddress) {
    	// 绑定端口前的验证
        validate();
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        return doBind(localAddress);
    }

    /**
     * 绑定地址正式代码开始
     * 
     * @param localAddress
     * @return
     */
    private ChannelFuture doBind(final SocketAddress localAddress) {
    	// 初始化并注册
        final ChannelFuture regFuture = initAndRegister();
        
        // 通道返回
        final Channel channel = regFuture.channel();
        
        // 如果有异常的话直接返回
        if (regFuture.cause() != null) {
            return regFuture;
        }

        final ChannelPromise promise;
        // 注册的reg如果处理完毕， 一般来说都会走到这个if(true)的分支
        if (regFuture.isDone()) {
        	// 创建一个DefaultChannelPromise。
            promise = channel.newPromise();
            doBind0(regFuture, channel, localAddress, promise);
        } else {
            // Registration future is almost always fulfilled already, but just in case it's not.
            promise = new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE);
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    doBind0(regFuture, channel, localAddress, promise);
                }
            });
        }

        return promise;
    }

    final ChannelFuture initAndRegister() {
    	// 新创建一个通道。
        final Channel channel = channelFactory().newChannel();
        try {
        	// channel初始化，由子类实现。ServerBootstrap or Bootstrap。
        	// 做了以下事情:
        	// 1. option&attr属性设置  2. 加了一个默认的handler叫ServerBootstrapAcceptor(TODO)。
            init(channel);
        } catch (Throwable t) {
            channel.unsafe().closeForcibly();
            return channel.newFailedFuture(t);
        }
        
        // group()返回的是之前定义好的线程组 EventLoopGroup, bossGroup
        // AppletDiscardServer最后走的是SingleThreadEvent去完成注册 这里比较难理解
        ChannelFuture regFuture = group().register(channel);
        
        // 如果future有异常返回的话..
        // TODO 如果是异步的话有可能走到这里的时候异常还没来得及抛出来
        if (regFuture.cause() != null) {
            if (channel.isRegistered()) {
                channel.close();
            } else {
                channel.unsafe().closeForcibly();
            }
        }

        // If we are here and the promise is not failed, it's one of the following cases:
        // 1) If we attempted registration from the event loop, the registration has been completed at this point.
        //    i.e. It's safe to attempt bind() or connect() now beause the channel has been registered.
        // 2) If we attempted registration from the other thread, the registration request has been successfully
        //    added to the event loop's task queue for later execution.
        //    i.e. It's safe to attempt bind() or connect() now:
        //         because bind() or connect() will be executed *after* the scheduled registration task is executed
        //         because register(), bind(), and connect() are all bound to the same thread.

        return regFuture;
    }

    abstract void init(Channel channel) throws Exception;

    private static void doBind0(
            final ChannelFuture regFuture, final Channel channel,
            final SocketAddress localAddress, final ChannelPromise promise) {

    	// 交给线程去绑定
    	
        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (regFuture.isSuccess()) {
                    channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                } else {
                    promise.setFailure(regFuture.cause());
                }
            }
        });
    }

    /**
     * the {@link ChannelHandler} to use for serving the requests.
     */
    @SuppressWarnings("unchecked")
    public B handler(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        this.handler = handler;
        return (B) this;
    }

    final SocketAddress localAddress() {
        return localAddress;
    }

    final ChannelFactory<? extends C> channelFactory() {
        return channelFactory;
    }

    final ChannelHandler handler() {
        return handler;
    }

    /**
     * Return the configured {@link EventLoopGroup} or {@code null} if non is configured yet.
     */
    public final EventLoopGroup group() {
        return group;
    }

    final Map<ChannelOption<?>, Object> options() {
        return options;
    }

    final Map<AttributeKey<?>, Object> attrs() {
        return attrs;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(StringUtil.simpleClassName(this));
        buf.append('(');
        if (group != null) {
            buf.append("group: ");
            buf.append(StringUtil.simpleClassName(group));
            buf.append(", ");
        }
        if (channelFactory != null) {
            buf.append("channelFactory: ");
            buf.append(channelFactory);
            buf.append(", ");
        }
        if (localAddress != null) {
            buf.append("localAddress: ");
            buf.append(localAddress);
            buf.append(", ");
        }
        synchronized (options) {
            if (!options.isEmpty()) {
                buf.append("options: ");
                buf.append(options);
                buf.append(", ");
            }
        }
        synchronized (attrs) {
            if (!attrs.isEmpty()) {
                buf.append("attrs: ");
                buf.append(attrs);
                buf.append(", ");
            }
        }
        if (handler != null) {
            buf.append("handler: ");
            buf.append(handler);
            buf.append(", ");
        }
        if (buf.charAt(buf.length() - 1) == '(') {
            buf.append(')');
        } else {
            buf.setCharAt(buf.length() - 2, ')');
            buf.setLength(buf.length() - 1);
        }
        return buf.toString();
    }

    // BootstrapChannelFactory私有静态类。只有当前类才能用到。
    // 这里，分2种情况。
    // 如果是ServerBootstrap来创建channel工厂， 这里的T是ServerChannel。比如我在AppletDiscardServer中用的是NioServerSocketChannel。
    // 如果是Bootstrap来创建channel工厂，这里的T是Channel。比如我在DiscardClient中用的是NioSocketChannel。
    private static final class BootstrapChannelFactory<T extends Channel> implements ChannelFactory<T> {
        private final Class<? extends T> clazz;

        // 创建BootstrapChannelFactory对象。初始化clazz。
        BootstrapChannelFactory(Class<? extends T> clazz) {
            this.clazz = clazz;
        }

        // 创建clazz的实例。
        @Override
        public T newChannel() {
            try {
                return clazz.newInstance();
            } catch (Throwable t) {
                throw new ChannelException("Unable to create Channel from class " + clazz, t);
            }
        }

        @Override
        public String toString() {
            return StringUtil.simpleClassName(clazz) + ".class";
        }
    }
}
