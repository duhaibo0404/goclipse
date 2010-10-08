package com.googlecode.goclipse.builder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Path;

import com.googlecode.goclipse.SysUtils;

/**
 * helper class to run an external process. 
 *
 */
public class ExternalCommand {
	private String command;
	private ProcessBuilder pBuilder;
	private List<String> args = new ArrayList<String>();
	private ProcessIStreamFilter resultsFilter;
	private ProcessIStreamFilter errorFilter;
	private ProcessOStreamFilter inputFilter;
	private boolean blockUntilDone = true;
	
	/**
	 * new external command using a full path
	 * @param command
	 */
	public ExternalCommand(String command) {
		this(command, true);
	}
	public ExternalCommand(String command, boolean blockUntilDone) {
		this.command = command;
		this.blockUntilDone  = blockUntilDone;
		pBuilder = new ProcessBuilder(args);
		String workingFolder = Path.fromOSString(command).removeLastSegments(1).toOSString();
		setWorkingFolder(workingFolder);
	}

	public void setEnvironment(Map<String, String> env) {
		if (env != null) {
			pBuilder.environment().putAll(env);
		}
	}
	
	public void setWorkingFolder(String folder) {
		pBuilder.directory(new File(folder));
	}
	
	public void setResultsFilter(ProcessIStreamFilter resultsFilter) {
		this.resultsFilter = resultsFilter;
	}

	public void setErrorFilter(ProcessIStreamFilter errorFilter) {
		this.errorFilter = errorFilter;
	}
	
	public void setInputFilter(ProcessOStreamFilter inputFilter) {
		this.inputFilter = inputFilter;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	/**
	 * returns an error string or null if no errors occured
	 * @param parameters
	 * @return
	 */
	public String execute(List<String> parameters) {
		String rez = null;
		try {
			args.clear();
			args.add(command);
			if (parameters != null) {
				args.addAll(parameters);
			}
			SysUtils.debug(pBuilder.directory() + " executing: " +  args);
			
			Process p = pBuilder.start();
			InStreamWorker processOutput = new InStreamWorker(p.getInputStream(), "output stream thread");
			processOutput.setFilter(resultsFilter);
			InStreamWorker processError = new InStreamWorker(p.getErrorStream(), "error stream thread");
			processError.setFilter(errorFilter);
			if (inputFilter != null) {
				inputFilter.setStream(p.getOutputStream());
			}
			
			processOutput.start();
			processError.start();
			if (blockUntilDone) {
				processOutput.join();
				processError.join();
			}
//
//			int exitValue = p.waitFor();
//			if (exitValue != 0){
//				rez = this.command+" completed with non-zero exit value ("+exitValue+")";
//			}
			//done
		}catch(Exception e) {
			e.printStackTrace();
			rez = e.getLocalizedMessage();
		}
		return rez;
	}
	private class InStreamWorker extends Thread {
		private InputStream is;
		private ProcessIStreamFilter filter;
		public InStreamWorker(InputStream is, String threadName) {
			super(threadName);
			this.is = is;
		}
		public void setFilter(ProcessIStreamFilter filter) {
			this.filter = filter;
			
		}
		public void run() {
			try {
				if (filter != null) {
					filter.process(is);
				}
				consume(is); //make sure it consumes everything
				is.close();
			} catch (IOException e) {
				SysUtils.debug(e);
			}
		}
	}
	private void consume(InputStream is) {
		//go through stream up to the end
		byte[] buf = new byte[256];
		try {
			while(is.read(buf, 0, buf.length)>0) {
				//just consume data
			}
		}catch(IOException e) {
			//ignore
		}
	}

}