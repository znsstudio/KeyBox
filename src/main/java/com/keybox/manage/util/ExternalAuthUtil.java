/**
 * Copyright 2015 Sean Kavanagh - sean.p.kavanagh6@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.keybox.manage.util;


import com.keybox.common.util.AppConfig;
import com.keybox.manage.db.AuthDB;
import com.keybox.manage.db.UserDB;
import com.keybox.manage.model.Auth;
import com.keybox.manage.model.User;
import org.apache.commons.lang3.StringUtils;

import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.security.Principal;
import java.sql.Connection;
import java.util.UUID;

import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.openstack.OSFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * External authentication utility for JAAS
 */
public class ExternalAuthUtil {

    private static Logger log = LoggerFactory.getLogger(ExternalAuthUtil.class);

    public static final boolean externalAuthEnabled = StringUtils.isNotEmpty(AppConfig.getProperty("jaasModule"));
    public static final boolean openStackAuthEnabled = StringUtils.isNotEmpty(OpenStackUtils.OPENSTACK_SERVER_API);
    private static final String JAAS_CONF = "jaas.conf";
    private static final String JAAS_MODULE = AppConfig.getProperty("jaasModule");


    static {
        if(externalAuthEnabled) {
            System.setProperty("java.security.auth.login.config", ExternalAuthUtil.class.getClassLoader().getResource(".").getPath() + JAAS_CONF);
        }
    }

    /**
     * external auth login method
     *
     * @param auth contains username and password
     * @return auth token if success
     */
    public static String login(final Auth auth) {

        String authToken = null;
        Connection con = null;
        if(auth != null && StringUtils.isNotEmpty(auth.getUsername()) && StringUtils.isNotEmpty(auth.getPassword())) {
            if (openStackAuthEnabled) {
                try {

                    OSClient os = OSFactory.builder()
                            .endpoint(OpenStackUtils.OPENSTACK_SERVER_API)
                            .credentials(auth.getUsername(), auth.getPassword())
                            .authenticate();

                    os.compute().keypairs().create("keybox@global_key", SSHUtil.getPublicKey());
                    if (StringUtils.isNotEmpty(os.getToken().getId())) {

                        con = DBUtils.getConn();
                        User user = AuthDB.getUserByUID(con, auth.getUsername());

                        if (user == null) {
                            user = new User();

                            user.setUserType(User.ADMINISTRATOR);
                            user.setUsername(auth.getUsername());

                            String[] name = os.getAccess().getUser().getName().split(" ");
                            if (name.length > 1) {
                                user.setFirstNm(name[0]);
                                user.setLastNm(name[name.length - 1]);
                            }
                            //set email
                            if (auth.getUsername().contains("@")) {
                                user.setEmail(auth.getUsername());
                            }

                            user.setId(UserDB.insertUser(con, user));
                        }

                        authToken = UUID.randomUUID().toString();
                        user.setAuthToken(authToken);
                        user.setAuthType(Auth.AUTH_EXTERNAL);
                        //set auth token
                        AuthDB.updateLogin(con, user);

                    }
                } catch (AuthenticationException ex) {
                    ex.printStackTrace();
                    //auth failed return empty
                    authToken = null;
                }

            } else if (externalAuthEnabled) {

                CallbackHandler handler = new CallbackHandler() {
                    @Override
                    public void handle(Callback[] callbacks) throws IOException,
                        UnsupportedCallbackException {
                        for (Callback callback : callbacks) {
                            if (callback instanceof NameCallback) {
                                ((NameCallback) callback).setName(auth.getUsername());
                            } else if (callback instanceof PasswordCallback) {
                                ((PasswordCallback) callback).setPassword(auth.getPassword().toCharArray());
                            }
                        }
                    }
                };

                try {
                    LoginContext loginContext = new LoginContext(JAAS_MODULE, handler);
                    //will throw exception if login fail
                    loginContext.login();
                    Subject subject = loginContext.getSubject();

                    con = DBUtils.getConn();
                    User user = AuthDB.getUserByUID(con, auth.getUsername());

                    if (user == null) {
                        user = new User();

                        user.setUserType(User.ADMINISTRATOR);
                        user.setUsername(auth.getUsername());

                        //if it looks like name is returned default it
                        for (Principal p : subject.getPrincipals()) {
                            if (p.getName().contains(" ")) {
                                String[] name = p.getName().split(" ");
                                if (name.length > 1) {
                                    user.setFirstNm(name[0]);
                                    user.setLastNm(name[name.length - 1]);
                                }
                            }
                        }
                        user.setId(UserDB.insertUser(con, user));
                    }

                } catch (LoginException e) {
                    //auth failed return empty
                    authToken = null;
               }
            }
        }
        DBUtils.closeConn(con);

        return authToken;
    }
}
