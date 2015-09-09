package org.crowdcrawler.proxycrawler.checker

import java.io.IOException
import java.net.SocketTimeoutException

import com.typesafe.scalalogging.Logger
import org.apache.http.annotation.ThreadSafe
import org.apache.http.conn.ConnectTimeoutException
import org.crowdcrawler.proxycrawler.ProxyInfo
import org.slf4j.LoggerFactory

import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool


@ThreadSafe
object ProxyChecker {
  private val LOGGER = Logger(LoggerFactory.getLogger(ProxyChecker.getClass.getName))


  /** Check the proxy and return new speed. */
  def check(proxies: List[ProxyInfo]) : List[ProxyInfo] = {
    val parList = proxies.par
    parList.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(parList.tasksupport.parallelismLevel * 16))
    parList.map(check).toList
  }


  /**
   * Check the speed of a proxy
   * @param proxyInfo proxy
   * @return A new proxy with speed, positive speed means bytes/s, negative speed(-n) means n failures
   */
  private def check(proxyInfo: ProxyInfo): ProxyInfo = {
    val start = System.currentTimeMillis
    try {
      LOGGER.info("Executing request via proxy " + proxyInfo)
      val (statusCode, bytes) = proxyInfo.schema match {
        case "HTTP" =>
          HttpProxyChecker.check(proxyInfo.host, proxyInfo.port)
        case "HTTPS" =>
          HttpsProxyChecker.check(proxyInfo.host, proxyInfo.port)
        case "SOCKS" | "SOCKS4" | "SOCKS5" =>
          SocksProxyChecker.check(proxyInfo.host, proxyInfo.port)
        case other => throw new IllegalArgumentException("Unsupported schema " + other)
      }
      val end = System.currentTimeMillis
      LOGGER.info("Time elapsed " + (end - start) + " milliseconds")

      if (statusCode != 200) {
        LOGGER.error("HTTP status code is " + statusCode)
        ProxyInfo(proxyInfo.host, proxyInfo.port, proxyInfo.schema, -1, proxyInfo.location, proxyInfo.from)
      } else {
        if (bytes > 0) {
          val speed = (bytes / ((end - start) / 1000.0)).toInt
          LOGGER.info("Speed is " + speed + " bytes/s")
          ProxyInfo(proxyInfo.host, proxyInfo.port, proxyInfo.schema, speed, proxyInfo.location, proxyInfo.from)
        } else {
          LOGGER.error("HTTP status code is 200 but the proxy failed to retrieve HTML source code")
          if (proxyInfo.speed >= 0) {
            ProxyInfo(proxyInfo.host, proxyInfo.port, proxyInfo.schema, -1, proxyInfo.location, proxyInfo.from)
          } else {
            ProxyInfo(proxyInfo.host, proxyInfo.port, proxyInfo.schema, proxyInfo.speed - 1,
              proxyInfo.location, proxyInfo.from)
          }
        }
      }
    } catch {
      case e: IOException =>
        val end = System.currentTimeMillis
        if (e.isInstanceOf[ConnectTimeoutException] || e.isInstanceOf[SocketTimeoutException]) {
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
