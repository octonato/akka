/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.scaladsl.v1

import akka.actor.typed.Behavior
import akka.persistence.typed.scaladsl.{ Effect, PersistentBehaviors }
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler

object ShoppingCartEntity {

  sealed trait ShoppingCart

  case object EmptyCart extends ShoppingCart {
    val addItemHandler = CommandHandler.command[CartCommand, CartEvent, ShoppingCart] {
      case AddItem(item: String) ⇒ Effect.persist(ItemAdded(item))
    }

    def eventHandler(evt: CartEvent): ShoppingCart = {
      evt match {
        case ItemAdded(item) ⇒ CartInUse(items = item :: Nil)
      }
    }
  }

  case class CartInUse(items: List[String]) extends ShoppingCart {

    def removeOne[T](el: T): ShoppingCart = {
      val newItems =
        if (items.contains(el)) {
          val i = items.indexOf(el)
          items.patch(i, List.empty, 1)
        } else {
          items
        }

      if (newItems.isEmpty) EmptyCart
      else copy(items = newItems)
    }

    def removeAll(): ShoppingCart = EmptyCart

    val commandHandler = CommandHandler.command[CartCommand, CartEvent, ShoppingCart] {
      case AddItem(item: String)    ⇒ Effect.persist(ItemAdded(item))
      case RemoveItem(item: String) ⇒ Effect.persist(items.find(_ == item).map(ItemRemoved))
      case Cancel                   ⇒ Effect.persist(Cancelled)
      case CheckOut                 ⇒ Effect.persist(CheckedOut(items))
    }

    def eventHandler(evt: CartEvent): ShoppingCart = {
      evt match {
        case ItemAdded(item)   ⇒ copy(items = item :: items)
        case ItemRemoved(item) ⇒ removeOne(item)
        case Cancelled         ⇒ removeAll()
        case CheckedOut(_)     ⇒ removeAll()
      }
    }
  }

  sealed trait CartCommand
  case class AddItem(item: String) extends CartCommand
  case class RemoveItem(item: String) extends CartCommand
  case object Cancel extends CartCommand
  case object CheckOut extends CartCommand

  sealed trait CartEvent
  case class ItemAdded(item: String) extends CartEvent
  case class ItemRemoved(item: String) extends CartEvent
  case object Cancelled extends CartEvent
  case class CheckedOut(items: List[String]) extends CartEvent

  // common command handler
  val addItemHandler = CommandHandler.command[CartCommand, CartEvent, ShoppingCart] {
    case AddItem(item: String) ⇒ Effect.persist(ItemAdded(item))
  }

  def behavior(userId: String): Behavior[CartCommand] =
    PersistentBehaviors.receive[CartCommand, CartEvent, ShoppingCart](
      persistenceId = userId,
      emptyState = EmptyCart,

      commandHandler = CommandHandler.byState {
        case EmptyCart       ⇒ EmptyCart.addItemHandler
        case cart: CartInUse ⇒ cart.commandHandler
      },
      eventHandler = {
        case (EmptyCart, evt)       ⇒ EmptyCart.eventHandler(evt)
        case (cart: CartInUse, evt) ⇒ cart.eventHandler(evt)
      }
    )
}
