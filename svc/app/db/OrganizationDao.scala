package db

import core.{Organization, OrganizationQuery, Role, User}
import lib.UrlKey
import anorm._
import play.api.db._
import play.api.Play.current
import java.util.UUID

object OrganizationDao {

  private val BaseQuery = """
    select guid::varchar, name, key
      from organizations
     where deleted_at is null
  """

  /**
   * Creates the org and assigns the user as its administrator.
   */
  def createWithAdministrator(user: User, name: String): Organization = {
    DB.withTransaction { implicit c =>
      val org = create(user, name)
      Membership.upsert(user, org, user, Role.Admin.key)
      OrganizationLog.create(user, org, s"Created organization and joined as ${Role.Admin.name}")
      org
    }
  }

  private def create(createdBy: User, name: String): Organization = {
    val org = Organization(guid = UUID.randomUUID.toString,
                           key = UrlKey.generate(name),
                           name = name)

    DB.withConnection { implicit c =>
      SQL("""
          insert into organizations
          (guid, name, key, created_by_guid)
          values
          ({guid}::uuid, {name}, {key}, {created_by_guid}::uuid)
          """).on('guid -> org.guid,
                  'name -> org.name,
                  'key -> org.key,
                  'created_by_guid -> createdBy.guid).execute()
    }

    org
  }

  def findByUserAndName(user: User, name: String): Option[Organization] = {
    findByUserAndKey(user, UrlKey.generate(name))
  }

  def findByUserAndKey(user: User, key: String): Option[Organization] = {
    findAll(OrganizationQuery(user_guid = user.guid, key = Some(key), limit = 1)).headOption
  }

  def findByUserAndGuid(user: User, guid: UUID): Option[Organization] = {
    findAll(OrganizationQuery(user_guid = user.guid, guid = Some(guid.toString), limit = 1)).headOption
  }

  def findAll(query: OrganizationQuery): Seq[Organization] = {
    val sql = Seq(
      Some(BaseQuery.trim),
      query.guid.map { v => "and guid = {guid}::uuid" },
      Some("and guid in (select organization_guid from memberships where deleted_at is null and user_guid = {user_guid}::uuid)"),
      query.key.map { v => "and key = lower(trim({key}))" },
      query.name.map { v => "and lower(name) = lower(trim({name}))" },
      Some(s"order by lower(name) limit ${query.limit} offset ${query.offset}")
    ).flatten.mkString("\n   ")

    val bind = Seq(
      query.guid.map { v => 'guid -> toParameterValue(v) },
      Some('user_guid -> toParameterValue(query.user_guid)),
      query.key.map { v => 'key -> toParameterValue(v) },
      query.name.map { v => 'name -> toParameterValue(v) }
    ).flatten

    DB.withConnection { implicit c =>
      SQL(sql).on(bind: _*)().toList.map { row =>
        Organization(guid = row[String]("guid"),
                     name = row[String]("name"),
                     key = row[String]("key"))
      }.toSeq
    }
  }

}