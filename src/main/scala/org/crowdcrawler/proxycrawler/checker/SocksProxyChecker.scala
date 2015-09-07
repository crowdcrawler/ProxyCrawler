package org.crowdcrawler.proxycrawler.checker

import java.net
import java.net.{InetSocketAddress, Socket, URI}
import java.nio.charset.StandardCharsets
import javax.net.ssl.{HostnameVerifier, SSLContext}

import org.apache.http.annotation.ThreadSafe
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.socket.{ConnectionSocketFactory, PlainConnectionSocketFactory}
import org.apache.http.conn.ssl.{NoopHostnameVerifier, SSLConnectionSocketFactory}
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.protocol.HttpContext
import org.apache.http.util.EntityUtils


@ThreadSafe
object SocksProxyChecker extends AbstractProxyChecker {
  private class MyHttpConnectionSocketFactory extends PlainConnectionSocketFactory {
    override def createSocket(context: HttpContext): Socket = {
      val socksAddress = context.getAttribute("socks.address").asInstanceOf[InetSocketAddress]
      val proxy = new net.Proxy(net.Proxy.Type.SOCKS, socksAddress)
      new Socket(proxy)
    }
  }

  private class MyHttpsConnectionSocketFactory(sslContext: SSLContext, verifier: HostnameVerifier)
    extends SSLConnectionSocketFactory(sslContext) {
    override def createSocket(context: HttpContext): Socket = {
      val socksAddress = context.getAttribute("socks.address").asInstanceOf[InetSocketAddress]
      val proxy = new net.Proxy(net.Proxy.Type.SOCKS, socksAddress)
      new Socket(proxy)
    }
  }

  private val CLIENT = {
    val reg = RegistryBuilder.create[ConnectionSocketFactory]()
      .register("http", new MyHttpConnectionSocketFactory())
      .register("https",
        new MyHttpsConnectionSocketFactory(HttpsProxyChecker.SSL_CONTEXT, NoopHostnameVerifier.INSTANCE))
      .build()
    val cm = new PoolingHttpClientConnectionManager(reg)
    cm.setMaxTotal(AbstractProxyChecker.MAX_CONN)
    HttpClients.custom().setConnectionManager(cm).disableRedirectHandling().build()
  }
  private val TARGET_URL = new URI("http://www.baidu.com")


  def check(host: String, port: Int): (Int, Int) = {
    val request = new HttpGet(TARGET_URL)
    AbstractProxyChecker.configureRequest(request)

    val httpContext = {
      val socksAddress = new InetSocketAddress(host, port)
      val context = HttpClientContext.create()
      context.setAttribute("socks.address", socksAddress)
      context.setRequestConfig(AbstractProxyChecker.REQUEST_CONFIG)
      context
    }

    val response = CLIENT.execute(request, httpContext)

    val statusCode = response.getStatusLine.getStatusCode
    val html = EntityUtils.toString(response.getEntity, StandardCharsets.UTF_8)
    if (statusCode == 200 && html.contains("<title>百度一下")) (statusCode, html.getBytes.length) else (statusCode, -1)
  }
}
