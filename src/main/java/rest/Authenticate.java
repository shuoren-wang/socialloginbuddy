package rest;

import manager.SessionHandler;
import manager.SocialServerRequestHandler;
import model.Session;
import model.Token;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import util.Constant;
import util.TestConstant;
import util.Util;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Date;


@Path("authenticate")
public class Authenticate {
    private static final Logger LOGGER = LogManager.getLogger(Authenticate.class);

    @GET
    @Path("/")
    public Response redirectToSocialServer(
            @QueryParam("state") String clientState,
            @QueryParam("redirect_uri") String clientRedirectUri
    ) throws Exception {
        if(StringUtils.isEmpty(clientState) || StringUtils.isEmpty(clientRedirectUri)) {
            //todo: try ping url, if timeout => throw error
            String errMessage = "Error: Missing state or redirect_uri";
            LOGGER.error(errMessage);
            return Response.status(HttpStatus.SC_FORBIDDEN).entity(errMessage).build();
        }

        String appState = SessionHandler.getCookieState(clientState);
        URI location = new URI(getAuthorizationUrl(TestConstant.GOOGLE_URL_AUTH, appState));
        SessionHandler.storeRedirectUri(appState, clientRedirectUri);

        return Response
                .temporaryRedirect(location)
                .cookie(new NewCookie(appState, clientState))
                .build();
    }

    private String getAuthorizationUrl(String url, String state){
        return  url + "?" +
                Constant.CLIENT_ID + "=" + TestConstant.CLIENT_ID_VALUE +
                "&" +
                Constant.RESPONSE_TYPE + "=" + Constant.CODE +
                "&" +
                Constant.SCOPE + "=" + TestConstant.SCOPE_VALUE +
                "&" +
                Constant.REDIRECT_URL + "=" + Util.REDIRECT_URL +
                "&" +
                Constant.STATE + "=" + state;
    }

    @GET
    @Path("redirect")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getUserInfo(
        @QueryParam("state") String appState,
        @QueryParam("code") String code,
        @Context HttpHeaders headers
    ) throws Exception {
        if(code == null || appState == null){
            String errMessage = "Error: code or state is null";
            LOGGER.error(errMessage);
            return Response.status(HttpStatus.SC_FORBIDDEN).entity(errMessage).build();
        }

        //todo put in one class
        Session session = SessionHandler.getSession(appState, headers);
        String clientState = session.getClientState();
        String clientRedirectUri = session.getClientRedirectUrl();

        Token token = SocialServerRequestHandler.getTokenFromSocialServer(TestConstant.GOOGLE_URL_TOKEN, code);
        String accessToken = token.getAccessToken();
        String idToken = token.getIdToken();
        String userInfo = SocialServerRequestHandler.getUserInfo(TestConstant.GOOGLE_URL_USERINFO, accessToken);

        SessionHandler.removeState(appState);
        LOGGER.info("Removed SocialLoginBuddy state [" + appState + "]");

        //todo change url
        URI location = new URI(Util.getTempClientRedirectUrl(clientRedirectUri, clientState, idToken, userInfo, accessToken));
        return Response
                .temporaryRedirect(location)
                .cookie(new NewCookie(headers.getCookies().get(appState), "deleted", 0, new Date(0), false, false))
                .build();
    }
}