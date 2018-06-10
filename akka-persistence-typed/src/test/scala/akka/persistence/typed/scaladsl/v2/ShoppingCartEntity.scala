/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.scaladsl.v2

import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler

object ShoppingCartEntity extends PersistentEntity {

  type Command = CartCommand
  type Event = CartEvent
  type State = ShoppingCart

  sealed trait ShoppingCart

  case object EmptyCart extends ShoppingCart {
    val addItemHandler = CommandHandler {
      case AddItem(item: String) ⇒ Effect.persist(ItemAdded(item))
    }

    val eventHandler = EventHandler {
      case ItemAdded(item) ⇒ CartInUse(items = item :: Nil)
    }
  }

  case class CartInUse(items: List[String]) extends ShoppingCart {

    def removeOne[T](el: T): ShoppingCart = {
      val newItems =
        if (items.contains(el)) {
          val i = items.indexOf(el)
          items.patch(i, List.empty, 1)
        } else items

      if (newItems.isEmpty) EmptyCart
      else copy(items = newItems)
    }

    def removeAll(): ShoppingCart = EmptyCart

    val commandHandler = CommandHandler {
      case AddItem(item: String)    ⇒ Effect.persist(ItemAdded(item))
      case RemoveItem(item: String) ⇒ Effect.persist(items.find(_ == item).map(ItemRemoved))
      case Cancel                   ⇒ Effect.persist(Cancelled)
      case CheckOut                 ⇒ Effect.persist(CheckedOut(items))
    }

    val eventHandler = EventHandler {
      case ItemAdded(item)   ⇒ copy(items = item :: items)
      case ItemRemoved(item) ⇒ removeOne(item)
      case Cancelled         ⇒ removeAll()
      case CheckedOut(_)     ⇒ removeAll()
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

  override val emptyState: ShoppingCart = EmptyCart
  override val commandHandlers: ShoppingCartEntity.CommandHandlers = {
    case EmptyCart       ⇒ EmptyCart.addItemHandler
    case cart: CartInUse ⇒ cart.commandHandler
  }
  override val eventHandlers: ShoppingCartEntity.EventHandlers = {
    case EmptyCart       ⇒ EmptyCart.eventHandler
    case cart: CartInUse ⇒ cart.eventHandler
  }
}
