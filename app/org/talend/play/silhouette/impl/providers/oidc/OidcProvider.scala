package org.talend.play.silhouette.impl.providers.oidc

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl
import com.mohiva.play.silhouette.impl.exceptions.{ProfileRetrievalException, UnexpectedResponseException}
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider._
import com.mohiva.play.silhouette.impl.providers._
import org.talend.play.silhouette.impl.providers.oidc.OidcProvider._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.ws.WSResponse

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Base Provider for Open Id Connect.
  *
  * @see https://developers.Oidc.com/+/api/auth-migration#timetable
  * @see https://developers.Oidc.com/+/api/auth-migration#oauth2login
  * @see https://developers.Oidc.com/accounts/docs/OAuth2Login
  * @see https://developers.Oidc.com/+/api/latest/people
  */
trait BaseOidcProvider extends OAuth2Provider {

  /**
    * The content type to parse a profile from.
    */
  override type Content = JsValue

  /**
    * The provider ID.
    */
  override val id = ID

  /**
    * Defines the URLs that are needed to retrieve the profile data.
    */
  override protected val urls = Map("userInfoUri" -> this.settings.customProperties("userInfoUri"))

  /**
    * Builds the social profile.
    *
    * @param authInfo The auth info received from the provider.
    * @return On success the build social profile, otherwise a failure.
    */
  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {

//.format(authInfo.accessToken)
    httpLayer.url(urls("userInfoUri"))
      .withHeaders("Authorization" -> ("Bearer " + authInfo.accessToken), ("Accept" -> "application/json"))
      .get()
      .flatMap { response =>
      val json = response.json
      (json \ "error").asOpt[JsObject] match {
        case Some(error) =>
          val errorCode = (error \ "code").as[Int]
          val errorMsg = (error \ "message").as[String]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, errorCode, errorMsg))
        case _ => profileParser.parse(json)
      }
    }
  }

  override protected def buildInfo(response: WSResponse): Try[OAuth2Info] = {
//    response.json.validate[OidcInfo].asEither.fold(
//      error => Failure(new UnexpectedResponseException(InvalidInfoFormat.format(id, error))),
//      info => Success(info)
//    )
    response.json.validate[OAuth2Info](OidcInfo.infoReads).asEither.fold(
      error => Failure(new UnexpectedResponseException(InvalidInfoFormat.format(id, error))),
      info => Success(info)
    )
  }
}

/**
  * The profile parser for the common social profile.
  */
class OidcProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile] {

  /**
    * Parses the social profile.
    *
    * @param json The content returned from the provider.
    * @return The social profile from given result.
    */
  override def parse(json: JsValue) = Future.successful {

    // there's also preferred_username
    val userID = (json \ "sub").as[String]
    val firstName = (json \ "given_name").asOpt[String]
    val lastName = (json \ "family_name").asOpt[String]
    val fullName = (json \ "name").asOpt[String]

    // https://developers.Oidc.com/+/api/latest/people#emails.type
    val emailValue = (json \ "email").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID),
      firstName = firstName,
      lastName = lastName,
      fullName = fullName,
      avatarURL = None,
      email = emailValue)
  }
}

/**
  * The Oidc OAuth2 Provider.
  *
  * @param httpLayer The HTTP layer implementation.
  * @param stateProvider The state provider implementation.
  * @param settings The provider settings.
  */
class OidcProvider(
                      protected val httpLayer: HTTPLayer,
                      protected val stateProvider: OAuth2StateProvider,
                      val settings: OAuth2Settings)
  extends BaseOidcProvider with CommonSocialProfileBuilder {

  /**
    * The type of this class.
    */
  type Self = OidcProvider

  /**
    * The profile parser implementation.
    */
  val profileParser = new OidcProfileParser

  /**
    * Gets a provider initialized with a new settings object.
    *
    * @param f A function which gets the settings passed and returns different settings.
    * @return An instance of the provider initialized with new settings.
    */
  def withSettings(f: (Settings) => Settings) = {
    new OidcProvider(httpLayer, stateProvider, f(settings))
  }
}

/**
  * The companion object.
  */
object OidcProvider {

  /**
    * The error messages.
    */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, message: %s"

  /**
    * The Oidc constants.
    */
  val ID = "oidc"

  /**
   * Used for Oidc authorization code flow
   */
  val Scope = "scope"
  /**
    * Used for Oidc authorization code flow
    */
  val IdToken = "id_token"
}

/**
 * Parses response from access token Oidc endpoint.
 */
object OidcInfo {

  /**
    * Converts the JSON into a [[impl.providers.OAuth2Info]] object.
    *
    * idToken is stored inside oAuth2Indo#param(id_token)
    * scope is stored inside oAuth2Indo#param(scope) (as space delimited string list)
    */
  val infoReads: Reads[OAuth2Info] = (
    (__ \ AccessToken).read[String] and
    (__ \ TokenType).readNullable[String] and
    (__ \ ExpiresIn).readNullable[Int] and
    (__ \ RefreshToken).readNullable[String] and
    (__ \ OidcProvider.Scope).readNullable[String] and
    (__ \ IdToken).readNullable[String]
    ) ((accessToken: String, tokenType: Option[String], expiresIn: Option[Int], refreshToken: Option[String], scope: Option[String], idToken: Option[String]) =>
      OAuth2Info.apply(accessToken, tokenType, expiresIn, refreshToken, Some(Map(OidcProvider.Scope -> scope.getOrElse(null), IdToken -> idToken.getOrElse(null))))
    )
}