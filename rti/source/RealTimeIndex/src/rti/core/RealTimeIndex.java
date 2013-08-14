package rti.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import net.paoding.analysis.analyzer.PaodingAnalyzer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

public class RealTimeIndex {

	static Log logger = LogFactory.getLog(RealTimeIndex.class.getName());

	private static String indexPath = null;
	private static long mergeInterval = 60 * 30; // 30 minutes by default. merge ram to fs.
	private static int indexPort = 9081;
	private static int searchPort = 9091;
	private static int threadPoolSize = 100; // the user thread pool size
	private static SearchThread searchThread = null;
	private static IndexThread indexThread = null;
	private static MergeThread mergeThread = null;
	
	public static final String CHARSET = "UTF-8";

	public static SearchThread getSearchThread() {
		return searchThread;
	}

	public static IndexThread getIndexThread() {
		return indexThread;
	}

	public static MergeThread getMergeThread() {
		return mergeThread;
	}

	public static IndexWriter initDir (Directory dir, boolean create) {
		Analyzer analyzer = new PaodingAnalyzer(); // create analyzer
		IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_35, analyzer);
		if (create) {
			// Create a new index in the directory, removing any previously
			// indexed documents:
			iwc.setOpenMode(OpenMode.CREATE);
		} else {
			// Add new documents to an existing index:
			iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
		}
		
		// TODO: iwc optimization
		
		IndexWriter writer = null;
		try {
			writer = new IndexWriter(dir, iwc);
			writer.commit();
		} catch (Exception e) {
			logger.error("initial dir error. " + e.getMessage());
		}
		return writer;
	}
	
	public static void main(String[] args) {

		String usage = "\tjava rti.core.RealTimeIndex"
				+ " -index INDEX_PATH [-interval MERGE_INTERVAL (s)] [-indexport INDEX_PORT]"
				+ "[-searchport SEARCH_PORT] [-update]\n"
				+ "or: \tjava rti.core.RealTimeIndex"
				+ " -ind INDEX_PATH [-int MERGE_INTERVAL (s)] [-ip INDEX_PORT]"
				+ "[-sp SEARCH_PORT] [-u]\n\n"
				+ "This creats a Lucene index in INDEX_PATH that can be searched with SearchFiles";

		boolean create = true;

		// handle the parameters
		boolean errorParam = false;
		for (int i = 0; i < args.length; i++) {
			try {
				if ("-index".equals(args[i]) || "-ind".equals(args[i])) {
					indexPath = args[i + 1];
					i++;
				} else if ("-interval".equals(args[i]) || "-int".equals(args[i])) {
					mergeInterval = Long.parseLong(args[i + 1]);
					i++;
				} else if ("-indexport".equals(args[i]) || "-ip".equals(args[i])) {
					indexPort = Integer.parseInt(args[i + 1]);
					i++;
				} else if ("-searchport".equals(args[i]) || "-sp".equals(args[i])) {
					searchPort = Integer.parseInt(args[i + 1]);
					i++;
				} else if ("-tpoolsize".equals(args[i]) || "-tps".equals(args[i])) {
					threadPoolSize = Integer.parseInt(args[i + 1]);
					i++;
				} else if ("-update".equals(args[i]) || "-u".equals(args[i])) {
					create = false;
				}
			} catch (Exception e) {
				errorParam = true;
				break;
			}
		}

		if (indexPath == null || errorParam) {
			System.err.println("Usage: " + usage);
			System.exit(1);
		}

		try {
			// disk index
			Directory fsDir = FSDirectory.open(new File(indexPath));

			// ram index
			Directory ramDir = new RAMDirectory();
			
			// initial the ram dir
			IndexWriter ramWriter = initDir(ramDir, true);
			
			// initial the fs dir
			IndexWriter fsWriter = initDir(fsDir, create);
			
			// start index thread
			indexThread = new IndexThread(ramWriter, indexPort);
			Thread index = new Thread(indexThread, "indexThread");
			index.start();
			
			// start index merge thread
			mergeThread = new MergeThread(ramDir, fsWriter, mergeInterval);
			Thread merge = new Thread(mergeThread, "mergeThread");
			merge.start();
			
			// start search thread
			searchThread = new SearchThread(ramDir, fsDir, searchPort, threadPoolSize);
			Thread search = new Thread(searchThread, "searchThread");
			search.start();
			
			// release object
			fsDir = null;
			ramDir = null;

		} catch (Exception e) {
			logger.error("Start error. " + e.getMessage());
			System.exit(1);
		}
		
		logger.info("Real Time Index System successfully started.");
		// output control options
		System.out.println("\nATTENTION: At any time, you can input q [enter] to exit.\n");
		BufferedReader ctrlReader = new BufferedReader(new InputStreamReader(System.in));
		String ctrl = "";
		while (true) {
			// read use's control
			try {
				ctrl = ctrlReader.readLine();
			} catch (IOException e) {
				logger.error("Read control option from user error. " + e.getMessage());
				continue;
			}
			
			if(ctrl.equals("q") || ctrl.equals("Q")) {
				// stop all threads
				getIndexThread().stop();
				getSearchThread().stop();
				getMergeThread().stop();
				
				try {
					ctrlReader.close();
				} catch (IOException e) {
					// do nothing
				}
				
				break;
			}
		}
	}

}
