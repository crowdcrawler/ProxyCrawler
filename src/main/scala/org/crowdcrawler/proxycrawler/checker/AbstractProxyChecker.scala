package org.crowdcrawler.proxycrawler.checker

import java.io.IOException

import org.apache.http.{HttpHeaders, HttpHost}
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.crowdcrawler.proxycrawler.ProxyCrawler


private[checker] trait AbstractProxyChecker {
  /**
   * Check whether a proxy is valid.
   * @param host hostname or IP address
   * @param port port number
   * @return (code, bytes), code: HTTP status code, bytes: bytes retrieved from target URL, -1 if error happened
   */
  @throws(classOf[IOException])
  def check(host: String, port: Int): (Int, Int)
}


private[checker] object AbstractProxyChecker {
  val TIMEOUT = 30000  // 30000 milliseconds
  val MAX_CONN = 100000
  val REQUEST_CONFIG = RequestConfig.custom.setConnectTimeout(TIMEOUT).setSocketTimeout(TIMEOUT)
    .setRedirectsEnabled(false).setRelativeRedirectsAllowed(false).setCircularRedirectsAllowed(false)
    .build()


  def configureRequest(request: HttpGet, proxy: Option[HttpHost] = None): Unit = {
    ProxyCrawler.DEFAULT_HEADERS.foreach { case (key, value) =>
      request.setHeader(key, value)
    }
    // disable keep-alive
    request.setHeader(HttpHeaders.CONNECTION, "close")
    val requestConfig = if (proxy.isDefined) {
      RequestConfig.copy(AbstractProxyChecker.REQUEST_CONFIG).setProxy(proxy.get).build()
    } else {
      REQUEST_CONFIG
    }
    request.setConfig(requestConfig)
  }
}
