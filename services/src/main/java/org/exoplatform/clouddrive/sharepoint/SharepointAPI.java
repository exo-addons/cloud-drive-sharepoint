/*
 * Copyright (C) 2003-2016 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.clouddrive.sharepoint;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.core.MediaType;

import org.apache.chemistry.opencmis.client.api.ChangeEvent;
import org.apache.chemistry.opencmis.client.api.ChangeEvents;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.ObjectFactory;
import org.apache.chemistry.opencmis.client.api.Property;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ObjectDataImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertiesImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertyIdImpl;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import org.exoplatform.clouddrive.CloudDriveAccessException;
import org.exoplatform.clouddrive.CloudDriveException;
import org.exoplatform.clouddrive.cmis.CMISAPI;
import org.exoplatform.clouddrive.cmis.CMISException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.ws.frameworks.json.JsonParser;
import org.exoplatform.ws.frameworks.json.impl.JsonDefaultHandler;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.exoplatform.ws.frameworks.json.impl.JsonParserImpl;
import org.exoplatform.ws.frameworks.json.value.JsonValue;

/**
 * All calls to Sharepoint API here.
 */
public class SharepointAPI extends CMISAPI {

  /** The Constant LOG. */
  protected static final Log    LOG                     = ExoLogger.getLogger(SharepointAPI.class);

  /** The Constant MAJOR_VERSION_ID_SUFFIX. */
  protected static final String MAJOR_VERSION_ID_SUFFIX = "-512";

  /** The Constant REST_CONTEXTINFO. */
  protected static final String REST_CONTEXTINFO        = "%s/_api/contextinfo";

  /** The Constant REST_CURRENTUSER. */
  protected static final String REST_CURRENTUSER        = "%s/_api/Web/CurrentUser";

  /** The Constant REST_SITETITLE. */
  protected static final String REST_SITETITLE          = "%s/_api/Web/Title";

  /**
   * The Class ChangesIterator.
   */
  protected class ChangesIterator extends org.exoplatform.clouddrive.cmis.CMISAPI.ChangesIterator {

    /**
     * Instantiates a new changes iterator.
     *
     * @param startChangeToken the start change token
     * @throws CMISException the CMIS exception
     * @throws CloudDriveAccessException the cloud drive access exception
     */
    ChangesIterator(ChangeToken startChangeToken) throws CMISException, CloudDriveAccessException {
      super(startChangeToken);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Iterator<ChangeEvent> nextChunk() throws CMISException, CloudDriveAccessException {
      final ChangeToken startChangeToken = changeToken; // if it's firstRun then
                                                        // it is start token

      Iterator<ChangeEvent> nextChunk = super.nextChunk();

      if (!nextChunk.hasNext() && firstRun) {
        // if first run return empty changes:
        // ensure that startChangeToken isn't of DELETED and thus not existing
        // event in SP's Changes Log
        // try changes with previous token in the full log (null) - this request
        // can be long (as will read all
        // available log).
        ChangeEvents allEvents = session(true).getContentChanges(null, true, Integer.MAX_VALUE);
        List<ChangeEvent> allChanges = allEvents.getChangeEvents();
        if (allChanges.size() > 0) {
          for (int i = allChanges.size() - 1; i >= 0; i--) {
            ChangeToken et = readToken(allChanges.get(i));
            if (et.isBefore(startChangeToken)) {
              this.changeToken = et;
              nextChunk = super.nextChunk();
              break;
            }
          }
        }
      }

      return nextChunk;
    }
  }

  /**
   * The Class SPChangeToken.
   */
  protected class SPChangeToken extends ChangeToken {

    /** The timestamp. */
    protected final long timestamp;

    /** The index. */
    protected final long index;

    /**
     * Instantiates a new SP change token.
     *
     * @param token the token
     * @throws CMISException the CMIS exception
     */
    protected SPChangeToken(String token) throws CMISException {
      super(token);

      String[] ta = this.token.split(";");
      if (ta.length >= 6) {
        try {
          this.timestamp = Long.valueOf(ta[3]);
        } catch (NumberFormatException e) {
          throw new CMISException("Cannot parse change token timestamp: " + this.token, e);
        }
        try {
          this.index = Long.valueOf(ta[4]);
        } catch (NumberFormatException e) {
          throw new CMISException("Cannot parse change token index: " + this.token, e);
        }
      } else {
        this.timestamp = 0;
        this.index = 0;
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(ChangeToken other) {
      if (!isEmpty() && !other.isEmpty() && other instanceof SPChangeToken) {
        SPChangeToken otherSP = (SPChangeToken) other;
        return this.getIndex() == otherSP.getIndex() && this.getTimestamp() == otherSP.getTimestamp();
      }
      return false;
    }

    /**
     * Equals.
     *
     * @param other the other
     * @return true, if successful
     */
    public boolean equals(SPChangeToken other) {
      if (!isEmpty() && !other.isEmpty()) {
        return this.getIndex() == other.getIndex() && this.getTimestamp() == other.getTimestamp();
      }
      return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAfter(ChangeToken other) {
      if (other instanceof SPChangeToken) {
        SPChangeToken otherSP = (SPChangeToken) other;
        // TODO return this.getTimestamp() >= otherSP.getTimestamp();
        return this.getIndex() >= otherSP.getIndex();
      }
      return super.isAfter(other); // compareTo() > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBefore(ChangeToken other) {
      if (other instanceof SPChangeToken) {
        SPChangeToken otherSP = (SPChangeToken) other;
        // TODO return this.getTimestamp() <= otherSP.getTimestamp();
        return this.getIndex() <= otherSP.getIndex();
      }
      return super.isBefore(other); // compareTo() < 0;
    }

    /**
     * Gets the timestamp.
     *
     * @return the timestamp
     */
    public long getTimestamp() {
      return timestamp;
    }

    /**
     * Gets the index.
     *
     * @return the index
     */
    public long getIndex() {
      return index;
    }
  }

  /**
   * The Class RESTClient.
   */
  protected class RESTClient {

    /** The http client. */
    protected final HttpClient  httpClient;

    /** The context. */
    protected final HttpContext context;

    /**
     * Instantiates a new REST client.
     */
    protected RESTClient() {
      super();

      SchemeRegistry schemeReg = new SchemeRegistry();
      schemeReg.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
      SSLSocketFactory socketFactory;
      try {
        SSLContext sslContext = SSLContext.getInstance(SSLSocketFactory.TLS);
        KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmfactory.init(null, null);
        KeyManager[] keymanagers = kmfactory.getKeyManagers();
        TrustManagerFactory tmfactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmfactory.init((KeyStore) null);
        TrustManager[] trustmanagers = tmfactory.getTrustManagers();
        sslContext.init(keymanagers, trustmanagers, null);
        socketFactory = new SSLSocketFactory(sslContext, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
      } catch (Exception ex) {
        throw new IllegalStateException("Failure initializing default SSL context for SharePoint REST client", ex);
      }
      schemeReg.register(new Scheme("https", 443, socketFactory));

      ThreadSafeClientConnManager connectionManager = new ThreadSafeClientConnManager(schemeReg);
      // XXX 2 recommended by RFC 2616 sec 8.1.4, we make it bigger for quicker
      // // upload
      connectionManager.setDefaultMaxPerRoute(4);
      // 20 by default, we twice it also
      connectionManager.setMaxTotal(40);

      DefaultHttpClient client = new DefaultHttpClient(connectionManager);

      // TODO force BASIC only?
      // List<String> authpref = new ArrayList<String>();
      // authpref.add(AuthPolicy.BASIC);
      // client.getParams().setParameter(AuthPNames.PROXY_AUTH_PREF, authpref);

      client.getCredentialsProvider().setCredentials(new AuthScope(siteHost.getHostName(), siteHost.getPort()),
                                                     new UsernamePasswordCredentials(userName, password));

      this.httpClient = client;

      // Create AuthCache instance
      AuthCache authCache = new BasicAuthCache();
      // Generate BASIC scheme object and add it to the local auth cache
      BasicScheme basicAuth = new BasicScheme();
      authCache.put(siteHost, basicAuth);

      // Add AuthCache to the execution context
      this.context = new BasicHttpContext();
      this.context.setAttribute(ClientContext.AUTH_CACHE, authCache);
    }

    /**
     * Execute.
     *
     * @param request the request
     * @return the http response
     * @throws ClientProtocolException the client protocol exception
     * @throws IOException Signals that an I/O exception has occurred.
     */
    HttpResponse execute(HttpUriRequest request) throws ClientProtocolException, IOException {
      return this.httpClient.execute(request, context);
    }

    /**
     * Gets the.
     *
     * @param uri the uri
     * @param opName the op name
     * @return the http response
     * @throws ClientProtocolException the client protocol exception
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws IllegalStateException the illegal state exception
     * @throws SharepointServiceNotFound the sharepoint service not found
     * @throws SharepointException the sharepoint exception
     */
    HttpResponse get(String uri, String opName) throws ClientProtocolException,
                                                IOException,
                                                IllegalStateException,
                                                SharepointServiceNotFound,
                                                SharepointException {
      HttpGet httpget = new HttpGet(uri);
      httpget.setHeader("accept", "application/json;odata=verbose");
      HttpResponse resp = execute(httpget);
      checkError(resp, opName);
      return resp;
    }

    /**
     * Check error.
     *
     * @param resp the resp
     * @param opName the op name
     * @throws IllegalStateException the illegal state exception
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws SharepointException the sharepoint exception
     * @throws SharepointServiceNotFound the sharepoint service not found
     */
    void checkError(HttpResponse resp, String opName) throws IllegalStateException,
                                                      IOException,
                                                      SharepointException,
                                                      SharepointServiceNotFound {
      int statusCode = resp.getStatusLine().getStatusCode();
      switch (statusCode) {
      case HttpStatus.SC_NOT_FOUND:
        throw new SharepointServiceNotFound("SharePoint service " + opName + " not found");
      case HttpStatus.SC_FORBIDDEN:
        throw new SharepointException("Access to SharePoint " + opName + " forbidden. " + readText(resp));
      default:
        break;
      }
    }
  }

  /**
   * SharePoint user data.
   */
  public class User {

    /** The id. */
    final String  id;

    /** The login name. */
    final String  loginName;

    /** The title. */
    final String  title;

    /** The email. */
    final String  email;

    /** The is site admin. */
    final boolean isSiteAdmin;

    /**
     * Instantiates a new user.
     *
     * @param id the id
     * @param loginName the login name
     * @param title the title
     * @param email the email
     * @param isSiteAdmin the is site admin
     */
    User(String id, String loginName, String title, String email, boolean isSiteAdmin) {
      super();
      this.id = id;
      this.loginName = loginName;
      this.title = title;
      this.email = email;
      this.isSiteAdmin = isSiteAdmin;
    }

    /**
     * Gets the id.
     *
     * @return the id
     */
    public String getId() {
      return id;
    }

    /**
     * Gets the login name.
     *
     * @return the loginName
     */
    public String getLoginName() {
      return loginName;
    }

    /**
     * Gets the title.
     *
     * @return the title
     */
    public String getTitle() {
      return title;
    }

    /**
     * Gets the email.
     *
     * @return the email
     */
    public String getEmail() {
      return email;
    }

    /**
     * Gets the checks if is site admin.
     *
     * @return the isSiteAdmin
     */
    public boolean getIsSiteAdmin() {
      return isSiteAdmin;
    }

  }

  /**
   * Access parameter for HTTP client to native SP APIes.
   */
  protected final String     siteURL, userName, password;

  /** The site host. */
  protected final HttpHost   siteHost;

  /**
   * HTTP client to native SP APIes.
   */
  protected final RESTClient nativeClient;

  /**
   * Current SharePoint user.
   */
  protected final User       siteUser;

  /**
   * SharePoint site title.
   */
  protected final String     siteTitle;

  /**
   * Create API from user credentials.
   *
   * @param serviceURL {@link String}
   * @param userName {@link String}
   * @param password {@link String}
   * @throws CMISException the CMIS exception
   * @throws CloudDriveException the cloud drive exception
   */
  protected SharepointAPI(String serviceURL, String userName, String password) throws CMISException, CloudDriveException {
    super(serviceURL, userName, password);

    this.userName = userName;
    this.password = password;

    // extract site host URL from CMIS AtomPub service
    try {
      URI uri = new URI(serviceURL);
      String scheme = uri.getScheme();
      String hostname = uri.getHost();
      int port = uri.getPort();
      this.siteHost = new HttpHost(hostname, port, scheme);
      this.siteURL = this.siteHost.toURI().toString();
    } catch (URISyntaxException e) {
      throw new CMISException("Error parsing service URL", e);
    }

    this.nativeClient = new RESTClient();

    this.siteTitle = readSiteTitle();
    this.siteUser = readSiteUser();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected ChangesIterator getChanges(ChangeToken changeToken) throws CMISException, CloudDriveAccessException {
    return new ChangesIterator(changeToken);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getUserTitle() {
    if (siteUser != null) {
      return siteUser.getTitle();
    }
    return super.getUserTitle();
  }

  // ******* SharePoint specific *********

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getUser() {
    return super.getUser();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getPassword() {
    return super.getPassword();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getServiceURL() {
    return super.getServiceURL();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getRepositoryId() {
    return super.getRepositoryId();
  }

  /**
   * Gets the current SharePoint user.
   *
   * @return the siteUser
   */
  public User getSiteUser() {
    return siteUser;
  }

  /**
   * Gets the sharePoint site title.
   *
   * @return the siteTitle
   */
  public String getSiteTitle() {
    return siteTitle;
  }

  /**
   * Gets the access parameter for HTTP client to native SP APIes.
   *
   * @return the siteURL
   */
  public String getSiteURL() {
    return siteURL;
  }

  /**
   * Read site name from ShapePoint API.
   *
   * @return the string
   * @throws SharepointServiceNotFound the sharepoint service not found
   * @throws SharepointException the sharepoint exception
   * @throws CloudDriveAccessException the cloud drive access exception
   */
  protected String readSiteTitle() throws SharepointServiceNotFound, SharepointException, CloudDriveAccessException {
    String reqURI = String.format(REST_SITETITLE, siteURL);
    try {
      HttpResponse resp = nativeClient.get(reqURI, "Web Site title");
      StatusLine status = resp.getStatusLine();
      if (status != null) {
        if (status.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
          String message = status.getReasonPhrase();
          if (message == null || message.length() == 0) {
            message = resp.getEntity().toString();
          }
          try {
            message += ". " + readError(readJson(resp));
          } catch (SharepointException e) {
            // w/o entity message
          }
          throw new CloudDriveAccessException("Unauthorized for reading site title. " + message);
        }
        JsonValue json = readJson(resp);
        JsonValue dv = json.getElement("d");
        if (dv != null && !dv.isNull()) {// TODO dv null and resp is 401 - run
                                         // proper ex
          JsonValue tv = dv.getElement("Title");
          if (tv != null) {
            return tv.isNull() ? tv.toString() : tv.getStringValue();
          } else {
            throw new SharepointException("Title request doesn't return a title value");
          }
        } else {
          throw new SharepointException("Title request doesn't return an expected body (d)");
        }
      } else {
        throw new SharepointException("Cannot read site title: response without status");
      }
    } catch (ClientProtocolException e) {
      throw new SharepointException("Protocol error reading site title", e);
    } catch (IOException e) {
      throw new SharepointException("Error reading site title (" + e.getMessage() + ")", e);
    } catch (IllegalStateException e) {
      throw new SharepointException("Error fetching site title content (" + e.getMessage() + ")", e);
    } catch (JsonException e) {
      throw new SharepointException("Error reading site title content (" + e.getMessage() + ")", e);
    }
  }

  /**
   * Read current user name from ShapePoint API.
   *
   * @return the user
   * @throws SharepointException the sharepoint exception
   * @throws SharepointServiceNotFound the sharepoint service not found
   * @throws CloudDriveAccessException the cloud drive access exception
   */
  protected User readSiteUser() throws SharepointException, SharepointServiceNotFound, CloudDriveAccessException {
    String reqURI = String.format(REST_CURRENTUSER, siteURL);
    try {
      HttpResponse resp = nativeClient.get(reqURI, "Web Site current user");
      StatusLine status = resp.getStatusLine();
      if (status != null) {
        if (status.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
          String message = status.getReasonPhrase();
          if (message == null || message.length() == 0) {
            message = resp.getEntity().toString();
          }
          try {
            message += ". " + readError(readJson(resp));
          } catch (SharepointException e) {
            // w/o entity message
          }
          throw new CloudDriveAccessException("Unauthorized for reading current user: " + message);
        }
        JsonValue json = readJson(resp);
        JsonValue dv = json.getElement("d");
        if (dv != null && !dv.isNull()) {
          String id, loginName, title, email;
          boolean isAdmin;

          JsonValue v = dv.getElement("Id");
          if (v != null && !v.isNull()) {
            id = v.getStringValue();
          } else {
            throw new SharepointException("Current user request doesn't return Id");
          }

          v = dv.getElement("LoginName");
          if (v != null && !v.isNull()) {
            loginName = v.getStringValue();
          } else {
            throw new SharepointException("Current user request doesn't return LoginName");
          }

          v = dv.getElement("Title");
          if (v != null && !v.isNull()) {
            title = v.getStringValue();
          } else {
            throw new SharepointException("Current user request doesn't return Title");
          }

          v = dv.getElement("Email");
          email = v == null || v.isNull() ? "" : v.getStringValue();

          v = dv.getElement("IsSiteAdmin");
          isAdmin = v == null || v.isNull() ? false : v.getBooleanValue();

          return new User(id, loginName, title, email, isAdmin);
        } else {
          throw new SharepointException("Current user request doesn't return an expected body (d)");
        }
      } else {
        throw new SharepointException("Cannot read current user: response without status");
      }
    } catch (ClientProtocolException e) {
      throw new SharepointException("Protocol error reading curent user", e);
    } catch (IOException e) {
      throw new SharepointException("Error reading curent user (" + e.getMessage() + ")", e);
    } catch (IllegalStateException e) {
      throw new SharepointException("Error fetching curent user (" + e.getMessage() + ")", e);
    } catch (JsonException e) {
      throw new SharepointException("Error reading curent user data (" + e.getMessage() + ")", e);
    }
  }

  /**
   * Read json.
   *
   * @param resp the resp
   * @return the json value
   * @throws JsonException the json exception
   * @throws IllegalStateException the illegal state exception
   * @throws IOException Signals that an I/O exception has occurred.
   * @throws SharepointException the sharepoint exception
   */
  protected JsonValue readJson(HttpResponse resp) throws JsonException, IllegalStateException, IOException, SharepointException {
    HttpEntity entity = resp.getEntity();
    Header contentType = entity.getContentType();
    if (contentType != null && contentType.getValue() != null && contentType.getValue().startsWith(MediaType.APPLICATION_JSON)) {
      InputStream content = entity.getContent();
      JsonParser jsonParser = new JsonParserImpl();
      JsonDefaultHandler handler = new JsonDefaultHandler();
      jsonParser.parse(new InputStreamReader(content), handler);
      return handler.getJsonObject();
    } else {
      throw new SharepointException("Not JSON content");
    }
  }

  /**
   * Read text.
   *
   * @param resp the resp
   * @return the string
   * @throws IllegalStateException the illegal state exception
   * @throws IOException Signals that an I/O exception has occurred.
   */
  protected String readText(HttpResponse resp) throws IllegalStateException, IOException {
    HttpEntity entity = resp.getEntity();
    Header contentType = entity.getContentType();
    StringBuilder text = new StringBuilder();
    if (contentType == null || contentType.getValue() == null || contentType.getValue().startsWith(MediaType.TEXT_PLAIN)) {
      // read as text if type unknown or plain/text
      Charset charset;
      try {
        String contentCharset = contentType.getElements()[0].getParameterByName("charset").getValue();
        charset = Charset.forName(contentCharset);
      } catch (Throwable e) {
        LOG.warn("Error reading SharePoint response content type charset: " + e.getMessage()
            + ". JVM default chartset will be used.");
        charset = Charset.defaultCharset();
      }
      InputStream content = entity.getContent();
      try {
        byte[] buff = new byte[1024];
        int r = 0;
        while ((r = content.read(buff)) != -1) {
          text.append(new String(buff, 0, r, charset));
        }
      } finally {
        content.close();
      }
    }
    return text.toString();
  }

  /**
   * Read error.
   *
   * @param json the json
   * @return the string
   */
  protected String readError(JsonValue json) {
    JsonValue error = json.getElement("error");
    if (error != null) {
      JsonValue errorMsg = error.getElement("message");
      if (errorMsg != null) {
        JsonValue emv = errorMsg.getElement("value");
        if (errorMsg != null) {
          return emv.getStringValue();
        }
      }
      return error.getStringValue();
    }
    return "".intern();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected ChangeToken readToken(String tokenString) throws CMISException {
    return new SPChangeToken(tokenString);
  }

  /**
   * {@inheritDoc}
   */
  protected CmisObject rename(String newName, CmisObject obj, Session session) {
    // XXX With SP we cannot just rename using existing object instance as the
    // server will return
    // updateConflict telling it's not current object.
    // This is probably due to the change token in existing object. Thus we
    // create a new object instance and
    // try rename via it.

    // Other ways to try:
    // * use native SP API
    // * use low level API (CMIS bindings)

    ObjectDataImpl data = new ObjectDataImpl();

    // copy major properties (id, types) fron original object
    PropertiesImpl props = new PropertiesImpl();

    ObjectFactory factory = session.getObjectFactory();

    // TODO cleanup?
    // for (Property<?> prop : obj.getProperties()) {
    // PropertyData<?> pdata;
    // if (PropertyIds.OBJECT_TYPE_ID.equals(prop.getId())) {
    // Property<String> objTypeId = obj.getProperty(PropertyIds.OBJECT_TYPE_ID);
    // pdata = new PropertyIdImpl(PropertyIds.OBJECT_TYPE_ID,
    // objTypeId.getValues());
    // } else {
    // @SuppressWarnings("unchecked")
    // // XXX nasty cast
    // Property<Object> p = (Property<Object>) prop;
    // pdata = factory.createProperty(p.getDefinition(), p.getValues());
    // }
    // props.addProperty(pdata);
    // }

    Property<String> objectId = obj.getProperty(PropertyIds.OBJECT_ID);
    props.addProperty(factory.createProperty(objectId.getDefinition(), objectId.getValues()));

    Property<String> name = obj.getProperty(PropertyIds.NAME);
    props.addProperty(factory.createProperty(name.getDefinition(), name.getValues()));

    Property<String> baseTypeId = obj.getProperty(PropertyIds.BASE_TYPE_ID);
    props.addProperty(factory.createProperty(baseTypeId.getDefinition(), baseTypeId.getValues()));

    Property<String> objTypeId = obj.getProperty(PropertyIds.OBJECT_TYPE_ID);
    PropertyIdImpl objTypeIdProp = new PropertyIdImpl(PropertyIds.OBJECT_TYPE_ID, objTypeId.getValues());
    props.addProperty(objTypeIdProp);

    // set props
    data.setProperties(props);

    data.setAcl(obj.getAcl());
    data.setAllowableActions(obj.getAllowableActions());

    // new object instance
    CmisObject newObj = factory.convertObject(data, fileContext);

    // and use super's logic with it
    CmisObject renamed = super.rename(newName, newObj, session);
    if (renamed == newObj) {
      // return existing object refreshed
      obj.refresh();
      return obj;
    } else if (renamed == obj) {
      // return existing object, it is already refreshed in the super.rename()
      return obj;
    } else {
      // return renamed object
      return renamed;
    }
  }
}
