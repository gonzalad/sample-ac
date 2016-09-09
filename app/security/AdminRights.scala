package security

import com.mohiva.play.silhouette.api.{ Authenticator, Authorization }
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import models.User
import play.api.i18n.Messages
import play.api.mvc.Request

import scala.concurrent.Future

/**
 * @author agonzalez
 */
case class AdminRights() extends Authorization[User, SessionAuthenticator] {

  override def isAuthorized[B](identity: User, authenticator: SessionAuthenticator)(implicit request: Request[B]): Future[Boolean] = {
    println("isAuthorized called")
    Future.successful(identity.roles.contains("openid"))
  }
}
