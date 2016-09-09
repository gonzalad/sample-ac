package security

import com.mohiva.play.silhouette.api.{ Authenticator, Authorization }
import com.mohiva.play.silhouette.impl.authenticators.{ CookieAuthenticator, SessionAuthenticator }
import models.User
import play.api.i18n.Messages
import play.api.mvc.Request

import scala.concurrent.Future

/**
 * @author agonzalez
 */
case class WithProvider[A <: Authenticator](provider: String) extends Authorization[User, A] {

  def isAuthorized[B](user: User, authenticator: A)(implicit request: Request[B]): Future[Boolean] = {
    Future.successful(user.loginInfo.providerID == provider)
  }
}