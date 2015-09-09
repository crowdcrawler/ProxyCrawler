package org.crowdcrawler.proxycrawler.crawler.plugins

import org.crowdcrawler.proxycrawler.ProxyInfo
import org.jsoup.Jsoup
import java.net.URI
import java.nio.charset.Charset

import scala.collection.mutable


/**
 * For <a href="http://proxy.ipcn.org/">proxy.iopcn.org</a>.
 */
final class IpcnOrgPlugin extends AbstractPlugin {

  val seeds: List[URI] = List(
    new URI("http://proxy.ipcn.org/proxylist.html"),
    new URI("http://proxy.ipcn.org/proxylist2.html")
  )


  def extract(html: String): List[ProxyInfo] = {
    val result = mutable.ListBuffer.empty[ProxyInfo]
    val doc = Jsoup.parse(html)
    val preText = doc.select("tr > td > pre").text
    val rows = preText.split("\n")
    for (row <- rows) {
      if (row.matches("[0-9]+(?:\\.[0-9]+){3}:[0-9]+")) {
        val splitted = row.split(":")
        val host = splitted(0)
        val port = splitted(1).toInt

        result += ProxyInfo(host, port, "HTTP", 0, null, null)
      }
    }
    result.toList
  }

  def next(html: String): List[URI] = List()

  override val responseCharset: Charset = Charset.forName("GB2312")
}
