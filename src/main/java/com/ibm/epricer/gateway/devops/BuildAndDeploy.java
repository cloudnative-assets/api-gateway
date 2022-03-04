package com.ibm.epricer.gateway.devops;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;


public class BuildAndDeploy {
	private static final String TRAVIS_HOST_URL = "https://travis.ibm.com/";
    private static final String GIT_COMMIT_NUMBER = "git rev-list --count --first-parent HEAD";
    private static final String GIT_COMMIT_HASH = "git rev-parse --short HEAD";
    private static final String GIT_COMMIT_MESSAGE = "git log --format=%B -n 1 ";
    private static final String GIT_COMMITTER = "git log --format='%ae' -n 1 ";
    private static final String GIT_UNCOMMITTED = "git status --porcelain";
    private static final String BUILD_TEMPLATE = "docker build -t %s --build-arg JAR_FILE=%s --build-arg EPRICER_SERVICEVER=%s .";
    private static final String LOGIN_TEMPLATE = "docker login -u=%s -p=%s %s";
    private static final String PUSH_TEMPLATE = "docker push %s";
    private static final String TAG_TEMPLATE = "docker tag %s %s";
    
    public static void main(String[] args) throws InterruptedException, IOException {
    	try {
        	long start = System.currentTimeMillis();
            if (args.length != 15) {
                System.err.printf("15 parameters are expected, but received %s\n", args.length);
                System.exit(1);
            }

            final String jarFile = args[0];
            final String registry = args[1];
            final String project = args[2];
            final String artifact = args[3];
            final String userName = args[4];
            final String password = args[5];
            final String gateway = args[6];
            final String gatewayId = args[7];
            final String gatewayPwd = args[8];
            final String buildNumber = args[9];
            final String buildId = args[10];
            final String logUrl = args[11];
            final String workloadRepo=args[12];
            final String workloadRepoUser=args[13];
            final String workloadRepoUserPassword=args[14];
            
            System.out.println("Build Arguments");
            System.out.println("jarFile:"+jarFile+", registry:"+registry+", project:"+project+", artifact:"+artifact+", pipelineId:"+userName+", pipelinePassword:[secured]"+", gateway:"+gateway+" gatewayId:"+gatewayId+", gatewayPassword: [secured]"+", buildNumber:"+ buildNumber+", buildId:"+ buildId+", logurl:"+logUrl + ", workload repo:"+workloadRepo);

            SimpleDateFormat f = new SimpleDateFormat("HHmmss");
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            String time = f.format(Date.from(Instant.now()));

            String t1 = runCommand(GIT_COMMIT_NUMBER, false);
            String t2 = runCommand(GIT_COMMIT_HASH, false);
            
            String commiter = runCommand(GIT_COMMITTER + t2, true);
            
            String commitMessage = runCommand(GIT_COMMIT_MESSAGE + t2, true);
            
            Build buildRecord = createBuildRecord(buildId, buildNumber, logUrl, t2, commiter, commitMessage, t1, project, artifact);
            String buildRecordId = null;
            try {
            	RegisterBuild registerBuild = new RegisterBuild();
            	buildRecordId = registerBuild.start(buildRecord, gateway, gatewayId, gatewayPwd);
            	buildRecord.id = buildRecordId;
                String uncommitted = runCommand(GIT_UNCOMMITTED, true);
                String tag = t1 + "-" + t2 + "-" + time;
//                if (StringUtils.isNotBlank(uncommitted)) {
//                    System.out.println("WARNING: uncommitted changes exist!");
//                    tag = tag + (uncommitted.isBlank() ? "" : "-" + time);
//                }
                String imageName = registry + "/" + project + "/" + artifact + ":" + tag;

                System.out.printf("Building image: %s\n", imageName);
                String buildCommand = String.format(BUILD_TEMPLATE, imageName, jarFile, tag); 
                runCommand(buildCommand, true);

                System.out.printf("Logging in to registry: %s\n", registry);
                String loginCommand = String.format(LOGIN_TEMPLATE, userName, password, registry);
                runCommand(loginCommand, true);
                
                System.out.printf("Pushing the image: %s\n", imageName);
                String pushCommand = String.format(PUSH_TEMPLATE, imageName);
                runCommand(pushCommand, true);
                System.out.printf("Image %s successfully pushed to %s\n", imageName, registry);
                
                System.out.printf("Logging in to registry: %s\n", workloadRepo);
                String centralRepologinCommand = String.format(LOGIN_TEMPLATE, workloadRepoUser, workloadRepoUserPassword, workloadRepo);
                runCommand(centralRepologinCommand, true);
                
                System.out.println("Tagging the image for central workload repository");
                String centralRepoTag = workloadRepo+"/"+project+"/"+artifact+":"+tag;
                String tagCommand = String.format(TAG_TEMPLATE, imageName, centralRepoTag);
                runCommand(tagCommand, true);
                
                System.out.printf("Ready to push the image to central workload repository: %s\n", workloadRepo);
                String centralRepoPushCommand = String.format(PUSH_TEMPLATE, centralRepoTag);
                runCommand(centralRepoPushCommand, true);
                System.out.printf("Image %s successfully pushed to %s\n", centralRepoTag, workloadRepo);
                
                
                buildRecord.imagetag=tag;
                long end = System.currentTimeMillis();
            	updateBuildRecord(buildRecord, end-start, true, "Build Success");
            	registerBuild.finish(buildRecord, gateway, gatewayId, gatewayPwd);
            	
            } catch (Throwable th) {
            	long end = System.currentTimeMillis();
            	th.printStackTrace();
            	System.err.printf("Command failed due to %s\n", th.getMessage());
            	updateBuildRecord(buildRecord, end-start, false, th.getMessage());
            	System.exit(1);
            }
    		
    	} catch (Throwable th) {
    		th.printStackTrace();
    		// updateBuildRecord(buildRecord, end-start, false, th.getMessage());
    		System.err.printf("Build failed with exception %s\n", th.getMessage());
    		System.exit(1);
    	}
        
    }

    private static String runCommand(String command, boolean print) {
        try {
            Process proc = Runtime.getRuntime().exec(command);
            System.out.printf("Started process: %s\n", proc.info());
            String output = readStream(proc.getInputStream());
            if (print && !output.isBlank()) {
                System.out.println(output);
            }
            String error = readStream(proc.getErrorStream());
            if (!error.isBlank()) {
                System.err.println(error);
            }
            int code = proc.waitFor();
            if (code != 0) {
                System.err.printf("Command failed with error code: %s\n", code);
                throw new IllegalStateException("Command failed with error code: " + code );
            }
            return output;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Command failed with exception", e);
        }
    }

    private static String readStream(InputStream is) throws IOException {
        try (InputStreamReader isr = new InputStreamReader(is); BufferedReader reader = new BufferedReader(isr)) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }
    
    private static Build createBuildRecord(String buildid, String buildnumber, String logurl, String gitsha, String commiter, String commitMsg, String commitNumber, String project, String service) {
    	String buildLog = logurl.replace("https:///", TRAVIS_HOST_URL);
    	Build build = new Build();
    	build.buildid=buildid;
    	build.buildnumber = buildnumber;
    	build.buildlogurl=buildLog;
    	build.commitsha = gitsha;
    	build.commitedby = commiter;
    	build.commitmessage = commitMsg;
    	build.commitnumber = commitNumber;
    	build.project = project;
    	build.service = service;
    	build.phase = "CI";
    	build.status = "Running";
    	return build;
    }
    
    private static void updateBuildRecord(Build build, long buildtime, boolean success, String message) {
    	build.buildtime = buildtime;
    	build.status = "Finished";
    	build.success = success;
    	build.statusmessage = message;
    }
}
