package de.unihd.dbs.uima.annotator.heideltime.substitutions;

import java.util.regex.MatchResult;

public class Lowercase implements Function {
	Expression expr;
	
	public Lowercase(Expression expr) {
		this.expr = expr;
	}
	
	@Override
	public CharSequence evaluate(MatchResult ruleMatch) {
		return expr.evaluate(ruleMatch).toString().toLowerCase();
	}
	
	@Override
	public String toString() {
		return "%LOWERCASE%(" + expr + ")";
	}
}
