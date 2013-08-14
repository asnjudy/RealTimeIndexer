package rti.core;

import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import net.paoding.analysis.analyzer.PaodingAnalyzer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeFilter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.util.Version;
import org.json.JSONObject;

import rti.util.AllDocCollector;

public class SearchFiles {

	static Log logger = LogFactory.getLog(SearchFiles.class.getName());

	private IndexSearcher ramSearcher = null;
	private IndexSearcher mergingSearcher = null;
	private IndexSearcher fsSearcher = null;

	public SearchFiles(IndexSearcher ramSearcher,
			IndexSearcher mergingSearcher, IndexSearcher fsSearcher) {
		this.ramSearcher = ramSearcher;
		this.mergingSearcher = mergingSearcher;
		this.fsSearcher = fsSearcher;
	}

	public JSONObject docToJSON(Document doc) {
		JSONObject jsonObj = new JSONObject();
		try {

			long longTime = Long.parseLong(doc.get("created_at"));
			Date date = new Date(longTime);
			DateFormat fullFormat = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);
			String fullDate = fullFormat.format(date);

			jsonObj.put("created_at", fullDate);
			jsonObj.put("id", Long.parseLong(doc.get("id")));
			jsonObj.put("text", doc.get("text"));
			jsonObj.put("source", doc.get("source"));
			jsonObj.put("favorited", Boolean.parseBoolean(doc.get("favorited")));
			jsonObj.put("truncated", Boolean.parseBoolean(doc.get("truncated")));
			jsonObj.put("in_reply_to_status_id",
					doc.get("in_reply_to_status_id"));
			jsonObj.put("in_reply_to_user_id", doc.get("in_reply_to_user_id"));
			jsonObj.put("in_reply_to_screen_name",
					doc.get("in_reply_to_screen_name"));
			jsonObj.put("geo", doc.get("geo"));
			jsonObj.put("mid", doc.get("mid"));
			jsonObj.put("reposts_count",
					Long.parseLong(doc.get("reposts_count")));
			jsonObj.put("comments_count",
					Long.parseLong(doc.get("comments_count")));
			jsonObj.put("annotations", doc.get("annotations"));

			jsonObj.put("user", doc.get("user"));
		} catch (Exception e) {
			logger.error("SearchFiles (UserThread) docToJSON failed. "
					+ e.getMessage());
			return null;
		}
		return jsonObj;
	}

	public List<String> search(String keywords, long start, long end, int backnum) {
		// TODO: following
		// if backnum == -1 return all
		// else return top backnum. NOT IMPLEMENTED NOW!
		logger.debug("search process started.");
		try {
			QueryParser parser = new QueryParser(Version.LUCENE_35, "text",
					new PaodingAnalyzer());
			Query query = parser.parse(keywords);
			AllDocCollector ramCollector = new AllDocCollector();
			AllDocCollector fsCollector = new AllDocCollector();
			AllDocCollector mergingCollector = new AllDocCollector();

			Filter filter = null;

			if (start == -1) {
				if (end == -1) {
					filter = null;
				} else {
					long startTime = 0;
					filter = NumericRangeFilter.newLongRange("created_at",
							startTime, end, true, true);
				}
			} else {
				if (end == -1) {
					long endTime = System.currentTimeMillis();
					filter = NumericRangeFilter.newLongRange("created_at",
							start, endTime, true, true);
				} else {
					filter = NumericRangeFilter.newLongRange("created_at",
							start, end, true, true);
				}
			}

			List<String> result = new ArrayList<String>();
			// search ram
			// ramSearcher.search(query, ramCollector);
			ramSearcher.search(query, filter, ramCollector);

			List<ScoreDoc> ramDocs = ramCollector.getHits();

			// search fs
			// fsSearcher.search(query, fsCollector);
			fsSearcher.search(query, filter, fsCollector);
			List<ScoreDoc> fsDocs = fsCollector.getHits();

			// if mergingReader is not null, search it
			if (mergingSearcher != null) {
				// mergingSearcher.search(query, mergingCollector);
				mergingSearcher.search(query, filter, mergingCollector);

				List<ScoreDoc> mergingDocs = mergingCollector.getHits();

				if (backnum < 0) {
					Iterator<ScoreDoc> ramIter = ramDocs.iterator();
					while (ramIter.hasNext()) {
						ScoreDoc scoreDoc = ramIter.next();

						Document doc = ramSearcher.doc(scoreDoc.doc);
						// doc to json.
						JSONObject jsonObj = docToJSON(doc);
						if (jsonObj != null) {
							jsonObj.put("lucenescore",
									(double) (scoreDoc.score));
							String str = jsonObj.toString();
							result.add(str);
						}
					}

					Iterator<ScoreDoc> fsIter = fsDocs.iterator();
					while (fsIter.hasNext()) {
						ScoreDoc scoreDoc = fsIter.next();
						Document doc = fsSearcher.doc(scoreDoc.doc);
						// doc to json.
						JSONObject jsonObj = docToJSON(doc);
						if (jsonObj != null) {
							jsonObj.put("lucenescore",
									(double) (scoreDoc.score));
							String str = jsonObj.toString();
							result.add(str);
						}
					}

					Iterator<ScoreDoc> mergingIter = mergingDocs.iterator();
					while (mergingIter.hasNext()) {
						ScoreDoc scoreDoc = mergingIter.next();
						Document doc = mergingSearcher.doc(scoreDoc.doc);
						// doc to json.
						JSONObject jsonObj = docToJSON(doc);
						if (jsonObj != null) {
							jsonObj.put("lucenescore",
									(double) (scoreDoc.score));
							String str = jsonObj.toString();
							result.add(str);
						}
					}

				} else {

					List<Float> tempScores = new ArrayList<Float>();
					List<Document> tempDocs = new ArrayList<Document>();
					{// merge ramDocs and fsDocs
						int i, j, num;
						for (i = 0, j = 0, num = 0; i < ramDocs.size()
								&& j < fsDocs.size() && num < backnum;) {
							float ramScore = ramDocs.get(i).score;
							float fsScore = fsDocs.get(j).score;

							if (ramScore > fsScore) {
								tempDocs.add(ramSearcher.doc(ramDocs.get(i).doc));
								tempScores.add(new Float(ramScore));

								i = i + 1;
								num = num + 1;
							} else {
								tempDocs.add(fsSearcher.doc(fsDocs.get(j).doc));
								tempScores.add(new Float(fsScore));
								j = j + 1;
								num = num + 1;
							}
						}

						for (; i < ramDocs.size() && num < backnum;) {
							tempDocs.add(ramSearcher.doc(ramDocs.get(i).doc));
							tempScores.add(new Float(ramDocs.get(i).score));
							i = i + 1;
							num = num + 1;
						}

						for (; j < fsDocs.size() && num < backnum;) {
							tempDocs.add(fsSearcher.doc(fsDocs.get(j).doc));
							tempScores.add(new Float(fsDocs.get(j).score));
							j = j + 1;
							num = num + 1;
						}
					}// merge ramDocs and fsDocs

					List<Document> resultDocs = new ArrayList<Document>();
					List<Float> resultScores = new ArrayList<Float>();
					{// merge tempDocs and mergingDocs
						int i, j, num;
						for (i = 0, j = 0, num = 0; i < tempDocs.size()
								&& j < mergingDocs.size() && num < backnum;) {
							float tempScore = tempScores.get(i).floatValue();
							float mergingScore = mergingDocs.get(j).score;

							if (tempScore > mergingScore) {
								resultDocs.add(tempDocs.get(i));
								resultScores.add(tempScores.get(i));
								i = i + 1;
								num = num + 1;
							} else {
								resultDocs.add(mergingSearcher.doc(mergingDocs
										.get(j).doc));
								resultScores.add(new Float(mergingScore));
								j = j + 1;
								num = num + 1;
							}
						}

						for (; i < ramDocs.size() && num < backnum;) {
							resultDocs.add(tempDocs.get(i));
							resultScores.add(tempScores.get(i));
							i = i + 1;
							num = num + 1;
						}

						for (; j < fsDocs.size() && num < backnum;) {
							resultDocs.add(mergingSearcher.doc(mergingDocs
									.get(j).doc));
							resultScores
									.add(new Float(mergingDocs.get(j).score));
							j = j + 1;
							num = num + 1;
						}
					}// merge tempDocs and mergingDocs

					// write resultDocs to result
					for (int i = 0; i < resultDocs.size(); ++i) {
						Document doc = resultDocs.get(i);
						JSONObject jsonObj = docToJSON(doc);
						if (jsonObj != null) {
							jsonObj.put("lucenescore", resultScores.get(i)
									.doubleValue());
							result.add(jsonObj.toString());
						}
					}
				}
			} else {
				if (backnum < 0) {
					Iterator<ScoreDoc> ramIter = ramDocs.iterator();
					while (ramIter.hasNext()) {
						ScoreDoc scoreDoc = ramIter.next();

						Document doc = ramSearcher.doc(scoreDoc.doc);
						// doc to json.
						JSONObject jsonObj = docToJSON(doc);
						if (jsonObj != null) {
							jsonObj.put("lucenescore",
									(double) (scoreDoc.score));
							String str = jsonObj.toString();
							result.add(str);
						}
					}

					Iterator<ScoreDoc> fsIter = fsDocs.iterator();
					while (fsIter.hasNext()) {
						ScoreDoc scoreDoc = fsIter.next();
						Document doc = fsSearcher.doc(scoreDoc.doc);
						// doc to json.
						JSONObject jsonObj = docToJSON(doc);
						if (jsonObj != null) {
							jsonObj.put("lucenescore",
									(double) (scoreDoc.score));
							String str = jsonObj.toString();
							result.add(str);
						}
					}
				} else {
					List<Float> tempScores = new ArrayList<Float>();
					List<Document> tempDocs = new ArrayList<Document>();
					{// merge ramDocs and fsDocs
						int i, j, num;
						for (i = 0, j = 0, num = 0; i < ramDocs.size()
								&& j < fsDocs.size() && num < backnum;) {
							float ramScore = ramDocs.get(i).score;
							float fsScore = fsDocs.get(j).score;

							if (ramScore > fsScore) {
								tempDocs.add(ramSearcher.doc(ramDocs.get(i).doc));
								tempScores.add(new Float(ramScore));

								i = i + 1;
								num = num + 1;
							} else {
								tempDocs.add(fsSearcher.doc(fsDocs.get(j).doc));
								tempScores.add(new Float(fsScore));
								j = j + 1;
								num = num + 1;
							}
						}

						for (; i < ramDocs.size() && num < backnum;) {
							tempDocs.add(ramSearcher.doc(ramDocs.get(i).doc));
							tempScores.add(new Float(ramDocs.get(i).score));
							i = i + 1;
							num = num + 1;
						}

						for (; j < fsDocs.size() && num < backnum;) {
							tempDocs.add(fsSearcher.doc(fsDocs.get(j).doc));
							tempScores.add(new Float(fsDocs.get(j).score));
							j = j + 1;
							num = num + 1;
						}
					}// merge ramDocs and fsDocs

					// write tempDocs to result
					for (int i = 0; i < tempDocs.size(); ++i) {
						Document doc = tempDocs.get(i);
						JSONObject jsonObj = docToJSON(doc);
						if (jsonObj != null) {
							jsonObj.put("lucenescore", tempScores.get(i)
									.doubleValue());
							result.add(jsonObj.toString());
						}
					}
				}
			}

			// merge the search result and return
			logger.debug("search process end.");
			return result;
		} catch (Exception e) {
			logger.error("SearchFiles (UserThread) search index failed. "
					+ e.getMessage());
			return null;
		}
	}

	public void close() {
		try {
			if (this.mergingSearcher != null)
				this.mergingSearcher.close();
			this.ramSearcher.close();
			this.fsSearcher.close();
		} catch (Exception e) {
			logger.error("SearchFiles (UserThread) close error. "
					+ e.getMessage());
		}
	}
}
