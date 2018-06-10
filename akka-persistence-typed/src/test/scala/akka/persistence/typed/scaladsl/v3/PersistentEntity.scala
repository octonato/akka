/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.scaladsl.v3

import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.internal.SideEffect
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler
import akka.persistence.typed.scaladsl.{ Effect, PersistentBehavior, PersistentBehaviors }

trait PersistentEntity {

  type Command
  type Event
  type State

  type CommandHandlerF = (ActorContext[Command], Command) ⇒ Effect[Event, State]
  type EventHandlerPF = PartialFunction[Event, State]

  type CommandHandlers = PartialFunction[State, CommandHandlerF]
  type EventHandlers = PartialFunction[State, EventHandlerPF]

  val initialCommandHandlers: CommandHandlerF
  val initialEventHandlers: EventHandlerPF

  val activeCommandHandlers: CommandHandlers
  val activeEventHandlers: EventHandlers

  object CommandHandler {
    def apply(commandHandler: Command ⇒ Effect[Event, State]): CommandHandlerF =
      (_, cmd) ⇒ commandHandler(cmd)

    def withContext(commandHandler: CommandHandlerF): CommandHandlerF = commandHandler
  }

  object EventHandler {
    def apply(eventHandler: EventHandlerPF): EventHandlerPF = eventHandler
  }

  def behavior(persistenceId: String): PersistentBehavior[Command, Event, Option[State]] =
    PersistentBehaviors.receive[Command, Event, Option[State]](
      persistenceId = persistenceId,
      emptyState = None,
      commandHandler = PersistentBehaviors.CommandHandler.byState[Command, Event, Option[State]] {
        case None ⇒ (ctx, _, cmd) ⇒ {
          val effect = initialCommandHandlers(ctx, cmd)

          convertEffect(effect)
        }
        case Some(state) if activeCommandHandlers.isDefinedAt(state) ⇒ (ctx, _, cmd) ⇒ {
          val effect = activeCommandHandlers(state)(ctx, cmd)
          convertEffect(effect)
        }
      },
      eventHandler = {
        case (None, event) ⇒ Some(initialEventHandlers(event))
        case (Some(st), event) if activeEventHandlers.isDefinedAt(st) ⇒ Some(activeEventHandlers(st)(event))
      })

  private def convertEffect(effect: Effect[Event, State]): Effect[Event, Option[State]] = {
    // FIXME traverse all chained effects and build up new that's replacing the SideEffect
    effect match {
      case SideEffect(callback) ⇒ SideEffect[Event, Option[State]] {
        case Some(state) ⇒ callback(state)
        case None        ⇒ println(s"# ignoring side effect from initialCommandHandler when no state") // FIXME
      }
    }
  }

}
