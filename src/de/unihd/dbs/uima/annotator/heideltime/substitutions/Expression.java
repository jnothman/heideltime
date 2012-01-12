package de.unihd.dbs.uima.annotator.heideltime.substitutions;

import java.util.regex.MatchResult;

public interface Expression {
	public CharSequence evaluate(MatchResult ruleMatch);
}
