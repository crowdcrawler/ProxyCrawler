package org.crowdcrawler.proxycrawler

import java.io.IOException
import java.net
import java.net.{Socket, InetSocketAddress, SocketTimeoutException, URI}
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLContext

import com.typesafe.scalalogging.Logger
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.conn.socket.{PlainConnectionSocketFactory, ConnectionSocketFactory}
import org.apache.http.conn.ssl.{TrustSelfSignedStrategy, SSLConnectionSocketFactory}
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.protocol.HttpContext
import org.apache.http.ssl.SSLContexts
import org.apache.http.util.EntityUtils
import org.apache.http.{HttpVersion, HttpHost}
import org.apache.http.client.methods.HttpGet
import org.slf4j.LoggerFactory


object ProxyChecker {
  private class MyHttpConnectionSocketFactory extends PlainConnectionSocketFactory {
    override def createSocket(context: HttpContext): Socket = {
      val socksaddr = context.getAttribute("socks.address").asInstanceOf[InetSocketAddress]
      val proxy = new net.Proxy(net.Proxy.Type.SOCKS, socksaddr)
      new Socket(proxy)
    }
  }

  private class MyHttpsConnectionSocketFactory(sslContext: SSLContext) extends SSLConnectionSocketFactory(sslContext) {
    override def createSocket(context: HttpContext): Socket = {
      val socksaddr = context.getAttribute("socks.address").asInstanceOf[InetSocketAddress]
      val proxy = new net.Proxy(net.Proxy.Type.SOCKS, socksaddr)
      new Socket(proxy)
    }
  }

  private val HTTP_TARGET_URL = new URI("http://www.baidu.com")
  private val HTTPS_TARGET_URL = new URI("https://www.google.com")
  private val TIMEOUT = 10000  // 10 seconds
  val LOGGER = Logger(LoggerFactory.getLogger(ProxyChecker.getClass.getName))

  val REQUEST_CONFIG = RequestConfig.custom.setConnectTimeout(TIMEOUT)
    .setSocketTimeout(TIMEOUT).build()

  /** HTTP client for HTTP and HTTPS proxies. */
  val CLIENT_HTTP  = HttpClients.createMinimal()
  /** HTTP client for SOCKS proxies. */
  val CLIENT_SOCKS = {
    val sSLContext = {
      val builder = SSLContexts.custom()
      builder.loadTrustMaterial(null, new TrustSelfSignedStrategy())
      builder.build()
    }

    val reg = RegistryBuilder.create[ConnectionSocketFactory]()
      .register("http", new MyHttpConnectionSocketFactory())
      .register("https", new MyHttpsConnectionSocketFactory(sSLContext))
      .build()
    val cm = new PoolingHttpClientConnectionManager(reg)
    HttpClients.custom().setConnectionManager(cm).build()
  }

  /** Check the proxy and return new speed. */
  def check(proxies: List[ProxyInfo]) : List[ProxyInfo] = proxies.par.map(check).toList


  private def check(proxyInfo: ProxyInfo): ProxyInfo = {
    proxyInfo.schema match {
      case "HTTP" => check(proxyInfo, HTTP_TARGET_URL)
      case "HTTPS" => check(proxyInfo, HTTPS_TARGET_URL)
      case "SOCKS" | "SOCKS4" | "SOCKS5" => check(proxyInfo, HTTP_TARGET_URL)
      case whoa => throw new IllegalArgumentException("Unsupported schema " + whoa)
    }
  }


  @throws(classOf[IOException])
  private def executeRequest(uri: URI, proxyInfo: ProxyInfo): (Int, String) = {
    val request = new HttpGet(uri)
    request.setProtocolVersion(HttpVersion.HTTP_1_1)
    ProxyCrawler.DEFAULT_HEADERS.foreach { case (key, value) =>
      request.setHeader(key, value)
    }

    val httpContext = proxyInfo.schema match {
      case "HTTP" | "HTTPS" =>
        val proxy = new HttpHost(proxyInfo.host, proxyInfo.port, proxyInfo.schema.toLowerCase)
        val requestConfig = RequestConfig.copy(REQUEST_CONFIG).setProxy(proxy).build()
        val tmp = new HttpClientContext()
        tmp.setRequestConfig(requestConfig)
        tmp
      case "SOCKS" | "SOCKS4" | "SOCKS5" =>
        val socksaddr = new InetSocketAddress(proxyInfo.host, proxyInfo.port)
        val context = HttpClientContext.create()
        context.setAttribute("socks.address", socksaddr)
        context
      case whoa => throw new IllegalArgumentException("Unsupported schema " + whoa)
    }

    val httpClient = proxyInfo.schema match {
      case "HTTP" | "HTTPS" => CLIENT_HTTP
      case "SOCKS" | "SOCKS4" | "SOCKS5" => CLIENT_SOCKS
      case whoa => throw new IllegalArgumentException("Unsupported schema " + whoa)
    }

    val response = httpClient.execute(request, httpContext)
    val statusCode = response.getStatusLine.getStatusCode
    val html = EntityUtils.toString(response.getEntity, StandardCharsets.UTF_8)
    (statusCode, html)
  }

  private def check(proxyInfo: ProxyInfo, targetURL: URI): ProxyInfo = {
    val start = System.currentTimeMillis
    try {
      LOGGER.info("Executing request " + targetURL + " via proxy " + proxyInfo)
      val (statusCode, html) = executeRequest(targetURL, proxyInfo)
      val end = System.currentTimeMillis
      if (statusCode != 200) {
        LOGGER.info("Time elapsed " + (end - start) + " milliseconds, status code is " + statusCode)
        ProxyInfo(proxyInfo.host, proxyInfo.port, proxyInfo.schema, -1, proxyInfo.location, proxyInfo.from)
      } else {
        val speed = (html.getBytes.length / ((end - start) / 1000.0)).toInt
        LOGGER.info("Time elapsed " + (end - start) + " milliseconds, Speed is " + speed + " bytes/s")
        if (speed != proxyInfo.speed) {
          ProxyInfo(proxyInfo.host, proxyInfo.port, proxyInfo.schema, speed, proxyInfo.location, proxyInfo.from)
        } else {
          proxyInfo
        }
      }
    } catch {
      case e: IOException =>
        val end = System.currentTimeMillis
        if (e.isInstanceOf[ConnectTimeoutException] ||  e.isInstanceOf[SocketTimeoutException]) {
          LOGGER.info(e.getClass.getName + " : " + e.getMessage)
          LOGGER.info("Time elapsed " + (end - start) + " milliseconds")
        } else {
          LOGGER.error(e.getClass.getName + " : " + e.getMessage)
          LOGGER.error("Time elapsed " + (end - start) + " milliseconds")
        }

        if (proxyInfo.speed >= 0) {
          ProxyInfo(proxyInfo.host, proxyInfo.port, proxyInfo.schema, -1, proxyInfo.location, proxyInfo.from)
        } else {
          ProxyInfo(proxyInfo.host, proxyInfo.port, proxyInfo.schema, proxyInfo.speed - 1,
            proxyInfo.location, proxyInfo.from)
        }
    }
  }
}
