/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.scaladsl.v2

import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.scaladsl.{ Effect, PersistentBehavior, PersistentBehaviors }

trait PersistentEntity {

  type Command
  type Event
  type State

  val emptyState: State
  val commandHandlers: CommandHandlers
  val eventHandlers: EventHandlers

  type CommandHandlerF = PersistentBehaviors.CommandHandler[Command, Event, State]
  type EventHandlerPF = PartialFunction[Event, State]

  type CommandHandlers = PartialFunction[State, CommandHandlerF]
  type EventHandlers = PartialFunction[State, EventHandlerPF]

  object CommandHandler {
    def apply(commandHandler: Command ⇒ Effect[Event, State]): CommandHandlerF =
      PersistentBehaviors.CommandHandler.command[Command, Event, State](commandHandler)

    def withContext(commandHandler: (Command, ActorContext[Command]) ⇒ Effect[Event, State]): CommandHandlerF =
      (ctx, _, cmd) ⇒ commandHandler(cmd, ctx)
  }

  def withContext(ctxToCommandHandler: ActorContext[Command] ⇒ CommandHandlerF): CommandHandlerF =
    (ctx, st, cmd) ⇒ ctxToCommandHandler(ctx)(ctx, st, cmd)

  object EventHandler {
    def apply(eventHandler: EventHandlerPF): EventHandlerPF = eventHandler
  }

  def behavior(persistenceId: String): PersistentBehavior[Command, Event, State] =
    PersistentBehaviors.receive[Command, Event, State](
      persistenceId = persistenceId,
      emptyState = emptyState,
      commandHandler = PersistentBehaviors.CommandHandler.byState {
        case s if commandHandlers.isDefinedAt(s) ⇒ commandHandlers(s)
      },
      eventHandler = {
        case (st, evt) if eventHandlers.isDefinedAt(st) ⇒ eventHandlers(st)(evt)
      }
    )

}
