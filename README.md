Oidc Application Client Demo
=======================================================

## Objective

[![Fazer deploy no Heroku](https://www.herokucdn.com/deploy/button.png)](https://heroku.com/deploy)

(The "Build App" phase will take a few minutes)

## Usage

1. Start Talend IAM OIDC 

   * install gradle
   * customize ~/.gradle/gradle.properties as stated in https://in.talend.com/13995845
   * clone https://github.com/Talend/platform-services
   * cd platform-services/iam
   * gradle clean buildDocker
   * cd idp
   * docker-compose -f build/docker-compose.yml up
   
1. Register your OAuth Application Client in IAM

   * create an end-user (i.e. alice/alice), as in https://in.talend.com/14681120
   * create the OAuth clientId and clientSecret (as in https://in.talend.com/14681120)
   
     with the following info :
     
   | Name             | Value                       |
   | ---------------- | --------------------------- |
   | Application Name | Sample Play Application     |
   | Redirect URL     | http://localhost:9999/login |
   
   Copy the created clientId and clientSecret, you'll need them in the following step.
   
1. Configure sample-ac application

   
     conf/silhouette.conf with the following info :
     
   | Name             | Value                       |
   | ---------------- | --------------------------- |
   | silhouette.oidc.clientId | <value returned by previous step>     |
   | silhouette.oidc.clientSecret | <value returned by previous step>     |
      
1. Start sample-ac application on port 9000
1. Test it :
   
   * http://localhost:9000/
   
     You'll be redirected to IAM authentication page, authenticate with alice/alice.
     
     You'll be back to initial page showing alice infos (those where obtained uunder the hood
     by OidcProvider)
     
     This is because index page is secured with silhouette :
     
     ```scala
       def index = silhouette.SecuredAction.async { implicit request =>
         Future.successful(Ok(views.html.home(request.identity)))
       }
     ```
   * http://localhost:9000/admin
     
     This one checks that the authenticated user has oidc scope.
     
     Quite useless for now (I must merge a PR in IAM whichs maps syncope groups to OAuth
     scopes, and then it will be a bit more usefull).