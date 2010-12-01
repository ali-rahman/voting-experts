package edu.arizona.ve.algorithm;

import java.util.List;

import edu.arizona.ve.corpus.Corpus;
import edu.arizona.ve.evaluation.Evaluator;
import edu.arizona.ve.trie.Trie;
import edu.arizona.ve.util.NF;
import edu.arizona.ve.util.Utils;

/**
*
* @author  Daniel Hewlett
* The Phoneme to Morpheme algorithm of Tanaka-Ishii and Jin (2006).
*/
public class PhonemeToMorpheme {
	Trie _forwardTrie, _backwardTrie;
	Corpus _corpus;
	private boolean[] _cutPoints;
	int _maxLen;
	
	public PhonemeToMorpheme(Trie forwardTrie, Trie backwardTrie, Corpus corpus, int maxLen) {
		_forwardTrie = forwardTrie;
		_backwardTrie = backwardTrie;
		_corpus = corpus;
		setCutPoints(new boolean[_corpus.getCutPoints().length]);
		_maxLen = maxLen;
	}
	
	public void runAlgorithm(double threshold) {
		boolean[] forward = runForward(threshold);
		boolean[] backward = runBackward(threshold);
		boolean[] combined = Utils.combineUnion(forward, backward);
		setCutPoints(combined);
	}
	
	private double hf(int m, int n) {
		List<String> subList = _corpus.getCleanChars().subList(m, n+1);
		return _forwardTrie.getEntropy(subList);
	}
	
	private double hb(int m, int n, List<String> revChars) {
		List<String> subList = revChars.subList(m, n+1);
		return _backwardTrie.getEntropy(subList);
	}
	
	private boolean[] runForward(double threshold) {
		boolean[] cuts = new boolean[getCutPoints().length];
		
		int m = 0, n = 1;
		
		while (n < cuts.length) {
			double diff = hf(m,n) - hf(m,n-1);
			if (diff > threshold) {
				cuts[n] = true;
			}
			
			if (n > m + _maxLen) {
				m = m + 1;
				n = m + 1;
			} else {
				n = n + 1;
			}
		}
		
		return cuts;
	}
	
	// To use Utils.combine, this should return a reversed set of cutPoints
	private boolean[] runBackward(double threshold) {
		boolean[] cuts = new boolean[getCutPoints().length];
		
		List<String> revChars = _corpus.getReverseCorpus().getCleanChars();
		
		int m = 0, n = 1;
		
		while (n < cuts.length) {
			double diff = hb(m,n,revChars) - hb(m,n-1,revChars);
			if (diff > threshold) {
				cuts[n] = true;
			}
			
			if (n > m + _maxLen) {
				m = m + 1;
				n = m + 1;
			} else {
				n = n + 1;
			}
		}
				
		return cuts; 
	}
	
	public static void main(String[] args) {
//		Corpus c = Corpus.autoLoad("latin-morph", "case");
//		Corpus c = Corpus.autoLoad("latin", "word");
		Corpus c = Corpus.autoLoad("caesar", "nocase");
		int maxLen = 8;
		Trie f = Trie.buildTrie(c, maxLen+1);
		Trie b = Trie.buildTrie(c.getReverseCorpus(), maxLen+1);
		
//		Corpus morph = Corpus.autoLoad("inuktitut-morph", "case");
		
		for (double threshold = 0.7; threshold < 2.0; threshold += 0.1) {
			PhonemeToMorpheme ptm = new PhonemeToMorpheme(f, b, c, maxLen);
			ptm.runAlgorithm(threshold);
			
			System.out.println("THRESHOLD: " + NF.format(threshold));
			Evaluator.evaluate(ptm.getCutPoints(), c.getCutPoints()).printResults();
			//e.printResults();
		}
		
//		CorpusWriter.writeCorpus(c, ptm._cutPoints);
	}

	public void setCutPoints(boolean[] _cutPoints) {
		this._cutPoints = _cutPoints;
	}

	public boolean[] getCutPoints() {
		return _cutPoints;
	}
}
