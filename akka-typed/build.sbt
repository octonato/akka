import akka.{ AkkaBuild, Formatting, Protobuf }

AkkaBuild.defaultSettings
AkkaBuild.mayChangeSettings
Formatting.formatSettings

// To be ablet to import ContainerFormats.proto
Protobuf.importPath := Some(baseDirectory.value / ".." / "akka-remote" / "src" / "main" / "protobuf" )

disablePlugins(MimaPlugin)

initialCommands := """
  import akka.typed._
  import akka.typed.scaladsl.Actor
  import scala.concurrent._
  import scala.concurrent.duration._
  import akka.util.Timeout
  implicit val timeout = Timeout(5.seconds)
"""
