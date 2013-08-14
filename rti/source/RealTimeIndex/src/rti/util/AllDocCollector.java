package rti.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Scorer;

public class AllDocCollector extends Collector {
	List<ScoreDoc> docs = new ArrayList<ScoreDoc>();
	private Scorer scorer;
	private int docBase;

	@Override
	public void setScorer(Scorer scorer) throws IOException {
		this.scorer = scorer;
	}

	@Override
	public void collect(int doc) throws IOException {
		docs.add(new ScoreDoc(doc + docBase, scorer.score()));
	}

	@Override
	public void setNextReader(IndexReader reader, int docBase)
			throws IOException {
		this.docBase = docBase;
	}

	@Override
	public boolean acceptsDocsOutOfOrder() {
		return true;
	}

	public void reset() {
		docs.clear();
	}

	public List<ScoreDoc> getHits() {
		return docs;
	}

}
