package rti.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;

import rti.util.Status;

public class SearchThread extends Status implements Runnable {

	static Log logger = LogFactory.getLog(SearchThread.class.getName());

	private Directory fsDir = null;
	// all search process share the same fsReader and merging Reader
	private IndexReader fsReader = null;
	private IndexReader mergingReader = null;
	private IndexReader ramReader = null; // ramReader will be reopened for each request
	
	private ExecutorService pool = null; // user thread pool
	private int searchPort = -1;
	private ServerSocket srvSocket = null;

	public SearchThread(Directory ramDir, Directory fsDir, int searchPort, int threadPoolSize) {
		this.searchPort = searchPort;
		this.fsDir = fsDir;
		this.pool = Executors.newFixedThreadPool(threadPoolSize);
		try {
			ramReader = IndexReader.open(ramDir);
			fsReader = IndexReader.open(fsDir);
		} catch (IOException e) {
			logger.error("SearchThread initiate failed. " + e.getMessage());
			System.exit(1);
		}
	}
	
	// change the reader when merging
	public boolean changeReader(Directory newRamDir) {
		try {
			mergingReader = ramReader;
			ramReader = IndexReader.open(newRamDir);
		} catch (Exception e) {
			logger.error("SearchThread change reader failed. " + e.getMessage());
			System.exit(1);
		}

		return true;
	}

	// reset the reader when merging is done
	public boolean resetReader() throws IOException {
		mergingReader.close();
		mergingReader = null;

		return true;
	}

	// reopen fsReader after merging complete
	public boolean reopenFsReader() {
		try {
			this.fsReader = IndexReader.open(this.fsDir);
		} catch (Exception e) {
			logger.error("SearchThread reopenFsReader failed. "
					+ e.getMessage());
			System.exit(1);
		}
		return true;
	}

	public void setRamReader(IndexReader ramReader) {
		this.ramReader = ramReader;
	}

	public void close() {
		pool.shutdown();
		try {
			this.fsReader.close();
			this.ramReader.close();
			if (this.mergingReader != null)
				this.mergingReader.close();
		} catch (IOException e) {
			logger.error("SearchThread close error. " + e.getMessage());
		}
	}
	
	public void stop() {
		super.stop();
		try {
			this.srvSocket.close();
		} catch (IOException e) {
			// do nothing
		}
	}

	@Override
	public void run() {
		// listen for new search request
		// for each request, start a userThread to handle it
		// if MergeThread.status=WORKING
		// new UserThread(6params)
		// if MergeThread.status=SLEEPING
		// new UserThread(5params)

		// create a socket listening for new search request
		try {
			srvSocket = new ServerSocket(searchPort);
			logger.debug("search server socket listening for new request.");
		} catch (IOException e) {
			logger.error("SearchThread create socket error. " + e.getMessage());
			System.exit(1);
		}

		while (this.getStatus() >= 0) {

			try {
				Socket connSocket = srvSocket.accept(); // a new request coming
				logger.info("there comes a new search request.");
				this.setStatus(WORKING);
			
				// for each request, start a userThread to handle it
				if (RealTimeIndex.getMergeThread().getStatus() == WORKING) {
					// start user thread
					UserThread userThread = new UserThread(ramReader, mergingReader, fsReader, connSocket);			
					pool.execute(userThread);
					
				} else {
					// start user thread
					UserThread userThread = new UserThread(ramReader, fsReader, connSocket);
					pool.execute(userThread);
				}
				logger.debug("new UserThread start successfully.");

			} catch (NumberFormatException e) {
				logger.error("IndexThread user request error. "
						+ e.getMessage());
				continue;
			} catch (Exception e) {
				//logger.error("IndexThread socket error. " + e.getMessage());
				continue;
			}
		}

		// close everything on exit
		logger.info("closing the search thread...");
		try {
			srvSocket.close();
		} catch (IOException e) {
			// do noting
		}

		this.close();
	}

}
