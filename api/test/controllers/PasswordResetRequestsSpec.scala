package controllers

import db.PasswordResetRequestsDao
import com.bryzek.apidoc.api.v0.models.{PasswordReset, PasswordResetRequest, User}
import com.bryzek.apidoc.api.v0.errors.ErrorsResponse
import java.util.UUID

import play.api.test._
import play.api.test.Helpers._

class PasswordResetRequestsSpec extends BaseSpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  "POST /password_reset_requests" in new WithServer {
    val user = createUser()
    PasswordResetRequestsDao.findAll(userGuid = Some(user.guid)).map(_.userGuid) must be(Seq.empty)
    val pr = createPasswordRequest(user.email)
    PasswordResetRequestsDao.findAll(userGuid = Some(user.guid)).map(_.userGuid) must be(Seq(user.guid))
  }

  "POST /password_reset_requests does not reveal whether or not email exists" in new WithServer {
    val user = createUser()
    val pr = createPasswordRequest("other-" + user.email)
    PasswordResetRequestsDao.findAll(userGuid = Some(user.guid)).map(_.userGuid) must be(Seq.empty)
  }

}
