package de.unihd.dbs.uima.annotator.heideltime.substitutions;

import java.util.Map;
import java.util.regex.MatchResult;

import org.apache.uima.UIMAFramework;
import org.apache.uima.util.Level;


public class Normalization implements Function {
	String normGroup;
	Map<String, String> lookup;
	Expression keyExpression;

	public Normalization(String normGroup, Map<String, String> lookup, Expression key) {
		this.normGroup = normGroup;
		this.lookup = lookup;
		this.keyExpression = key;
	}
	
	@Override
	public CharSequence evaluate(MatchResult ruleMatch) {
		CharSequence key = keyExpression.evaluate(ruleMatch);
		if (key == null) {
			UIMAFramework.getLogger(Normalization.class).log(Level.FINE, "Empty part to normalize in " + this);
			return "";
		}
		String res = this.lookup.get(key);
		if (res == null) {
			throw new RuntimeException("No normalization key found: \"" + key + "\" [from " + this + "]\n" + ruleMatch);
		}
		return res;
	}

	@Override
	public String toString() {
		return "%" + normGroup + "(" + keyExpression + ")";
	}
}
