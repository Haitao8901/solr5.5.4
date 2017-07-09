package solr;

import java.io.File;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 * Servlet implementation class CopyConfigServlet
 */
public class CreateConfigServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger("solrlog");   
    /**
     * @see HttpServlet#HttpServlet()
     */
    public CreateConfigServlet() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		logger.info("CreateConfig servlet enter......");
		String coreName = request.getParameter("core");
		String solrHome = this.getInitParameter("solrHome");
		String customCFGpath = this.getServletContext().getInitParameter("customConfigPath");
		String defaultCFGpath = this.getServletContext().getInitParameter("defaultConfigPath");
		
		String copyFrom = null;
		String copyTo = solrHome + File.separator + coreName;
		//won't be null
		if(!"".equals(customCFGpath)){
			copyFrom = customCFGpath;
		}else{
			copyFrom = this.getServletContext().getRealPath(defaultCFGpath);
		}
		
		try{
			FolderCopy.copyFolder(copyFrom, copyTo);
			response.getWriter().write("0000-Create config files for " + coreName + " success.");
		}catch(Exception e){
			logger.info("Copy config files failed due to " + e.getMessage());
			logger.error(e);
			logger.info("Delete the folder that may have been created.");
			try{
				FolderCopy.deleteFolder(copyTo);
			}catch(Exception ee){
				logger.info("Delete folder failed due to " + e.getMessage());
				logger.error(e);
			}
			response.getWriter().write("9999-Create config files for " + coreName + " failed due to " + e.getMessage());
		}
	}
}
