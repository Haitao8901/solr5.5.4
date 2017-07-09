package solr;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Properties;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.util.IOUtils;
import org.apache.solr.core.ConfigSet;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.CorePropertiesLocator;
import org.apache.solr.core.NodeConfig;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.CoreContainer.CoreLoadFailure;
import org.apache.solr.logging.MDCLoggingContext;
import org.apache.solr.util.SolrIdentifierValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * There must be a exist solrconfig.xml before invoke the create http request.
 * This class is used to create a new core without the solrconfig.xml
 * because system will create it automatically.
 * @author Cyber.haitao
 * */
public class CyberCoreContainer extends CoreContainer {
	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * Default config file home.
	 * When create a new core, files under this path will be cloned to the new core
	 */
	protected String defaultConfigHome;

	public CyberCoreContainer(String defaultConfig, NodeConfig config, Properties properties, boolean asyncSolrCoreLoad) {
		super(config, properties, new CorePropertiesLocator(config.getCoreRootDirectory()), asyncSolrCoreLoad);
		this.defaultConfigHome = defaultConfig;
	}
	
	protected SolrCore create(CoreDescriptor dcore, boolean publishState) {
		//First create related config directory and files
		String copyTo = solrHome + File.separator + dcore.getName();
		//won't be null
		String copyFrom = defaultConfigHome ;
		try{
			FolderCopy.copyFolder(copyFrom, copyTo);
		}catch(Exception e){
			logger.info("Copy config files failed due to " + e.getMessage());
			logger.error(e.getMessage(), e);
			logger.info("Delete the folder that may have been created.");
			try{
				FolderCopy.deleteFolder(copyTo);
			}catch(Exception ee){
				logger.info("Delete folder failed due to " + e.getMessage());
				logger.error(ee.getMessage(), ee);
			}
			
		    SolrException solrException = new SolrException(ErrorCode.SERVER_ERROR, "Unable to create core [" + dcore.getName() + "], create config files failed.", e);
		    throw solrException;
		}
		return super.create(dcore, publishState);
	}
	
	  /**
	   *
	   * This is invoked when start the system filter and load the exist core.
	   * As we have override the create method in subclass CyberCoreContainer,
	   * so here need to skip the copy config file process.
	   * Because the core have been created. No need to copy the default config file to the core.
	   * 
	   * */
	  protected SolrCore createFromLoad(CoreDescriptor dcore, boolean publishState) {
		  return super.create(dcore, publishState);
	  }
}
