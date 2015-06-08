package builder.api_json

import core.{Importer, TypeValidator, TypesProvider, TypesProviderEnum}
import com.bryzek.apidoc.spec.v0.models.{Import, Service}
import lib.{Datatype, DatatypeResolver, Type}

private[api_json] case class InternalServiceFormTypesProvider(internal: InternalServiceForm) extends TypesProvider {

  override def enums = internal.enums.map { enum =>
    TypesProviderEnum(
      name = enum.name,
      values = enum.values.flatMap(_.name)
    )
  }

  override def unionNames = internal.unions.map(_.name)

  override def modelNames = internal.models.map(_.name)

}

/**
  * Takes an internal service form and recursively builds up a type
  * provider for all enums and all models specified in the service or
  * in any of the imports. Takes care to avoid importing the same
  * service multiple times (based on uniqueness of the import URIs)
  */
private[api_json] case class RecursiveTypesProvider(
  internal: InternalServiceForm
) extends TypesProvider {

  override def enums = providers.map(_.enums).flatten

  override def unionNames = providers.map(_.unionNames).flatten

  override def modelNames = providers.map(_.modelNames).flatten

  private lazy val providers = Seq(InternalServiceFormTypesProvider(internal)) ++ resolve(internal.imports.flatMap(_.uri))

  private def resolve(
    importUris: Seq[String],
    imported: Set[String] = Set.empty
  ): Seq[TypesProvider] = {
    importUris.headOption match {
      case None => Seq.empty
      case Some(uri) => {
        if (imported.contains(uri.toLowerCase.trim)) {
          // already imported
          resolve(importUris.drop(1), imported)
        } else {
          val importer = Importer(internal.fetcher, uri)
          importer.validate match {
            case Nil => {
              Seq(TypesProvider.FromService(importer.service)) ++ resolve(importUris.drop(1), imported ++ Set(uri))
            }
            case errors => {
              // There are errors w/ this import - skip it
              resolve(importUris.drop(1), imported ++ Set(uri))
            }
          }
        }
      }
    }
  }

}

private[api_json] case class TypeResolver(
  defaultNamespace: Option[String],
  provider: TypesProvider
) {

  private val resolver = DatatypeResolver(
    enumNames = provider.enums.map(_.name),
    modelNames = provider.modelNames,
    unionNames = provider.unionNames
  )

  private lazy val validator = TypeValidator(
    defaultNamespace = defaultNamespace,
    provider.enums
  )

  def toType(name: String): Option[Type] = {
    resolver.toType(name)
  }

  def parseWithError(internal: InternalDatatype): Datatype = {
    parse(internal).getOrElse {
      sys.error(s"Unrecognized datatype[${internal.label}]")
    }
  }

  /**
    * Resolves the type name into instances of a first class Type.
    */
  def parse(internal: InternalDatatype): Option[Datatype] = {
    resolver.parse(internal.label)
  }

  def assertValidDefault(pd: Datatype, value: String) {
    validate(pd, value) match {
      case None => {}
      case Some(msg) => sys.error(msg)
    }
  }

  def validate(
    pd: Datatype,
    value: String,
    errorPrefix: Option[String] = None
  ): Option[String] = {
    validator.validate(pd, value, errorPrefix)
  }

}
