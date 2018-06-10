/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.scaladsl.v1

import akka.actor.typed.Behavior
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler._
import akka.persistence.typed.scaladsl.{ Effect, PersistentBehaviors }

object AccountEntity {

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

  sealed trait Account

  case object EmptyAccount extends Account {
    val commandHandler =
      command[AccountCommand, AccountEvent, Account] {
        case CreateAccount(owner, currency) ⇒ Effect.persist(AccountCreated(owner, currency))
      }

    def applyEvent(evt: CreateAccount) = OpenedAccount(0.0, evt.owner, evt.currency)
  }

  case class OpenedAccount(balance: Double, owner: String, currency: String) extends Account {

    val commandHandlers: CommandHandler[AccountCommand, AccountEvent, Account] =
      (ctx, _, cmd) ⇒ cmd match {
        case Deposit(amount) ⇒
          Effect
            .persist(Deposited(amount))
            .andThen { _ ⇒
              ctx.log.info("Got some fresh money!")
            }

        case Withdraw(amount) ⇒
          if (balance - amount >= 0.0)
            Effect.persist(Withdrawn(amount))
          else
            Effect.unhandled

        case CloseAccount ⇒
          if (balance == 0.0) Effect.persist(AccountClosed)
          else Effect.unhandled
      }

    def applyEvent(evt: AccountEvent) =
      evt match {
        case Deposited(amount) ⇒ copy(balance = balance + amount)
        case Withdrawn(amount) ⇒ copy(balance = balance - amount)
        case AccountClosed     ⇒ ClosedAccount
      }
  }

  case object ClosedAccount extends Account {
    val commandHandler =
      command[AccountCommand, AccountEvent, Account] {
        _ ⇒ Effect.unhandled
      }
  }

  def behavior(accountNumber: String): Behavior[AccountCommand] =
    PersistentBehaviors.receive[AccountCommand, AccountEvent, Account](
      persistenceId = accountNumber,
      emptyState = EmptyAccount,
      commandHandler = byState {
        case EmptyAccount       ⇒ EmptyAccount.commandHandler
        case acc: OpenedAccount ⇒ acc.commandHandlers
        case ClosedAccount      ⇒ ClosedAccount.commandHandler
      },
      eventHandler = {
        case (EmptyAccount, evt: CreateAccount)      ⇒ EmptyAccount.applyEvent(evt)
        case (acc: OpenedAccount, evt: AccountEvent) ⇒ acc.applyEvent(evt)
      }
    )
}
