/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.scaladsl.v3

import akka.Done
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.PersistentBehaviors
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler
import akka.persistence.typed.internal.SideEffect

object InDepthPersistentBehaviorSpec3 {

  abstract class OptionalInitialState[Command, Event, State] {

    protected def initialCommandHandler: (ActorContext[Command], Command) ⇒ Effect[Event, State]

    protected def activeCommandHandler: CommandHandler[Command, Event, State]

    protected def initialEventHandler: Event ⇒ State

    protected def activeEventHandler: (State, Event) ⇒ State

    final val initialState: Option[State] = None

    final def commandHandler: CommandHandler[Command, Event, Option[State]] = {
      // capture the functions once, in case they are defined as defs
      val initial = initialCommandHandler
      val active = activeCommandHandler
      CommandHandler.byState {
        case Some(_) ⇒ convertEffect(active)
        case None    ⇒ convertInitialEffect(initial)
      }
    }

    private def convertEffect(handler: CommandHandler[Command, Event, State]): CommandHandler[Command, Event, Option[State]] = {
      (ctx, optState, cmd) ⇒
        optState match {
          case Some(state) ⇒
            val effect = handler(ctx, state, cmd)
            convertEffect(effect)
          case None ⇒
            throw new IllegalStateException("Undefined state")
        }
    }

    private def convertInitialEffect(handler: (ActorContext[Command], Command) ⇒ Effect[Event, State]): CommandHandler[Command, Event, Option[State]] = {
      (ctx, _, cmd) ⇒
        val effect = handler(ctx, cmd)
        convertEffect(effect)
    }

    private def convertEffect(effect: Effect[Event, State]): Effect[Event, Option[State]] = {
      // FIXME traverse all chained effects and build up new that's replacing the SideEffect
      effect match {
        case SideEffect(callback) ⇒ SideEffect[Event, Option[State]] {
          case Some(state) ⇒ callback(state)
          case None        ⇒ println(s"# ignoring side effect from initialCommandHandler when no state") // FIXME
        }
      }
    }

    final def eventHandler(state: Option[State], event: Event): Option[State] =
      state match {
        case None    ⇒ Some(initialEventHandler(event))
        case Some(s) ⇒ Some(activeEventHandler(s, event))
      }

  }

  //#event
  sealed trait BlogEvent extends Serializable
  final case class PostAdded(
    postId:  String,
    content: PostContent) extends BlogEvent

  final case class BodyChanged(
    postId:  String,
    newBody: String) extends BlogEvent
  final case class Published(postId: String) extends BlogEvent
  //#event

  //#state
  object BlogState {
    val empty: Option[BlogState] = None
  }

  final case class BlogState(content: PostContent, published: Boolean) {
    def withContent(newContent: PostContent): BlogState =
      copy(content = newContent)
    def postId: String = content.postId
  }
  //#state

  //#commands
  sealed trait BlogCommand extends Serializable
  final case class AddPost(content: PostContent, replyTo: ActorRef[AddPostDone]) extends BlogCommand
  final case class AddPostDone(postId: String)
  final case class GetPost(replyTo: ActorRef[PostContent]) extends BlogCommand
  final case class ChangeBody(newBody: String, replyTo: ActorRef[Done]) extends BlogCommand
  final case class Publish(replyTo: ActorRef[Done]) extends BlogCommand
  final case object PassivatePost extends BlogCommand
  final case class PostContent(postId: String, title: String, body: String)
  //#commands

  class BlogPostEntity(ctx: ActorContext[BlogCommand]) extends OptionalInitialState[BlogCommand, BlogEvent, BlogState] {
    override protected def initialCommandHandler = { (ctx, cmd) ⇒
      cmd match {
        case AddPost(content, replyTo) ⇒
          val evt = PostAdded(content.postId, content)
          Effect.persist(evt).andThen { state2 ⇒
            // After persist is done additional side effects can be performed
            replyTo ! AddPostDone(content.postId)
          }
        case PassivatePost ⇒
          Effect.stop
        case _ ⇒
          Effect.unhandled
      }
    }

    override protected def activeCommandHandler = { (ctx, state, cmd) ⇒
      cmd match {
        case ChangeBody(newBody, replyTo) ⇒
          val evt = BodyChanged(state.postId, newBody)
          Effect.persist(evt).andThen { _ ⇒
            replyTo ! Done
          }
        case Publish(replyTo) ⇒
          Effect.persist(Published(state.postId)).andThen { _ ⇒
            println(s"Blog post ${state.postId} was published")
            replyTo ! Done
          }
        case GetPost(replyTo) ⇒
          replyTo ! state.content
          Effect.none
        case _: AddPost ⇒
          Effect.unhandled
        case PassivatePost ⇒
          Effect.stop
      }
    }

    override protected def initialEventHandler = {
      case PostAdded(postId, content) ⇒
        BlogState(content, published = false)
    }

    override protected def activeEventHandler = { (state, event) ⇒
      event match {
        case BodyChanged(_, newBody) ⇒
          state.withContent(state.content.copy(body = newBody))

        case Published(_) ⇒
          state.copy(published = true)
      }
    }
  }

  //#behavior
  def behavior(entityId: String): Behavior[BlogCommand] = {

    Behaviors.setup { ctx ⇒
      val entity = new BlogPostEntity(ctx)

      PersistentBehaviors.receive[BlogCommand, BlogEvent, Option[BlogState]](
        persistenceId = "Blog-" + entityId,
        emptyState = entity.initialState,
        commandHandler = entity.commandHandler,
        eventHandler = entity.eventHandler)
    }
  }
  //#behavior
}

