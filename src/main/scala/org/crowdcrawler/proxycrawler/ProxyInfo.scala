package org.crowdcrawler.proxycrawler

import java.net.URI

/**
 * Proxy information.
 *
 * @param host IP or DNS name
 * @param port port number
 * @param schema HTTP, HTTPS, SOCKS4, SOCKS5 or SOCKS
 * @param speed speed, optional
 * @param location location, optional
 * @param from  which web page is this proxy crawled from, optional
 */
case class ProxyInfo (host: String, port: Int, schema: String, speed: Int, location: String, from: URI)
  extends Ordered[ProxyInfo] {
  import scala.math.Ordered.orderingToOrdered

  def compare(that: ProxyInfo): Int = (this.speed, this.host, this.port, this.schema,
    this.location, this.from) compare (that.speed, that.host, that.port, that.schema, that.location, that.from)
}
