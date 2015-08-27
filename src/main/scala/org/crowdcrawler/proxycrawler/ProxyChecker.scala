package org.crowdcrawler.proxycrawler

import java.io.IOException
import java.net.{SocketTimeoutException, URI}
import java.nio.charset.StandardCharsets

import com.typesafe.scalalogging.Logger
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.apache.http.{HttpVersion, HttpHost}
import org.apache.http.client.methods.HttpGet
import org.slf4j.LoggerFactory


object ProxyChecker {
  private val HTTP_TARGET_URL = new URI("http://www.baidu.com")
  private val HTTPS_TARGET_URL = new URI("https://www.google.com")
  private val TIMEOUT = 10000  // 10 seconds
  val LOGGER = Logger(LoggerFactory.getLogger(ProxyChecker.getClass.getName))

  val REQUEST_CONFIG = RequestConfig.custom.setConnectTimeout(TIMEOUT)
    .setSocketTimeout(TIMEOUT).build()

  val CLIENT  = HttpClients.createMinimal()

  /** Check the proxy and return new speed. */
  def check(proxies: List[ProxyInfo]) : List[ProxyInfo] = proxies.par.map(check).toList


  private def check(proxyInfo: ProxyInfo): ProxyInfo = {
    if (proxyInfo.`type` == "HTTP") {
      check(proxyInfo, HTTP_TARGET_URL)
    } else {
      check(proxyInfo, HTTPS_TARGET_URL)
    }
  }

  @throws(classOf[IOException])
  private def executeRequest(uri: URI, proxy: HttpHost): (Int, String) = {
    val request = new HttpGet(uri)
    request.setProtocolVersion(HttpVersion.HTTP_1_1)
    ProxyCrawler.DEFAULT_HEADERS.foreach { case (key, value) =>
      request.setHeader(key, value)
    }

    val httpContext = {
      val requestConfig = RequestConfig.copy(REQUEST_CONFIG).setProxy(proxy).build()
      val tmp = new HttpClientContext()
      tmp.setRequestConfig(requestConfig)
      tmp
    }
    val response = CLIENT.execute(request, httpContext)
    val statusCode = response.getStatusLine.getStatusCode
    val html = EntityUtils.toString(response.getEntity, StandardCharsets.UTF_8)
    (statusCode, html)
  }


  private def check(proxyInfo: ProxyInfo, targetURL: URI): ProxyInfo = {
    val proxy = new HttpHost(proxyInfo.host, proxyInfo.port, proxyInfo.`type`.toLowerCase)
    val start = System.currentTimeMillis
    try {
      LOGGER.info("Executing request " + targetURL + " via proxy " + proxy)
      val (statusCode, html) = executeRequest(targetURL, proxy)
      val end = System.currentTimeMillis
      if (statusCode != 200) {
        LOGGER.info("Time elapsed " + (end - start) + " milliseconds, status code is " + statusCode)
        ProxyInfo(proxyInfo.host, proxyInfo.port, proxyInfo.`type`, -1, proxyInfo.location)
      } else {
        val speed = (html.getBytes.length / ((end - start) / 1000.0)).toInt
        LOGGER.info("Time elapsed " + (end - start) + " milliseconds, Speed is " + speed + " bytes/s")
        if (speed != proxyInfo.speed) {
          ProxyInfo(proxyInfo.host, proxyInfo.port, proxyInfo.`type`, speed, proxyInfo.location)
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
          ProxyInfo(proxyInfo.host, proxyInfo.port, proxyInfo.`type`, -1, proxyInfo.location)
        } else {
          ProxyInfo(proxyInfo.host, proxyInfo.port, proxyInfo.`type`, proxyInfo.speed - 1,
            proxyInfo.location)
        }
    }
  }
}
