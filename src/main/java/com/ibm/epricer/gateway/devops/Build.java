package com.ibm.epricer.gateway.devops;

import java.util.Date;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
public class Build {
	public String id;
	
	public String buildnumber;
	
	public String buildid;
	
	public String buildlogurl;
	
	public String status;
	
	public String statusmessage;
	
	public boolean success;
	
	public String project;
	
	public String service;
		
	public String commitnumber;
	
	public String commitsha;
	
	public String commitedby;
	
	public String commitmessage;
	
	public String imagetag;
	
	public String initiatorcomment;
	
	public String approvercomment;
	
	public String phase;
	
	public Date timestamp;

	public Long buildtime;
	
	
	
	public String toJson() throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.writeValueAsString(this);
	}
}
