/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.scaladsl.v3

import akka.persistence.typed.scaladsl.Effect

object AccountEntity extends PersistentEntity {

  type Command = AccountCommand
  type Event = AccountEvent
  type State = Account

  sealed trait AccountCommand
  case class CreateAccount(owner: String, currency: String) extends AccountCommand
  case class Deposit(amount: Double) extends AccountCommand
  case class Withdraw(amount: Double) extends AccountCommand
  case object CloseAccount extends AccountCommand

  sealed trait AccountEvent
  case class AccountCreated(owner: String, currency: String) extends AccountEvent
  case class Deposited(amount: Double) extends AccountEvent
  case class Withdrawn(amount: Double) extends AccountEvent
  case object AccountClosed extends AccountEvent

  // // Account model as an ADT
  sealed trait Account

  case class OpenedAccount(balance: Double, owner: String, currency: String) extends Account {

    // we can use CommandHandler.withContext to bring the Context in scope
    val commandHandlers = CommandHandler.withContext {
      case (ctx, Deposit(amount)) ⇒
        Effect
          .persist(Deposited(amount))
          .andThen { _ ⇒
            ctx.log.info("Got some fresh money!")
          }

      case (_, Withdraw(amount)) ⇒
        if (balance - amount >= 0.0)
          Effect.persist(Withdrawn(amount))
        else
          Effect.unhandled

      case (_, CloseAccount) ⇒
        if (balance == 0.0) Effect.persist(AccountClosed)
        else Effect.unhandled
    }

    val eventHandlers = EventHandler {
      case Deposited(amount) ⇒ copy(balance = balance + amount)
      case Withdrawn(amount) ⇒ copy(balance = balance - amount)
      case AccountClosed     ⇒ ClosedAccount
    }
  }

  case object ClosedAccount extends Account {
    val commandHandler = CommandHandler {
      _ ⇒ Effect.unhandled
    }
  }

  val initialCommandHandlers = CommandHandler {
    case CreateAccount(owner, currency) ⇒ Effect.persist(AccountCreated(owner, currency))
  }

  val initialEventHandlers = EventHandler {
    case AccountCreated(owner, currency) ⇒ OpenedAccount(0.0, owner, currency)
  }

  override val activeCommandHandlers: AccountEntity.CommandHandlers = {
    case acc: OpenedAccount ⇒ acc.commandHandlers
    case ClosedAccount      ⇒ ClosedAccount.commandHandler
  }

  override val activeEventHandlers: AccountEntity.EventHandlers = {
    case acc: OpenedAccount ⇒ acc.eventHandlers
  }
}
