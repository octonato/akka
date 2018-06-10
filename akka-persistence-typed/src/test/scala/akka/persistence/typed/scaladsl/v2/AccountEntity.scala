/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.scaladsl.v2
import akka.persistence.typed.scaladsl.v1.AccountEntity.{ AccountClosed, CloseAccount }
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.v2.AccountEntity.AccountCommand

/**
 * In this experiment, the user needs to define a model by extending PersistentEntity
 *
 * Command and event handlers are defined inside the model using utility classes like [[PersistentEntity.CommandHandler]]
 * and [[PersistentEntity.EventHandler]].
 * On their turn, they require similar functions, always from Cmd => Effect or from Evt => State, so the overall API feels similar and familiar.
 *
 * The state is never passed because the functions are meant to be defined in the model itself, so the State is explicitly available.
 *
 * The [[PersistentEntity.CommandHandler]] has a variant in which we can bring the ActorContext[State] into scope.
 * This is not always available, because the API focus on command handler first. Context being considered a power user API.
 *
 */
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

  // Account model as an ADT
  sealed trait Account

  case object EmptyAccount extends Account {
    val commandHandler = CommandHandler {
      case CreateAccount(owner, currency) ⇒ Effect.persist(AccountCreated(owner, currency))
    }

    val eventHandler = EventHandler {
      case AccountCreated(owner, currency) ⇒ OpenedAccount(0.0, owner, currency)
    }
  }

  case class OpenedAccount(balance: Double, owner: String, currency: String) extends Account {

    // we can use CommandHandler.withContext to bring the Context in scope
    val commandHandlers = CommandHandler.withContext {
      case (Deposit(amount), ctx) ⇒
        Effect
          .persist(Deposited(amount))
          .andThen { _ ⇒
            ctx.log.info("Got some fresh money!")
          }

      case (Withdraw(amount), _) ⇒
        if (balance - amount >= 0.0)
          Effect.persist(Withdrawn(amount))
        else
          Effect.unhandled

      case (CloseAccount, _) ⇒
        if (balance == 0.0) Effect.persist(AccountClosed)
        else Effect.unhandled
    }

    // alternatively we can wrap a CommandHandler with a withContext
    // that has the advantage that the shape of the CommandHandler stays the same
    // and we don't need to pattern match on a tuple which adds noise
    val commandHandlers2 =
      withContext { ctx ⇒
        CommandHandler {
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

  override val emptyState: Account = EmptyAccount

  override val commandHandlers: CommandHandlers = {
    case EmptyAccount       ⇒ EmptyAccount.commandHandler
    case acc: OpenedAccount ⇒ acc.commandHandlers
    case ClosedAccount      ⇒ ClosedAccount.commandHandler
  }

  override val eventHandlers: EventHandlers = {
    case EmptyAccount       ⇒ EmptyAccount.eventHandler
    case acc: OpenedAccount ⇒ acc.eventHandlers
  }

}
