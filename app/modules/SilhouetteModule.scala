package modules

import com.google.inject.{AbstractModule, Provides}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.services._
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.api.{Environment, EventBus}
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.daos.DelegableAuthInfoDAO
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth1._
import com.mohiva.play.silhouette.impl.providers.oauth1.secrets.{CookieSecretProvider, CookieSecretSettings}
import com.mohiva.play.silhouette.impl.providers.oauth1.services.PlayOAuth1Service
import com.mohiva.play.silhouette.impl.providers.oauth2._
import com.mohiva.play.silhouette.impl.providers.oauth2.state.{CookieStateProvider, CookieStateSettings, DummyStateProvider}
import com.mohiva.play.silhouette.impl.providers.openid.YahooProvider
import com.mohiva.play.silhouette.impl.providers.openid.services.PlayOpenIDService
import com.mohiva.play.silhouette.impl.repositories.DelegableAuthInfoRepository
import com.mohiva.play.silhouette.impl.services._
import com.mohiva.play.silhouette.impl.util._
import models.User
import models.daos._
import models.services.{UserService, UserServiceImpl}
import net.codingwell.scalaguice.ScalaModule
import org.talend.play.silhouette.impl.providers.oidc.OidcProvider
import play.api.Play
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.openid.OpenIdClient

/**
 * The Guice module which wires all Silhouette dependencies.
 */
class SilhouetteModule extends AbstractModule with ScalaModule {

  /**
   * Configures the module.
   */
  def configure() {
    bind[UserService].to[UserServiceImpl]
    bind[UserDAO].to[UserDAOImpl]
    bind[DelegableAuthInfoDAO[PasswordInfo]].to[PasswordInfoDAO]
    bind[DelegableAuthInfoDAO[OAuth1Info]].to[OAuth1InfoDAO]
    bind[DelegableAuthInfoDAO[OAuth2Info]].to[OAuth2InfoDAO]
    bind[DelegableAuthInfoDAO[OpenIDInfo]].to[OpenIDInfoDAO]
    bind[CacheLayer].to[PlayCacheLayer]
    bind[HTTPLayer].toInstance(new PlayHTTPLayer)
    bind[IDGenerator].toInstance(new SecureRandomIDGenerator())
    bind[PasswordHasher].toInstance(new BCryptPasswordHasher)
    bind[FingerprintGenerator].toInstance(new DefaultFingerprintGenerator(false))
    bind[EventBus].toInstance(EventBus())
  }

  /**
   * Provides the Silhouette environment.
   *
   * @param userService The user service implementation.
   * @param authenticatorService The authentication service implementation.
   * @param eventBus The event bus instance.
   * @return The Silhouette environment.
   */
  @Provides
  def provideEnvironment(
    userService: UserService,
    authenticatorService: AuthenticatorService[SessionAuthenticator],
    eventBus: EventBus): Environment[User, SessionAuthenticator] = {

    Environment[User, SessionAuthenticator](
      userService,
      authenticatorService,
      Seq(),
      eventBus
    )
  }

  /**
   * Provides the social provider registry.
   *
   * @param facebookProvider The Facebook provider implementation.
   * @param googleProvider The Google provider implementation.
   * @param vkProvider The VK provider implementation.
   * @param clefProvider The Clef provider implementation.
   * @param twitterProvider The Twitter provider implementation.
   * @param xingProvider The Xing provider implementation.
   * @param yahooProvider The Yahoo provider implementation.
   * @param githubProvider The Yahoo provider implementation.
   * @return The Silhouette environment.
   */
  @Provides
  def provideSocialProviderRegistry(
//    facebookProvider: FacebookProvider,
//    googleProvider: GoogleProvider,
//    vkProvider: VKProvider,
//    clefProvider: ClefProvider,
//    twitterProvider: TwitterProvider,
//    xingProvider: XingProvider,
//    yahooProvider: YahooProvider
//      githubProvider: GitHubProvider
    oidcProvider: OidcProvider): SocialProviderRegistry = {

    SocialProviderRegistry(Seq(
//      googleProvider,
//      facebookProvider,
//      twitterProvider,
//      vkProvider,
//      xingProvider,
//      yahooProvider
//      clefProvider,
//      githubProvider
        oidcProvider
    ))
  }

  /**
   * Provides the authenticator service.
   *
   * @param fingerprintGenerator The fingerprint generator implementation.
   * @return The authenticator service.
   */
  @Provides
  def provideAuthenticatorService(
    fingerprintGenerator: FingerprintGenerator): AuthenticatorService[SessionAuthenticator] = {
    new SessionAuthenticatorService(SessionAuthenticatorSettings(
      sessionKey = Play.configuration.getString("silhouette.authenticator.sessionKey").get,
      encryptAuthenticator = Play.configuration.getBoolean("silhouette.authenticator.encryptAuthenticator").get,
      useFingerprinting = Play.configuration.getBoolean("silhouette.authenticator.useFingerprinting").get,
      authenticatorIdleTimeout = Play.configuration.getInt("silhouette.authenticator.authenticatorIdleTimeout"),
      authenticatorExpiry = Play.configuration.getInt("silhouette.authenticator.authenticatorExpiry").get
    ), fingerprintGenerator, Clock())
  }

  /**
   * Provides the auth info repository.
   *
   * @param passwordInfoDAO The implementation of the delegable password auth info DAO.
   * @param oauth1InfoDAO The implementation of the delegable OAuth1 auth info DAO.
   * @param oauth2InfoDAO The implementation of the delegable OAuth2 auth info DAO.
   * @param openIDInfoDAO The implementation of the delegable OpenID auth info DAO.
   * @return The auth info repository instance.
   */
  @Provides
  def provideAuthInfoRepository(
    passwordInfoDAO: DelegableAuthInfoDAO[PasswordInfo],
    oauth1InfoDAO: DelegableAuthInfoDAO[OAuth1Info],
    oauth2InfoDAO: DelegableAuthInfoDAO[OAuth2Info],
    openIDInfoDAO: DelegableAuthInfoDAO[OpenIDInfo]): AuthInfoRepository = {

    new DelegableAuthInfoRepository(passwordInfoDAO, oauth1InfoDAO, oauth2InfoDAO, openIDInfoDAO)
  }

  /**
   * Provides the avatar service.
   *
   * @param httpLayer The HTTP layer implementation.
   * @return The avatar service implementation.
   */
  @Provides
  def provideAvatarService(httpLayer: HTTPLayer): AvatarService = new GravatarService(httpLayer)

  /**
   * Provides the OAuth1 token secret provider.
   *
   * @return The OAuth1 token secret provider implementation.
   */
  @Provides
  def provideOAuth1TokenSecretProvider: OAuth1TokenSecretProvider = {
    new CookieSecretProvider(CookieSecretSettings(
      cookieName = Play.configuration.getString("silhouette.oauth1TokenSecretProvider.cookieName").get,
      cookiePath = Play.configuration.getString("silhouette.oauth1TokenSecretProvider.cookiePath").get,
      cookieDomain = Play.configuration.getString("silhouette.oauth1TokenSecretProvider.cookieDomain"),
      secureCookie = Play.configuration.getBoolean("silhouette.oauth1TokenSecretProvider.secureCookie").get,
      httpOnlyCookie = Play.configuration.getBoolean("silhouette.oauth1TokenSecretProvider.httpOnlyCookie").get,
      expirationTime = Play.configuration.getInt("silhouette.oauth1TokenSecretProvider.expirationTime").get
    ), Clock())
  }

  /**
   * Provides the OAuth2 state provider.
   *
   * @param idGenerator The ID generator implementation.
   * @return The OAuth2 state provider implementation.
   */
  @Provides
  def provideOAuth2StateProvider(idGenerator: IDGenerator): OAuth2StateProvider = {
    new CookieStateProvider(CookieStateSettings(
      cookieName = Play.configuration.getString("silhouette.oauth2StateProvider.cookieName").get,
      cookiePath = Play.configuration.getString("silhouette.oauth2StateProvider.cookiePath").get,
      cookieDomain = Play.configuration.getString("silhouette.oauth2StateProvider.cookieDomain"),
      secureCookie = Play.configuration.getBoolean("silhouette.oauth2StateProvider.secureCookie").get,
      httpOnlyCookie = Play.configuration.getBoolean("silhouette.oauth2StateProvider.httpOnlyCookie").get,
      expirationTime = Play.configuration.getInt("silhouette.oauth2StateProvider.expirationTime").get
    ), idGenerator, Clock())
  }

  /**
   * Provides the credentials provider.
   *
   * @param authInfoRepository The auth info repository implementation.
   * @param passwordHasher The default password hasher implementation.
   * @return The credentials provider.
   */
  @Provides
  def provideCredentialsProvider(
    authInfoRepository: AuthInfoRepository,
    passwordHasher: PasswordHasher): CredentialsProvider = {

    new CredentialsProvider(authInfoRepository, passwordHasher, Seq(passwordHasher))
  }

  /**
   * Provides the Facebook provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @return The Facebook provider.
   */
  @Provides
  def provideFacebookProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider): FacebookProvider = {
    new FacebookProvider(httpLayer, stateProvider, OAuth2Settings(
      authorizationURL = Play.configuration.getString("silhouette.facebook.authorizationURL"),
      accessTokenURL = Play.configuration.getString("silhouette.facebook.accessTokenURL").get,
      redirectURL = Play.configuration.getString("silhouette.facebook.redirectURL").get,
      clientID = Play.configuration.getString("silhouette.facebook.clientID").getOrElse(""),
      clientSecret = Play.configuration.getString("silhouette.facebook.clientSecret").getOrElse(""),
      scope = Play.configuration.getString("silhouette.facebook.scope")))
  }

  /**
   * Provides the Google provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @return The Google provider.
   */
  @Provides
  def provideGoogleProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider): GoogleProvider = {
    new GoogleProvider(httpLayer, stateProvider, OAuth2Settings(
      authorizationURL = Play.configuration.getString("silhouette.google.authorizationURL"),
      accessTokenURL = Play.configuration.getString("silhouette.google.accessTokenURL").get,
      redirectURL = Play.configuration.getString("silhouette.google.redirectURL").get,
      clientID = Play.configuration.getString("silhouette.google.clientID").getOrElse(""),
      clientSecret = Play.configuration.getString("silhouette.google.clientSecret").getOrElse(""),
      scope = Play.configuration.getString("silhouette.google.scope")))
  }

  /**
   * Provides the VK provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @return The VK provider.
   */
  @Provides
  def provideVKProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider): VKProvider = {
    new VKProvider(httpLayer, stateProvider, OAuth2Settings(
      authorizationURL = Play.configuration.getString("silhouette.vk.authorizationURL"),
      accessTokenURL = Play.configuration.getString("silhouette.vk.accessTokenURL").get,
      redirectURL = Play.configuration.getString("silhouette.vk.redirectURL").get,
      clientID = Play.configuration.getString("silhouette.vk.clientID").getOrElse(""),
      clientSecret = Play.configuration.getString("silhouette.vk.clientSecret").getOrElse(""),
      scope = Play.configuration.getString("silhouette.vk.scope")))
  }

  /**
   * Provides the Clef provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @return The Clef provider.
   */
  @Provides
  def provideClefProvider(httpLayer: HTTPLayer): ClefProvider = {
    new ClefProvider(httpLayer, new DummyStateProvider, OAuth2Settings(
      accessTokenURL = Play.configuration.getString("silhouette.clef.accessTokenURL").get,
      redirectURL = Play.configuration.getString("silhouette.clef.redirectURL").get,
      clientID = Play.configuration.getString("silhouette.clef.clientID").getOrElse(""),
      clientSecret = Play.configuration.getString("silhouette.clef.clientSecret").getOrElse("")))
  }

  /**
   * Provides the Twitter provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param tokenSecretProvider The token secret provider implementation.
   * @return The Twitter provider.
   */
  @Provides
  def provideTwitterProvider(httpLayer: HTTPLayer, tokenSecretProvider: OAuth1TokenSecretProvider): TwitterProvider = {
    val settings = OAuth1Settings(
      requestTokenURL = Play.configuration.getString("silhouette.twitter.requestTokenURL").get,
      accessTokenURL = Play.configuration.getString("silhouette.twitter.accessTokenURL").get,
      authorizationURL = Play.configuration.getString("silhouette.twitter.authorizationURL").get,
      callbackURL = Play.configuration.getString("silhouette.twitter.callbackURL").get,
      consumerKey = Play.configuration.getString("silhouette.twitter.consumerKey").getOrElse(""),
      consumerSecret = Play.configuration.getString("silhouette.twitter.consumerSecret").getOrElse(""))

    new TwitterProvider(httpLayer, new PlayOAuth1Service(settings), tokenSecretProvider, settings)
  }

  /**
   * Provides the Xing provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param tokenSecretProvider The token secret provider implementation.
   * @return The Xing provider.
   */
  @Provides
  def provideXingProvider(httpLayer: HTTPLayer, tokenSecretProvider: OAuth1TokenSecretProvider): XingProvider = {
    val settings = OAuth1Settings(
      requestTokenURL = Play.configuration.getString("silhouette.xing.requestTokenURL").get,
      accessTokenURL = Play.configuration.getString("silhouette.xing.accessTokenURL").get,
      authorizationURL = Play.configuration.getString("silhouette.xing.authorizationURL").get,
      callbackURL = Play.configuration.getString("silhouette.xing.callbackURL").get,
      consumerKey = Play.configuration.getString("silhouette.xing.consumerKey").getOrElse(""),
      consumerSecret = Play.configuration.getString("silhouette.xing.consumerSecret").getOrElse(""))

    new XingProvider(httpLayer, new PlayOAuth1Service(settings), tokenSecretProvider, settings)
  }

  /**
   * Provides the Yahoo provider.
   *
   * @param cacheLayer The cache layer implementation.
   * @param httpLayer The HTTP layer implementation.
   * @param client The OpenID client implementation.
   * @return The Yahoo provider.
   */
  @Provides
  def provideYahooProvider(cacheLayer: CacheLayer, httpLayer: HTTPLayer, client: OpenIdClient): YahooProvider = {
    import scala.collection.JavaConversions._
    val settings = OpenIDSettings(
      providerURL = Play.configuration.getString("silhouette.yahoo.providerURL").get,
      callbackURL = Play.configuration.getString("silhouette.yahoo.callbackURL").get,
      axRequired = Play.configuration.getObject("silhouette.yahoo.axRequired").map(_.mapValues(_.unwrapped().toString).toSeq).getOrElse(Seq()),
      axOptional = Play.configuration.getObject("silhouette.yahoo.axOptional").map(_.mapValues(_.unwrapped().toString).toSeq).getOrElse(Seq()),
      realm = Play.configuration.getString("silhouette.yahoo.realm"))

    new YahooProvider(httpLayer, new PlayOpenIDService(client, settings), settings)
  }
  /**
   * Provides the GitHub provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @return The GitHub provider.
   */
  @Provides
  def provideGitHubProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider): GitHubProvider = {
    new GitHubProvider(httpLayer, stateProvider, OAuth2Settings(
      authorizationURL = Play.configuration.getString("silhouette.github.authorizationUrl"),
      accessTokenURL = Play.configuration.getString("silhouette.github.accessTokenUrl").get,
      redirectURL = Play.configuration.getString("silhouette.github.redirectURL").get,
      clientID = Play.configuration.getString("silhouette.github.clientId").getOrElse(""),
      clientSecret = Play.configuration.getString("silhouette.github.clientSecret").getOrElse(""),
      scope = Play.configuration.getString("silhouette.github.scope")))
  }

  @Provides
  def provideOpenIdProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider): OidcProvider = {
    new OidcProvider(httpLayer, stateProvider, OAuth2Settings(
      authorizationURL = Play.configuration.getString("silhouette.oidc.authorizationUrl"),
      accessTokenURL = Play.configuration.getString("silhouette.oidc.accessTokenUrl").get,
      redirectURL = Play.configuration.getString("silhouette.oidc.redirectURL").get,
      clientID = Play.configuration.getString("silhouette.oidc.clientId").getOrElse(""),
      clientSecret = Play.configuration.getString("silhouette.oidc.clientSecret").getOrElse(""),
      scope = Play.configuration.getString("silhouette.oidc.scope"),
      customProperties = Map("userInfoUri" -> Play.configuration.getString("silhouette.oidc.userInfoUri").get)
    ))
  }
}
