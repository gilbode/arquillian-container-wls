/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.container.wls.remote_12_1_3;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.client.filter.CsrfProtectionFilter;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.jsonp.JsonProcessingFeature;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.arquillian.container.wls.ShrinkWrapUtil;
import org.jboss.arquillian.container.wls.remote_12_1_3.WebLogicRemoteConfiguration;
import org.jboss.shrinkwrap.api.Archive;

/**
 * A utility class for performing operations relevant to a remote WebLogic
 * container used by Arquillian. Relies completely on the REST client to perform
 * deployments. WLS 12.1.3 containers and higher are encouraged to use this
 * class.
 * 
 * @author Vineet Reynolds
 *
 */
public class RemoteContainer {
	private static final Logger LOGGER = Logger.getLogger(RemoteContainer.class
			.getName());

	private WebLogicRemoteConfiguration configuration;

	public RemoteContainer(WebLogicRemoteConfiguration configuration) {
		this.configuration = configuration;
	}

	/**
	 * No-op.
	 * 
	 * @throws org.jboss.arquillian.container.spi.client.container.LifecycleException
	 * 
	 */
	public void start() throws LifecycleException {
	}

	/**
	 * No-op.
	 *
	 * @throws org.jboss.arquillian.container.spi.client.container.LifecycleException
	 *             When there is failure in closing the JMX connection.
	 */
	public void stop() throws LifecycleException {
	}

	/**
	 * Invokes REST API to deploy an application.
	 *
	 * @param archive
	 *            The ShrinkWrap archive to deploy
	 * @return The metadata for the deployed application
	 * @throws org.jboss.arquillian.container.spi.client.container.DeploymentException
	 *             REST API call fails fails.
	 */
	@SuppressWarnings("resource")
	public ProtocolMetaData deploy(Archive<?> archive)
			throws DeploymentException {

		String deploymentName = getDeploymentName(archive);
		File deploymentArchive = ShrinkWrapUtil.toFile(archive);

		JsonObject model = Json
				.createObjectBuilder()
				.add("name", deploymentName)
				.add("targets",
						Json.createArrayBuilder()
								.add(configuration.getTarget()).build())
				.build();
		MultiPart request = new MultiPart()
				.bodyPart(
						new FormDataBodyPart("model", model,
								MediaType.APPLICATION_JSON_TYPE))
				.bodyPart(
						new FileDataBodyPart("deployment", deploymentArchive,
								MediaType.APPLICATION_OCTET_STREAM_TYPE))
				.type(MediaType.MULTIPART_FORM_DATA_TYPE);
		URL adminUrl = null;
		URI applicationRestURI = null;
		try {
			adminUrl = new URL(configuration.getAdminUrl());
			applicationRestURI = new URI(adminUrl.toURI().toString()
					+ "/management/wls/latest/deployments/application")
					.normalize();
		} catch (MalformedURLException e) {
			throw new DeploymentException("Deployment failed", e);
		} catch (URISyntaxException e) {
			throw new DeploymentException("Deployment failed", e);
		}

		Client restClient = getRestClient();
		Response response = restClient.target(applicationRestURI)
				.request(MediaType.APPLICATION_JSON)
				.post(Entity.entity(request, request.getMediaType()));
		if (response.getStatus() != Status.CREATED.getStatusCode()) {
			throw new DeploymentException(response.toString());
		}

		response = restClient.target(response.getLocation())
				.request(MediaType.APPLICATION_JSON).get();
		if (response.getStatus() != Status.OK.getStatusCode()) {
			throw new DeploymentException(response.toString());
		}

		ProtocolMetaData metadata = new ProtocolMetaData();
		try {

			HTTPContext httpContext = new HTTPContext(adminUrl.getHost(),
					adminUrl.getPort());
			
			JsonObject jsonResponse = response.readEntity(JsonObject.class);
			if (jsonResponse.containsKey("item")) {
				JsonObject item = jsonResponse.getJsonObject("item");
				if (item.containsKey("servlets")) {
					JsonArray servlets = item.getJsonArray("servlets");
					for (JsonValue servlet : servlets) {
						JsonObject servletJsonObject = (JsonObject) servlet;
						if (servletJsonObject.containsKey("servletName")
								&& servletJsonObject.containsKey("contextPath")) {
							httpContext
									.add(new Servlet(servletJsonObject
											.getString("servletName"),
											servletJsonObject
													.getString("contextPath")));
						}
					}
				}
			}
			
			metadata.addContext(httpContext);
			
			restClient.close();

		} catch (Exception ex) {
			throw new DeploymentException(
					"Failed to populate the HTTPContext with the deployment details",
					ex);
		}
		return metadata;
	}

	private Client getRestClient() {
		HttpAuthenticationFeature httpAuthFeature = HttpAuthenticationFeature
				.universalBuilder()
				.credentialsForBasic(configuration.getAdminUserName(),
						configuration.getAdminPassword()).build();
		Client restClient = ClientBuilder.newBuilder()
				.register(new LoggingFilter(LOGGER, true))
				.register(CsrfProtectionFilter.class).register(httpAuthFeature)
				.register(MultiPartFeature.class)
				.register(JsonProcessingFeature.class).build();
		return restClient;
	}

	/**
	 * Wraps the operation of forking a weblogic.Deployer process to undeploy an
	 * application.
	 *
	 * @param archive
	 *            The ShrinkWrap archive to undeploy
	 * @throws org.jboss.arquillian.container.spi.client.container.DeploymentException
	 *             When forking of weblogic.Deployer fails, or when interaction
	 *             with the forked process fails, or when undeployment cannot be
	 *             confirmed.
	 */
	public void undeploy(Archive<?> archive) throws DeploymentException {

		String deploymentName = getDeploymentName(archive);
		Response response = null;
		try {
			Client restClient = getRestClient();
			response = restClient
					.target(new URI(
							configuration.getAdminUrl()
									+ "/management/wls/latest/deployments/application/id/"
									+ deploymentName)).request().delete();
			restClient.close();
		} catch (URISyntaxException e) {
			throw new DeploymentException("Deployment failed", e);
		}

		if (response.getStatus() != Status.OK.getStatusCode()) {
			throw new DeploymentException(response.toString());
		}
	}

	private String getDeploymentName(Archive<?> archive) {
		String archiveFilename = archive.getName();
		int indexOfDot = archiveFilename.indexOf(".");
		if (indexOfDot != -1) {
			return archiveFilename.substring(0, indexOfDot);
		}
		return archiveFilename;
	}

}
