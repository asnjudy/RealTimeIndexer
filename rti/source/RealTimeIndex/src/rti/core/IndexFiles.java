package rti.core;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.util.NumericUtils;
import org.json.JSONObject;

import rti.util.JSONWEIBO;

public class IndexFiles {

	static Log logger = LogFactory.getLog(IndexFiles.class.getName());

	private IndexWriter ramWriter = null;
	private byte[] ramLock = new byte[0]; // ramwriter's lock

	public IndexFiles(IndexWriter ramWriter) {
			this.ramWriter = ramWriter;
	}

	public void changeWriter(IndexWriter iw) {
		synchronized (ramLock) {
			try {
				this.ramWriter.close();
				this.ramWriter = iw;
			} catch (Exception e) {
				logger.error("IndexFiles (IndexThread) change writer failed. "
						+ e.getMessage());
				System.exit(1);
			}
		}
	}
	
	// do sth on exit
	public void close() {
		try {
			this.ramWriter.close();
		} catch (Exception e) {
			logger.error("IndexFiles (IndexThread) close ramWriter error. " + e.getMessage());
		}
	}

	// create index
	public void index(String docsPath) {
		synchronized (ramLock) {
			// create index here
			int i = 0;
			int j = 0;
			
			try
			{
				JSONWEIBO.toJSONArray(docsPath);
				
				logger.info("index process start: " + JSONWEIBO.getLength() + " files.");
				
				for (i = 0;i < JSONWEIBO.getLength();i++)
				{
					Document doc = new Document();
					JSONObject jj = JSONWEIBO.getJSONObject(i);
					//Iterator keys = jj.keys();
				    
					DateFormat fullFormat = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);
					Date date  = fullFormat.parse(jj.getString("created_at"));
					doc.add(new NumericField(JSONWEIBO.dateLabel,NumericUtils.PRECISION_STEP_DEFAULT,Field.Store.YES, true).setLongValue(date.getTime()));
					 
					for (j = 0;j < JSONWEIBO.strLabel.length;j++)
					{
						if (jj.has(JSONWEIBO.strLabel[j]))
						{
							Field labFiled = null;
							if ("text".equalsIgnoreCase(JSONWEIBO.strLabel[j]))
							{
								labFiled = new Field("text",jj.getString("text"),Field.Store.YES, Field.Index.ANALYZED);
								labFiled.setIndexOptions(IndexOptions.DOCS_ONLY);
							}
							else
							{
								labFiled = new Field(JSONWEIBO.strLabel[j], jj.getString(JSONWEIBO.strLabel[j]),Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS);
								labFiled.setIndexOptions(IndexOptions.DOCS_ONLY);
							}
					        doc.add(labFiled);
						}
						else
						{
							doc.add(new Field(JSONWEIBO.strLabel[j],"null",Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
						}
					}
					
					for (j = 0;j < JSONWEIBO.longLabel.length;j++)
					{
						if (jj.has(JSONWEIBO.longLabel[j]))
						{
							doc.add(new Field(JSONWEIBO.longLabel[j],Long.toString(jj.getLong(JSONWEIBO.longLabel[j])),Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
						}
						else
						{
							doc.add(new Field(JSONWEIBO.longLabel[j],Long.toString(0),Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
						}
					}
					
					for (j = 0;j < JSONWEIBO.boolLabel.length;j++)
					{
						if (jj.has(JSONWEIBO.boolLabel[j]) && jj.getBoolean(JSONWEIBO.boolLabel[j]))
						{
							doc.add(new Field(JSONWEIBO.boolLabel[j],"true",Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
						}
						else
						{
							doc.add(new Field(JSONWEIBO.boolLabel[j],"false",Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
						}
					}
					
					for (j = 0;j < JSONWEIBO.objLabel.length;j++)
					{
							if (jj.has(JSONWEIBO.objLabel[j]))
							{
								//String a = jj.get(JSONWEIBO.longLabel[j]).toString();
								doc.add(new Field(JSONWEIBO.objLabel[j],jj.get(JSONWEIBO.objLabel[j]).toString(),Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
							}
							else
							{
								doc.add(new Field(JSONWEIBO.objLabel[j],"null",Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
							}
					}
					
					ramWriter.addDocument(doc);
				}
				
				ramWriter.commit();
				logger.info("index process end.");
			}
			catch(Exception e)
			{
				logger.error("IndexFiles (IndexThread) add index failed. " + e.getMessage());
			}
		}
	}
}
