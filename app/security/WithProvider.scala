package security

import com.mohiva.play.silhouette.api.Authorization
import com.mohiva.play.silhouette.impl.authenticators.{CookieAuthenticator, SessionAuthenticator}
import models.User
import play.api.i18n.Messages
import play.api.mvc.Request

import scala.concurrent.Future

/**
  * @author agonzalez
  */
case class WithProvider(provider: String) extends Authorization[User, SessionAuthenticator] {

  def isAuthorized[B](user: User, authenticator: SessionAuthenticator)(implicit request: Request[B], messages: Messages) = {
    Future.successful(user.loginInfo.providerID == provider)
  }
}