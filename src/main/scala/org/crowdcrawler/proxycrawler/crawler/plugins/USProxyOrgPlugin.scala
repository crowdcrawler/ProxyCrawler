package org.crowdcrawler.proxycrawler.crawler.plugins

import org.crowdcrawler.proxycrawler.ProxyInfo
import org.jsoup.Jsoup
import java.net.URI

import scala.collection.mutable
import scala.collection.JavaConversions._



/**
 * For <a href="http://www.us-proxy.org/">us-proxy.org</a>.
 */
final class USProxyOrgPlugin extends AbstractPlugin {

  val seeds: List[URI] = List(new URI("http://www.us-proxy.org/"))

  def extract(html: String): List[ProxyInfo] = {
    val result = mutable.ListBuffer.empty[ProxyInfo]
    val doc = Jsoup.parse(html)
    val rows = doc.select("table#proxylisttable > tbody > tr")
    for (row <- rows) {
      val tds = row.select("td")
      val host = tds.get(0).text
      val port = tds.get(1).text.toInt
      val location = tds.get(3).text
      val schema= if (tds.get(6).text == "no") "HTTP" else "HTTPS"

      result += ProxyInfo(host, port, schema, 0, location, null)
    }
    result.toList
  }

  def next(html: String): List[URI] = List()
}
