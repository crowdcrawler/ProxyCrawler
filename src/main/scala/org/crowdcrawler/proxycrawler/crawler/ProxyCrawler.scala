package org.crowdcrawler.proxycrawler

import java.io.IOException
import java.net.URI
import java.security.cert.X509Certificate

import com.typesafe.scalalogging.Logger
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.{TrustStrategy, SSLContexts}
import org.apache.http.conn.ssl.{NoopHostnameVerifier, SSLConnectionSocketFactory}
import org.apache.http.util.EntityUtils
import org.crowdcrawler.proxycrawler.crawler.plugins.AbstractPlugin

import org.apache.http.HttpHeaders
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.collection.mutable


class ProxyCrawler(plugins: List[AbstractPlugin]) {
  /** Existed URIs. */
  private val existed = mutable.Set.empty[URI]
  private val LOGGER = Logger(LoggerFactory.getLogger(classOf[ProxyCrawler]))


  def crawl(): List[ProxyInfo] = {
    val result = mutable.ListBuffer.empty[ProxyInfo]
    for (plugin <- plugins) {
      LOGGER.info("Plugin " + plugin.getClass.getName + " started.")
      result ++= crawl(plugin)
      LOGGER.info("Plugin " + plugin.getClass.getName + " finished.")
    }
    result.toList
  }

  private def crawl(plugin: AbstractPlugin): List[ProxyInfo] = {
    val result = mutable.ListBuffer.empty[ProxyInfo]
    val uris = mutable.Queue.empty[URI]
    val seeds = plugin.seeds
    uris ++= seeds

    while (uris.nonEmpty) {
      val uri = uris.dequeue()
      if (!existed.contains(uri)) {
        existed.add(uri)
        try {
          LOGGER.info("Crawling " + uri.toString)
          val response = ProxyCrawler.CLIENT.execute(ProxyCrawler.createRequest(uri, plugin.customizedHeaders))
          val statusCode = response.getStatusLine.getStatusCode
          if (statusCode == 200) {
            val html = EntityUtils.toString(response.getEntity, plugin.responseCharset)

            result ++= plugin.extract(html).map(p => ProxyInfo(p.host, p.port, p.schema, p.speed, p.location, uri))

            val nextURIs = plugin.next(html)
            nextURIs.filter(p => !existed.contains(p)).foreach(p => uris.enqueue(p))
          } else {
            LOGGER.warn("statusCode = " + statusCode + ", " + uri + " skipped")
          }
        } catch {
          case e: IOException =>
            LOGGER.error("Error crawling " + uri.toString + ", skipped", e)
        }
      }
    }
    result.toList
  }

}

object ProxyCrawler {

  val DEFAULT_HEADERS = immutable.Map((HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac" +
    " OS X 10_10_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.130 Safari/537.36"),
    (HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;" +
      "q=0.9,image/webp,*/*;q=0.8"),
    (HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, sdch"),
    (HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.8,zh-CN;q=0.6,zh;q=0.4"),
    (HttpHeaders.CONNECTION, "keep-alive")
  )

  private val CLIENT = {
    // trust all certificates including self-signed certificates
    val sslContext = SSLContexts.custom().loadTrustMaterial(null, new TrustStrategy() {
      def isTrusted(chain: Array[X509Certificate], authType: String) = true
    }).build()
    val connectionFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE)
    HttpClients.custom().setSSLSocketFactory(connectionFactory).build()
  }

  def apply(classNames: String*): ProxyCrawler = {
    val plugins = mutable.ListBuffer.empty[AbstractPlugin]
    for (className <- classNames) {
      val clazz = Class.forName("org.crowdcrawler.proxycrawler.crawler.plugins." + className)
      plugins += clazz.newInstance().asInstanceOf[AbstractPlugin]
    }
    new ProxyCrawler(plugins.toList)
  }

  private def createRequest(uri: URI, headers: immutable.Map[String, String]): HttpGet = {
    val request = new HttpGet(uri)
    for (header <- headers) {
      request.setHeader(header._1, header._2)
    }
    request
  }
}
