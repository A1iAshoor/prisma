package cool.graph.api.mutations.mutations

import cool.graph.api.ApiDependencies
import cool.graph.api.database.DataResolver
import cool.graph.api.database.mutactions.MutactionGroup
import cool.graph.api.mutations.{ClientMutation, ReturnValueResult}
import cool.graph.shared.models.{Model, Project}
import cool.graph.util.coolSangria.Sangria
import sangria.schema

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UpdateOrCreate(model: Model, project: Project, args: schema.Args, dataResolver: DataResolver, allowSettingManagedFields: Boolean = false)(
    implicit apiDependencies: ApiDependencies)
    extends ClientMutation(model, args, dataResolver) {

  val argsPointer: Map[String, Any] = args.raw.get("input") match {
    case Some(value) => value.asInstanceOf[Map[String, Any]]
    case None        => args.raw
  }

  val updateMutation: Update = {
    val updateArgs = Sangria.rawArgs(argsPointer("update").asInstanceOf[Map[String, Any]])
    new Update(model, project, updateArgs, dataResolver)
  }
  val createMutation: Create = {
    val createArgs = Sangria.rawArgs(argsPointer("create").asInstanceOf[Map[String, Any]])
    new Create(model, project, createArgs, dataResolver)
  }

  override def prepareMutactions(): Future[List[MutactionGroup]] = {
    for {
      item <- updateMutation.dataItem
      mutactionGroups <- if (item.isDefined) {
                          updateMutation.prepareMutactions()
                        } else {
                          createMutation.prepareMutactions()
                        }
    } yield {
      mutactionGroups
    }
  }

  override def getReturnValue: Future[ReturnValueResult] = {
    updateMutation.dataItem.flatMap {
      case Some(dataItem) => returnValueById(model, dataItem.id)
      case None           => returnValueById(model, createMutation.id)
    }
  }
}