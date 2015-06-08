package controllers

import com.bryzek.apidoc.api.v0.models.{ Domain, Organization, User }
import models._
import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import scala.concurrent.Future

object Domains extends Controller {

  implicit val context = scala.concurrent.ExecutionContext.Implicits.global

  def index(orgKey: String) = AuthenticatedOrg { implicit request =>
    request.requireMember
    val tpl = request.mainTemplate(title = Some("Domains"))
    Ok(views.html.domains.index(tpl.copy(settings = Some(SettingsMenu(section = Some(SettingSection.Domains))))))
  }

  def create(orgKey: String) = AuthenticatedOrg { implicit request =>
    request.requireAdmin
    val tpl = request.mainTemplate(title = Some("Add Domain"))
    Ok(views.html.domains.form(tpl, domainForm))
  }

  def postCreate(orgKey: String) = AuthenticatedOrg.async { implicit request =>
    request.requireAdmin
    val tpl = request.mainTemplate(title = Some("Add Domain"))
    val boundForm = domainForm.bindFromRequest
    boundForm.fold (

      errors => Future {
        Ok(views.html.domains.form(tpl, errors))
      },

      valid => {
        request.api.Domains.postByOrgKey(
          orgKey = request.org.key,
          domain = Domain(valid.name)
        ).map { d =>
          Redirect(routes.Domains.index(request.org.key)).flashing("success" -> s"Domain added")
        }.recover {
          case response: com.bryzek.apidoc.api.v0.errors.ErrorsResponse => {
            Ok(views.html.domains.form(tpl, boundForm, response.errors.map(_.message)))
          }
        }
      }

    )

  }

  def postRemove(orgKey: String, domain: String) = AuthenticatedOrg.async { implicit request =>
    request.requireAdmin

    for {
      response <- request.api.Domains.deleteByOrgKeyAndName(orgKey, domain)
    } yield {
      Redirect(routes.Domains.index(request.org.key)).flashing("success" -> s"Domain removed")
    }
  }
 
  case class DomainData(name: String)
  private[this] val domainForm = Form(
    mapping(
      "name" -> nonEmptyText
    )(DomainData.apply)(DomainData.unapply)
  )
}
