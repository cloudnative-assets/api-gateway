package com.ibm.epricer.gateway.devops;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;


public class RegisterBuild {
	static final String BUILD_START_ENDPOINT = "/services/ops/build/v1/start";
	static final String BUILD_FINISH_ENDPOINT = "/services/ops/build/v1/finish";
	static final String CONTENT_TYPE = "Content-Type";
	static final String CONTENT_TYPE_JSON = "application/json";
	static final String METHOD_POST = "POST";
	public static void main(String[] args) {
		Build build = new Build();
		build.buildnumber = "1";
		build.buildid="1234521";
		build.commitedby="ksc@in.ibm.com";
		build.commitmessage="test commit";
		build.commitnumber="1";
		build.commitsha="xyzone";
		build.phase="CI";
		build.project="hw-pricing-tribe-ops";
		build.service="test-svc-two";
		build.status="Running";
		
		String gateway = "https://pricing-gateway.dal1a.ciocloud.nonprod.intranet.ibm.com";
		String userId = "kiranchowdhury@in.ibm.com";
		String pwd = "";
		
		RegisterBuild registerBuild = new RegisterBuild();
		registerBuild.start(build, gateway, userId, pwd);
	}
	
	public static class NoHostVerifyer implements HostnameVerifier {

		@Override
		public boolean verify(String arg0, SSLSession arg1) {
			return true;
		}
		
	}
	
	String start(Build build, String gateway, String userId, String pwd) {
		String startBuildEndpoint = gateway + BUILD_START_ENDPOINT;
		String buildRecordId = callOpsBuildManager(build, startBuildEndpoint, userId, pwd);
		System.out.println("Build record identifier " + buildRecordId);
		return buildRecordId;
	}
	
	String finish(Build build, String gateway, String userId, String pwd) {
		String finishEndpoint = gateway + BUILD_FINISH_ENDPOINT;
		String buildRecordId = callOpsBuildManager(build, finishEndpoint, userId, pwd);
		return buildRecordId;
	}
	
	private String callOpsBuildManager(Build build, String endpoint, String userId, String pwd) {
		try {
			String authentication = userId + ':' + pwd;
			String encodedAuth = Base64.getEncoder().encodeToString(authentication.getBytes());
			URL url = new URL(endpoint);
			HttpURLConnection connection = null;
			connection = (HttpsURLConnection)url.openConnection();
			((HttpsURLConnection) connection).setHostnameVerifier(new NoHostVerifyer());
			connection.setRequestMethod(METHOD_POST);
			connection.setRequestProperty(CONTENT_TYPE, CONTENT_TYPE_JSON);
			connection.setDoOutput(true);
			connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
			String input = build.toJson();
			OutputStream os = connection.getOutputStream();
			long start = System.currentTimeMillis();
			System.out.println("Calling Ops Build Manager service in " + endpoint);
			System.out.println("Registering build record \n" + input);
			os.write(input.getBytes());
			os.flush();
	        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
	            throw new Exception("Failed : HTTP error code : "
	                + connection.getResponseCode());
	        }
	        BufferedReader br = new BufferedReader(new InputStreamReader(
	                (connection.getInputStream())));
	        String response;
	        StringBuffer outputBuffer = new StringBuffer();
	        System.out.println("\nResponse received from OPS Build Manager .... \n");
	        while ((response = br.readLine()) != null) {
	            outputBuffer.append(response);
	        }
	        long end = System.currentTimeMillis();
	        connection.disconnect();
	        String id = outputBuffer.toString().replace("\"","");
            System.out.println("Build Recored successfully Regeistered in Ops database with ID = " + id);
	        System.out.println("\nTime taken to register the build record in Ops build manager = " + (end-start) + " milliseconds");

	        return id;
		} catch (Throwable ex) {
			throw new IllegalStateException(ex);
		}
	}
}
