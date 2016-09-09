package modules

import com.google.inject.{ AbstractModule, Provides }
import com.mohiva.play.silhouette.api.actions.{ SecuredErrorHandler, UnsecuredErrorHandler }
import com.mohiva.play.silhouette.api.crypto.{ Base64AuthenticatorEncoder, CookieSigner, Crypter }
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.services._
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.api.{ Environment, EventBus, Silhouette, SilhouetteProvider }
import com.mohiva.play.silhouette.crypto.{ JcaCookieSigner, JcaCookieSignerSettings, JcaCrypter, JcaCrypterSettings }
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth1._
import com.mohiva.play.silhouette.impl.providers.oauth1.secrets.{ CookieSecretProvider, CookieSecretSettings }
import com.mohiva.play.silhouette.impl.providers.oauth1.services.PlayOAuth1Service
import com.mohiva.play.silhouette.impl.providers.oauth2._
import com.mohiva.play.silhouette.impl.providers.oauth2.state.{ CookieStateProvider, CookieStateSettings, DummyStateProvider }
import com.mohiva.play.silhouette.impl.services._
import com.mohiva.play.silhouette.impl.util._
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.daos.{ DelegableAuthInfoDAO, InMemoryAuthInfoDAO }
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import models.daos._
import models.services.{ UserService, UserServiceImpl }
import net.codingwell.scalaguice.ScalaModule
import org.talend.play.silhouette.impl.providers.oidc.OidcProvider
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WSClient
import play.api.{ Configuration, Play }
import security.DefaultEnv
import utils.{ CustomSecuredErrorHandler, CustomUnsecuredErrorHandler }

import scala.concurrent.duration.DurationLong

/**
 * The Guice module which wires all Silhouette dependencies.
 */
class SilhouetteModule extends AbstractModule with ScalaModule {

  /**
   * Configures the module.
   */
  def configure() {
    bind[Silhouette[DefaultEnv]].to[SilhouetteProvider[DefaultEnv]]
    bind[UnsecuredErrorHandler].to[CustomUnsecuredErrorHandler]
    bind[SecuredErrorHandler].to[CustomSecuredErrorHandler]
    bind[UserService].to[UserServiceImpl]
    bind[UserDAO].to[UserDAOImpl]
    bind[CacheLayer].to[PlayCacheLayer]
    bind[IDGenerator].toInstance(new SecureRandomIDGenerator())
    bind[PasswordHasher].toInstance(new BCryptPasswordHasher)
    bind[FingerprintGenerator].toInstance(new DefaultFingerprintGenerator(false))
    bind[EventBus].toInstance(EventBus())
    bind[Clock].toInstance(Clock())

    // Replace this with the bindings to your concrete DAOs
    bind[DelegableAuthInfoDAO[PasswordInfo]].toInstance(new InMemoryAuthInfoDAO[PasswordInfo])
    bind[DelegableAuthInfoDAO[OAuth1Info]].toInstance(new InMemoryAuthInfoDAO[OAuth1Info])
    bind[DelegableAuthInfoDAO[OAuth2Info]].toInstance(new InMemoryAuthInfoDAO[OAuth2Info])
    bind[DelegableAuthInfoDAO[OpenIDInfo]].toInstance(new InMemoryAuthInfoDAO[OpenIDInfo])
  }

  /**
   * Provides the HTTP layer implementation.
   *
   * @param client Play's WS client.
   * @return The HTTP layer implementation.
   */
  @Provides
  def provideHTTPLayer(client: WSClient): HTTPLayer = new PlayHTTPLayer(client)

  /**
   * Provides the Silhouette environment.
   *
   * @param userService The user service implementation.
   * @param authenticatorService The authentication service implementation.
   * @param eventBus The event bus instance.
   * @return The Silhouette environment.
   */
  /*@Provides
  def provideEnvironment(
                          userService: UserService,
                          authenticatorService: AuthenticatorService[CookieAuthenticator],
                          eventBus: EventBus): Environment[DefaultEnv] = {

    Environment[DefaultEnv](
      userService,
      authenticatorService,
      Seq(),
      eventBus
    )
  }*/

  /**
   * Provides the social provider registry.
   *
   * @param facebookProvider The Facebook provider implementation.
   * @param googleProvider   The Google provider implementation.
   * @param vkProvider       The VK provider implementation.
   * @param clefProvider     The Clef provider implementation.
   * @param twitterProvider  The Twitter provider implementation.
   * @param xingProvider     The Xing provider implementation.
   * @param yahooProvider    The Yahoo provider implementation.
   * @param githubProvider   The Yahoo provider implementation.
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
    oidcProvider: OidcProvider
  ): SocialProviderRegistry = {

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
   * Provides the Silhouette environment.
   *
   * @param userService The user service implementation.
   *                    The authentication service implementation.
   * @param eventBus    The event bus instance.
   * @return The Silhouette environment.
   */
  @Provides
  def provideEnvironment(
    userService: UserService,
    authenticatorService: AuthenticatorService[SessionAuthenticator],
    eventBus: EventBus
  ): Environment[DefaultEnv] = {

    Environment[DefaultEnv](
      userService,
      authenticatorService,
      Seq(),
      eventBus
    )
  }

  /**
   * Provides the authenticator service.
   *
   * @param fingerprintGenerator The fingerprint generator implementation.
   * @return The authenticator service.
   */
  @Provides
  def provideAuthenticatorService(
    fingerprintGenerator: FingerprintGenerator,
    configuration: Configuration
  ): AuthenticatorService[SessionAuthenticator] = {

    val settings = SessionAuthenticatorSettings(
      sessionKey = configuration.getString("silhouette.authenticator.sessionKey").get,
      useFingerprinting = configuration.getBoolean("silhouette.authenticator.useFingerprinting").get,
      authenticatorIdleTimeout = new Some(new DurationLong(configuration.getLong("silhouette.authenticator.authenticatorIdleTimeout").get).seconds),
      authenticatorExpiry = new DurationLong(configuration.getLong("silhouette.authenticator.authenticatorExpiry").get).seconds
    )

    new SessionAuthenticatorService(settings, fingerprintGenerator, new Base64AuthenticatorEncoder(), Clock())
  }

  /**
   * Provides the auth info repository.
   *
   * @param passwordInfoDAO The implementation of the delegable password auth info DAO.
   * @param oauth1InfoDAO   The implementation of the delegable OAuth1 auth info DAO.
   * @param oauth2InfoDAO   The implementation of the delegable OAuth2 auth info DAO.
   * @param openIDInfoDAO   The implementation of the delegable OpenID auth info DAO.
   * @return The auth info repository instance.
   */
  @Provides
  def provideAuthInfoRepository(
    passwordInfoDAO: DelegableAuthInfoDAO[PasswordInfo],
    oauth1InfoDAO: DelegableAuthInfoDAO[OAuth1Info],
    oauth2InfoDAO: DelegableAuthInfoDAO[OAuth2Info],
    openIDInfoDAO: DelegableAuthInfoDAO[OpenIDInfo]
  ): AuthInfoRepository = {

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
  def provideOAuth1TokenSecretProvider(configuration: Configuration, cookieSigner: CookieSigner, crypter: Crypter): OAuth1TokenSecretProvider = {
    new CookieSecretProvider(CookieSecretSettings(
      cookieName = configuration.getString("silhouette.oauth1TokenSecretProvider.cookieName").get,
      cookiePath = configuration.getString("silhouette.oauth1TokenSecretProvider.cookiePath").get,
      cookieDomain = configuration.getString("silhouette.oauth1TokenSecretProvider.cookieDomain"),
      secureCookie = configuration.getBoolean("silhouette.oauth1TokenSecretProvider.secureCookie").get,
      httpOnlyCookie = configuration.getBoolean("silhouette.oauth1TokenSecretProvider.httpOnlyCookie").get,
      expirationTime = new DurationLong(configuration.getLong("silhouette.oauth1TokenSecretProvider.expirationTime").get).seconds
    ), cookieSigner, crypter, Clock())
  }

  /**
   * Provides the crypter for the cookie token secret provider.
   *
   * @param configuration The Play configuration.
   * @return The crypter for the OAuth1 token secret provider.
   */
  @Provides
  def provideOAuth1TokenSecretCrypter(configuration: Configuration): Crypter = {
    val settings = JcaCrypterSettings(configuration.getString("authenticator.crypter.key").get)
    new JcaCrypter(settings)
  }

  /**
   * Provides the cookie signer for the OAuth1 token secret provider.
   *
   * @param configuration The Play configuration.
   * @return The cookie signer for the OAuth1 token secret provider.
   */
  @Provides
  def provideCookieSigner(configuration: Configuration): CookieSigner = {
    val settings: JcaCookieSignerSettings = JcaCookieSignerSettings(key = configuration.getString("silhouette.authenticator.cookie.signer.key").get)
    new JcaCookieSigner(settings)
  }

  /**
   * Provides the OAuth2 state provider.
   *
   * @param idGenerator The ID generator implementation.
   * @return The OAuth2 state provider implementation.
   */
  @Provides
  def provideOAuth2StateProvider(configuration: Configuration, idGenerator: IDGenerator, cookieSigner: CookieSigner): OAuth2StateProvider = {
    new CookieStateProvider(CookieStateSettings(
      cookieName = configuration.getString("silhouette.oauth2StateProvider.cookieName").get,
      cookiePath = configuration.getString("silhouette.oauth2StateProvider.cookiePath").get,
      cookieDomain = configuration.getString("silhouette.oauth2StateProvider.cookieDomain"),
      secureCookie = configuration.getBoolean("silhouette.oauth2StateProvider.secureCookie").get,
      httpOnlyCookie = configuration.getBoolean("silhouette.oauth2StateProvider.httpOnlyCookie").get,
      expirationTime = new DurationLong(configuration.getLong("silhouette.oauth2StateProvider.expirationTime").get).seconds
    ), idGenerator, cookieSigner, Clock())
  }

  /**
   * Provides the password hasher registry.
   *
   * @param passwordHasher The default password hasher implementation.
   * @return The password hasher registry.
   */
  @Provides
  def providePasswordHasherRegistry(passwordHasher: PasswordHasher): PasswordHasherRegistry = {
    new PasswordHasherRegistry(passwordHasher)
  }

  /**
   * Provides the credentials provider.
   *
   * @param authInfoRepository     The auth info repository implementation.
   * @param passwordHasherRegistry The password hasher registry.
   * @return The credentials provider.
   */
  @Provides
  def provideCredentialsProvider(
    authInfoRepository: AuthInfoRepository,
    passwordHasherRegistry: PasswordHasherRegistry
  ): CredentialsProvider = {

    new CredentialsProvider(authInfoRepository, passwordHasherRegistry)
  }

  /**
   * Provides the Facebook provider.
   *
   * @param httpLayer     The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @return The Facebook provider.
   */
  @Provides
  def provideFacebookProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, configuration: Configuration): FacebookProvider = {
    new FacebookProvider(httpLayer, stateProvider, OAuth2Settings(
      authorizationURL = configuration.getString("silhouette.facebook.authorizationURL"),
      accessTokenURL = configuration.getString("silhouette.facebook.accessTokenURL").get,
      redirectURL = configuration.getString("silhouette.facebook.redirectURL").get,
      clientID = configuration.getString("silhouette.facebook.clientID").getOrElse(""),
      clientSecret = configuration.getString("silhouette.facebook.clientSecret").getOrElse(""),
      scope = configuration.getString("silhouette.facebook.scope")
    ))
  }

  /**
   * Provides the Google provider.
   *
   * @param httpLayer     The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @return The Google provider.
   */
  @Provides
  def provideGoogleProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, configuration: Configuration): GoogleProvider = {
    new GoogleProvider(httpLayer, stateProvider, OAuth2Settings(
      authorizationURL = configuration.getString("silhouette.google.authorizationURL"),
      accessTokenURL = configuration.getString("silhouette.google.accessTokenURL").get,
      redirectURL = configuration.getString("silhouette.google.redirectURL").get,
      clientID = configuration.getString("silhouette.google.clientID").getOrElse(""),
      clientSecret = configuration.getString("silhouette.google.clientSecret").getOrElse(""),
      scope = configuration.getString("silhouette.google.scope")
    ))
  }

  /**
   * Provides the VK provider.
   *
   * @param httpLayer     The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @return The VK provider.
   */
  @Provides
  def provideVKProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, configuration: Configuration): VKProvider = {
    new VKProvider(httpLayer, stateProvider, OAuth2Settings(
      authorizationURL = configuration.getString("silhouette.vk.authorizationURL"),
      accessTokenURL = configuration.getString("silhouette.vk.accessTokenURL").get,
      redirectURL = configuration.getString("silhouette.vk.redirectURL").get,
      clientID = configuration.getString("silhouette.vk.clientID").getOrElse(""),
      clientSecret = configuration.getString("silhouette.vk.clientSecret").getOrElse(""),
      scope = configuration.getString("silhouette.vk.scope")
    ))
  }

  /**
   * Provides the Clef provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @return The Clef provider.
   */
  @Provides
  def provideClefProvider(httpLayer: HTTPLayer, configuration: Configuration): ClefProvider = {
    new ClefProvider(httpLayer, new DummyStateProvider, OAuth2Settings(
      accessTokenURL = configuration.getString("silhouette.clef.accessTokenURL").get,
      redirectURL = configuration.getString("silhouette.clef.redirectURL").get,
      clientID = configuration.getString("silhouette.clef.clientID").getOrElse(""),
      clientSecret = configuration.getString("silhouette.clef.clientSecret").getOrElse("")
    ))
  }

  /**
   * Provides the Twitter provider.
   *
   * @param httpLayer           The HTTP layer implementation.
   * @param tokenSecretProvider The token secret provider implementation.
   * @return The Twitter provider.
   */
  @Provides
  def provideTwitterProvider(httpLayer: HTTPLayer, tokenSecretProvider: OAuth1TokenSecretProvider, configuration: Configuration): TwitterProvider = {
    val settings = OAuth1Settings(
      requestTokenURL = configuration.getString("silhouette.twitter.requestTokenURL").get,
      accessTokenURL = configuration.getString("silhouette.twitter.accessTokenURL").get,
      authorizationURL = configuration.getString("silhouette.twitter.authorizationURL").get,
      callbackURL = configuration.getString("silhouette.twitter.callbackURL").get,
      consumerKey = configuration.getString("silhouette.twitter.consumerKey").getOrElse(""),
      consumerSecret = configuration.getString("silhouette.twitter.consumerSecret").getOrElse("")
    )

    new TwitterProvider(httpLayer, new PlayOAuth1Service(settings), tokenSecretProvider, settings)
  }

  /**
   * Provides the Xing provider.
   *
   * @param httpLayer           The HTTP layer implementation.
   * @param tokenSecretProvider The token secret provider implementation.
   * @return The Xing provider.
   */
  @Provides
  def provideXingProvider(httpLayer: HTTPLayer, tokenSecretProvider: OAuth1TokenSecretProvider, configuration: Configuration): XingProvider = {
    val settings = OAuth1Settings(
      requestTokenURL = configuration.getString("silhouette.xing.requestTokenURL").get,
      accessTokenURL = configuration.getString("silhouette.xing.accessTokenURL").get,
      authorizationURL = configuration.getString("silhouette.xing.authorizationURL").get,
      callbackURL = configuration.getString("silhouette.xing.callbackURL").get,
      consumerKey = configuration.getString("silhouette.xing.consumerKey").getOrElse(""),
      consumerSecret = configuration.getString("silhouette.xing.consumerSecret").getOrElse("")
    )

    new XingProvider(httpLayer, new PlayOAuth1Service(settings), tokenSecretProvider, settings)
  }

  //  /**
  //   * Provides the Yahoo provider.
  //   *
  //   * @param cacheLayer The cache layer implementation.
  //   * @param httpLayer The HTTP layer implementation.
  //   * @param client The OpenID client implementation.
  //   * @return The Yahoo provider.
  //   */
  //  @Provides
  //  def provideYahooProvider(cacheLayer: CacheLayer, httpLayer: HTTPLayer, client: OpenIdClient): YahooProvider = {
  //    import scala.collection.JavaConversions._
  //    val settings = OpenIDSettings(
  //      providerURL = configuration.getString("silhouette.yahoo.providerURL").get,
  //      callbackURL = configuration.getString("silhouette.yahoo.callbackURL").get,
  //      axRequired = Play.configuration.getObject("silhouette.yahoo.axRequired").map(_.mapValues(_.unwrapped().toString).toSeq).getOrElse(Seq()),
  //      axOptional = Play.configuration.getObject("silhouette.yahoo.axOptional").map(_.mapValues(_.unwrapped().toString).toSeq).getOrElse(Seq()),
  //      realm = configuration.getString("silhouette.yahoo.realm")
  //    )
  //
  //    new YahooProvider(httpLayer, new PlayOpenIDService(client, settings), settings)
  //  }
  /**
   * Provides the GitHub provider.
   *
   * @param httpLayer     The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @return The GitHub provider.
   */
  @Provides
  def provideGitHubProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, configuration: Configuration): GitHubProvider = {
    new GitHubProvider(httpLayer, stateProvider, OAuth2Settings(
      authorizationURL = configuration.getString("silhouette.github.authorizationUrl"),
      accessTokenURL = configuration.getString("silhouette.github.accessTokenUrl").get,
      redirectURL = configuration.getString("silhouette.github.redirectURL").get,
      clientID = configuration.getString("silhouette.github.clientId").getOrElse(""),
      clientSecret = configuration.getString("silhouette.github.clientSecret").getOrElse(""),
      scope = configuration.getString("silhouette.github.scope")
    ))
  }

  @Provides
  def provideOpenIdProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, configuration: Configuration): OidcProvider = {
    new OidcProvider(httpLayer, stateProvider, OAuth2Settings(
      authorizationURL = configuration.getString("silhouette.oidc.authorizationUrl"),
      accessTokenURL = configuration.getString("silhouette.oidc.accessTokenUrl").get,
      redirectURL = configuration.getString("silhouette.oidc.redirectURL").get,
      clientID = configuration.getString("silhouette.oidc.clientId").getOrElse(""),
      clientSecret = configuration.getString("silhouette.oidc.clientSecret").getOrElse(""),
      scope = configuration.getString("silhouette.oidc.scope"),
      customProperties = Map("userInfoUri" -> configuration.getString("silhouette.oidc.userInfoUri").get)
    ))
  }
}
