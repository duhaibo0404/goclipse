package com.googlecode.goclipse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.jface.preference.IPreferenceNode;
import org.eclipse.jface.preference.IPreferencePage;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.googlecode.goclipse.builder.Arch;
import com.googlecode.goclipse.builder.ExternalCommand;
import com.googlecode.goclipse.builder.GoConstants;
import com.googlecode.goclipse.builder.ProcessIStreamFilter;
import com.googlecode.goclipse.preferences.GoPreferencePage;
import com.googlecode.goclipse.preferences.PreferenceConstants;

/**
 * Provides environmental utility methods for acquiring and storing a user
 * session's contextual data.
 * 
 * @author steel
 */
public class Environment {

	private static final String PROJECT_SOURCE_FOLDERS = "com.googlecode.goclipse.environment.source.folders";
	private static final String PROJECT_OUTPUT_FOLDERS = "com.googlecode.goclipse.environment.output.folders";
	public static final String DEFAULT_PROJECT_OUTPUT_FOLDER = "bin";

	public static final boolean DEBUG = true;
	public static final Environment INSTANCE = new Environment();
	private final IPreferencesService preferences;
	private Map<String, Properties> propertiesMap = new HashMap<String, Properties>();
	private String depToolPath;
	
	private static final int DEP_TOOL_VERSION = 1;

	private Environment() {
		preferences = Platform.getPreferencesService();
	}

	/**
	 * 
	 * @return true if the preferences have been set for all values
	 */
	public boolean isValid() {
		String goarch = Activator.getDefault().getPreferenceStore().getString(
				PreferenceConstants.GOARCH);
		
		String goos = Activator.getDefault().getPreferenceStore().getString(
				PreferenceConstants.GOOS);
		
		String goroot = Activator.getDefault().getPreferenceStore().getString(
				PreferenceConstants.GOROOT);

		
		if (goroot == null || goroot.length() == 0 || goos == null
					|| goos.length() == 0 || goarch == null
					|| goarch.length() == 0) {
				Activator.getDefault().getLog().log(new Status(Status.ERROR, Activator.PLUGIN_ID, GoConstants.INVALID_PREFERENCES_MESSAGE));
				return false;
		}
		return true;


	}
	
	private void buildDependencyTool() {

		if (!isValid()) {
			return;
		}
		String goarch = Activator.getDefault().getPreferenceStore().getString(
				PreferenceConstants.GOARCH);
		
		String goos = Activator.getDefault().getPreferenceStore().getString(
				PreferenceConstants.GOOS);
		
		Arch arch = Arch.getArch(goarch);
		
		IWorkspace root = ResourcesPlugin.getWorkspace();
		IPath base = root.getRoot().getLocation();
		IPath versionPath = base.append(".metadata").append(".go");
		IPath tools = versionPath.append(goos).append(goarch).append("tools");
		String toolsPath = tools.toOSString();
		File toolsFile = new File(toolsPath);
		if (!toolsFile.exists()) {
			toolsFile.mkdirs();
		}
		String depToolName = "dep";
		String versionPropertiesFileName = "version.properties";
		String depToolExe = depToolName + arch.getExecutableExt();
		String depToolGo = depToolName + GoConstants.GO_SOURCE_FILE_EXTENSION;
		String depToolObj = depToolName + arch.getExtension();
		String aDepToolPath = toolsPath + File.separator + depToolExe;
		File exeFile = new File(aDepToolPath);
		Properties versionProperties = new Properties();
		File propertiesFile = new File(versionPath.toOSString() + File.separator + versionPropertiesFileName);
		if (propertiesFile.exists()) {
			try {
				versionProperties.load(new FileInputStream(propertiesFile));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			String installedVersion = versionProperties.getProperty("depToolVersion");
			if (null != installedVersion) {
				int version = 0;
				try {
					version = Integer.parseInt(installedVersion);
				}catch(NumberFormatException nfe) {
					
				}
				if (version == DEP_TOOL_VERSION && exeFile.exists()) {
					depToolPath = aDepToolPath;
					SysUtils.debug("exe tool is ok");
					return; // everyting in place		
				}
			}
		}
		if (exeFile.exists()) {
			exeFile.delete();
		}
		// will compile it from source
		//first, save the source in .metadata
		URL srcString = Activator.getDefault().getBundle().getEntry(
				"/tools/src/dep/dep.go");
		saveSource(toolsFile, srcString, toolsPath + File.separator + depToolGo);
		
		//setup compile
		MsgFilter mf = new MsgFilter();
		String compilerPath = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.COMPILER_PATH);
		ExternalCommand compile = new ExternalCommand(compilerPath);
		compile.setEnvironment(GoConstants.environment());
		compile.setWorkingFolder(toolsPath);
		List<String> args = new ArrayList<String>();
		//output file option
		args.add(GoConstants.COMPILER_OPTION_O);
		args.add(depToolObj);
		args.add(depToolGo);
		compile.setResultsFilter(mf);
		compile.execute(args);
		
		if (!mf.hadError) {
			mf.clear();	
			//do linker
			String linkerPath = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.LINKER_PATH);
			compile.setCommand(linkerPath);
			args.clear();
			args.add(GoConstants.COMPILER_OPTION_O);
			args.add(depToolExe);
			args.add(depToolObj);
			compile.execute(args);
			
			if (!mf.hadError) {
				versionProperties.setProperty("depToolVersion", String.valueOf(DEP_TOOL_VERSION));
				try {
					versionProperties.store(new FileOutputStream(propertiesFile), "automatically generated, do not change");
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				depToolPath = aDepToolPath;
			}
		}
		//done
	}
	
	class MsgFilter implements ProcessIStreamFilter {
		public boolean hadError = false;
		@Override
		public void process(InputStream iStream) {
			try {
				InputStreamReader isr = new InputStreamReader(iStream);
			    BufferedReader br = new BufferedReader(isr);
			    String line;
		        while ((line = br.readLine()) != null) {
		        	SysUtils.debug("error in parse connector:" + line);
		        	hadError = true;
		        }
			}catch(Exception e) {
				SysUtils.debug(e);
			}
		}

		@Override
		public void clear() {		
			hadError = false;
		}
		
	}

	private void saveSource(File toolsFile, URL srcURL, String outPath) {
		if (srcURL != null) {
			try {
				File outFile = new File(outPath);
				InputStream in = srcURL.openStream();

				// For Overwrite the file.
				OutputStream out = new FileOutputStream(outFile);

				byte[] buf = new byte[1024];
				int len;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
				in.close();
				out.close();
				SysUtils.debug("File copied:" + outPath);
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
		}

	}

	public String getDependencyTool() {
		if (depToolPath == null){
			buildDependencyTool();
		}
		return depToolPath;
	}

	/**
	 * @param project
	 * @return
	 */
	private Properties getProperties(IProject project) {
		Properties properties = propertiesMap.get(project.getName());
		if (properties == null) {
			properties = loadProperties(project);
			propertiesMap.put(project.getName(), properties);
		}
		return properties;
	}

	/**
	 * @param project
	 * @return
	 */
	private Properties loadProperties(IProject project) {
		Properties properties = new Properties();
		try {
			properties.loadFromXML(new FileInputStream(project
					.getWorkingLocation(Activator.PLUGIN_ID)
					+ "/properties.xml"));
		} catch (InvalidPropertiesFormatException e) {
			// e.printStackTrace();
		} catch (FileNotFoundException e) {
			// e.printStackTrace();
		} catch (IOException e) {
			// e.printStackTrace();
		}
		return properties;
	}

	/**
	 * @param project
	 * @return
	 */
	private void saveProperties(IProject project) {
		Properties properties = getProperties(project);
		try {
			System.out.println("writing to "
					+ project.getWorkingLocation(Activator.PLUGIN_ID)
					+ "/properties.xml");
			properties.storeToXML(new FileOutputStream(project
					.getWorkingLocation(Activator.PLUGIN_ID)
					+ "/properties.xml", false), " this is a comment");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @return the preferences
	 */
	public IPreferencesService getPreferences() {
		return preferences;
	}

	/**
	 * Determines the current project of the active context; if possible. else
	 * return null.
	 * 
	 * @return IProject | null
	 */
	public IProject getCurrentProject() {
		IProject project = null;

		try {
			project = extractSelection(
					PlatformUI.getWorkbench().getActiveWorkbenchWindow()
							.getActivePage().getSelection()).getProject();
		} catch (Exception e) {
		}

		if (project == null) {
			IWorkspace root = ResourcesPlugin.getWorkspace();
			IProject[] projects = root.getRoot().getProjects();

			for (int i = 0; i < projects.length - 1; i++) {
				for (int j = i + 1; j < projects.length; j++) {
					if (projects[i].getName().compareToIgnoreCase(
							projects[j].getName()) > 0) { // ascending
						// sort
						IProject temp = projects[i];
						projects[i] = projects[j]; // swapping
						projects[j] = temp;

					}
				}
			}

			if (projects.length > 0) {
				return projects[0];
			}
		}
		return project;
	}

	/**
	 * Returns the selected resource
	 * 
	 * @param sel
	 * @return
	 */
	public IResource extractSelection(ISelection sel) {
		if (!(sel instanceof IStructuredSelection))
			return null;
		IStructuredSelection ss = (IStructuredSelection) sel;
		Object element = ss.getFirstElement();
		if (element instanceof IResource)
			return (IResource) element;
		if (!(element instanceof IAdaptable))
			return null;
		IAdaptable adaptable = (IAdaptable) element;
		Object adapter = adaptable.getAdapter(IResource.class);
		return (IResource) adapter;
	}

	public void getWorkingLocation() {
		ISelectionService selectionService = PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow().getSelectionService();
	}

	/**
	 * Returns the active editor or null if there is not one
	 * 
	 * @return
	 */
	public IEditorPart getActiveEditor() {
		IWorkbench iworkbench = PlatformUI.getWorkbench();
		if (iworkbench != null) {
			IWorkbenchWindow iworkbenchwindow = iworkbench
					.getActiveWorkbenchWindow();
			if (iworkbenchwindow != null) {
				IWorkbenchPage iworkbenchpage = iworkbenchwindow
						.getActivePage();
				if (iworkbenchpage != null) {
					return iworkbenchpage.getActiveEditor();
				}
			}
		}
		return null;
	}

	/**
	 * Get the shell of the Active Workbench Window
	 * 
	 * @return
	 */
	public Shell getShell() {
		IWorkbench workbench = PlatformUI.getWorkbench();
		IWorkbenchWindow wWindow = workbench.getActiveWorkbenchWindow();
		return wWindow.getShell();
	}

	/**
	 * @author steel
	 */
	class SyncHelper implements Runnable {
		IResource resource;

		public void run() {
			IWorkbenchWindow activeWindow = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow();

			if (activeWindow != null) {
				IStructuredSelection ssel = null;
				ISelection sel = activeWindow.getSelectionService()
						.getSelection();

				if (sel instanceof IStructuredSelection) {
					ssel = (IStructuredSelection) sel;

					IResource resource = null;
					Object obj = ssel.getFirstElement();

					if (obj instanceof IResource) {
						resource = (IResource) obj;
					} else if (obj instanceof IAdaptable) {
						IAdaptable adaptable = (IAdaptable) obj;
						resource = (IResource) adaptable
								.getAdapter(IResource.class);
					}
				}
			}
		}
	}

	/**
	 * Sets the source folder properties on the currently active project
	 * 
	 * @param sourceFolders
	 */
	public void setSourceFolders(String[] sourceFolders) {
		setSourceFolders(getCurrentProject(), sourceFolders);
	}

	/**
	 * Sets the source folder properties on the given project
	 * 
	 * @param sourceFolders
	 */
	public void setSourceFolders(IProject project, String[] sourceFolders) {
		Properties properties = getProperties(project);
		String sourceFolderStr = "";
		for (String folder : sourceFolders) {
			sourceFolderStr = sourceFolderStr + folder + ";";
		}
		properties.put(PROJECT_SOURCE_FOLDERS, sourceFolderStr);
		saveProperties(project);
	}

	/**
	 * Return a list of source folders for the currently active project
	 * 
	 * @return
	 */
	public String[] getSourceFoldersAsStringArray(IProject project) {
		Properties properties = getProperties(project);
		ArrayList<IFolder> list = new ArrayList<IFolder>();
		String str = properties.getProperty(PROJECT_SOURCE_FOLDERS);
		if (str != null) {
			return str.split(";");
		} else {
			return new String[] {};
		}
	}

	/**
	 * TODO need to implement & make public or delete
	 * 
	 * @return
	 */
	private List<IFolder> getSourceFolders(IProject project) {
		String[] folderPaths = getSourceFoldersAsStringArray(project);
		return null;
	}

	/**
	 * Set the output folder for the active project
	 */
	public void setOutputFolder(String outputFolder) {
		setOutputFolder(getCurrentProject(), outputFolder);
	}

	/**
	 * Set the output folder for the given project
	 */
	public void setOutputFolder(IProject project, String outputFolder) {
		Properties properties = getProperties(project);
		properties.setProperty(PROJECT_OUTPUT_FOLDERS, outputFolder);
		saveProperties(project);
	}

	/**
	 * Return the output folder for the active project
	 */
	public String getOutputFolder() {
		return getOutputFolder(getCurrentProject());
	}

	/**
	 * Return the output folder for the active project
	 */
	public String getOutputFolder(IProject project) {
		Properties properties = getProperties(project);
		String str = properties.getProperty(PROJECT_OUTPUT_FOLDERS);
		return str == null ? DEFAULT_PROJECT_OUTPUT_FOLDER : str;
	}

}
