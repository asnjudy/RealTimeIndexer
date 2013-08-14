package rti.core;

import java.io.IOException;
import java.sql.Timestamp;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import rti.util.Status;

public class MergeThread extends Status implements Runnable {

	static Log logger = LogFactory.getLog(MergeThread.class.getName());

	private long mergeInterval = -1;
	private Directory ramDir = null;
	private IndexWriter fsWriter = null;
	private Timestamp lastMerge = null;

	public void setMergeInterval(long mergeInterval) {
		this.mergeInterval = mergeInterval;
	}

	public MergeThread(Directory ramDir, IndexWriter fsWriter, long mergeInterval) {
		this.ramDir = ramDir;
		this.mergeInterval = mergeInterval;
		this.lastMerge = new Timestamp(System.currentTimeMillis());
		this.fsWriter = fsWriter;
	}

	@Override
	public void run() {

		while (this.getStatus() >= 0) {
			// check the interval and ram status then start merge process
			Timestamp now = new Timestamp(System.currentTimeMillis());

			if (now.getTime() - lastMerge.getTime() > this.mergeInterval * 1000) {
				// TODO: check whether ramDir has index in it
				logger.info("start merging the ram index to disk.");
				this.setStatus(WORKING);
				try {
					// before merge
					Directory newRamDir = new RAMDirectory();
					// initial the dir
					IndexWriter iw = RealTimeIndex.initDir(newRamDir, true);
					RealTimeIndex.getSearchThread().changeReader(newRamDir);
					RealTimeIndex.getIndexThread().changeWriter(iw); // synchronized method
					
					// start merge process
					this.merge(this.ramDir);

					// after merge
					RealTimeIndex.getSearchThread().resetReader();
					RealTimeIndex.getSearchThread().reopenFsReader();
					//this.ramDir.close(); // some userThread may be still using it
					this.ramDir = newRamDir;
					this.lastMerge = now;
				} catch (Exception e) {
					logger.error("MergeThread merge index failed. "
							+ e.getMessage());
					System.exit(1);
				}
				this.setStatus(SLEEPING);
				logger.info("merging process end.");
			}
		}

		// write ram index to fs on exit
		logger.info("closing the merge thread...");
		this.close();
	}

	// merge ram to fs
	public void merge(Directory rd) throws CorruptIndexException, IOException {
		logger.debug("start to flush ram to fs.");
		fsWriter.addIndexes(new Directory[] { rd });
		fsWriter.commit();
		logger.debug("flush ram to fs end.");
	}

	public void close() {
		try {
			this.setStatus(WORKING);
			this.merge(ramDir);
			fsWriter.close();
		} catch (Exception e) {
			logger.error("MergeThread merge ram index to fs on exit failed. "
					+ e.getMessage());
		}
		this.setStatus(SLEEPING);
	}

}
