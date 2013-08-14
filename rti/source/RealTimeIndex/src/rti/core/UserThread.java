package rti.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;

import rti.util.Status;

public class UserThread extends Status implements Runnable {

	//static Logger logger = Logger.getLogger(UserThread.class.getName());
	static Log logger = LogFactory.getLog(UserThread.class.getName());
	
	private static String head = "{\"statuses\": ["; // result head
	private static String tail = "]}"; // result tail

	private Socket socket = null;
	private SearchFiles searchFiles = null;
	private IndexReader ramReader = null;

	public UserThread(IndexReader ramReader, IndexReader fsReader, Socket socket) throws IOException {
		this.ramReader = ramReader;
		this.socket = socket;

		if (this.reopenRamReader()) // change SearchThread's ramReader
			RealTimeIndex.getSearchThread().setRamReader(this.ramReader);

		this.searchFiles = new SearchFiles(new IndexSearcher(this.ramReader), null,
				new IndexSearcher(fsReader));
	}

	public UserThread(IndexReader ramReader, IndexReader mergingReader, IndexReader fsReader, Socket socket)
			throws IOException {
		this.ramReader = ramReader;
		this.socket = socket;

		if (this.reopenRamReader()) // change SearchThread's ramReader
			RealTimeIndex.getSearchThread().setRamReader(this.ramReader);

		this.searchFiles = new SearchFiles(new IndexSearcher(this.ramReader),
				new IndexSearcher(mergingReader), new IndexSearcher(fsReader));
	}

	public boolean reopenRamReader() throws IOException {
		// if IndexThread.status=WORKING then we cannot reopen it
		// if IndexThread.status=SLEEPING then we reopen it
		if (RealTimeIndex.getIndexThread().getStatus() == SLEEPING) {
			IndexReader tempReader = IndexReader.openIfChanged(ramReader);
			if (tempReader != null) {
				this.ramReader = tempReader;
				return true;
			}
		}
		return false;
	}

	@Override
	public void run() {

		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), RealTimeIndex.CHARSET));
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), RealTimeIndex.CHARSET));
			this.setStatus(WORKING);

			String req = in.readLine();
			logger.debug("new request: " + req);
			
			String[] reqs = req.split("&");
			
			// requests format: keywords&start&end&back_num
			// start, end and back_num may be -1, if they are not used.
			// encode: utf-8
			if (reqs.length == 4) { // a new valid search request	
				String keyword = reqs[0];
				long start = Long.parseLong(reqs[1]);
				long end = Long.parseLong(reqs[2]);
				int backnum = Integer.parseInt(reqs[3]);
				
				logger.info(socket.getInetAddress() + " search for '" + keyword  + "', back number " + backnum + ".");
				
				// call search
				List<String> result = this.searchFiles.search(keyword, start, end, backnum);
				
				// return results to client
				try {
					out.write(head);
					if (result != null && result.size() != 0) {
						logger.debug("get " + result.size() + " search hit(s).");
						int i = 0;
						for(; i < result.size() - 1; i++) {
							out.write(result.get(i) + ", ");
						}
						out.write(result.get(i));
					} else {
						logger.debug("get 0 search hit.");
					}
					out.write(tail);
					out.write("\r\n");
					out.flush();
					logger.debug("send the search results to client successfully.");
				} catch (NumberFormatException e) {
					logger.error("UserThread user request error. "
							+ e.getMessage());
				} catch (IOException e) {
					logger.error("send search results to client error. " + e.getMessage());
				}
			} else {
				out.write("INVALID REQUEST FORMAT.\r\n");
				out.flush();
				logger.debug("invalid request.");
			}
			
			// close everything on exit
			//logger.debug("closing the user thread...");
			this.searchFiles.close();
			in.close();
			out.close();
			this.socket.close();
			
		} catch (IOException e) {
			logger.error("UserThread socket error. " + e.getMessage());
		}
	}

}
