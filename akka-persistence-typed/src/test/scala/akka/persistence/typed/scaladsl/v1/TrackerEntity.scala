/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.scaladsl.v1

import java.time.{ Duration, LocalDateTime, Period }

import akka.actor.typed.Behavior
import akka.persistence.typed.scaladsl.{ Effect, PersistentBehaviors }
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler._
import akka.persistence.typed.scaladsl.v1.AccountEntity.AccountCommand

object TrackerEntity {

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

    val commandHandler = command[TrackerCommand, TrackerEvent, Tracker] {

      case Start ⇒
        if (current.isEmpty) Effect.persist(Started(LocalDateTime.now()))
        else Effect.none

      case Stop ⇒
        if (current.nonEmpty) Effect.persist(Stopped(LocalDateTime.now()))
        else Effect.none
    }

    def eventHandler(evt: TrackerEvent) =
      evt match {
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

  def behavior(projectNumber: String): Behavior[TrackerCommand] = {
    PersistentBehaviors.receive[TrackerCommand, TrackerEvent, Tracker](
      persistenceId = projectNumber,
      emptyState = Tracker(),
      commandHandler = byState {
        case tracker ⇒ tracker.commandHandler
      },
      eventHandler = (tracker, evt) ⇒ tracker.eventHandler(evt)
    )
  }
}
