/**
 * Copyright 2013 Sean Kavanagh - sean.p.kavanagh6@gmail.com
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
package com.keybox.manage.db;

import com.keybox.manage.model.HostSystem;
import com.keybox.manage.model.SortedSet;
import com.keybox.manage.util.DBUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * DAO used to manage systems
 */
public class SystemDB {

    private static Logger log = LoggerFactory.getLogger(SystemDB.class);

	public static final String FILTER_BY_PROFILE_ID = "profile_id";

	public static final String SORT_BY_NAME = "display_nm";
	public static final String SORT_BY_USER = "user";
	public static final String SORT_BY_HOST = "host";
	public static final String SORT_BY_STATUS = "status_cd";


	/**
	 * method to do order by based on the sorted set object for systems for user
	 *
	 * @param sortedSet sorted set object
	 * @param userId    user id
	 * @return sortedSet with list of host systems
	 */
	public static SortedSet getUserSystemSet(SortedSet sortedSet, Long userId) {
		List<HostSystem> hostSystemList = new ArrayList<HostSystem>();

		String orderBy = "";
		if (sortedSet.getOrderByField() != null && !sortedSet.getOrderByField().trim().equals("")) {
			orderBy = " order by " + sortedSet.getOrderByField() + " " + sortedSet.getOrderByDirection();
		}
		String sql = "select * from system where id in (select distinct system_id from  system_map m, user_map um where m.profile_id=um.profile_id and um.user_id=? ";
		//if profile id exists add to statement
		sql += StringUtils.isNotEmpty(sortedSet.getFilterMap().get(FILTER_BY_PROFILE_ID)) ? " and um.profile_id=? " : "";
		sql += ") " + orderBy;

		//get user for auth token
		Connection con = null;
		try {
			con = DBUtils.getConn();
			PreparedStatement stmt = con.prepareStatement(sql);
			stmt.setLong(1, userId);
			//filter by profile id if exists
			if (StringUtils.isNotEmpty(sortedSet.getFilterMap().get(FILTER_BY_PROFILE_ID))) {
				stmt.setLong(2, Long.valueOf(sortedSet.getFilterMap().get(FILTER_BY_PROFILE_ID)));
			}

			ResultSet rs = stmt.executeQuery();

			while (rs.next()) {
				HostSystem hostSystem = new HostSystem();
				hostSystem.setId(rs.getLong("id"));
				hostSystem.setDisplayNm(rs.getString("display_nm"));
				hostSystem.setUser(rs.getString("user"));
				hostSystem.setHost(rs.getString("host"));
				hostSystem.setPort(rs.getInt("port"));
				hostSystem.setAuthorizedKeys(rs.getString("authorized_keys"));
				hostSystem.setStatusCd(rs.getString("status_cd"));
				hostSystem.setOpenstackId(rs.getString("openstack_id"));
				hostSystemList.add(hostSystem);
			}
			DBUtils.closeRs(rs);
			DBUtils.closeStmt(stmt);

		} catch (Exception e) {
			log.error(e.toString(), e);
		}
		DBUtils.closeConn(con);


		sortedSet.setItemList(hostSystemList);
		return sortedSet;

	}

	/**
	 * method to do order by based on the sorted set object for systems
	 *
	 * @param sortedSet sorted set object
	 * @return sortedSet with list of host systems
	 */
	public static SortedSet getSystemSet(SortedSet sortedSet) {
		return getSystemSet(sortedSet, null);
	}

	/**
	 * method to do order by based on the sorted set object for systems
	 *
	 * @param sortedSet sorted set object
	 * @param showOpenstack boolean to only show openstack
	 * @return sortedSet with list of host systems
	 */
	public static SortedSet getSystemSet(SortedSet sortedSet, Boolean showOpenstack) {
		List<HostSystem> hostSystemList = new ArrayList<HostSystem>();

		String orderBy = "";
		if (sortedSet.getOrderByField() != null && !sortedSet.getOrderByField().trim().equals("")) {
			orderBy = " order by " + sortedSet.getOrderByField() + " " + sortedSet.getOrderByDirection();
		}
		String sql = "select * from  system s ";
		//if profile id exists add to statement
		sql += StringUtils.isNotEmpty(sortedSet.getFilterMap().get(FILTER_BY_PROFILE_ID)) ? ",system_map m where s.id=m.system_id and m.profile_id=? and " : " where 1=1 ";
		if(showOpenstack!=null) {
			sql += showOpenstack ? " and openstack_id is not null" : " and openstack_id is null";
		}
		sql += orderBy;

		Connection con = null;
		try {
			con = DBUtils.getConn();
			PreparedStatement stmt = con.prepareStatement(sql);
			if (StringUtils.isNotEmpty(sortedSet.getFilterMap().get(FILTER_BY_PROFILE_ID))) {
				stmt.setLong(1, Long.valueOf(sortedSet.getFilterMap().get(FILTER_BY_PROFILE_ID)));
			}
			ResultSet rs = stmt.executeQuery();

			while (rs.next()) {
				HostSystem hostSystem = new HostSystem();
				hostSystem.setId(rs.getLong("id"));
				hostSystem.setDisplayNm(rs.getString("display_nm"));
				hostSystem.setUser(rs.getString("user"));
				hostSystem.setHost(rs.getString("host"));
				hostSystem.setPort(rs.getInt("port"));
				hostSystem.setAuthorizedKeys(rs.getString("authorized_keys"));
				hostSystem.setStatusCd(rs.getString("status_cd"));
				hostSystem.setOpenstackId(rs.getString("openstack_id"));
				hostSystemList.add(hostSystem);
			}
			DBUtils.closeRs(rs);
			DBUtils.closeStmt(stmt);

		} catch (Exception e) {
			log.error(e.toString(), e);
		}
		DBUtils.closeConn(con);


		sortedSet.setItemList(hostSystemList);
		return sortedSet;

	}


	/**
	 * returns system by id
	 *
	 * @param id system id
	 * @return system
	 */
	public static HostSystem getSystem(Long id) {

		HostSystem hostSystem = null;

		Connection con = null;

		try {
			con = DBUtils.getConn();

			hostSystem = getSystem(con, id);


		} catch (Exception e) {
			log.error(e.toString(), e);
		}
		DBUtils.closeConn(con);


		return hostSystem;
	}


	/**
	 * returns system by id
	 *
	 * @param con DB connection
	 * @param id  system id
	 * @return system
	 */
	public static HostSystem getSystem(Connection con, Long id) {

		HostSystem hostSystem = null;


		try {

			PreparedStatement stmt = con.prepareStatement("select * from  system where id=?");
			stmt.setLong(1, id);
			ResultSet rs = stmt.executeQuery();

			while (rs.next()) {
				hostSystem = new HostSystem();
				hostSystem.setId(rs.getLong("id"));
				hostSystem.setDisplayNm(rs.getString("display_nm"));
				hostSystem.setUser(rs.getString("user"));
				hostSystem.setHost(rs.getString("host"));
				hostSystem.setPort(rs.getInt("port"));
				hostSystem.setAuthorizedKeys(rs.getString("authorized_keys"));
				hostSystem.setStatusCd(rs.getString("status_cd"));
				hostSystem.setOpenstackId(rs.getString("openstack_id"));
			}
			DBUtils.closeRs(rs);
			DBUtils.closeStmt(stmt);

		} catch (Exception e) {
			log.error(e.toString(), e);
		}


		return hostSystem;
	}


	/**
	 * inserts host system into DB
	 *
	 * @param con DB connection
	 * @param hostSystem host system object
	 * @return user id
	 */
	public static Long insertSystem(Connection con, HostSystem hostSystem) {

		Long systemId = null;
		try {
			PreparedStatement stmt = con.prepareStatement("insert into system (display_nm, user, host, port, authorized_keys, status_cd, openstack_id) values (?,?,?,?,?,?,?)", PreparedStatement.RETURN_GENERATED_KEYS);
			stmt.setString(1, hostSystem.getDisplayNm());
			stmt.setString(2, hostSystem.getUser());
			stmt.setString(3, hostSystem.getHost());
			stmt.setInt(4, hostSystem.getPort());
			stmt.setString(5, hostSystem.getAuthorizedKeys());
			stmt.setString(6, hostSystem.getStatusCd());
			stmt.setString(7, hostSystem.getOpenstackId());
			stmt.execute();

			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next()) {
				systemId = rs.getLong(1);
			}
			DBUtils.closeStmt(stmt);

		} catch (Exception e) {
			log.error(e.toString(), e);
		}
		return systemId;

	}

	/**
	 * inserts host system into DB
	 *
	 * @param hostSystem host system object
	 * @return user id
	 */
	public static Long insertSystem(HostSystem hostSystem) {

		Connection con = null;
		Long systemId = null;
		try {
			con = DBUtils.getConn();
			systemId = insertSystem(con, hostSystem);
		} catch (Exception e) {
			log.error(e.toString(), e);
		}

		DBUtils.closeConn(con);

		return systemId;
	}

	/**
	 * updates host system record
	 *
	 * @param con DB connection
	 * @param hostSystem host system object
	 */
	public static void updateSystem(Connection con, HostSystem hostSystem) {

		try {
			PreparedStatement stmt = con.prepareStatement("update system set display_nm=?, user=?, host=?, port=?, authorized_keys=?, status_cd=?, openstack_id=?  where id=?");
			stmt.setString(1, hostSystem.getDisplayNm());
			stmt.setString(2, hostSystem.getUser());
			stmt.setString(3, hostSystem.getHost());
			stmt.setInt(4, hostSystem.getPort());
			stmt.setString(5, hostSystem.getAuthorizedKeys());
			stmt.setString(6, hostSystem.getStatusCd());
			stmt.setString(7, hostSystem.getOpenstackId());
			stmt.setLong(8, hostSystem.getId());
			stmt.execute();
			DBUtils.closeStmt(stmt);

		} catch (Exception e) {
			log.error(e.toString(), e);
		}
	}

	/**
	 * updates host system record
	 *
	 * @param hostSystem host system object
	 */
	public static void updateSystem(HostSystem hostSystem) {

		Connection con = null;

		try {
			con = DBUtils.getConn();
			updateSystem(con, hostSystem);

		} catch (Exception e) {
			log.error(e.toString(), e);
		}
		DBUtils.closeConn(con);
	}

	/**
	 * deletes host system
	 *
	 * @param hostSystemId host system id
	 */
	public static void deleteSystem(Long hostSystemId) {


		Connection con = null;

		try {
			con = DBUtils.getConn();

			PreparedStatement stmt = con.prepareStatement("delete from system where id=?");
			stmt.setLong(1, hostSystemId);
			stmt.execute();
			DBUtils.closeStmt(stmt);

		} catch (Exception e) {
			log.error(e.toString(), e);
		}
		DBUtils.closeConn(con);

	}

	/**
	 * returns the host systems
	 *
	 * @param systemIdList list of host system ids
	 * @return host system with array of public keys
	 */
	public static List<HostSystem> getSystems(List<Long> systemIdList) {

		Connection con = null;
		List<HostSystem> hostSystemListReturn = new ArrayList<HostSystem>();

		try {
			con = DBUtils.getConn();
			for (Long systemId : systemIdList) {
				HostSystem hostSystem = getSystem(con, systemId);
				hostSystemListReturn.add(hostSystem);
			}

		} catch (Exception e) {
			log.error(e.toString(), e);
		}
		DBUtils.closeConn(con);


		return hostSystemListReturn;

	}

	/**
	 * returns system by OpenStack id
	 *
	 * @param openstackId openstack id
	 * @return system
	 */
	public static HostSystem getOpenStackSystem(String openstackId) {

		HostSystem hostSystem = null;
		Connection con = null;
		try {
			con = DBUtils.getConn();
			hostSystem = getOpenStackSystem(con, openstackId);
		} catch (Exception e) {
			log.error(e.toString(), e);
		}
		DBUtils.closeConn(con);


		return hostSystem;
	}


	/**
	 * returns system by OpenStack id
	 *
	 * @param con DB connection
	 * @param openstackId openstack id
	 * @return system
	 */
	public static HostSystem getOpenStackSystem(Connection con, String openstackId) {

		HostSystem hostSystem = null;

		try {

			PreparedStatement stmt = con.prepareStatement("select * from  system where openstack_id=?");
			stmt.setString(1, openstackId);
			ResultSet rs = stmt.executeQuery();

			while (rs.next()) {
				hostSystem = new HostSystem();
				hostSystem.setId(rs.getLong("id"));
				hostSystem.setDisplayNm(rs.getString("display_nm"));
				hostSystem.setUser(rs.getString("user"));
				hostSystem.setHost(rs.getString("host"));
				hostSystem.setPort(rs.getInt("port"));
				hostSystem.setAuthorizedKeys(rs.getString("authorized_keys"));
				hostSystem.setStatusCd(rs.getString("status_cd"));
				hostSystem.setOpenstackId(rs.getString("openstack_id"));
			}
			DBUtils.closeRs(rs);
			DBUtils.closeStmt(stmt);

		} catch (Exception e) {
			log.error(e.toString(), e);
		}


		return hostSystem;
	}
	/**
	 * returns all systems
	 *
	 * @return system list
	 */
	public static List<HostSystem> getAllSystems() {

		List<HostSystem> hostSystemList = new ArrayList<HostSystem>();

		Connection con = null;

		try {
			con=DBUtils.getConn();
			PreparedStatement stmt = con.prepareStatement("select * from  system");
			ResultSet rs = stmt.executeQuery();

			while (rs.next()) {
				HostSystem hostSystem = new HostSystem();
				hostSystem.setId(rs.getLong("id"));
				hostSystem.setDisplayNm(rs.getString("display_nm"));
				hostSystem.setUser(rs.getString("user"));
				hostSystem.setHost(rs.getString("host"));
				hostSystem.setPort(rs.getInt("port"));
				hostSystem.setAuthorizedKeys(rs.getString("authorized_keys"));
				hostSystem.setStatusCd(rs.getString("status_cd"));
				hostSystem.setOpenstackId(rs.getString("openstack_id"));
				hostSystemList.add(hostSystem);
			}
			DBUtils.closeRs(rs);
			DBUtils.closeStmt(stmt);

		} catch (Exception e) {
			log.error(e.toString(), e);
		}

		DBUtils.closeConn(con);

		return hostSystemList;

	}


	/**
	 * returns all system ids
	 *
	 * @param con DB connection
	 * @return system
	 */
	public static List<Long> getAllSystemIds(Connection con) {

		List<Long> systemIdList = new ArrayList<Long>();


		try {
			PreparedStatement stmt = con.prepareStatement("select * from  system");
			ResultSet rs = stmt.executeQuery();

			while (rs.next()) {
				systemIdList.add(rs.getLong("id"));
			}
			DBUtils.closeRs(rs);
			DBUtils.closeStmt(stmt);

		} catch (Exception e) {
			log.error(e.toString(), e);
		}


		return systemIdList;

	}

	/**
	 * returns all system ids for user
	 *
	 * @param con    DB connection
	 * @param userId user id
	 * @return system
	 */
	public static List<Long> getAllSystemIdsForUser(Connection con, Long userId) {

		List<Long> systemIdList = new ArrayList<Long>();
		try {
			PreparedStatement stmt = con.prepareStatement("select distinct system_id from  system_map m, user_map um where m.profile_id=um.profile_id and um.user_id=?");
			stmt.setLong(1, userId);
			ResultSet rs = stmt.executeQuery();

			while (rs.next()) {
				systemIdList.add(rs.getLong("system_id"));
			}
			DBUtils.closeRs(rs);
			DBUtils.closeStmt(stmt);

		} catch (Exception e) {
			log.error(e.toString(), e);
		}

		return systemIdList;

	}

	/**
	 * returns all system ids for user
	 *
	 * @param userId user id
	 * @return system
	 */
	public static List<Long> getAllSystemIdsForUser(Long userId) {
		Connection con = null;
		List<Long> systemIdList = new ArrayList<Long>();
		try {
			con = DBUtils.getConn();
			systemIdList = getAllSystemIdsForUser(con, userId);

		} catch (Exception ex) {
			log.error(ex.toString(), ex);
		}
		DBUtils.closeConn(con);
		return systemIdList;
	}

	/**
	 * returns all system ids
	 *
	 * @return system
	 */
	public static List<Long> getAllSystemIds() {
		Connection con = null;
		List<Long> systemIdList = new ArrayList<Long>();
		try {
			con = DBUtils.getConn();
			systemIdList = getAllSystemIds(con);

		} catch (Exception ex) {
			log.error(ex.toString(), ex);
		}
		DBUtils.closeConn(con);
		return systemIdList;

	}

	/**
	 * method to check system permissions for user
	 *
	 * @param con DB connection
	 * @param systemSelectIdList list of system ids to check
	 * @param userId             user id
	 * @return only system ids that user has perms for
	 */
	public static List<Long> checkSystemPerms(Connection con, List<Long> systemSelectIdList, Long userId) {

		List<Long> systemIdList = new ArrayList<Long>();
		List<Long> userSystemIdList = getAllSystemIdsForUser(con, userId);

		for (Long systemId : userSystemIdList) {
			if (systemSelectIdList.contains(systemId)) {
				systemIdList.add(systemId);
			}
		}

		return systemIdList;

	}

	/**
	 * saves a set of VMs obtained from OpenStack
	 *
	 * @param hostSystemList
	 */
	public static void saveOpenStackVms(List<HostSystem> hostSystemList) {
		Connection con = null;
		try {
			con = DBUtils.getConn();
			for (HostSystem hostSystem : hostSystemList) {
				HostSystem prevSystem = getOpenStackSystem(con, hostSystem.getOpenstackId());
				if(prevSystem != null) {
					//use previous systems settings
					hostSystem.setId(prevSystem.getId());
					hostSystem.setUser(prevSystem.getUser());
					hostSystem.setPort(prevSystem.getPort());
					hostSystem.setAuthorizedKeys(prevSystem.getAuthorizedKeys());
					hostSystem.setStatusCd(prevSystem.getStatusCd());
					hostSystem.setHost(prevSystem.getHost());
					updateSystem(con, hostSystem);
				} else {
					insertSystem(con, hostSystem);
				}
			}

		} catch (Exception ex) {
			log.error(ex.toString(), ex);
		}
		DBUtils.closeConn(con);

	}



}
