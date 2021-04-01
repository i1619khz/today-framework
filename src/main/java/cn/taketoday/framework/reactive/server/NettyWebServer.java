/**
 * Original Author -> 杨海健 (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2021 All Rights Reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see [http://www.gnu.org/licenses/]
 */
package cn.taketoday.framework.reactive.server;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.LongAdder;

import javax.annotation.PreDestroy;

import cn.taketoday.context.ApplicationContext;
import cn.taketoday.context.exception.ConfigurationException;
import cn.taketoday.context.utils.Assert;
import cn.taketoday.framework.StandardWebServerApplicationContext;
import cn.taketoday.framework.WebServerApplicationContext;
import cn.taketoday.framework.WebServerException;
import cn.taketoday.framework.reactive.NettyWebServerApplicationLoader;
import cn.taketoday.framework.reactive.ReactiveChannelHandler;
import cn.taketoday.framework.server.AbstractWebServer;
import cn.taketoday.framework.server.WebServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.ResourceLeakDetector;

/**
 * Netty {@link WebServer}
 *
 * @author TODAY 2019-07-02 21:15
 */
public class NettyWebServer extends AbstractWebServer implements WebServer {

  /**
   * the number of threads that will be used by
   * {@link io.netty.util.concurrent.MultithreadEventExecutorGroup}
   *
   * For parent {@link EventLoopGroup}
   *
   * @see io.netty.util.concurrent.MultithreadEventExecutorGroup
   */
  private int threadCount = 2;

  /**
   * the number of threads that will be used by
   * {@link io.netty.util.concurrent.MultithreadEventExecutorGroup}
   *
   * For child {@link EventLoopGroup}
   *
   * @see io.netty.util.concurrent.MultithreadEventExecutorGroup
   */
  private int acceptThreadCount = 2;

  /**
   * A channel where the I/O operation associated with this future takes place.
   */
  private Channel channel;
  private EventLoopGroup childGroup;
  private EventLoopGroup parentGroup;
  private Class<? extends ServerSocketChannel> socketChannel;

  /**
   * Framework Channel Initializer
   */
  private NettyServerInitializer nettyServerInitializer;

  @Override
  protected void initApplicationContext(ApplicationContext context) {
    super.initApplicationContext(context);
    if (context instanceof StandardWebServerApplicationContext) {
      ((StandardWebServerApplicationContext) context).setContextPath(getContextPath());
    }
  }

  /**
   * Subclasses can override this method to perform epoll is available logic
   */
  protected boolean epollIsAvailable() {
    try {
      Object obj = Class.forName("io.netty.channel.epoll.Epoll").getMethod("isAvailable").invoke(null);
      return obj != null
              && Boolean.parseBoolean(obj.toString())
              && System.getProperty("os.name").toLowerCase().contains("linux");
    }
    catch (Exception e) {
      return false;
    }
  }

  @Override
  protected void contextInitialized() {
    super.contextInitialized();

    try {
      new NettyWebServerApplicationLoader(this::getMergedInitializers)
              .onStartup(obtainApplicationContext());
    }
    catch (Throwable e) {
      throw new ConfigurationException(e);
    }
  }

  @Override
  public void start() {
    final ServerBootstrap bootstrap = new ServerBootstrap();
    preBootstrap(bootstrap);

    // enable epoll
    if (epollIsAvailable()) {
      bootstrap.option(EpollChannelOption.SO_REUSEPORT, true);
      if (socketChannel == null) {
        socketChannel = EpollServerSocketChannel.class;
      }
      if (parentGroup == null) {
        parentGroup = new EpollEventLoopGroup(threadCount, new NamedThreadFactory("epoll-parent@"));
      }
      if (childGroup == null) {
        childGroup = new EpollEventLoopGroup(acceptThreadCount, new NamedThreadFactory("epoll-child@"));
      }
    }
    else {
      if (parentGroup == null) {
        parentGroup = new NioEventLoopGroup(acceptThreadCount, new NamedThreadFactory("parent@"));
      }
      if (childGroup == null) {
        childGroup = new NioEventLoopGroup(threadCount, new NamedThreadFactory("child@"));
      }
      if (socketChannel == null) {
        socketChannel = NioServerSocketChannel.class;
      }
    }

    bootstrap.group(parentGroup, childGroup)
            .channel(socketChannel);

    NettyServerInitializer nettyServerInitializer = getNettyServerInitializer();
    Assert.state(nettyServerInitializer != null, "No NettyServerInitializer");

    bootstrap.childHandler(nettyServerInitializer);
    bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);

    postBootstrap(bootstrap);

    final ChannelFuture channelFuture = bootstrap.bind(getHost(), getPort());
    channel = channelFuture.channel();
    try {
      channelFuture.sync();
    }
    catch (InterruptedException e) {
      log.error("Interrupted", e);
      throw new WebServerException(e);
    }
    finally {
      childGroup.shutdownGracefully();
      parentGroup.shutdownGracefully();
    }
  }

  protected void preBootstrap(ServerBootstrap bootstrap) {
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
  }

  protected void postBootstrap(ServerBootstrap bootstrap) {
    bootstrap.handler(new LoggingHandler(LogLevel.INFO));
  }

  protected NettyServerInitializer getNettyServerInitializer() {
    NettyServerInitializer serverInitializer = this.nettyServerInitializer;

    if (serverInitializer == null) {
      final WebServerApplicationContext context = obtainApplicationContext();
      serverInitializer = context.getBean(NettyServerInitializer.class);
      if (serverInitializer == null) {
        final ReactiveChannelHandler reactiveDispatcher = context.getBean(ReactiveChannelHandler.class);
        serverInitializer = new NettyServerInitializer(reactiveDispatcher);
      }
    }
    return serverInitializer;
  }

  @PreDestroy
  @Override
  public void stop() {
    log.info("shutdown: [{}]", this);

    if (this.parentGroup != null) {
      this.parentGroup.shutdownGracefully();
    }
    if (this.childGroup != null) {
      this.childGroup.shutdownGracefully();
    }
  }

  public static class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final LongAdder threadNumber = new LongAdder();

    public NamedThreadFactory(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
      threadNumber.add(1);
      return new Thread(runnable, prefix.concat("thread-") + threadNumber.intValue());
    }
  }

  /**
   * Returns a channel where the I/O operation associated with this future takes place.
   *
   * @return a channel where the I/O operation associated with this future takes place.
   */
  public Channel getChannel() {
    return channel;
  }

  public EventLoopGroup getChildGroup() {
    return childGroup;
  }

  public EventLoopGroup getParentGroup() {
    return parentGroup;
  }

  public Class<? extends ServerSocketChannel> getSocketChannel() {
    return socketChannel;
  }

  /**
   * Set a channel where the I/O operation associated with this future takes place.
   *
   * @param channel
   *         A channel where the I/O operation associated with this future takes place.
   */
  public void setChannel(Channel channel) {
    this.channel = channel;
  }

  public void setParentGroup(EventLoopGroup parentGroup) {
    this.parentGroup = parentGroup;
  }

  public void setChildGroup(EventLoopGroup childGroup) {
    this.childGroup = childGroup;
  }

  public void setSocketChannel(Class<? extends ServerSocketChannel> socketChannel) {
    this.socketChannel = socketChannel;
  }

  public void setAcceptThreadCount(int acceptThreadCount) {
    this.acceptThreadCount = acceptThreadCount;
  }

  public void setThreadCount(int threadCount) {
    this.threadCount = threadCount;
  }

  public int getThreadCount() {
    return threadCount;
  }

  public int getAcceptThreadCount() {
    return acceptThreadCount;
  }

  public void setNettyServerInitializer(NettyServerInitializer nettyServerInitializer) {
    this.nettyServerInitializer = nettyServerInitializer;
  }
}
