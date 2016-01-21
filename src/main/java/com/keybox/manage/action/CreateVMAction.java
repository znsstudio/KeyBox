/**
 * Copyright 2016 Sean Kavanagh - sean.p.kavanagh6@gmail.com
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
package com.keybox.manage.action;

import com.keybox.common.util.AuthUtil;
import com.keybox.manage.db.SystemDB;
import com.keybox.manage.db.UserDB;
import com.keybox.manage.model.Auth;
import com.keybox.manage.model.HostSystem;
import com.keybox.manage.model.OpenStackVM;
import com.keybox.manage.util.SSHUtil;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.compute.*;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.SecurityGroup;
import org.openstack4j.openstack.OSFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Action to create vm
 */
public class CreateVMAction extends ActionSupport implements ServletRequestAware, ServletResponseAware {

    private static Logger log = LoggerFactory.getLogger(CreateVMAction.class);
    HttpServletResponse servletResponse;
    HttpServletRequest servletRequest;
    OpenStackVM openStackVM = new OpenStackVM();
    Map<String,String> imageMap = new HashMap<>();
    Map<String,String> flavorMap = new HashMap<>();
    Map<String,String> networkMap = new HashMap<>();
    List<String> securityList = new ArrayList<>();

    @Action(value = "/manage/createVM",
            results = {
                    @Result(name = "success", location = "/manage/create_vm.jsp")
            }
    )
    public String createVM() {

        if(UserDB.getUser(AuthUtil.getUserId(servletRequest.getSession())).getAuthType().equals(Auth.AUTH_OPENSTACK)) {
            try {
                OSClient os = OSFactory.clientFromAccess(AuthUtil.getOpenStackAccess(servletRequest.getSession()));
                for (Flavor flavor : os.compute().flavors().list()) {
                    flavorMap.put(flavor.getId(), flavor.getName());
                }

                for (Image image : os.compute().images().list()) {
                    imageMap.put(image.getId(), image.getName());
                }

                for (Network network : os.networking().network().list()) {
                    networkMap.put(network.getId(), network.getName());
                }

                for (SecurityGroup group : os.networking().securitygroup().list()) {
                    securityList.add(group.getName());
                }
            } catch (Exception ex) {
                log.error(ex.toString(), ex);
            }
        } else {
            addActionError("Must be logged in with an OpenStack account. - <a href=\"/logout.action\">Logout</a> ");
        }

        return SUCCESS;
    }



    @Action(value = "/manage/saveVM",
            results = {
                    @Result(name = "input", location = "/manage/create_vm.jsp"),
                    @Result(name = "success", location = "/admin/menu.action", type = "redirect")
            }
    )
    public String saveVM() {

        Server server = null;
        OSClient os = null;
        try {
            os = OSFactory.clientFromAccess(AuthUtil.getOpenStackAccess(servletRequest.getSession()));

            if (os.compute().keypairs().get("keybox-global_key") != null) {
                os.compute().keypairs().delete("keybox-global_key");
            }

            os.compute().keypairs().create("keybox-global_key", SSHUtil.getPublicKey());

            ServerCreate serverCreate = os.compute().servers().serverBuilder()
                    .image(openStackVM.getImage())
                    .flavor(openStackVM.getFlavor())
                    .name(openStackVM.getName()).keypairName("keybox-global_key")
                    .addSecurityGroup(openStackVM.getSecurityGroup())
                    .build();
            server = os.compute().servers().boot(serverCreate);
            server = os.compute().servers().get(server.getId());
            System.out.println(ReflectionToStringBuilder.toString(server));

        } catch (Exception ex){
            log.error(ex.toString(), ex);
            addActionError(ex.getMessage());
        }

        if(hasActionErrors() || server == null) {
            if (os!=null) {
                //populate drop downs
                for (Flavor flavor : os.compute().flavors().list()) {
                    flavorMap.put(flavor.getId(), flavor.getName());
                }

                for (Image image : os.compute().images().list()) {
                    imageMap.put(image.getId(), image.getName());
                }

                /*for (Network network : os.networking().network().list()) {
                    networkMap.put(network.getId(), network.getName());
                }*/

                for (SecurityGroup group : os.networking().securitygroup().list()) {
                    securityList.add(group.getName());
                }
            }
            return INPUT;

        } else {
            HostSystem hostSystem = new HostSystem();
            hostSystem.setDisplayNm(server.getName());
            hostSystem.setHost(server.getHost());

            SystemDB.insertSystem(hostSystem);
            return SUCCESS;
        }
    }

    public HttpServletRequest getServletRequest() {
        return servletRequest;
    }

    @Override
    public void setServletRequest(HttpServletRequest servletRequest) {
        this.servletRequest = servletRequest;
    }

    public HttpServletResponse getServletResponse() {
        return servletResponse;
    }

    @Override
    public void setServletResponse(HttpServletResponse servletResponse) {
        this.servletResponse = servletResponse;
    }

    public OpenStackVM getOpenStackVM() {
        return openStackVM;
    }

    public void setOpenStackVM(OpenStackVM openStackVM) {
        this.openStackVM = openStackVM;
    }

    public Map<String, String> getImageMap() {
        return imageMap;
    }

    public void setImageMap(Map<String, String> imageMap) {
        this.imageMap = imageMap;
    }

    public Map<String, String> getFlavorMap() {
        return flavorMap;
    }

    public void setFlavorMap(Map<String, String> flavorMap) {
        this.flavorMap = flavorMap;
    }

    public List<String> getSecurityList() {
        return securityList;
    }

    public void setSecurityList(List<String> securityList) {
        this.securityList = securityList;
    }

    public Map<String, String> getNetworkMap() {
        return networkMap;
    }

    public void setNetworkMap(Map<String, String> networkMap) {
        this.networkMap = networkMap;
    }
}
