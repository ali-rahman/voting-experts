package edu.arizona.trie;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import edu.arizona.corpus.Corpus;
import edu.arizona.util.NF;
import edu.arizona.util.Printer;
import edu.arizona.util.Utils;
/**
 *
 * @author  Wesley Kerr, Daniel Hewlett
 */
public class Trie {
	
	// Special members for root node
	public int maxDepthSeen = 0;
	private TreeMap<Integer,StatNode> statistics = null; 
	
	// All nodes have these members
	public List<String> prefix;
	public TreeMap<String,Trie> children;
	
	public double  freq;
	public double  stdFreq;

	public double  boundaryEntropy;
	public double  stdBoundaryEntropy;
	
	public double  probability = -1;
	public double  internalEntropy;
	public double  stdInternalEntropy;

	public int depth = -1;

	/** Creates a new instance of Trie */
	public Trie() {
		children = new TreeMap<String,Trie>();
	}

	/**
	 * process the statistics for the entire tree.
	 * Requires touching all of the nodes within the tree.
	 * Depth level traversal.
	 */
	public void generateStatistics() {
		statistics = new TreeMap<Integer,StatNode>();

		for (int i = 1; i <= maxDepthSeen; ++i) {
			statistics.put(new Integer(i), new StatNode());
		}

		fillStatistics(statistics);

		for (int i = 1; i <= maxDepthSeen; ++i) {
			StatNode s = (StatNode) statistics.get(new Integer(i));
			s.calculate();
		}
		standardize(statistics);
	}

	/**
	 * this just displays the stats for for a given depth
	 * @param depth
	 */
	public void printStats(int depth) {
		if (depth != -1) {
			StatNode s = (StatNode) statistics.get(new Integer(depth));
			s.print(depth);
			return;
		}

		for (int i = 1; i <= maxDepthSeen; ++i) {
			StatNode s = (StatNode) statistics.get(new Integer(i));
			s.print(i);
		}
	}	

	public StatNode getStatNode(int depth) {
		return statistics.get(depth);
	}	
	
	/**
	 * wrapper function so that we can just call the insert
	 * on the sequence and it will keep track of what was
	 * inserted.
	 * @param sequence
	 * @param count
	 */
	public Trie put(List<String> sequence, double count)  { 
		maxDepthSeen = Math.max(maxDepthSeen, sequence.size());
		return put(new LinkedList<String>(), sequence, count);
	}
	
	/**
	 * 
	 * @param prefix
	 * @param suffix
	 * @param count
	 */
	public Trie put(List<String> prefix, List<String> suffix, double count) { 
		this.prefix = prefix;
		this.freq += count;
		
		//System.out.println("Pre " + prefix + " Suf " + suffix + " Freq " + freq);
		if (suffix != null && suffix.size() > 0) { 
			String obj = suffix.get(0);
			Trie t = children.get(obj);
			if (t == null) { 
				t = new Trie();
				children.put(obj, t);
			}

			List<String> newPrefix = new ArrayList<String>(prefix);
			newPrefix.add(obj);
			
			List<String> newSuffix = new ArrayList<String>(suffix.subList(1, suffix.size()));
			return t.put(newPrefix, newSuffix, count);
		} else { 
			return this;
		}
	}

	protected void calculateEntropy() {
		double f = 0;
		double sum = 0;

		if (children.size() == 0) {
			boundaryEntropy = 0;
			return;
		}

		for (Trie t : children.values()) { 
			sum += t.freq;
		}

		boundaryEntropy  = 0;
		if (sum > 0) {
			double norm = 1.0 / sum;
			for (Trie t : children.values()) { 
				f = norm * t.freq;
				boundaryEntropy += ( -f * Math.log(f));
			}
		}
	}
	
	// this will get called on the root node, passing total frequency down the tree
	protected void calculateInternalEntropy() {
		if (probability == -1) { // this means we are the root node
			for (Trie child : children.values()) {
				child.probHelper(freq);
			}
		} // else do nothing, it's already there
	}
	
	protected void probHelper(double totalFrequency) {
		probability = freq / totalFrequency;
		internalEntropy = -Math.log(probability);
		
		for (Trie child : children.values()) {
			child.probHelper(totalFrequency);
		}
	}

	public void fillStatistics(TreeMap<Integer,StatNode> stats) {
		calculateInternalEntropy();
		calculateEntropy();
		if (prefix != null && !prefix.isEmpty()) {
			StatNode stat = (StatNode) stats.get(new Integer(prefix.size()));
			stat.frequencies.add(new Double(freq));
			stat.internalEntropies.add(internalEntropy);
			stat.boundaryEntropies.add(new Double(boundaryEntropy));
		}

		if (children.size() == 0) return;

		for (Trie t : children.values()) { 
			t.fillStatistics(stats);
		}
	}

	public void standardize(TreeMap<Integer,StatNode> stats) {
		if (prefix != null && !prefix.isEmpty()) {
			StatNode stat = (StatNode) stats.get(new Integer(prefix.size()));
			stdFreq = (stat.stdDevFreq == 0) ? 0 : (freq - stat.meanFreq) / stat.stdDevFreq;
			stdInternalEntropy = (stat.stdDevIntEnt == 0) ? 0 : (internalEntropy - stat.meanIntEnt) / stat.stdDevIntEnt;
			stdBoundaryEntropy = (stat.stdDevEnt == 0) ? 0 : (boundaryEntropy - stat.meanEnt) / stat.stdDevEnt;
		}

		if (children.size() == 0) return;

		for (Trie t : children.values()) {
			t.standardize(stats);
		}
	}

	public HashMap<String,Integer> getWords() {
		HashMap<String, Integer> result = new HashMap<String,Integer>();
		
		for (Map.Entry<String,Trie> e : children.entrySet() ) {
			String child = e.getKey();
			Trie subTrie = e.getValue();
			
			if (child.equals("*")) {
				result.put(Utils.fromList(subTrie.prefix), (int) subTrie.freq);
			} else {
				result.putAll(subTrie.getWords());
			}
		}
		
		return result;
	}
	
	/**
	 * get the children of the specified sequence of objects
	 * @param sequence
	 * @return
	 */
	public TreeMap<String,Trie> getChildren(List<String> sequence) {
		if (sequence == null || sequence.size() == 0) {
			return children;
		}

		String child = sequence.get(0);
		if (!children.containsKey(child))
			return new TreeMap<String,Trie>();

		Trie t = children.get(child);
		List<String> suffix = sequence.subList(1, sequence.size());
		return t.getChildren(suffix);
	}

	public double getFreq(List<String> sequence) {
		if (sequence == null || sequence.size() == 0) 
			return freq;

		String child = sequence.get(0);
		if (!children.containsKey(child))
			return 0;

		Trie t = children.get(child);
		List<String> suffix = sequence.subList(1,sequence.size());
		return t.getFreq(suffix);
	}
	
	/**
	 * returns the standardized frequence for the sequence
	 * @param word
	 * @return
	 */
	public double getStdFreq(List<String> sequence) {
		if (sequence == null || sequence.size() == 0) 
			return stdFreq;

		String child = sequence.get(0);
		if (!children.containsKey(child))
			return 0;

		Trie t = children.get(child);
		List<String> suffix = sequence.subList(1,sequence.size());
		return t.getStdFreq(suffix);
	}

	public double getStdIntEntropy(List<String> sequence) {
		if (sequence == null || sequence.isEmpty()) 
			return stdInternalEntropy;
		
		String child = sequence.get(0);
		if (!children.containsKey(child))
			return Double.POSITIVE_INFINITY;   
						// NB: zero is OK here because the stdIntEnropy is always negative. Really?
						// NO NO NO the STANDARD entropy is not always negative - it's a standard normal dist
						// ... dumbass!

		Trie t = children.get(child);
		List<String> suffix = sequence.subList(1,sequence.size());
		return t.getStdIntEntropy(suffix);
	}
	
	/**
	 * return the standard entropy for the sequence
	 * @param word
	 * @return
	 */
	public double getStdEntropy(List<String> sequence) {
		if (sequence == null || sequence.size() == 0) 
			return stdBoundaryEntropy;

		String child = sequence.get(0);
		if (!children.containsKey(child))
			return 0;

		Trie t = children.get(child);
		List<String> suffix = sequence.subList(1,sequence.size());
		return t.getStdEntropy(suffix);
	}

	/**
	 * return the standard entropy for the sequence
	 * @param word
	 * @return
	 */
	public double getEntropy(List<String> sequence) {
		if (sequence == null || sequence.size() == 0)
			return boundaryEntropy;

		String child = sequence.get(0);
		if (!children.containsKey(child))
			return 0;

		Trie t = children.get(child);
		List<String> suffix = sequence.subList(1,sequence.size());
		return t.getEntropy(suffix);
	}


	/**
	 * return the frequency information for the sequence
	 * @param sequence
	 * @return
	 */
	public List<Double> getCountList(List<String> sequence) { 
		if (sequence == null || sequence.size() == 0) {
			List<Double> l = new LinkedList<Double>();
			l.add(new Double(freq));
			return l;
		}

		String child = sequence.get(0);
		if (!children.containsKey(child))
			return null;

		Trie t = children.get(child);
		List<String> suffix = sequence.subList(1,sequence.size());
		List<Double> l = t.getCountList(suffix);
		if (l == null) 
			return null;

		if (!prefix.equals(""))
			l.add(0, new Double(freq));
		return l;
	}

	/** 
	 * print information about the tree
	 * @param path
	 * @param tab
	 * @param nf
	 */
	public void print(String path, String tab) {
		if (!children.containsKey(path))
			return;

		Trie t = children.get(path);
		t.display("", true);
	}
	
	/**
	 * print information for sequence (stored in trie)
	 * @param word
	 * @param nf
	 */
	public void printSequence(List<String> sequence) {
		if (sequence != null && sequence.size() > 0) {
			String child = sequence.get(0);

			if (!children.containsKey(child))
				return;

			Trie t = children.get(child);
			t.printSequence(sequence.subList(1, sequence.size()));
		} else {
			printData("");
		}
	}

	/**
	 * perform the actually printing
	 * @param tab
	 * @param nf
	 */
	protected void printData(String tab) {
		System.out.print(tab);
		for (String t : prefix) 
			System.out.print(t + " ");
		System.out.print("Freq: " + NF.format(freq) + " ");
		System.out.print("StdFreq: " + NF.format(stdFreq) + " ");
		System.out.print("Entropy: " + NF.format(boundaryEntropy) + " ");
		System.out.print("StdEntropy: " + NF.format(stdBoundaryEntropy) + " ");
		System.out.print("IntEntropy: " + NF.format(internalEntropy) + " ");
		System.out.print("StdIntEntropy: " + NF.format(stdInternalEntropy) + " ");
		System.out.println();
	}

	/**
	 * decide what to print
	 * @param tab
	 * @param nf
	 * @param all
	 */
	public void display(String tab, boolean all) {
		printData(tab);

		if (children.size() == 0 || !all) return;

		printChildren(tab, all);
	}

	protected void printChildren(String tab, boolean all) {
		for (Trie t : children.values()) { 
			t.display(tab + " ", all);
		}
	}

	/**
	 * 
	 * @param word
	 * @param nf
	 * @param all
	 */
	public void printExtensions(List<String> sequence, boolean all) {
		if (sequence == null || sequence.size() == 0) {
			printChildren("", all);
			return;
		}

		String child = sequence.get(0);
		if (!children.containsKey(child))
			return;

		Trie t = children.get(child);
		t.printExtensions(sequence.subList(1, sequence.size()), all);

	}

	public void levelOrder() {
		LinkedList<Trie> l = new LinkedList<Trie>();
		l.add(this);
		while (l.size() > 0) {
			Trie curr = l.removeFirst();
			curr.printData("");

			for (Trie t : curr.children.values()) { 
				l.addLast(t);
			}
		}
	}
	
	public void setFrequency(double freq) { 
		this.freq = freq;
	}
	
	public void toXML(PrintWriter out) { 
		out.write("<Trie>\n");
		
		LinkedList<Trie> l = new LinkedList<Trie>();
		l.add(this);
		while (l.size() > 0) { 
			Trie curr = l.removeFirst();
			curr.xmlDetail(out);
			
			for (Trie t : curr.children.values()) { 
				l.addLast(t);
			}
		}
		out.write("</Trie>\n");
		out.flush();
	}
	
	public void xmlDetail(PrintWriter out) { 
		out.write("  <TrieNode ");
		out.write("prefix=\"");
		Printer.printList(prefix, out);
		out.write("\" ");
		out.write("freq=\"" + freq + "\" ");
		out.write("/>\n");
	}
	
	
	
	
	
	// STATIC METHODS (old style)
	
	/**
	 * given a set of tokens and a window size make sure to 
	 * add all of the sets of tokens with the window size
	 * @param tokens
	 * @param windowSize
	 */
	public static void addAll(Trie root, List<String> tokens, int windowSize) {
		root.depth = windowSize;
		
		for (int i = 0; i < tokens.size(); ++i) { 
			List<String> tmp = null;
			if (i+windowSize > tokens.size()) { 
				tmp = tokens.subList(i, tokens.size());
			} else { 
				tmp = tokens.subList(i, i+windowSize);
			}
			
			root.put(tmp, 1);
		}		
	}
	
	public static Trie buildTrie(Corpus c, int depth) {
		return buildTrie(c.getCleanChars(), depth);
	}
	
	public static Trie buildTrie(List<String> tokens, int depth) {
		Trie root = new Trie();
		addAll(root,tokens,depth);
		root.generateStatistics();
		return root;
	}
	
	public static Trie buildBackwardTrie(Corpus c, int depth) {
		return buildBackwardTrie(c.getCleanChars(), depth);
	}
	
	public static Trie buildBackwardTrie(List<String> tokens, int depth) {
		ArrayList<String> backwardCorpus = new ArrayList<String>(tokens);
		Collections.reverse(backwardCorpus);
		return buildTrie(backwardCorpus, depth);
	}
	
	public static void extractWords(Trie t) {
		List<String> begin = Arrays.asList(new String[]{"*"});
		
		HashMap<String,Integer> wordCounts = new HashMap<String,Integer>();
		
		// The sub-trie rooted at "*" - the "lexicon"
		TreeMap<String, Trie> lexicon = t.getChildren(begin);
		
		for (Trie subTrie : lexicon.values()) {
			wordCounts.putAll(subTrie.getWords());
		}
		
		class Holder implements Comparable<Holder> {
			String word;
			int count;
			
			public int compareTo(Holder o) {
				return -(new Integer(count).compareTo(o.count));
			}
		}

		Vector<Holder> v = new Vector<Holder>();
		for (Map.Entry<String,Integer> e : wordCounts.entrySet()) {
			Holder h = new Holder(); h.word = e.getKey().substring(1, e.getKey().length()-1); h.count = e.getValue().intValue();
			v.add(h);
		}
		
		Collections.sort(v);
		
		System.out.println("WORDS! at last.");
		try {
			PrintStream out = new PrintStream("lexica/seed.lex");
			
			for (Holder h : v) {
				if (h.count > 50) {
					out.println(h.word + "\t" + h.count);
				}
			}
			
			out.close();
		} catch (FileNotFoundException e1) { e1.printStackTrace(); }
	}
	
	public static void loadFromFile(final Trie root, BufferedReader in) 
			throws Exception { 
		try {
			SAXParserFactory spf = SAXParserFactory.newInstance();
			spf.setValidating(false);
			
			SAXParser sp = spf.newSAXParser();
			InputSource input = new InputSource(in);
			sp.parse(input, new DefaultHandler() { 
				public void startElement(String uri, String localName, String qName, Attributes a) {
					if (qName.equals("TrieNode")) { 
						String s = a.getValue("prefix");
						double freq = Double.parseDouble(a.getValue("freq"));

						if ("".equals(s)) {
							root.setFrequency(freq);
						} else { 
							List<String> list = new LinkedList<String>();
							String[] sarray = s.split("\\|");
							for (String str : sarray) 
								list.add(str);

							// put in the node and make sure to get it back in order
							// to set it's frequency
							root.put(list, 0).setFrequency(freq);
						}
					}
				}
			});
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public double getIntEntropy(List<String> sequence) {
		if (sequence == null || sequence.size() == 0)
			return internalEntropy;

		String child = sequence.get(0);
		if (!children.containsKey(child))
			return 0;

		Trie t = children.get(child);
		List<String> suffix = sequence.subList(1,sequence.size());
		return t.getIntEntropy(suffix);
	}
	
}
