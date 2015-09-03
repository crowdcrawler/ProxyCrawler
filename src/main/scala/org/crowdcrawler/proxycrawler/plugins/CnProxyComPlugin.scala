package org.crowdcrawler.proxycrawler.plugins

import org.crowdcrawler.proxycrawler.ProxyInfo
import org.jsoup.Jsoup
import java.net.URI
import java.nio.charset.Charset
import scala.collection.{immutable,mutable}
import util.control.Breaks._

/**
 * For <a href="http://www.cnproxy.com/">cnproxy.com</a>
 */
class CnProxyComPlugin extends AbstractPlugin {
  /** Port numbers are encrypted, this map is to decrypt it.  */
  private val charNum = immutable.Map(
    "v" -> "3",
    "m" -> "4",
    "a" -> "2",
    "l" -> "9",
    "q" -> "0",
    "b" -> "5",
    "i" -> "7",
    "w" -> "6",
    "r" -> "8",
    "c" -> "1"
  )

  val seeds: List[URI] = {
    List(
      new URI("http://www.cnproxy.com/proxy1.html"),
      new URI("http://www.cnproxy.com/proxy2.html"),
      new URI("http://www.cnproxy.com/proxy3.html"),
      new URI("http://www.cnproxy.com/proxy4.html"),
      new URI("http://www.cnproxy.com/proxy5.html"),
      new URI("http://www.cnproxy.com/proxy6.html"),
      new URI("http://www.cnproxy.com/proxy7.html"),
      new URI("http://www.cnproxy.com/proxy8.html"),
      new URI("http://www.cnproxy.com/proxy9.html"),
      new URI("http://www.cnproxy.com/proxy10.html"),
      new URI("http://www.cnproxy.com/proxyedu1.html"),
      new URI("http://www.cnproxy.com/proxyedu2.html")
    )
  }

  private def decryptPort(encrypted: String): Int =
    encrypted.split("\\+").map(str => charNum(str)).mkString.toInt


  def extract(html: String): List[ProxyInfo] = {
    val result = mutable.ListBuffer.empty[ProxyInfo]

    val doc = Jsoup.parse(html)
    val rows = doc.select("#proxylisttb > table").get(2).select("tr")

    for (i <- 1 until rows.size()) {
      breakable {
        // skip the first row
        val row = rows.get(i)
        val tds = row.select("td")
        val host = tds.get(0).text
        val port = {
          val pattern = "document.write(\":\"+"
          val original = tds.get(0).html()
          val pos1 = original.indexOf(pattern)
          if (pos1 == -1) break
          val pos2 = original.indexOf(")</script>", pos1)
          if (pos2 == -1) break
          val portStr = original.substring(pos1 + pattern.length, pos2)

          decryptPort(portStr)
        }
        val schema = tds.get(1).text
        val speeds = tds.get(2).text
        val speed = {
          val splitted = speeds.split(",")
          var sum = 0
          for (str <- splitted) {
            val tmp = str.toInt
            sum += tmp
          }
          sum / splitted.length
        }
        val country = tds.get(3).text
        val proxyInfo = ProxyInfo(host, port, schema, speed, country, null)
        result += proxyInfo
      }
    }
    result.toList
  }

  def next(html: String): List[URI] = List()

  override val responseCharset: Charset = Charset.forName("GB2312")
}
