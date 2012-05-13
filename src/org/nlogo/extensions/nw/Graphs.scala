package org.nlogo.extensions.nw

import org.nlogo.agent.Agent
import org.nlogo.agent.AgentSet
import org.nlogo.agent.Link
import org.nlogo.agent.Turtle
import org.nlogo.api.ExtensionException
import org.nlogo.api.ExtensionObject
import org.nlogo.extensions.nw.GraphUtil.EnrichAgentSet
import scala.Option.option2Iterable
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

object GraphUtil {
  implicit def EnrichAgentSet(agentSet: AgentSet) = new RichAgentSet(agentSet)
  class RichAgentSet(agentSet: AgentSet) {
    def toIterable[T <: Agent] = agentSet.agents.asScala.map(_.asInstanceOf[T])
  }
}

trait NetLogoGraph {
  val isStatic: Boolean
  val isValidLink: Link => Boolean
  val isValidTurtle: Turtle => Boolean
  def validTurtle(t: Turtle) = Option(t).filter(isValidTurtle)
  def validLink(l: Link) = Option(l).filter(isValidLink)
  // TODO: if I figure out how, get rid of validTurtle / validLink in favor of:
  // def valid[A](obj: A)(implicit isValid: A => Boolean) = Option(obj).filter(isValid)

  lazy val asJungGraph = new UntypedJungGraph(this)
  lazy val asDirectedJungGraph = new DirectedJungGraph(this)
  lazy val asUndirectedJungGraph = new UndirectedJungGraph(this)

  protected val linkSet: AgentSet
  val world = linkSet.world
  val isDirected = linkSet.isDirected
  val isUndirected = !isDirected
  def links: Iterable[Link]
  def turtles: Iterable[Turtle]

  def edges(turtle: Turtle): Iterable[Link]

  def directedInEdges(turtle: Turtle): Iterable[Link]
  def directedOutEdges(turtle: Turtle): Iterable[Link]

  def inEdges(turtle: Turtle) =
    if (isDirected) directedInEdges(turtle) else edges(turtle)
  def outEdges(turtle: Turtle) =
    if (isDirected) directedOutEdges(turtle) else edges(turtle)

}

class LiveNetLogoGraph(
  protected val linkSet: AgentSet)
  extends NetLogoGraph {
  val isStatic = false
  val linkManager = world.linkManager
  val isAllLinks = linkSet eq world.links
  val isLinkBreed = world.isLinkBreed(linkSet)

  if (!(isAllLinks || isLinkBreed))
    throw new ExtensionException("link set must be a link breed")

  override val isValidLink = linkSet.contains(_: Link)
  override val isValidTurtle = (_: Turtle).id != -1

  def links = linkSet.toIterable[Link]
  def turtles = world.turtles.toIterable[Turtle]

  def edges(turtle: Turtle) =
    linkManager.findLinksWith(turtle, linkSet).toIterable[Link]
  def directedInEdges(turtle: Turtle) =
    linkManager.findLinksTo(turtle, linkSet).toIterable[Link]
  def directedOutEdges(turtle: Turtle) =
    linkManager.findLinksFrom(turtle, linkSet).toIterable[Link]

}

class StaticNetLogoGraph(
  protected val linkSet: AgentSet,
  protected val turtleSet: AgentSet)
  extends NetLogoGraph with ExtensionObject {

  val isStatic = true

  override val isValidLink = linkVector.contains(_: Link)
  override val isValidTurtle = turtleVector.contains(_: Turtle)

  private val turtleVector = Vector() ++ turtleSet.toIterable[Turtle]
  private val linkVector = Vector() ++ linkSet.toIterable[Link]
    .filter { l => turtleVector.contains(l.end1) && turtleVector.contains(l.end2) }

  def turtles = turtleVector: Iterable[Turtle]
  def links = linkVector: Iterable[Link]

  def edges(turtle: Turtle) = linkVector.filter(l => l.end1 == turtle || l.end2 == turtle)

  private lazy val inEdgesMap: Map[Turtle, Iterable[Link]] = links.groupBy(_.end2)
  override def directedInEdges(turtle: Turtle) = inEdgesMap.get(turtle).flatten

  private lazy val outEdgesMap: Map[Turtle, Iterable[Link]] = links.groupBy(_.end1)
  override def directedOutEdges(turtle: Turtle) = outEdgesMap.get(turtle).flatten

  // ExtensionObject methods:

  override def dump(readable: Boolean, exporting: Boolean, reference: Boolean): String =
    sys.error("TODO")

  override def getExtensionName: String = "nw" // TODO change this here if we rename to "network"
  override def getNLTypeName: String = ""
  override def recursivelyEqual(obj: AnyRef): Boolean =
    Option(obj)
      .collect { case g: StaticNetLogoGraph => g }
      .filter(g => g.linkVector == this.linkVector)
      .filter(g => g.turtleVector == this.turtleVector)
      .isDefined
}