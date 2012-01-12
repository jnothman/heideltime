package de.unihd.dbs.uima.annotator.heideltime.substitutions;

import java.util.regex.MatchResult;

public class MatchGroup implements Function {
	
	int group;

	public MatchGroup(int group) {
		this.group = group;
	}
	
	@Override
	public CharSequence evaluate(MatchResult ruleMatch) {
		return ruleMatch.group(group);
	}
	
	@Override
	public String toString() {
		return "group(" + group + ")";
	}
}
