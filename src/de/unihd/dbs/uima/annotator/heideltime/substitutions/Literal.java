package de.unihd.dbs.uima.annotator.heideltime.substitutions;

import java.util.regex.MatchResult;

public class Literal implements Expression {
	String value;
	public Literal(String value) {
		this.value = value;
	}
	
	@Override
	public CharSequence evaluate(MatchResult ruleMatch) {
		return value;
	}
	
	public String toString() {
		return value;
	}
}
