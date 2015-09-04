package org.crowdcrawler.proxycrawler.plugins

import java.net.URI

import org.crowdcrawler.proxycrawler.ProxyInfo
import org.jsoup.Jsoup

import scala.collection.mutable
import scala.collection.JavaConversions._


/**
 * For <a href=https://proxy-list.org/>proxy-list.org</a>
 */
class ProxyListOrg extends AbstractPlugin {

  val seeds: List[URI] = List(new URI("https://proxy-list.org/english/index.php?p=1"))


  def extract(html: String): List[ProxyInfo] = {
    val result = mutable.ListBuffer.empty[ProxyInfo]
    val doc = Jsoup.parse(html)
    val rows = doc.select("div.table-wrap > div > ul")
    for (row <- rows) {
      val hostPort = row.select("li.proxy").text()
      val host = hostPort.split(":")(0)
      val port = hostPort.split(":")(1).toInt
      val schema = {
        val tmp = row.select("li.https").text()
        if (tmp == "-") "HTTP" else tmp.toUpperCase
      }
      val speed = {
        val tmp = row.select("li.speed").text()
        if (tmp.contains("kbit")) {
          (tmp.dropRight(4).toDouble * 1024).toInt
        } else {
          0
        }
      }
      val location = row.select("li.country-city > div > span.country").first().attr("title")
      result += ProxyInfo(host, port, schema, speed, location, null)
    }
    result.toList
  }


  def next(html: String): List[URI] = {
    val result = mutable.ListBuffer.empty[URI]
    val rootURL = "https://proxy-list.org/english"

    val doc = Jsoup.parse(html)
    val rows = doc.select("div.table-menu > a.item[href]")
    for (row <- rows) {
      val href = row.attr("href")
      result += new URI(rootURL + href.substring(1))
    }
    result.toList
  }
}
