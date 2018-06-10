/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.scaladsl.v2

import java.time.{ Duration, LocalDateTime }

import akka.actor.typed.Behavior
import akka.persistence.typed.scaladsl.{ Effect, PersistentBehaviors }

object TrackerEntity extends PersistentEntity {

  override type Command = TrackerCommand
  override type Event = TrackerEvent
  override type State = Tracker

  case class Tracker(current: Option[LocalDateTime] = None, total: List[Duration] = List.empty) {

    def start(startTime: LocalDateTime): Tracker =
      current
        .map(_ ⇒ this)
        .getOrElse {
          this.copy(current = Some(startTime))
        }

    def stop(endTime: LocalDateTime): Tracker =
      current
        .map { startTime ⇒
          val duration = Duration.between(startTime, endTime)
          this.copy(current = None, total = total :+ duration)
        }
        .getOrElse(this)

    val commandHandler = CommandHandler {
      case Start ⇒
        if (current.isEmpty) Effect.persist(Started(LocalDateTime.now()))
        else Effect.none

      case Stop ⇒
        if (current.nonEmpty) Effect.persist(Stopped(LocalDateTime.now()))
        else Effect.none
    }

    val eventHandler = EventHandler {
      case Started(time) ⇒ this.start(time)
      case Stopped(time) ⇒ this.stop(time)
    }
  }

  sealed trait TrackerCommand
  case object Start extends TrackerCommand
  case object Stop extends TrackerCommand

  sealed trait TrackerEvent
  case class Started(now: LocalDateTime) extends TrackerEvent
  case class Stopped(now: LocalDateTime) extends TrackerEvent

  override val emptyState = Tracker()

  override val commandHandlers: TrackerEntity.CommandHandlers = {
    case tracker ⇒ tracker.commandHandler
  }

  override val eventHandlers: TrackerEntity.EventHandlers = {
    case tracker ⇒ tracker.eventHandler
  }
}
