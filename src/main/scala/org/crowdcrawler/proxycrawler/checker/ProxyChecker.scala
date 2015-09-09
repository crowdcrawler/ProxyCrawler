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


import akka.actor._
import scala.collection.mutable.ArrayBuffer
import WorkPullingPattern._


private object WorkPullingPattern {
  sealed trait Message
  case object GiveMeWork extends Message
  case class RegisterWorker(worker: ActorRef) extends Message
}


private class Master(var proxies: List[ProxyInfo], validProxies: ArrayBuffer[ProxyInfo])
  extends Actor with ActorLogging {

  var registerWorker = 0
  var stoppedWorker  = 0

  // Watch and check for termination
  final def receive = {
    case RegisterWorker(worker) =>
      log.info(s"worker $worker registered")
      context.watch(worker)
      registerWorker += 1
    case Terminated(worker) =>
      log.info(s"worker $worker terminated")
      stoppedWorker += 1
      if (registerWorker == stoppedWorker) {
        log.info("All workers finished, now shutdown")
        context.system.terminate()
      }
    case GiveMeWork =>
      if (proxies.isEmpty) {
        sender ! PoisonPill
      } else {
        sender ! proxies.head
        proxies = proxies.tail
      }
    case proxyInfo: ProxyInfo =>
      if(proxyInfo.speed > 0) log.info(s"A new valid proxy $proxyInfo added")
      validProxies += proxyInfo
  }

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy
}

// supervise workers
private class Supervisor(master: ActorRef, workers: Int) extends Actor with ActorLogging {
  def receive = {
    case "Start" =>
      0 until workers foreach(i =>context.actorOf(Props(classOf[Worker], master), "worker" + i))
  }

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy
}


private class Worker(master: ActorRef) extends Actor with ActorLogging {
  override def preStart(): Unit = {
    super.preStart()
    master ! RegisterWorker(self)
    master ! GiveMeWork
  }

  def receive = {
    case proxyInfo: ProxyInfo =>
      val validProxy = ProxyChecker.check(proxyInfo)
      master ! validProxy
      master ! GiveMeWork
  }
}

@ThreadSafe
object ProxyChecker {
  private val LOGGER = Logger(LoggerFactory.getLogger(ProxyChecker.getClass.getName))


  /** Check the proxy and return new speed. */
  @Deprecated
  def check1(proxies: List[ProxyInfo]) : List[ProxyInfo] = {
    val parList = proxies.par
    parList.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(parList.tasksupport.parallelismLevel * 16))
    parList.map(check).toList
  }


  /**
   * Launch parallel HTTP requests using actors.
   *
   * The performance is the same as above, because the ProxcyChecker.execute()
   * in the Worker actors is asynchronous.
   */
  def check(proxies: List[ProxyInfo]) : List[ProxyInfo] =  {
    val validProxies = ArrayBuffer.empty[ProxyInfo]
    val system = ActorSystem("system")
    val master = system.actorOf(Props(classOf[Master], proxies, validProxies), "master")
    val supervisor = system.actorOf(Props(classOf[Supervisor], master, proxies.size), "supervisor")
    supervisor ! "Start"

    system.awaitTermination()
    validProxies.toList
  }

  /**
   * Check the speed of a proxy
   * @param proxyInfo proxy
   * @return A new proxy with speed, positive speed means bytes/s, negative speed(-n) means n failures
   */
  private[proxycrawler] def check(proxyInfo: ProxyInfo): ProxyInfo = {
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
