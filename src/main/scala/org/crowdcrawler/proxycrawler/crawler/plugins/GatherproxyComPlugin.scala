package org.crowdcrawler.proxycrawler.crawler.plugins

import java.net.URI

import org.crowdcrawler.proxycrawler.ProxyInfo
import org.jsoup.Jsoup

import scala.collection.JavaConversions._
import scala.collection.mutable



/**
 * For <a href="http://gatherproxy.com/">gatherproxy.com</a>.
 */
class GatherproxyComPlugin extends AbstractPlugin {

  val seeds: List[URI] = List(new URI("http://gatherproxy.com/"))

  def extract(html: String): List[ProxyInfo] = {
    val result = mutable.ListBuffer.empty[ProxyInfo]
    val doc = Jsoup.parse(html)
    val rows = doc.select("table#proxylisttable > tbody > tr")
    for (row <- rows) {
      val tds = row.select("td")
      val host = tds.get(0).text
      val port = Integer.valueOf(tds.get(1).text)
      val location = tds.get(3).text
      val schema = if (tds.get(6).text == "no") "HTTP" else "HTTPS"
      result.add(ProxyInfo(host, port, schema, 0, location, null))
    }
    result.toList
  }

  def next(html: String): List[URI] = List()
}
