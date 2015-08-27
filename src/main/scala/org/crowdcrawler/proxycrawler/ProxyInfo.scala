package org.crowdcrawler.proxycrawler

case class ProxyInfo (host: String, port: Int, `type`: String, speed: Int, location: String)
  extends Ordered[ProxyInfo] {
  import scala.math.Ordered.orderingToOrdered

  def compare(that: ProxyInfo): Int = (this.speed, this.host, this.port, this.`type`,
    this.location) compare (that.speed, that.host, that.port, that.`type`, that.location)
}
