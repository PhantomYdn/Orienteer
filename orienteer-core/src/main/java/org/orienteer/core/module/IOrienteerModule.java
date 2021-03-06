package org.orienteer.core.module;

import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.record.impl.ODocument;
import org.orienteer.core.OrienteerWebApplication;
import ru.ydn.wicket.wicketorientdb.OrientDbWebApplication;

import java.util.Set;

/**
 * Interface for Orienteer modules
 */
public interface IOrienteerModule
{
	String OMODULE_CLASS = "OModule";
	String OMODULE_NAME = "name";
	String OMODULE_VERSION = "version";
	String OMODULE_ACTIVATE = "activate";

	/**
	 * Name of the module. Should be static
	 * @return name of the module
	 */
	public String getName();
	
	/**
	 * Current version of the module
	 * @return a version as number
	 */
	public int getVersion();
	
	/**
	 * Get dependencies for this module. Dependencies should be static
	 * @return set with dependencies
	 */
	public Set<String> getDependencies();
	
	/**
	 * Install this application in the environment
	 * @param app {@link OrientDbWebApplication}
	 * @param db database
	 * @return {@link ODocument} for a module or null of default OModule is OK
	 */
	public ODocument onInstall(OrienteerWebApplication app, ODatabaseSession db);
	
	/**
	 * Update installed module
	 * @param app {@link OrientDbWebApplication}
	 * @param db database
	 * @param moduleDoc module {@link ODocument}
	 * @param oldVersion previous version
	 * @param newVersion new version
	 * @return {@link ODocument} of a module or null if document was not exchanged
	 */
	public ODocument onUpdate(OrienteerWebApplication app, ODatabaseSession db, ODocument moduleDoc, int oldVersion, int newVersion);
	
	/**
	 * Uninstall this module
	 * @param app {@link OrientDbWebApplication}
	 * @param db database
	 * @param moduleDoc module {@link ODocument}
	 */
	public void onUninstall(OrienteerWebApplication app, ODatabaseSession db, ODocument moduleDoc);
	
	/**
	 * Run this module
	 * @param app {@link OrientDbWebApplication}
	 * @param db database
	 * @param moduleDoc module {@link ODocument}
	 */
	public void onInitialize(OrienteerWebApplication app, ODatabaseSession db, ODocument moduleDoc);
	
	/**
	 * Invoked when module configuration was changed
	 * @param app {@link OrientDbWebApplication}
	 * @param db database
	 * @param moduleDoc module {@link ODocument}
	 */
	public void onConfigurationChange(OrienteerWebApplication app, ODatabaseSession db, ODocument moduleDoc);
	
	/**
	 * Stop this module
	 * @param app {@link OrientDbWebApplication}
	 * @param db database
	 * @param moduleDoc module {@link ODocument}
	 */
	public void onDestroy(OrienteerWebApplication app, ODatabaseSession db, ODocument moduleDoc);
}
