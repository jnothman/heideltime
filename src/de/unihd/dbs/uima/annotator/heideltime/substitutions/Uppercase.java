package de.unihd.dbs.uima.annotator.heideltime.substitutions;

import java.util.regex.MatchResult;

public class Uppercase implements Function {
	Expression expr;
	
	public Uppercase(Expression expr) {
		this.expr = expr;
	}
	
	@Override
	public CharSequence evaluate(MatchResult ruleMatch) {
		return expr.evaluate(ruleMatch).toString().toUpperCase();
	}
	
	@Override
	public String toString() {
		return "%UPPERCASE%(" + expr + ")";
	}
}
