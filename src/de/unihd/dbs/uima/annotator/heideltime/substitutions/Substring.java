package de.unihd.dbs.uima.annotator.heideltime.substitutions;

import java.util.regex.MatchResult;

public class Substring implements Function {
	Expression expr;
	int start;
	int end;
	
	public Substring(Expression expr, int start, int end) {
		this.expr = expr;
		this.start = start;
		this.end = end;
	}
	
	@Override
	public CharSequence evaluate(MatchResult ruleMatch) {
		return expr.evaluate(ruleMatch).subSequence(start, end);
	}
	
	@Override
	public String toString() {
		return "%SUBSTRING%(" + expr + "," + start + "," + end + ")";
	}
}
