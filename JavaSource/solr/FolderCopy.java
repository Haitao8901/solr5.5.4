package solr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.apache.log4j.Logger;

public class FolderCopy {
	private static Logger logger = Logger.getLogger("solrlog"); 
	public static void main(String[] args) throws Exception{
		String from = "D:\\apache-tomcat-7.0.54";
		String to = "C:\\targetsrc";
		copyFolder(from, to);
	}
	
	static void copyFolder(String from, String to) throws Exception{
		logger.info("Copy folder from " + from + " to " + to);
		File toFile = new File(to);
		if(!toFile.exists()){
			logger.info("Create directory " + to);
			toFile.mkdirs();
		}
		
		File fromFile  = new File(from);
		if(fromFile.isDirectory()){
			File[] files = fromFile.listFiles();
			for(File file:files){
				if(file.isDirectory()){
					copyFolder(file.getAbsolutePath(), to+File.separator + file.getName());
				}else{
					String fileName = file.getName();
					copyFile(file, toFile.getAbsolutePath() + File.separator + fileName);
				}
			}
		}else{
			String targetPath = toFile.getAbsolutePath() + File.separator + fromFile.getName();
			copyFile(fromFile, targetPath);
		}
	}
	
	private static void copyFile(File file, String targetPath) throws Exception{
		FileOutputStream fo = null;
		FileInputStream fi = null;
		try{
			logger.info("Copy file " + file.getName() + " to " + targetPath);
			fo = new FileOutputStream(targetPath);
			fi = new FileInputStream(file);
			int byteread = 0; 
			byte[] bytes = new byte[1024];
			while((byteread = fi.read(bytes)) != -1){
				fo.write(bytes, 0, byteread);
			}
			fi.close();
			fo.flush();
			fo.close();
		}finally{
			try{
				if(fi != null){
					fi.close();
				}
				if(fo != null){
					fo.close();
				}
			}catch(Exception e){
				
			}
		}
	}
	
	static void deleteFolder(String path) throws Exception{
		logger.info("Delete folder " + path);
		File deletefile = new File(path);
		if(!deletefile.exists()){
			return;
		}
		if(deletefile.isDirectory()){
			File[] files = deletefile.listFiles();
			for(File file:files){
				if(file.isDirectory()){
					deleteFolder(file.getAbsolutePath());
				}else{
					String fileName = file.getName();
					boolean deleted = file.delete();
					if(deleted){
						logger.info("Delete file " + fileName + " success.");
					}else{
						logger.info("Delete file " + fileName + " failed.");
					}
				}
			}
			boolean deleted = deletefile.delete();
			if(deleted){
				logger.info("Delete folder " + deletefile.getName() + " success.");
			}else{
				logger.info("Delete folder " + deletefile.getName() + " failed.");
			}
		}else{
			String fileName = deletefile.getName();
			boolean deleted = deletefile.delete();
			if(deleted){
				logger.info("Delete file " + fileName + " success.");
			}else{
				logger.info("Delete file " + fileName + " failed.");
			}
		}
	}
}
