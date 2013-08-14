package rti.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.IndexWriter;

import rti.util.Status;

public class IndexThread extends Status implements Runnable {

	static Log logger = LogFactory.getLog(IndexThread.class.getName());

	private IndexFiles indexFiles = null;
	private String docsPath = null;
	private int indexPort = -1;
	private long lastTime = -1;
	private ServerSocket srvSocket = null;

	public IndexThread(IndexWriter ramWriter, int indexPort) {
		this.indexFiles = new IndexFiles(ramWriter);
		this.indexPort = indexPort;
	}

	// change the writer and directory when merging
	public boolean changeWriter(IndexWriter iw) {
		this.indexFiles.changeWriter(iw);
		return true;
	}

	public void stop() {
		super.stop();
		try {
			this.srvSocket.close(); // force to close it
		} catch (IOException e) {
			// do nothing
		}
	}
	
	@Override
	public void run() {

		// create a socket listening for new index request
		try {
			srvSocket = new ServerSocket(indexPort);
			logger.debug("index server socket listening for new request");
		} catch (Exception e) {
			logger.error("IndexThread create socket error. " + e.getMessage());
			System.exit(1);
		}

		while (this.getStatus() >= 0) {

			try {
				Socket connSocket = srvSocket.accept(); // a new request coming
				logger.info("there comes a new index request.");
				BufferedReader reqReader = new BufferedReader(new InputStreamReader(connSocket.getInputStream()));
				
				String[] reqs = reqReader.readLine().split("&"); 
				
				// request format: 1&path&start&end&interval
				// start, end are long
				// interval is integer, seconds
				if(reqs.length == 5 && Integer.parseInt(reqs[0]) == 1) { // a new index request
					// set docsPath
					if (!reqs[1].toUpperCase().equals("NULL"))
						this.docsPath = reqs[1];
					
					long start = Long.parseLong(reqs[2]);
					//long end = Long.parseLong(reqs[3]);
					
					// TODO: set the mergeThread interval
					//long interval = Long.parseLong(reqs[4]);
					
					if(lastTime == -1 || lastTime < start) { // time stamp is right
						final File doc = new File(docsPath);
						if (!doc.exists() || !doc.canRead()) {
							logger.error("Document '"
									+ doc.getAbsolutePath()
									+ "' does not exist or is not readable, please check the path");
							continue;
						} else {
							// call the index function
							this.setStatus(WORKING);
							logger.debug("call the index method.");
							indexFiles.index(docsPath);
						}
						
						this.setStatus(SLEEPING);
						this.lastTime = start;
					}
				}
				
				reqReader.close();
				connSocket.close();
		
			} catch (NumberFormatException e) {
				logger.error("IndexThread user request invalid. " + e.getMessage());
				continue;
			} catch (IllegalArgumentException e) {
				logger.error("IndexThread user request invalid. " + e.getMessage());
				continue;
			} catch (Exception e) {
				//logger.error("IndexThread socket error. " + e.getMessage());
				continue;
			}
		}

		// close everything on exit
		logger.info("closing the index thread...");
		try {
			srvSocket.close();
		} catch (IOException e) {
			// do nothing
		}
		this.indexFiles.close();
	}

}
