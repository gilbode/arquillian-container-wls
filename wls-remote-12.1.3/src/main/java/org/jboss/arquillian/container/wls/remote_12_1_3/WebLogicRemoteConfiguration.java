package org.jboss.arquillian.container.wls.remote_12_1_3;

import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;
import org.jboss.arquillian.container.wls.Validate;

/**
 * Arquillian properties for the WebLogic 12.1.3 containers. Properties derived
 * from the
 * {@link org.jboss.arquillian.container.wls.CommonWebLogicConfiguration} class
 * can be overridden or added to, here.
 * 
 * @author Vineet Reynolds
 *
 */
public class WebLogicRemoteConfiguration implements ContainerConfiguration {
	private String adminUrl;
	private String adminUserName;
	private String adminPassword;
	private String target;

	public void validate() throws ConfigurationException {
		Validate.notNullOrEmpty(adminUrl,
				"The adminUrl is empty. Verify the property in arquillian.xml");
		Validate.notNullOrEmpty(
				adminUserName,
				"The username provided to weblogic.Deployer is empty. Verify the credentials in arquillian.xml");
		Validate.notNullOrEmpty(
				adminPassword,
				"The password provided to weblogic.Deployer is empty. Verify the credentials in arquillian.xml");
		Validate.notNullOrEmpty(
				target,
				"The target for the deployment is empty. Verify the properties in arquillian.xml");
	}

	public String getAdminUrl() {
		return adminUrl;
	}

	/**
	 * @param adminUrl
	 *            The administration URL to connect to.
	 */
	public void setAdminUrl(String adminUrl) {
		this.adminUrl = adminUrl;
	}

	public String getAdminUserName() {
		return adminUserName;
	}

	/**
	 * @param adminUserName
	 *            The name of the Administrator user.
	 */
	public void setAdminUserName(String adminUserName) {
		this.adminUserName = adminUserName;
	}

	public String getAdminPassword() {
		return adminPassword;
	}

	/**
	 * @param adminPassword
	 *            The password of the Administrator user.
	 */
	public void setAdminPassword(String adminPassword) {
		this.adminPassword = adminPassword;
	}

	public String getTarget() {
		return target;
	}

	/**
	 * @param target
	 *            The name of the target for the deployment. This can be the
	 *            name of the Admin Server i.e. "AdminServer", the name of an
	 *            individual Managed Server or the name of a Cluster (not yet
	 *            supported).
	 */
	public void setTarget(String target) {
		this.target = target;
	}
}
