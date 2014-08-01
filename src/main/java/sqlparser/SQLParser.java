/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt, Peter Hayes
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package sqlparser;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;

import org.json.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saves HTML reports for the project and publishes them.
 * 
 * @author Kohsuke Kawaguchi
 * @author Mike Rooney
 */
public class SQLParser extends Recorder {
    private final HtmlPublisherTarget reportTarget;

    @DataBoundConstructor
    public SQLParser(String reportName, String reportDir, String reportFile, boolean keepAll, boolean allowMissing) {
        this.reportTarget = new HtmlPublisherTarget(reportName, reportDir, reportFile, keepAll, allowMissing);
    }
    
    public HtmlPublisherTarget getReportTarget() {
        return this.reportTarget;
    }

    private static void writeFile(ArrayList<String> lines, File path) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(path));
        for (int i = 0; i < lines.size(); i++) {
            bw.write(lines.get(i));
            bw.newLine();
        }
        bw.close();
        return;
    }

    public ArrayList<String> readFile(String filePath) throws java.io.FileNotFoundException,
            java.io.IOException {
        ArrayList<String> aList = new ArrayList<String>();

        try {
            final InputStream is = this.getClass().getResourceAsStream(filePath);
            try {
                final Reader r = new InputStreamReader(is);
                try {
                    final BufferedReader br = new BufferedReader(r);
                    try {
                        String line = null;
                        while ((line = br.readLine()) != null) {
                            aList.add(line);
                        }
                        br.close();
                        r.close();
                        is.close();
                    } finally {
                        try {
                            br.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } finally {
                    try {
                        r.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            // failure
            e.printStackTrace();
        }

        return aList;
    }

    protected static String resolveParametersInString(AbstractBuild<?, ?> build, BuildListener listener, String input) {
        try {
            return build.getEnvironment(listener).expand(input);
        } catch (Exception e) {
            listener.getLogger().println("Failed to resolve parameters in string \""+
            input+"\" due to following error:\n"+e.getMessage());
        }
        return input;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException {
    	listener.getLogger().println("[SQLParser] Archiving SQL reports...");
        
    	
    	
    	listener.getLogger().println(reportTarget);
    	listener.getLogger().println(reportTarget.getKeepAll());
    	boolean keepAll = this.reportTarget.getKeepAll();
        ArrayList<String> reportLines = new ArrayList<String>();

        //Grab the contents of the header and footer as arrays
        FilePath archiveDir = build.getWorkspace().child(resolveParametersInString(build, listener, this.reportTarget.getReportDir()));
        FilePath cargoLog = build.getWorkspace().child(resolveParametersInString(build, listener, this.reportTarget.getReportFile()));
        FilePath targetDir = this.reportTarget.getArchiveTarget(build);
        
    	listener.getLogger().println(cargoLog.getBaseName());
        try{
	        BufferedReader in = new BufferedReader(new InputStreamReader(cargoLog.read()));
	        String line = null;
	        
	        String nextType = "";
	        String connection = "";
	        String query = "";
	        ArrayList<String> results = new ArrayList<String>(); 
	        
	        boolean printNext = false;
	        Pattern connectionPattern = Pattern.compile("jdbcds.*ConnectionLogger");
	        Pattern statementPattern = Pattern.compile("jdbcds.*StatementLogger");
	        Pattern resultPattern = Pattern.compile("jdbcds.*ResultSetLogger");
	        Pattern totalPattern = Pattern.compile("Total of (.*) rows read");
	        
	        JSONObject queries = new JSONObject();
	        
	        while((line = in.readLine()) != null) {
	        	Matcher connectionMatcher = connectionPattern.matcher(line);
	        	Matcher statementMatcher = statementPattern.matcher(line);
	        	Matcher resultMatcher = resultPattern.matcher(line);
	        	Matcher totalMatcher = totalPattern.matcher(line);
	        	
	            if(printNext) {
	                if(nextType == "statement" && totalMatcher.find()){
	                	JSONObject queryObj = new JSONObject();
	                	
	                	queryObj.put("connection", connection);
	                	queryObj.put("query", query);
	                	queryObj.put("results", results);
	                	queryObj.put("total", totalMatcher.group(1));
	                	
	                	if(queries.isNull("queries")){
	                		queries.put("queries", new JSONArray());
	                	}

	                	queries.append("queries", queryObj);
	                    results.clear();
	                } else if (nextType == "statement") {
	                    query = line.substring(6);
	                } else if (nextType == "connection") {
	                    connection = line.substring(6);
	                } else if (nextType == "result") {
	                	String result = line.substring(31);
	                	result = result.substring(0, result.length()-1);
	                    results.add(result);
	                }
	            }
	            
	            if (connectionMatcher.find()) {
	                printNext = true;
	                nextType = "connection";
	            } else if (statementMatcher.find()) {
	                printNext = true;
	                nextType = "statement";
	            }  else if (resultMatcher.find()) {
	                printNext = true;
	                nextType = "result";
	            }
	            else {
	                printNext = false;
	            }
	        }
	        
	        reportLines.add(queries.toString(4));
        } catch (Exception e) {
        	e.printStackTrace(listener.fatalError("HTML Publisher failure"));
        	return false;
        }

        String levelString = keepAll ? "BUILD" : "PROJECT"; 
        listener.getLogger().println("[SQLParser] Archiving at " + levelString + " level " + archiveDir + " to " + targetDir);

        // Add the JS to change the link as appropriate.
//        String hudsonUrl = Hudson.getInstance().getRootUrl();
//        AbstractProject job = build.getProject();

        // If the URL isn't configured in Hudson, the best we can do is attempt to go Back.
//            if (hudsonUrl == null) {
//                reportLines.add("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").onclick = function() { history.go(-1); return false; };</script>");
//            } else {
//                String jobUrl = hudsonUrl + job.getUrl();
//                reportLines.add("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").href=\"" + jobUrl + "\";</script>");
//            }

        try {
            if (!archiveDir.exists()) {
                listener.error("Specified HTML directory '" + archiveDir + "' does not exist.");
                build.setResult(Result.FAILURE);
                return true;
            } else if (!keepAll) {
                // We are only keeping one copy at the project level, so remove the old one.
                targetDir.deleteRecursive();
            }

            if (archiveDir.copyRecursiveTo("**/*", targetDir) == 0) {
                listener.error("Directory '" + archiveDir + "' exists but failed copying to '" + targetDir + "'.");
                if (build.getResult().isBetterOrEqualTo(Result.UNSTABLE)) {
                    // If the build failed, don't complain that there was no coverage.
                    // The build probably didn't even get to the point where it produces coverage.
                    listener.error("This is especially strange since your build otherwise succeeded.");
                }
                build.setResult(Result.FAILURE);
                return true;
            }
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("HTML Publisher failure"));
            build.setResult(Result.FAILURE);
            return true;
        }

        // And write this as the index
        try {
            if(archiveDir.exists())
            {
                this.reportTarget.handleAction(build);
                writeFile(reportLines, new File(targetDir.getRemote(), this.reportTarget.getWrapperName()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        ArrayList<Action> actions = new ArrayList<Action>();
        actions.add(reportTarget.getProjectAction(project));
        if (project instanceof MatrixProject && ((MatrixProject) project).getActiveConfigurations() != null){
            for (MatrixConfiguration mc : ((MatrixProject) project).getActiveConfigurations()){
                try {
                  mc.onLoad(mc.getParent(), mc.getName());
                }
                catch (IOException e){
                    //Could not reload the configuration.
                }
            }
        }
        return actions;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public String getDisplayName() {
            // return Messages.JavadocArchiver_DisplayName();
            return "Publish SQL report";
        }
        
        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
//        public FormValidation doCheck(@AncestorInPath AbstractProject project,
//                @QueryParameter String value) throws IOException, ServletException {
//            FilePath ws = project.getSomeWorkspace();
//            return ws != null ? ws.validateRelativeDirectory(value) : FormValidation.ok();
//        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
}
