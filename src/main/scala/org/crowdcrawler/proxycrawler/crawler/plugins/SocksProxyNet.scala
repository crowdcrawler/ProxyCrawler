package org.crowdcrawler.proxycrawler.crawler.plugins

import java.net.URI

import org.crowdcrawler.proxycrawler.ProxyInfo
import org.jsoup.Jsoup

import scala.collection.mutable
import scala.collection.JavaConversions._

/**
 * For <a href="http://www.socks-proxy.net/">socks-proxy.net</a>
 */
class SocksProxyNet extends AbstractPlugin {
  val seeds: List[URI] = List(new URI("http://www.socks-proxy.net/"))

  def extract(html: String): List[ProxyInfo] = {
    val result = mutable.ListBuffer.empty[ProxyInfo]
    val doc = Jsoup.parse(html)
    val rows = doc.select("table#proxylisttable > tbody > tr")
    for (row <- rows) {
      val tds = row.select("td")
      val host = tds.get(0).text
      val port = tds.get(1).text.toInt
      val location = tds.get(3).text
      val schema= tds.get(4).text.toUpperCase

      result += ProxyInfo(host, port, schema, 0, location, null)
    }
    result.toList
  }

  def next(html: String): List[URI] = List()
}
