package actors

import akka.actor.{Actor, ActorRef, Props, Terminated}

/**
  * Created by cuitao on 2017/1/8.
  */
class ToiletReservationsActor extends Actor {
  var actorUserMap: Map[ActorRef, String] = Map[ActorRef, String]()
  var userActorMap: Map[String, ActorRef] = Map[String, ActorRef]()
  var queue: Set[String] = Set[String]()
  val slackActorRef: ActorRef = context.actorOf(Props[SlackActor])

  override def receive: Receive = {
    case ToiletReservationsActor.Login(username) =>
      attachClientSender(sender(), username)
      sendQueueTo(sender(), queue)
    case ToiletReservationsActor.Reserve =>
      procQueue(sender())((username, queue) => queue + username)(sendQueueToAll)
    case ToiletReservationsActor.ReservationCancel =>
      procQueue(sender())((username, queue) => queue - username)(sendQueueToAll)
    case ToiletReservationsActor.Complete =>
      procQueue(sender())((username, queue) => queue - username)(queue => {
        sendQueueToAll(queue)
        if(queue.nonEmpty)
          slackActorRef ! queue.head
      })
    case Terminated(ref) => detachClientSender(ref)
  }

  def procQueue(sender: ActorRef)(makeQueue: (String, Set[String]) => Set[String])(proc: Set[String] => Unit): Unit = {
    actorUserMap.get(sender).foreach(username => {
      val tmp = makeQueue(username, queue)
      if(tmp != queue) {
        queue = tmp
        proc(tmp)
      }
    })
  }
  def bridge(ref: ActorRef)(proc: ActorRef => Unit): Unit = for (name <- actorUserMap.get(ref); userActorRef <- userActorMap.get(name)) proc(userActorRef)

  def attachClientSender(sender: ActorRef, username: String): ActorRef = {
    bridge(sender)(_.tell("leave", sender))
    actorUserMap += (sender -> username)
    userActorMap += (username -> userActorMap.getOrElse(username, context.actorOf(Props[UserActor])))
    userActorMap(username).tell("join", sender)
    context.watch(sender)
  }

  def detachClientSender(sender: ActorRef): ActorRef = {
    bridge(sender)(_.tell("leave", sender))
    actorUserMap -= sender
    context.unwatch(sender)
  }

  def sendQueueTo(actorRef: ActorRef, queue: Set[String]): Unit = actorRef ! ToiletReservationsActor.QueueUpdate(queue)

  def sendQueueToAll(queue: Set[String]): Unit = userActorMap.values.foreach(ref => sendQueueTo(ref, queue))
}

object ToiletReservationsActor {

  case class Login(username: String)

  object Reserve

  object ReservationCancel

  object Complete

  //  case class CurrentUserUpdate(username: String)
  case class QueueUpdate(queue: Set[String])

}