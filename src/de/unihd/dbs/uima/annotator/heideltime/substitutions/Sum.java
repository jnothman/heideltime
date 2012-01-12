package de.unihd.dbs.uima.annotator.heideltime.substitutions;

import java.util.regex.MatchResult;

public class Sum implements Function {
	Expression a;
	Expression b;
	
	public Sum(Expression a, Expression b) {
		this.a = a;
		this.b = b;
	}
	
	@Override
	public CharSequence evaluate(MatchResult ruleMatch) {
		return "" + (Integer.parseInt(a.evaluate(ruleMatch).toString()) + 
				Integer.parseInt(b.evaluate(ruleMatch).toString()));
	}
	
	@Override
	public String toString() {
		return "%SUM%(" + a + "," + b + ")";
	}
}
