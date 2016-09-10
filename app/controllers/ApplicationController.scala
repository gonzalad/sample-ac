package controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.{ HandlerResult, LogoutEvent, Silhouette }
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import forms.{ SignInForm, SignUpForm }
import org.talend.play.silhouette.client.OAuthBearerTokenRequestFilter
import org.talend.play.silhouette.impl.providers.oidc.OidcProvider
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc.Action
import play.api.libs.json.Json
import play.api.libs.ws.{ WSClient, WSRequestFilter, WSResponse }
import play.api.mvc.Controller
import security.{ AdminRights, DefaultEnv, WithProvider }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * The basic application controller.
 *
 * @param messagesApi            The Play messages API.
 * @param silhouette             The Silhouette stack.
 * @param socialProviderRegistry The social provider registry.
 */
class ApplicationController @Inject() (
  val messagesApi: MessagesApi,
  val silhouette: Silhouette[DefaultEnv],
  socialProviderRegistry: SocialProviderRegistry,
  implicit val webJarAssets: WebJarAssets,
  ws: WSClient,
  implicit val ec: ExecutionContext,
  implicit val authInfoRepository: AuthInfoRepository
)
  extends Controller with I18nSupport {

  /**
   * Handles the index action.
   *
   * @return The result to display.
   */
  def index = silhouette.SecuredAction.async { implicit request =>
    Future.successful(Ok(views.html.home(request.identity)))
  }
  //  def index = silhouette.SecuredAction.async { implicit request =>
  //    Future.successful(Ok(views.html.home(request.identity)))
  //  }

  def hello = Action { request =>
    Ok("coucou you")
  }

  //  def secured = SecuredAction.async {
  //    Future.successful(Ok("coucou you"))
  //  }
  def secured = silhouette.SecuredAction(WithProvider[DefaultEnv#A](OidcProvider.ID)) {
    Ok("coucou you")
  }

  def admin = silhouette.SecuredAction(AdminRights()) {
    Ok("admin action called")
  }

  /**
   * Calling Resource Server
   * @return
   */
  def call = Action.async { implicit request =>
    silhouette.SecuredRequestHandler { userAwareRequest =>
      ws.url("http://localhost:9001/rs/secure")
        .withRequestFilter(new OAuthBearerTokenRequestFilter(Some(userAwareRequest.identity.loginInfo), authInfoRepository, ec))
        .get()
        .map { response =>
          if (response.status == 200) {
            HandlerResult(Ok(response.body))
          } else {
            HandlerResult(BadGateway(response.body))
          }
        }
    }.map {
      //case HandlerResult(r, Some(content: String)) => r
      case HandlerResult(r, None) => r
    }
    //Ok("coucou you")
  }

  //  def call = silhouette.UserAwareAction { request =>
  //    ws.url("http://localhost:9000/rs/secure")
  //      .withRequestFilter(new OAuthBearerTokenRequestFilter(request.identity.map { identity => identity.loginInfo.providerKey }))
  //      .get()
  //      .map { response =>
  //        Ok(response.body)
  //      }
  //  }

  def profile = silhouette.SecuredAction { implicit request =>
    Ok(Json.toJson(request.identity.loginInfo))
  }

  /**
   * Handles the Sign In action.
   *
   * @return The result to display.
   */
  def signIn = silhouette.UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) => Future.successful(Redirect(routes.ApplicationController.index()))
      case None => Future.successful(Ok(views.html.signIn(SignInForm.form, socialProviderRegistry)))
    }
  }

  /**
   * Handles the Sign Up action.
   *
   * @return The result to display.
   */
  def signUp = silhouette.UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) => Future.successful(Redirect(routes.ApplicationController.index()))
      case None => Future.successful(Ok(views.html.signUp(SignUpForm.form)))
    }
  }

  /**
   * Handles the Sign Out action.
   *
   * @return The result to display.
   */
  def signOut = silhouette.SecuredAction.async { implicit request =>
    val result = Redirect(routes.ApplicationController.index())
    silhouette.env.eventBus.publish(LogoutEvent(request.identity, request))
    silhouette.env.authenticatorService.discard(request.authenticator, result)
  }
}
