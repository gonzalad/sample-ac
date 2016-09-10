package org.talend.play.silhouette.client

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import play.api.http.HeaderNames
import play.api.libs.ws.{ WSRequest, WSRequestExecutor, WSRequestFilter, WSResponse }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * @author agonzalez
 */
class OAuthBearerTokenRequestFilter(val loginInfo: Option[LoginInfo], implicit val authInfoRepository: AuthInfoRepository, implicit val ec: ExecutionContext) extends WSRequestFilter {

  override def apply(next: WSRequestExecutor): WSRequestExecutor = {
    new WSRequestExecutor {
      override def execute(request: WSRequest): Future[WSResponse] = {

        if (loginInfo.isDefined) {

          authInfoRepository.find[OAuth2Info](loginInfo.get).flatMap { authInfo =>
            next.execute(request.withHeaders((HeaderNames.AUTHORIZATION -> s"Bearer ${authInfo.get.accessToken}")))
          }
          //val  =
        } else {
          next.execute(request)
        }
      }
    }
  }
}
//class OAuthBearerTokenRequestFilter(val accessToken: Option[String]) extends WSRequestFilter {
//
//  override def apply(next: WSRequestExecutor): WSRequestExecutor = {
//    new WSRequestExecutor {
//      override def execute(request: WSRequest): Future[WSResponse] = {
//        if (accessToken.isDefined) {
//          next.execute(request.withHeaders((HeaderNames.AUTHORIZATION -> s"Bearer ${accessToken.get}")))
//        } else {
//          next.execute(request)
//        }
//      }
//    }
//  }
//}
