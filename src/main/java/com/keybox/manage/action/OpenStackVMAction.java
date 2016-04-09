/**
 * Copyright 2016 Sean Kavanagh - sean.p.kavanagh6@gmail.com
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
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
import com.keybox.manage.model.SortedSet;
import com.keybox.manage.util.SSHUtil;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.compute.*;
import org.openstack4j.model.compute.FloatingIP;
import org.openstack4j.model.compute.ext.AvailabilityZone;
import org.openstack4j.model.network.*;
import org.openstack4j.openstack.OSFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;


/**
 * Action to create vm
 */
public class OpenStackVMAction extends ActionSupport implements ServletRequestAware, ServletResponseAware {

	private static String KEYBOX_KEY_NM = "keybox-global_key";
	private static Logger log = LoggerFactory.getLogger(OpenStackVMAction.class);

	HttpServletResponse servletResponse;
	HttpServletRequest servletRequest;
	SortedSet sortedSet = new SortedSet();
	OpenStackVM openStackVM = new OpenStackVM();
	HostSystem hostSystem = new HostSystem();
	Map<String, String> imageMap = new HashMap<>();
	Map<String, String> flavorMap = new HashMap<>();
	Map<String, String> networkMap = new HashMap<>();
	List<String> securityList = new ArrayList<>();


	@Action(value = "/manage/viewOpenstackVMs",
			results = {
					@Result(name = "success", location = "/manage/view_openstack_vms.jsp"),
			}
	)
	public String viewOpenstackVMs() {

		OSClient os = null;

		if (UserDB.getUser(AuthUtil.getUserId(servletRequest.getSession())).getAuthType().equals(Auth.AUTH_OPENSTACK)) {
			try {
				os = OSFactory.clientFromAccess(AuthUtil.getOpenStackAccess(servletRequest.getSession()));

				List<HostSystem> hostSystemList = new ArrayList<HostSystem>();
				for (Server server : os.compute().servers().list()) {
					HostSystem hostSystem = new HostSystem();
					hostSystem.setDisplayNm(server.getName());
					hostSystem.setOpenstackId(server.getId());
					hostSystem.setStatusCd(server.getVmState());

					for (String addrKey : server.getAddresses().getAddresses().keySet()) {
						for (Address address : server.getAddresses().getAddresses().get(addrKey)) {
							hostSystem.setHost(address.getAddr());
						}
					}

					if (KEYBOX_KEY_NM.equals(server.getKeyName())) {
						hostSystemList.add(hostSystem);
					}
				}

				SystemDB.saveOpenStackVms(hostSystemList);
				sortedSet = SystemDB.getSystemSet(sortedSet, true);

				//populate drop downs
				populateDropDowns(os);

			} catch (Exception ex) {
				log.error(ex.toString(), ex);
			}
		} else {
			addActionError("Must be logged in with an OpenStack account. - <a href=\"/logout.action\">Logout</a> ");
		}
		return SUCCESS;
	}


	@Action(value = "/manage/saveOpenStackVM",
			results = {
					@Result(name = "input", location = "/manage/create_openstack_vm.jsp"),
					@Result(name = "success", location = "/manage/viewOpenstackVMs.action", type = "redirect")
			}
	)
	public String saveOpenStackVM() {

		Server server = null;
		OSClient os = null;
		try {
			os = OSFactory.clientFromAccess(AuthUtil.getOpenStackAccess(servletRequest.getSession()));

			if (os.compute().keypairs().get(KEYBOX_KEY_NM) != null) {
				os.compute().keypairs().delete(KEYBOX_KEY_NM);
			}

			os.compute().keypairs().create(KEYBOX_KEY_NM, SSHUtil.getPublicKey());

			ServerCreate serverCreate = os.compute().servers().serverBuilder()
					.image(openStackVM.getImage())
					.flavor(openStackVM.getFlavor())
					.name(openStackVM.getName()).keypairName(KEYBOX_KEY_NM)
					.addSecurityGroup(openStackVM.getSecurityGroup())
					.build();
			server = os.compute().servers().boot(serverCreate);

			server = os.compute().servers().get(server.getId());
			for (String addrKey : server.getAddresses().getAddresses().keySet()) {
				for (Address address : server.getAddresses().getAddresses().get(addrKey)) {
					hostSystem.setHost(address.getAddr());
				}
			}

			String networkId = openStackVM.getNetwork();
			FloatingIP ip = null;
			if (StringUtils.isNotEmpty(networkId)) {
				Network network = os.networking().network().get(networkId);
				if (network != null) {
					ip = os.compute().floatingIps().allocateIP(network.getName());
					hostSystem.setHost(ip.getFloatingIpAddress());
				}
			}

			hostSystem.setDisplayNm(server.getName());
			hostSystem.setOpenstackId(server.getId());
			SystemDB.insertSystem(hostSystem);

			server = os.compute().servers().get(server.getId());
			if(ip!=null) {
				Thread.sleep(10000);

				ActionResponse r = os.compute().floatingIps().addFloatingIP(server, ip.getFloatingIpAddress());
				//System.out.println(r.isSuccess());
				//System.out.println(r.getCode());
				//System.out.println(r.getFault());
			}

		} catch (Exception ex) {
			log.error(ex.toString(), ex);
			addActionError(ex.getMessage());
		}

		if (hasActionErrors() || server == null) {
			if (os != null) {
				//populate drop downs
				populateDropDowns(os);
			}
			return INPUT;

		} else {
			return SUCCESS;
		}
	}

	@Action(value = "/manage/deleteOpenstackVM",
			results = {
					@Result(name = "success", location = "/manage/view_openstack_vms.jsp"),
			}
	)
	public String deleteOpenstackVM() {

		OSClient os = null;

		if (UserDB.getUser(AuthUtil.getUserId(servletRequest.getSession())).getAuthType().equals(Auth.AUTH_OPENSTACK)) {
			try {
				if (hostSystem.getId() != null) {
					hostSystem = SystemDB.getSystem(hostSystem.getId());
					SystemDB.deleteSystem(hostSystem.getId());

					if(hostSystem.getOpenstackId() != null) {
						os = OSFactory.clientFromAccess(AuthUtil.getOpenStackAccess(servletRequest.getSession()));
						os.compute().servers().delete(hostSystem.getOpenstackId());
					}
				}
				sortedSet = SystemDB.getSystemSet(sortedSet, true);

				//populate drop downs
				populateDropDowns(os);

			} catch (Exception ex) {
				log.error(ex.toString(), ex);
			}
		} else {
			addActionError("Must be logged in with an OpenStack account. - <a href=\"/logout.action\">Logout</a> ");
		}
		return SUCCESS;
	}

	/**
	 * populate drop downs for adding VMs
	 *
	 * @param os OpenStack client
	 */
	private void populateDropDowns(OSClient os) {

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

	public SortedSet getSortedSet() {
		return sortedSet;
	}

	public void setSortedSet(SortedSet sortedSet) {
		this.sortedSet = sortedSet;
	}

	public HostSystem getHostSystem() {
		return hostSystem;
	}

	public void setHostSystem(HostSystem hostSystem) {
		this.hostSystem = hostSystem;
	}
}
